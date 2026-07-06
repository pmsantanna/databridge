package io.databridge.migration;

import io.databridge.context.MigrationContext;

import java.util.List;
import java.util.Map;

/**
 * Migra os lançamentos de folha de pagamento da origem para o destino.
 */
public class MigratePayrollEntries extends AbstractMigrationRoutine<Map<String, Object>> {

    @Override
    public String routineName() {
        return "payroll_entries";
    }

    @Override
    protected void beforeMigration(MigrationContext ctx) {
        ctx.target().execute("""
            CREATE TABLE IF NOT EXISTS payroll_entries (
                id          BIGINT PRIMARY KEY,
                employee_id BIGINT NOT NULL,
                period      VARCHAR(7) NOT NULL,
                gross_pay   NUMERIC(12,2),
                net_pay     NUMERIC(12,2),
                paid_at     DATE
            )
        """);
    }

    @Override
    protected List<Map<String, Object>> fetchPage(MigrationContext ctx, int offset, int limit) {
        return ctx.source().queryForList("""
            SELECT id, employee_id, period, gross_pay, net_pay, paid_at
            FROM payroll_entries
            ORDER BY id
            LIMIT ? OFFSET ?
        """, limit, offset);
    }

    @Override
    protected void persist(List<Map<String, Object>> records, MigrationContext ctx) {
        for (Map<String, Object> r : records) {
            ctx.upsertIgnore(
                "payroll_entries",
                "id",
                new String[]{"id", "employee_id", "period", "gross_pay", "net_pay", "paid_at"},
                new Object[]{r.get("id"), r.get("employee_id"), r.get("period"), r.get("gross_pay"), r.get("net_pay"), r.get("paid_at")}
            );
        }
    }
}
