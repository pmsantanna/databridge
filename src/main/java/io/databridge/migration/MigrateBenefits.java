package io.databridge.migration;

import io.databridge.context.MigrationContext;

import java.util.List;
import java.util.Map;

/**
 * Migra o catálogo de benefícios da origem para o destino.
 */
public class MigrateBenefits extends AbstractMigrationRoutine<Map<String, Object>> {

    @Override
    public String routineName() {
        return "benefits";
    }

    @Override
    protected void beforeMigration(MigrationContext ctx) {
        ctx.target().execute("""
            CREATE TABLE IF NOT EXISTS benefits (
                id           BIGINT PRIMARY KEY,
                name         VARCHAR(150) NOT NULL,
                category     VARCHAR(50),
                monthly_cost NUMERIC(10,2)
            )
        """);
    }

    @Override
    protected List<Map<String, Object>> fetchPage(MigrationContext ctx, int offset, int limit) {
        return ctx.source().queryForList("""
            SELECT id, name, category, monthly_cost
            FROM benefits
            ORDER BY id
            LIMIT ? OFFSET ?
        """, limit, offset);
    }

    @Override
    protected void persist(List<Map<String, Object>> records, MigrationContext ctx) {
        for (Map<String, Object> r : records) {
            ctx.upsertIgnore(
                "benefits",
                "id",
                new String[]{"id", "name", "category", "monthly_cost"},
                new Object[]{r.get("id"), r.get("name"), r.get("category"), r.get("monthly_cost")}
            );
        }
    }
}
