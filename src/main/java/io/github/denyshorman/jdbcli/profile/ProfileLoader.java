package io.github.denyshorman.jdbcli.profile;

import io.github.denyshorman.jdbcli.config.Profile;
import io.github.denyshorman.jdbcli.exception.JdbcliException;
import io.github.denyshorman.jdbcli.util.FileSystemUtil;
import io.github.denyshorman.jdbcli.util.JsonUtil;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ProfileLoader {
    public static List<Profile> loadAll() {
        var profilesDir = getProfilesDir();

        if (!Files.exists(profilesDir)) {
            return List.of();
        }

        try (var paths = Files.list(profilesDir)) {
            return paths
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(p -> JsonUtil.MAPPER.readValue(p.toFile(), Profile.class))
                    .toList();
        } catch (Exception e) {
            throw new JdbcliException("Operation failed: " + e.getMessage(), e);
        }
    }

    public static @Nullable Profile load(String name) {
        var profilesDir = getProfilesDir();

        if (!Files.exists(profilesDir)) {
            return null;
        }

        var profilePath = profilesDir.resolve(name + ".json");

        if (!Files.exists(profilePath)) {
            return null;
        }

        try {
            return JsonUtil.MAPPER.readValue(profilePath.toFile(), Profile.class);
        } catch (Exception e) {
            throw new JdbcliException("Operation failed: " + e.getMessage(), e);
        }
    }

    private static Path getProfilesDir() {
        return FileSystemUtil.getJdbcliHome().resolve("profiles");
    }
}
