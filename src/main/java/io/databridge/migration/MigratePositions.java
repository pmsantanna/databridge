package io.databridge.migration;

import io.databridge.context.MigrationContext;

import java.util.List;
import java.util.Map;

/**
 * Migra a tabela de cargos da origem para o destino.
 */
public class MigratePositions extends AbstractMigrationRoutine<Map<String, Object>> {

    @Override
    public String routineName() {
        return "positions";
    }

    @Override
    protected void beforeMigration(MigrationContext ctx) {
        ctx.target().execute("""
            CREATE TABLE IF NOT EXISTS positions (
                id            BIGINT PRIMARY KEY,
                title         VARCHAR(150) NOT NULL,
                level         VARCHAR(50),
                department_id BIGINT
            )
        """);
    }

    @Override
    protected List<Map<String, Object>> fetchPage(MigrationContext ctx, int offset, int limit) {
        return ctx.source().queryForList("""
            SELECT id, title, level, department_id
            FROM positions
            ORDER BY id
            LIMIT ? OFFSET ?
        """, limit, offset);
    }

    @Override
    protected void persist(List<Map<String, Object>> records, MigrationContext ctx) {
        for (Map<String, Object> r : records) {
            ctx.upsertIgnore(
                "positions",
                "id",
                new String[]{"id", "title", "level", "department_id"},
                new Object[]{r.get("id"), r.get("title"), r.get("level"), r.get("department_id")}
            );
        }
    }
}
