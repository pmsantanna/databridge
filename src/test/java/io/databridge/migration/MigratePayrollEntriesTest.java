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
 * Testa MigratePayrollEntries com dois containers PostgreSQL reais.
 * Requer Docker rodando — sem mocks, sem dialetos alternativos.
 */
@Testcontainers
class MigratePayrollEntriesTest {

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
            CREATE TABLE IF NOT EXISTS payroll_entries (
                id          BIGINT PRIMARY KEY,
                employee_id BIGINT,
                period      VARCHAR(7),
                gross_pay   NUMERIC(12,2),
                net_pay     NUMERIC(12,2),
                paid_at     DATE
            )
        """);
        sourceTemplate.execute("TRUNCATE payroll_entries");

        for (int i = 1; i <= 18; i++) {
            sourceTemplate.update(
                "INSERT INTO payroll_entries VALUES (?, ?, '2026-06', ?, ?, CURRENT_DATE)",
                i, (i % 5) + 1, 5000.00, 4200.00
            );
        }

        // Limpa destino entre testes
        targetTemplate.execute("DROP TABLE IF EXISTS payroll_entries");

        ctx = new MigrationContext(sourceTemplate, targetTemplate, DbConfig.Dialect.POSTGRES);
    }

    @Test
    void shouldMigrateAllRecords() {
        new MigratePayrollEntries().execute(ctx, 7);

        List<Map<String, Object>> result = targetTemplate.queryForList(
            "SELECT * FROM payroll_entries ORDER BY id"
        );
        assertEquals(18, result.size());
    }

    @Test
    void shouldSaveCheckpointAfterEachPage() {
        new MigratePayrollEntries().execute(ctx, 7);

        Object checkpoint = ctx.get("MigratePayrollEntries.lastOffset");
        assertNotNull(checkpoint);
        assertEquals(18, (int) checkpoint);
    }

    @Test
    void shouldSkipDuplicatesOnConflict() {
        new MigratePayrollEntries().execute(ctx, 7);

        // Segunda execução no mesmo destino — ON CONFLICT DO NOTHING deve ignorar
        MigrationContext ctx2 = new MigrationContext(sourceTemplate, targetTemplate, DbConfig.Dialect.POSTGRES);
        new MigratePayrollEntries().execute(ctx2, 7);

        List<Map<String, Object>> result = targetTemplate.queryForList("SELECT * FROM payroll_entries");
        assertEquals(18, result.size(), "Não deve duplicar registros");
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
