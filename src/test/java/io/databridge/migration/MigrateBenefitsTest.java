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
 * Testa MigrateBenefits com dois containers PostgreSQL reais.
 * Requer Docker rodando — sem mocks, sem dialetos alternativos.
 */
@Testcontainers
class MigrateBenefitsTest {

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
            CREATE TABLE IF NOT EXISTS benefits (
                id           BIGINT PRIMARY KEY,
                name         VARCHAR(150),
                category     VARCHAR(50),
                monthly_cost NUMERIC(10,2)
            )
        """);
        sourceTemplate.execute("TRUNCATE benefits");

        for (int i = 1; i <= 8; i++) {
            sourceTemplate.update(
                "INSERT INTO benefits VALUES (?, ?, ?, ?)",
                i, "Benefit " + i, "Category " + (i % 2), 150.00
            );
        }

        // Limpa destino entre testes
        targetTemplate.execute("DROP TABLE IF EXISTS benefits");

        ctx = new MigrationContext(sourceTemplate, targetTemplate, DbConfig.Dialect.POSTGRES);
    }

    @Test
    void shouldMigrateAllRecords() {
        new MigrateBenefits().execute(ctx, 3);

        List<Map<String, Object>> result = targetTemplate.queryForList(
            "SELECT * FROM benefits ORDER BY id"
        );
        assertEquals(8, result.size());
    }

    @Test
    void shouldSaveCheckpointAfterEachPage() {
        new MigrateBenefits().execute(ctx, 3);

        Object checkpoint = ctx.get("MigrateBenefits.lastOffset");
        assertNotNull(checkpoint);
        assertEquals(8, (int) checkpoint);
    }

    @Test
    void shouldSkipDuplicatesOnConflict() {
        new MigrateBenefits().execute(ctx, 3);

        // Segunda execução no mesmo destino — ON CONFLICT DO NOTHING deve ignorar
        MigrationContext ctx2 = new MigrationContext(sourceTemplate, targetTemplate, DbConfig.Dialect.POSTGRES);
        new MigrateBenefits().execute(ctx2, 3);

        List<Map<String, Object>> result = targetTemplate.queryForList("SELECT * FROM benefits");
        assertEquals(8, result.size(), "Não deve duplicar registros");
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
