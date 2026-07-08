package io.databridge.migration;

import io.databridge.audit.AuditRepository;
import io.databridge.audit.MigrationRun;
import io.databridge.context.MigrationContext;
import io.databridge.util.ProgressPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Base de toda rotina de migração.
 *
 * <p>Subclasses precisam implementar apenas dois métodos:
 * <ul>
 *   <li>{@link #fetchPage} — busca uma página de registros na origem</li>
 *   <li>{@link #persist}   — persiste a lista no destino</li>
 * </ul>
 *
 * <p>Paginação, checkpointing, logging e auditoria são gerenciados aqui.
 * Se um {@link AuditRepository} estiver presente no contexto, cada execução
 * é registrada automaticamente na tabela {@code migration_runs}.
 *
 * @param <T> tipo do registro sendo migrado (ex: Employee, PayrollEntry)
 */
public abstract class AbstractMigrationRoutine<T> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private String checkpointKey() {
        return getClass().getSimpleName() + ".lastOffset";
    }

    public abstract String routineName();

    protected abstract List<T> fetchPage(MigrationContext ctx, int offset, int limit);

    protected abstract void persist(List<T> records, MigrationContext ctx);

    protected void beforeMigration(MigrationContext ctx) {}

    protected void afterMigration(MigrationContext ctx) {}

    /**
     * Executa a migração completa com paginação, checkpointing e auditoria.
     *
     * @param ctx      contexto da migração
     * @param pageSize quantos registros buscar por página
     */
    public final void execute(MigrationContext ctx, int pageSize) {
        log.info("=== Iniciando: {} ===", routineName());

        // --- auditoria: abre o run ---
        AuditRepository audit = ctx.auditRepository();
        MigrationRun run = null;
        if (audit != null) {
            run = audit.start(new MigrationRun(routineName()));
        }

        // Retoma do checkpoint se houver
        int offset = ctx.has(checkpointKey())
            ? (int) ctx.get(checkpointKey())
            : 0;

        if (offset > 0) {
            log.info("Retomando do checkpoint: offset={}", offset);
        }

        beforeMigration(ctx);

        ProgressPrinter progress = new ProgressPrinter(routineName());
        int page = 0;

        try {
            while (true) {
                List<T> records = fetchPage(ctx, offset, pageSize);

                if (records.isEmpty()) break;

                persist(records, ctx);

                ctx.incrementFetched(records.size());
                ctx.incrementInserted(records.size());
                ctx.set(checkpointKey(), offset + records.size());

                offset += records.size();
                page++;
                progress.print(page, offset);

                // --- auditoria: atualiza progresso a cada página ---
                if (audit != null && run != null) {
                    run.setFetched(ctx.totalFetched());
                    run.setInserted(ctx.totalInserted());
                    run.setSkipped(ctx.totalSkipped());
                    audit.updateProgress(run);
                }

                if (records.size() < pageSize) break;
            }

            afterMigration(ctx);

            // --- auditoria: marca como DONE ---
            if (audit != null && run != null) {
                run.setFetched(ctx.totalFetched());
                run.setInserted(ctx.totalInserted());
                run.setSkipped(ctx.totalSkipped());
                audit.finish(run);
            }

            log.info("=== Concluído: {} | {} registros migrados ===",
                routineName(), ctx.totalInserted());

        } catch (Exception e) {
            log.error("Erro na rotina '{}' no offset {}: {}", routineName(), offset, e.getMessage());
            log.info("Checkpoint salvo. Rode novamente para retomar.");

            // --- auditoria: marca como FAILED ---
            if (audit != null && run != null) {
                run.setFetched(ctx.totalFetched());
                run.setInserted(ctx.totalInserted());
                run.setSkipped(ctx.totalSkipped());
                audit.fail(run, e.getMessage());
            }

            throw new MigrationException("Falha em " + routineName(), e);
        }
    }
}
