package io.databridge.cli;

import io.databridge.context.DbConfig;
import io.databridge.context.MigrationContext;
import io.databridge.migration.MigrateBenefits;
import io.databridge.migration.MigrateDepartments;
import io.databridge.migration.MigrateEmployees;
import io.databridge.migration.MigratePayrollEntries;
import io.databridge.migration.MigratePositions;
import io.databridge.migration.MigrateVacationRequests;
import io.databridge.util.ProgressPrinter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * Ponto de entrada da CLI.
 *
 * Uso:
 *   java -jar databridge.jar migrate --help
 *   java -jar databridge.jar migrate --src-url jdbc:postgresql://... --tgt-url jdbc:postgresql://...
 */
@Command(
    name = "databridge",
    mixinStandardHelpOptions = true,
    version = "DataBridge 1.0.0",
    description = "A type-safe, resumable data migration framework for Java enterprise systems.",
    subcommands = {
        DataBridgeCLI.MigrateCommand.class,
        DataBridgeCLI.StatusCommand.class,
        CommandLine.HelpCommand.class
    }
)
public class DataBridgeCLI implements Runnable {

    @Override
    public void run() {
        // Sem subcomando → mostra o help
        CommandLine.usage(this, System.out);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new DataBridgeCLI()).execute(args);
        System.exit(exitCode);
    }

    // -------------------------------------------------------------------------
    // Subcomando: migrate
    // -------------------------------------------------------------------------

    @Command(
        name = "migrate",
        description = "Executes migration routines from source to target database.",
        mixinStandardHelpOptions = true
    )
    static class MigrateCommand implements Callable<Integer> {

        @Option(names = "--src-url",  required = true,  description = "Source JDBC URL")
        String srcUrl;

        @Option(names = "--src-user", defaultValue = "postgres", description = "Source DB username")
        String srcUser;

        @Option(names = "--src-pass", defaultValue = "",         description = "Source DB password", interactive = true, echo = false)
        String srcPass;

        @Option(names = "--tgt-url",  required = true,  description = "Target JDBC URL")
        String tgtUrl;

        @Option(names = "--tgt-user", defaultValue = "postgres", description = "Target DB username")
        String tgtUser;

        @Option(names = "--tgt-pass", defaultValue = "",         description = "Target DB password", interactive = true, echo = false)
        String tgtPass;

        @Option(names = {"-p", "--page-size"}, defaultValue = "500", description = "Records per page (default: 500)")
        int pageSize;

        @Option(names = {"-r", "--routine"}, description = "Run only this routine (ex: employees, departments, payroll_entries, positions, benefits, vacation_requests). Runs all if omitted.")
        String routine;

        @Override
        public Integer call() {
            ProgressPrinter.printHeader("DataBridge Migration Starting");

            MigrationContext ctx = MigrationContext.fromConfig(
                    DbConfig.postgres(srcUrl, srcUser, srcPass),
                    DbConfig.postgres(tgtUrl, tgtUser, tgtPass),
                    true
            );

            try {
                runRoutines(ctx);
                printSummary(ctx);
                return 0;
            } catch (Exception e) {
                ProgressPrinter.printError("Migration failed: " + e.getMessage());
                return 1;
            }
        }

        private void runRoutines(MigrationContext ctx) {
            // Adicione novas rotinas aqui conforme o projeto crescer
            if (routine == null || routine.equalsIgnoreCase("departments")) {
                new MigrateDepartments().execute(ctx, pageSize);
            }
            if (routine == null || routine.equalsIgnoreCase("employees")) {
                new MigrateEmployees().execute(ctx, pageSize);
            }
            if (routine == null || routine.equalsIgnoreCase("payroll_entries")) {
                new MigratePayrollEntries().execute(ctx, pageSize);
            }
            if (routine == null || routine.equalsIgnoreCase("positions")) {
                new MigratePositions().execute(ctx, pageSize);
            }
            if (routine == null || routine.equalsIgnoreCase("benefits")) {
                new MigrateBenefits().execute(ctx, pageSize);
            }
            if (routine == null || routine.equalsIgnoreCase("vacation_requests")) {
                new MigrateVacationRequests().execute(ctx, pageSize);
            }
        }

        private void printSummary(MigrationContext ctx) {
            System.out.println();
            ProgressPrinter.printSuccess(String.format(
                "Done! fetched=%d  inserted=%d  skipped=%d",
                ctx.totalFetched(), ctx.totalInserted(), ctx.totalSkipped()
            ));
        }
    }

    // -------------------------------------------------------------------------
    // Subcomando: status (placeholder — mostra checkpoints salvos)
    // -------------------------------------------------------------------------

    @Command(
        name = "status",
        description = "Shows checkpoint status from the last migration run.",
        mixinStandardHelpOptions = true
    )
    static class StatusCommand implements Callable<Integer> {

        @Override
        public Integer call() {
            // TODO: ler checkpoints persistidos em arquivo/banco e exibir
            System.out.println("No checkpoint data found. Run 'migrate' first.");
            return 0;
        }
    }
}
