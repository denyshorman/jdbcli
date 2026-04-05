package io.github.denyshorman.jdbcli.profile;

import io.github.denyshorman.jdbcli.config.Profile;
import io.github.denyshorman.jdbcli.exception.JdbcliException;
import io.github.denyshorman.jdbcli.util.FileSystemUtil;
import io.github.denyshorman.jdbcli.util.JsonUtil;

import java.nio.file.Files;

public class ProfileWriter {
    public static void save(Profile profile) {
        try {
            var profilesDir = FileSystemUtil.getJdbcliHome().resolve("profiles");
            Files.createDirectories(profilesDir);

            var file = profilesDir.resolve(profile.name() + ".json");
            JsonUtil.MAPPER.writeValue(file.toFile(), profile);
        } catch (Exception e) {
            throw new JdbcliException("Operation failed: " + e.getMessage(), e);
        }
    }
}
