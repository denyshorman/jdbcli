package io.github.denyshorman.jdbcli.format;

import io.github.denyshorman.jdbcli.exception.JdbcliException;
import org.jspecify.annotations.Nullable;

public class OutputFormatterFactory {
    public static OutputFormatter getFormatter(@Nullable String format) {
        if (format == null || format.isBlank()) {
            return new JsonOutputFormatter();
        }

        return switch (format.toLowerCase()) {
            case "json" -> new JsonOutputFormatter();
            case "csv" -> new CsvOutputFormatter();
            case "table" -> new TableOutputFormatter();
            default -> throw new JdbcliException("Unsupported output format: " + format);
        };
    }
}
