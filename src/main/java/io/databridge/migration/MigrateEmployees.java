package io.databridge.migration;

import io.databridge.context.MigrationContext;

import java.util.List;
import java.util.Map;

/**
 * Exemplo de rotina: migra a tabela de funcionários da origem para o destino.
 *
 * Demonstra o padrão mínimo de implementação do framework:
 * - fetchPage  → SELECT paginado na origem
 * - persist    → INSERT em lote no destino
 */
public class MigrateEmployees extends AbstractMigrationRoutine<Map<String, Object>> {

    @Override
    public String routineName() {
        return "employees";
    }

    @Override
    protected void beforeMigration(MigrationContext ctx) {
        ctx.target().execute("""
            CREATE TABLE IF NOT EXISTS employees (
                id         BIGINT PRIMARY KEY,
                name       VARCHAR(200) NOT NULL,
                department VARCHAR(100),
                hired_at   DATE,
                active     BOOLEAN DEFAULT TRUE
            )
        """);
    }

    @Override
    protected List<Map<String, Object>> fetchPage(MigrationContext ctx, int offset, int limit) {
        return ctx.source().queryForList("""
            SELECT id, name, department, hired_at, active
            FROM employees
            ORDER BY id
            LIMIT ? OFFSET ?
        """, limit, offset);
    }

    @Override
    protected void persist(List<Map<String, Object>> records, MigrationContext ctx) {
        for (Map<String, Object> r : records) {
            ctx.upsertIgnore(
                "employees",
                "id",
                new String[]{"id", "name", "department", "hired_at", "active"},
                new Object[]{r.get("id"), r.get("name"), r.get("department"), r.get("hired_at"), r.get("active")}
            );
        }
    }
}
