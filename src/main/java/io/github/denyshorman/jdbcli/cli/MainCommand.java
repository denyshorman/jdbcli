package io.github.denyshorman.jdbcli.cli;

import io.github.denyshorman.jdbcli.exception.JdbcliException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "jdbcli",
        version = "1.0",
        description = {
                "jdbcli — profile-driven database access over JDBC.",
                "",
                "Quick-start workflow:",
                "  1. jdbcli db init --name mydb        # scaffold a custom DB descriptor",
                "  2. jdbcli driver install --db mydb   # download the JDBC driver",
                "  3. jdbcli profile init --db mydb --name dev   # create a connection profile",
                "  4. jdbcli query --profile dev --sql \"SELECT 1\"  # run a query",
                "",
                "To upgrade jdbcli itself: jdbcli upgrade",
                "Set -Djdbcli.home=<path> to use a custom home directory instead of ~/.jdbcli.",
                "Set JDBCLI_DEBUG=1 to print full stack traces on error.",
        },
        subcommands = {
                DbCommand.class,
                DriverCommand.class,
                ProfileCommand.class,
                QueryCommand.class,
                UpgradeCommand.class,
        },
        mixinStandardHelpOptions = true
)
public class MainCommand implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MainCommand.class);

    public static void main(String[] args) {
        var cmd = new CommandLine(new MainCommand());

        cmd.setExecutionExceptionHandler((ex, cl, pr) -> {
            if (ex instanceof JdbcliException) {
                if (System.getenv("JDBCLI_DEBUG") != null) {
                    LOGGER.error("Error: {}", ex.getMessage(), ex);
                } else {
                    LOGGER.error("Error: {}", ex.getMessage());
                }
            } else {
                LOGGER.error("An unexpected error occurred", ex);
            }

            return 1;
        });

        var exitCode = cmd.execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
