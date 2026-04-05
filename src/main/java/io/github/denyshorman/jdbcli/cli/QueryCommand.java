package io.github.denyshorman.jdbcli.cli;

import io.github.denyshorman.jdbcli.exception.JdbcliException;
import io.github.denyshorman.jdbcli.execution.JdbcExecutor;
import io.github.denyshorman.jdbcli.format.OutputFormatterFactory;
import io.github.denyshorman.jdbcli.profile.ProfileLoader;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;

@Command(
        name = "query",
        description = {
                "Execute a SQL statement against a saved connection profile.",
                "",
                "Supports SELECT, DML (INSERT, UPDATE, DELETE), DDL (CREATE, ALTER, DROP),",
                "and database-specific blocks (PL/SQL, T-SQL, etc.).",
                "For SELECT, results are streamed as rows. For everything else, the number",
                "of affected rows is returned.",
                "",
                "Results are written to stdout; logs and errors go to stderr.",
                "",
                "Output formats:",
                "  json  (default) — newline-terminated JSON object, pipe-friendly with jq",
                "  csv             — RFC 4180 CSV with a header row",
                "  table           — human-readable fixed-width table",
                "",
                "Examples:",
                "  jdbcli query --profile dev --sql \"SELECT * FROM users LIMIT 10\"",
                "  jdbcli query --profile dev --file report.sql --format csv > out.csv",
                "  jdbcli query --profile dev --sql \"SELECT 1\" --format json > out.json",
        },
        mixinStandardHelpOptions = true
)
public class QueryCommand implements Runnable {
    @Option(names = "--profile", required = true, paramLabel = "<name>", description = "Name of the connection profile to use")
    String profileName;

    @ArgGroup(multiplicity = "1")
    QuerySource querySource;

    static class QuerySource {
        @Option(names = "--sql", description = "SQL query string to execute")
        @Nullable
        String sql;

        @Option(names = "--file", description = "Path to a file containing the SQL query to execute")
        @Nullable
        String file;
    }

    @Option(names = "--format", description = "Output format: json (default), csv, table", defaultValue = "json")
    String format;

    @Override
    public void run() {
        try {
            var profile = ProfileLoader.load(profileName);

            if (profile == null) {
                throw new JdbcliException("Profile not found");
            }

            var query = querySource.sql;

            if (query == null) {
                query = Files.readString(Path.of(querySource.file));
            }

            JdbcExecutor.execute(profile, query, OutputFormatterFactory.getFormatter(format));
        } catch (Exception e) {
            throw new JdbcliException("Operation failed: " + e.getMessage(), e);
        }
    }
}
