package io.github.denyshorman.jdbcli.execution;

import io.github.denyshorman.jdbcli.config.AuthConfig;
import io.github.denyshorman.jdbcli.config.Profile;
import io.github.denyshorman.jdbcli.exception.JdbcliException;
import io.github.denyshorman.jdbcli.format.OutputFormatter;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Driver;
import java.util.*;

public class JdbcExecutor {
    public static void execute(Profile profile, String sql, OutputFormatter formatter) throws Exception {
        var jar = Path.of(profile.driver().jar());

        if (!Files.exists(jar)) {
            throw new JdbcliException(
                    "Driver JAR not found: " + jar + ". " +
                            "Run 'jdbcli driver install --db " + profile.dbId() + "' to download it."
            );
        }

        try (var classLoader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, ClassLoader.getSystemClassLoader())) {
            executeQueryWithClassLoader(classLoader, profile, sql, formatter);
        }
    }

    private static void executeQueryWithClassLoader(
            URLClassLoader classLoader,
            Profile profile,
            String sql,
            OutputFormatter formatter
    ) throws Exception {
        var driver = (Driver) Class.forName(profile.driver().className(), true, classLoader).getDeclaredConstructor().newInstance();
        var props = new Properties();

        if (profile.auth() != null) {
            String u;
            String p;

            switch (profile.auth()) {
                case AuthConfig.Env env -> {
                    u = System.getenv(env.usernameEnv());
                    p = System.getenv(env.passwordEnv());
                }
                case AuthConfig.Plain plain -> {
                    u = plain.username();
                    p = plain.password();
                }
            }

            if (u != null) props.setProperty("user", u);
            if (p != null) props.setProperty("password", p);
        }

        if (profile.properties() != null) {
            props.putAll(profile.properties());
        }

        try (var conn = driver.connect(profile.jdbcUrl(), props); var stmt = conn.createStatement()) {
            if (profile.safety() != null) {
                if (profile.safety().readOnly() != null && profile.safety().readOnly()) {
                    conn.setReadOnly(true);
                }

                if (profile.safety().maxRows() != null && profile.safety().maxRows() > 0) {
                    stmt.setMaxRows(profile.safety().maxRows());
                }

                if (profile.safety().timeoutSeconds() != null && profile.safety().timeoutSeconds() > 0) {
                    stmt.setQueryTimeout(profile.safety().timeoutSeconds());
                }
            }

            var hasResultSet = stmt.execute(sql);

            if (hasResultSet) {
                try (var rs = stmt.getResultSet()) {
                    var metaData = rs.getMetaData();
                    var colCount = metaData.getColumnCount();
                    var labels = new String[colCount];
                    var seen = new HashMap<String, Integer>();

                    for (var i = 1; i <= colCount; i++) {
                        var base = metaData.getColumnLabel(i);
                        var count = seen.merge(base, 1, Integer::sum);
                        labels[i - 1] = count == 1 ? base : base + "_" + count;
                    }

                    var columns = new ArrayList<Map<String, Object>>();

                    for (var i = 1; i <= colCount; i++) {
                        columns.add(Map.of("name", labels[i - 1], "type", metaData.getColumnTypeName(i)));
                    }

                    var rows = new ArrayList<Map<String, Object>>();

                    while (rs.next()) {
                        var row = new LinkedHashMap<String, Object>();

                        for (var i = 1; i <= colCount; i++) {
                            row.put(labels[i - 1], readValue(rs.getObject(i)));
                        }

                        rows.add(row);
                    }

                    formatter.print(profile.name(), columns, rows);
                }
            } else {
                var rowsAffected = stmt.getUpdateCount();
                formatter.print(profile.name(), List.of(), List.of(Map.of("rowsAffected", rowsAffected == -1 ? 0 : rowsAffected)));
            }
        }
    }

    private static Object readValue(Object val) throws Exception {
        if (val instanceof Clob clob) {
            return clob.getSubString(1, (int) Math.min(clob.length(), Integer.MAX_VALUE));
        }

        if (val instanceof Blob blob) {
            var bytes = blob.getBytes(1, (int) Math.min(blob.length(), Integer.MAX_VALUE));
            return Base64.getEncoder().encodeToString(bytes);
        }

        return val;
    }
}
