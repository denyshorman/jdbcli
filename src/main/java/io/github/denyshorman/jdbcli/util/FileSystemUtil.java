package io.github.denyshorman.jdbcli.util;

import java.nio.file.Path;

public class FileSystemUtil {
    public static Path getJdbcliHome() {
        return Path.of(System.getProperty("user.home"), ".jdbcli");
    }
}
