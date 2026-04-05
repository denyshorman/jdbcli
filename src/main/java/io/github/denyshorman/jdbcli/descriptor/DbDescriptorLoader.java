package io.github.denyshorman.jdbcli.descriptor;

import io.github.denyshorman.jdbcli.config.DbDescriptor;
import io.github.denyshorman.jdbcli.exception.JdbcliException;
import io.github.denyshorman.jdbcli.util.FileSystemUtil;
import io.github.denyshorman.jdbcli.util.JsonUtil;

import java.nio.file.*;
import java.util.*;

import org.jspecify.annotations.Nullable;

public class DbDescriptorLoader {
    public static List<DbDescriptor> loadAll() {
        var result = new LinkedHashMap<String, DbDescriptor>();

        try {
            var url = DbDescriptorLoader.class.getResource("/dbs");

            if (url != null) {
                var uri = url.toURI();

                if ("jar".equals(uri.getScheme())) {
                    try {
                        var fs = FileSystems.getFileSystem(uri);
                        loadFromPath(fs.getPath("/dbs"), result);
                    } catch (Exception e) {
                        try (var fs = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
                            loadFromPath(fs.getPath("/dbs"), result);
                        }
                    }
                } else {
                    loadFromPath(Paths.get(uri), result);
                }
            }
        } catch (Exception e) {
            // Ignore missing or malformed built-ins gracefully
        }

        // Load user-provided descriptors from ~/.jdbcli/dbs/ (these will override built-ins)
        var dbsDir = FileSystemUtil.getJdbcliHome().resolve("dbs");

        if (Files.exists(dbsDir)) {
            loadFromPath(dbsDir, result);
        }

        return result.values().stream()
                .sorted(Comparator.comparing(DbDescriptor::id))
                .toList();
    }

    private static void loadFromPath(Path dir, Map<String, DbDescriptor> result) {
        try (var paths = Files.list(dir)) {
            paths.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                try (var is = Files.newInputStream(p)) {
                    var desc = JsonUtil.MAPPER.readValue(is, DbDescriptor.class);
                    result.put(desc.id(), desc);
                } catch (Exception e) {
                    throw new JdbcliException("Operation failed: " + e.getMessage(), e);
                }
            });
        } catch (Exception e) {
            throw new JdbcliException("Operation failed: " + e.getMessage(), e);
        }
    }

    public static @Nullable DbDescriptor load(String id) {
        return loadAll().stream().filter(d -> d.id().equals(id)).findFirst().orElse(null);
    }
}
