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
 * Testa MigrateDepartments com dois containers PostgreSQL reais.
 * Requer Docker rodando — sem mocks, sem dialetos alternativos.
 */
@Testcontainers
class MigrateDepartmentsTest {

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
            CREATE TABLE IF NOT EXISTS departments (
                id          BIGINT PRIMARY KEY,
                name        VARCHAR(150),
                cost_center VARCHAR(50),
                active      BOOLEAN
            )
        """);
        sourceTemplate.execute("TRUNCATE departments");

        for (int i = 1; i <= 12; i++) {
            sourceTemplate.update(
                "INSERT INTO departments VALUES (?, ?, ?, TRUE)",
                i, "Department " + i, "CC-" + (100 + i)
            );
        }

        // Limpa destino entre testes
        targetTemplate.execute("DROP TABLE IF EXISTS departments");

        ctx = new MigrationContext(sourceTemplate, targetTemplate, DbConfig.Dialect.POSTGRES);
    }

    @Test
    void shouldMigrateAllRecords() {
        new MigrateDepartments().execute(ctx, 5);

        List<Map<String, Object>> result = targetTemplate.queryForList(
            "SELECT * FROM departments ORDER BY id"
        );
        assertEquals(12, result.size());
    }

    @Test
    void shouldSaveCheckpointAfterEachPage() {
        new MigrateDepartments().execute(ctx, 5);

        Object checkpoint = ctx.get("MigrateDepartments.lastOffset");
        assertNotNull(checkpoint);
        assertEquals(12, (int) checkpoint);
    }

    @Test
    void shouldSkipDuplicatesOnConflict() {
        new MigrateDepartments().execute(ctx, 5);

        // Segunda execução no mesmo destino — ON CONFLICT DO NOTHING deve ignorar
        MigrationContext ctx2 = new MigrationContext(sourceTemplate, targetTemplate, DbConfig.Dialect.POSTGRES);
        new MigrateDepartments().execute(ctx2, 5);

        List<Map<String, Object>> result = targetTemplate.queryForList("SELECT * FROM departments");
        assertEquals(12, result.size(), "Não deve duplicar registros");
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
