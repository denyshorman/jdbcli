package io.github.denyshorman.jdbcli.util;

import java.nio.file.Path;

public class FileSystemUtil {
    public static Path getJdbcliHome() {
        var override = System.getProperty("jdbcli.home");

        if (override != null) {
            return Path.of(override);
        }

        return Path.of(System.getProperty("user.home"), ".jdbcli");
    }
}
