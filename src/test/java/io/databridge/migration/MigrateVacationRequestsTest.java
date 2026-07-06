package io.databridge.migration;

import io.databridge.context.DbConfig;
import io.databridge.context.MigrationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testa MigrateVacationRequests com dois containers PostgreSQL reais.
 * Requer Docker rodando — sem mocks, sem dialetos alternativos.
 */
@Testcontainers
class MigrateVacationRequestsTest {

    @Container
    static final PostgreSQLContainer<?> SOURCE_DB = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("source_db")
        .withUsername("postgres")
        .withPassword("postgres");

    @Container
    static final PostgreSQLContainer<?> TARGET_DB = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("target_db")
        .withUsername("postgres")
        .withPassword("postgres");

    private JdbcTemplate sourceTemplate;
    private JdbcTemplate targetTemplate;
    private MigrationContext ctx;

    @BeforeEach
    void setUp() {
        sourceTemplate = templateFor(SOURCE_DB);
        targetTemplate = templateFor(TARGET_DB);

        sourceTemplate.execute("""
            CREATE TABLE IF NOT EXISTS vacation_requests (
                id          BIGINT PRIMARY KEY,
                employee_id BIGINT,
                start_date  DATE,
                end_date    DATE,
                status      VARCHAR(20)
            )
        """);
        sourceTemplate.execute("TRUNCATE vacation_requests");

        for (int i = 1; i <= 15; i++) {
            sourceTemplate.update(
                "INSERT INTO vacation_requests VALUES (?, ?, CURRENT_DATE, CURRENT_DATE + 10, 'APPROVED')",
                i, (i % 5) + 1
            );
        }

        // Limpa destino entre testes
        targetTemplate.execute("DROP TABLE IF EXISTS vacation_requests");

        ctx = new MigrationContext(sourceTemplate, targetTemplate, DbConfig.Dialect.POSTGRES);
    }

    @Test
    void shouldMigrateAllRecords() {
        new MigrateVacationRequests().execute(ctx, 6);

        List<Map<String, Object>> result = targetTemplate.queryForList(
            "SELECT * FROM vacation_requests ORDER BY id"
        );
        assertEquals(15, result.size());
    }

    @Test
    void shouldSaveCheckpointAfterEachPage() {
        new MigrateVacationRequests().execute(ctx, 6);

        Object checkpoint = ctx.get("MigrateVacationRequests.lastOffset");
        assertNotNull(checkpoint);
        assertEquals(15, (int) checkpoint);
    }

    @Test
    void shouldSkipDuplicatesOnConflict() {
        new MigrateVacationRequests().execute(ctx, 6);

        // Segunda execução no mesmo destino — ON CONFLICT DO NOTHING deve ignorar
        MigrationContext ctx2 = new MigrationContext(sourceTemplate, targetTemplate, DbConfig.Dialect.POSTGRES);
        new MigrateVacationRequests().execute(ctx2, 6);

        List<Map<String, Object>> result = targetTemplate.queryForList("SELECT * FROM vacation_requests");
        assertEquals(15, result.size(), "Não deve duplicar registros");
    }

    // --- Helper ---

    private static JdbcTemplate templateFor(PostgreSQLContainer<?> container) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName(DbConfig.DRIVER_POSTGRES);
        ds.setUrl(container.getJdbcUrl());
        ds.setUsername(container.getUsername());
        ds.setPassword(container.getPassword());
        return new JdbcTemplate(ds);
    }
}
