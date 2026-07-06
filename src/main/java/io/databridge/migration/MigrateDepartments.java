package io.databridge.migration;

import io.databridge.context.MigrationContext;

import java.util.List;
import java.util.Map;

/**
 * Migra a tabela de departamentos da origem para o destino.
 */
public class MigrateDepartments extends AbstractMigrationRoutine<Map<String, Object>> {

    @Override
    public String routineName() {
        return "departments";
    }

    @Override
    protected void beforeMigration(MigrationContext ctx) {
        ctx.target().execute("""
            CREATE TABLE IF NOT EXISTS departments (
                id       BIGINT PRIMARY KEY,
                name     VARCHAR(150) NOT NULL,
                cost_center VARCHAR(50),
                active   BOOLEAN DEFAULT TRUE
            )
        """);
    }

    @Override
    protected List<Map<String, Object>> fetchPage(MigrationContext ctx, int offset, int limit) {
        return ctx.source().queryForList("""
            SELECT id, name, cost_center, active
            FROM departments
            ORDER BY id
            LIMIT ? OFFSET ?
        """, limit, offset);
    }

    @Override
    protected void persist(List<Map<String, Object>> records, MigrationContext ctx) {
        for (Map<String, Object> r : records) {
            ctx.upsertIgnore(
                "departments",
                "id",
                new String[]{"id", "name", "cost_center", "active"},
                new Object[]{r.get("id"), r.get("name"), r.get("cost_center"), r.get("active")}
            );
        }
    }
}
