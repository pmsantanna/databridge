package io.databridge.migration;

import io.databridge.context.MigrationContext;

import java.util.List;
import java.util.Map;

/**
 * Migra os pedidos de férias da origem para o destino.
 */
public class MigrateVacationRequests extends AbstractMigrationRoutine<Map<String, Object>> {

    @Override
    public String routineName() {
        return "vacation_requests";
    }

    @Override
    protected void beforeMigration(MigrationContext ctx) {
        ctx.target().execute("""
            CREATE TABLE IF NOT EXISTS vacation_requests (
                id          BIGINT PRIMARY KEY,
                employee_id BIGINT NOT NULL,
                start_date  DATE NOT NULL,
                end_date    DATE NOT NULL,
                status      VARCHAR(20) DEFAULT 'PENDING'
            )
        """);
    }

    @Override
    protected List<Map<String, Object>> fetchPage(MigrationContext ctx, int offset, int limit) {
        return ctx.source().queryForList("""
            SELECT id, employee_id, start_date, end_date, status
            FROM vacation_requests
            ORDER BY id
            LIMIT ? OFFSET ?
        """, limit, offset);
    }

    @Override
    protected void persist(List<Map<String, Object>> records, MigrationContext ctx) {
        for (Map<String, Object> r : records) {
            ctx.upsertIgnore(
                "vacation_requests",
                "id",
                new String[]{"id", "employee_id", "start_date", "end_date", "status"},
                new Object[]{r.get("id"), r.get("employee_id"), r.get("start_date"), r.get("end_date"), r.get("status")}
            );
        }
    }
}
