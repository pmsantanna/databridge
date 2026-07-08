package io.databridge.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Persiste o estado de execuções na tabela {@code migration_runs}.
 *
 * <p>A tabela é criada automaticamente na primeira execução se não existir.
 * Usa um JdbcTemplate separado — pode apontar para o banco destino,
 * para o banco origem, ou para um banco de controle dedicado.
 */
public class AuditRepository {

    private final JdbcTemplate jdbc;

    public AuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        createTableIfAbsent();
    }

    // --- DDL ---

    private void createTableIfAbsent() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS migration_runs (
                id          BIGSERIAL PRIMARY KEY,
                routine     VARCHAR(100)  NOT NULL,
                status      VARCHAR(20)   NOT NULL DEFAULT 'RUNNING',
                fetched     INT           NOT NULL DEFAULT 0,
                inserted    INT           NOT NULL DEFAULT 0,
                skipped     INT           NOT NULL DEFAULT 0,
                started_at  TIMESTAMP     NOT NULL,
                finished_at TIMESTAMP,
                error_msg   TEXT
            )
        """);
    }

    // --- Ciclo de vida de uma execução ---

    /**
     * Registra o início de uma rotina. Retorna o run com o ID gerado.
     */
    public MigrationRun start(MigrationRun run) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO migration_runs (routine, status, started_at) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, run.routine());
            ps.setString(2, run.status().name());
            ps.setTimestamp(3, Timestamp.valueOf(run.startedAt()));
            return ps;
        }, keys);

        Number id = (Number) keys.getKeys().get("id");
        run.setId(id.longValue());
        return run;
    }

    /**
     * Atualiza contadores no meio da execução (chamado a cada página).
     */
    public void updateProgress(MigrationRun run) {
        jdbc.update("""
            UPDATE migration_runs
               SET fetched = ?, inserted = ?, skipped = ?
             WHERE id = ?
            """,
            run.fetched(), run.inserted(), run.skipped(), run.id()
        );
    }

    /**
     * Marca a execução como DONE.
     */
    public void finish(MigrationRun run) {
        run.setStatus(MigrationRun.Status.DONE);
        run.setFinishedAt(LocalDateTime.now());
        jdbc.update("""
            UPDATE migration_runs
               SET status = ?, fetched = ?, inserted = ?, skipped = ?, finished_at = ?
             WHERE id = ?
            """,
            run.status().name(),
            run.fetched(), run.inserted(), run.skipped(),
            Timestamp.valueOf(run.finishedAt()),
            run.id()
        );
    }

    /**
     * Marca a execução como FAILED com mensagem de erro.
     */
    public void fail(MigrationRun run, String errorMsg) {
        run.setStatus(MigrationRun.Status.FAILED);
        run.setFinishedAt(LocalDateTime.now());
        run.setErrorMsg(errorMsg);
        jdbc.update("""
            UPDATE migration_runs
               SET status = ?, fetched = ?, inserted = ?, skipped = ?,
                   finished_at = ?, error_msg = ?
             WHERE id = ?
            """,
            run.status().name(),
            run.fetched(), run.inserted(), run.skipped(),
            Timestamp.valueOf(run.finishedAt()),
            errorMsg,
            run.id()
        );
    }
}
