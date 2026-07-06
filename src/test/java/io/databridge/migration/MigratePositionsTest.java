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
 * Testa MigratePositions com dois containers PostgreSQL reais.
 * Requer Docker rodando — sem mocks, sem dialetos alternativos.
 */
@Testcontainers
class MigratePositionsTest {

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
            CREATE TABLE IF NOT EXISTS positions (
                id            BIGINT PRIMARY KEY,
                title         VARCHAR(150),
                level         VARCHAR(50),
                department_id BIGINT
            )
        """);
        sourceTemplate.execute("TRUNCATE positions");

        for (int i = 1; i <= 10; i++) {
            sourceTemplate.update(
                "INSERT INTO positions VALUES (?, ?, ?, ?)",
                i, "Position " + i, "L" + ((i % 3) + 1), (i % 4) + 1
            );
        }

        // Limpa destino entre testes
        targetTemplate.execute("DROP TABLE IF EXISTS positions");

        ctx = new MigrationContext(sourceTemplate, targetTemplate, DbConfig.Dialect.POSTGRES);
    }

    @Test
    void shouldMigrateAllRecords() {
        new MigratePositions().execute(ctx, 4);

        List<Map<String, Object>> result = targetTemplate.queryForList(
            "SELECT * FROM positions ORDER BY id"
        );
        assertEquals(10, result.size());
    }

    @Test
    void shouldSaveCheckpointAfterEachPage() {
        new MigratePositions().execute(ctx, 4);

        Object checkpoint = ctx.get("MigratePositions.lastOffset");
        assertNotNull(checkpoint);
        assertEquals(10, (int) checkpoint);
    }

    @Test
    void shouldSkipDuplicatesOnConflict() {
        new MigratePositions().execute(ctx, 4);

        // Segunda execução no mesmo destino — ON CONFLICT DO NOTHING deve ignorar
        MigrationContext ctx2 = new MigrationContext(sourceTemplate, targetTemplate, DbConfig.Dialect.POSTGRES);
        new MigratePositions().execute(ctx2, 4);

        List<Map<String, Object>> result = targetTemplate.queryForList("SELECT * FROM positions");
        assertEquals(10, result.size(), "Não deve duplicar registros");
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
