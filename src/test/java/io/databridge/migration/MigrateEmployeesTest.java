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
 * Testa MigrateEmployees com dois containers PostgreSQL reais.
 * Requer Docker rodando — sem mocks, sem dialetos alternativos.
 */
@Testcontainers
class MigrateEmployeesTest {

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
            CREATE TABLE IF NOT EXISTS employees (
                id         BIGINT PRIMARY KEY,
                name       VARCHAR(200),
                department VARCHAR(100),
                hired_at   DATE,
                active     BOOLEAN
            )
        """);
        sourceTemplate.execute("TRUNCATE employees");

        for (int i = 1; i <= 25; i++) {
            sourceTemplate.update(
                "INSERT INTO employees VALUES (?, ?, ?, CURRENT_DATE, TRUE)",
                i, "Employee " + i, "Dept " + (i % 3)
            );
        }

        // Limpa destino entre testes
        targetTemplate.execute("DROP TABLE IF EXISTS employees");

        ctx = new MigrationContext(sourceTemplate, targetTemplate, DbConfig.Dialect.POSTGRES);
    }

    @Test
    void shouldMigrateAllRecords() {
        new MigrateEmployees().execute(ctx, 10);

        List<Map<String, Object>> result = targetTemplate.queryForList(
            "SELECT * FROM employees ORDER BY id"
        );
        assertEquals(25, result.size());
    }

    @Test
    void shouldIncrementContextCounters() {
        new MigrateEmployees().execute(ctx, 10);

        assertEquals(25, ctx.totalFetched());
        assertEquals(25, ctx.totalInserted());
    }

    @Test
    void shouldSaveCheckpointAfterEachPage() {
        new MigrateEmployees().execute(ctx, 10);

        Object checkpoint = ctx.get("MigrateEmployees.lastOffset");
        assertNotNull(checkpoint);
        assertEquals(25, (int) checkpoint);
    }

    @Test
    void shouldSkipDuplicatesOnConflict() {
        new MigrateEmployees().execute(ctx, 10);

        // Segunda execução no mesmo destino — ON CONFLICT DO NOTHING deve ignorar
        MigrationContext ctx2 = new MigrationContext(sourceTemplate, targetTemplate, DbConfig.Dialect.POSTGRES);
        new MigrateEmployees().execute(ctx2, 10);

        List<Map<String, Object>> result = targetTemplate.queryForList("SELECT * FROM employees");
        assertEquals(25, result.size(), "Não deve duplicar registros");
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
