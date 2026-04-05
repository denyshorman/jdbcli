package io.github.denyshorman.jdbcli.profile;

import io.github.denyshorman.jdbcli.config.Profile;
import io.github.denyshorman.jdbcli.exception.JdbcliException;
import io.github.denyshorman.jdbcli.util.FileSystemUtil;
import io.github.denyshorman.jdbcli.util.JsonUtil;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class ProfileLoader {
    public static List<Profile> loadAll() {
        var result = new ArrayList<Profile>();
        var profilesDir = FileSystemUtil.getJdbcliHome().resolve("profiles");

        try {
            if (Files.exists(profilesDir)) {
                try (var paths = Files.list(profilesDir)) {
                    paths.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                        try {
                            result.add(JsonUtil.MAPPER.readValue(p.toFile(), Profile.class));
                        } catch (Exception e) {
                            throw new JdbcliException("Operation failed: " + e.getMessage(), e);
                        }
                    });
                }
            }
        } catch (Exception e) {
            throw new JdbcliException("Operation failed: " + e.getMessage(), e);
        }

        return result;
    }

    public static @Nullable Profile load(String name) {
        return loadAll().stream().filter(p -> p.name().equals(name)).findFirst().orElse(null);
    }
}
