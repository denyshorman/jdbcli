package io.github.denyshorman.jdbcli.cli;

import io.github.denyshorman.jdbcli.config.AuthConfig;
import io.github.denyshorman.jdbcli.config.Profile;
import io.github.denyshorman.jdbcli.config.SafetyConfig;
import io.github.denyshorman.jdbcli.descriptor.DbDescriptorLoader;
import io.github.denyshorman.jdbcli.driver.DriverInstaller;
import io.github.denyshorman.jdbcli.exception.JdbcliException;
import io.github.denyshorman.jdbcli.profile.ProfileLoader;
import io.github.denyshorman.jdbcli.profile.ProfileWriter;
import io.github.denyshorman.jdbcli.util.FileSystemUtil;
import io.github.denyshorman.jdbcli.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.Map;

@Command(
        name = "profile",
        description = {
                "Manage connection profiles.",
                "",
                "A profile stores everything jdbcli needs to connect to a database: the",
                "JDBC URL, driver location, credentials, and optional safety limits",
                "(max rows, timeout, etc.).",
                "Profiles are saved as JSON files in ~/.jdbcli/profiles/.",
        },
        mixinStandardHelpOptions = true
)
public class ProfileCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileCommand.class);

    @Command(
            name = "init",
            description = {
                    "Create a connection profile template for a database descriptor.",
                    "",
                    "Writes a template to ~/.jdbcli/profiles/<name>.json pre-filled with",
                    "placeholder values derived from the descriptor. Edit the file to set",
                    "the real JDBC URL and credentials before running queries.",
                    "",
                    "Credential modes (set the 'auth.type' field in the JSON):",
                    "  plain — username/password stored directly in the profile file",
                    "  env   — username/password read from environment variables at runtime",
            },
            mixinStandardHelpOptions = true
    )
    public void init(
            @Option(names = "--db", required = true, paramLabel = "<id>", description = "Descriptor ID the profile is based on (e.g. postgres)")
            String dbName,

            @Option(names = "--name", required = true, paramLabel = "<name>", description = "Name for the new profile (used with --profile in query)")
            String name
    ) {
        var db = DbDescriptorLoader.load(dbName);

        if (db == null) {
            throw new JdbcliException("DB not found");
        }

        var exampleUrl = db.examples().getOrDefault("jdbcUrl", "jdbc:" + dbName + "://localhost/mydb");
        var downloadUrl = db.driver().url() != null ? db.driver().url() : DriverInstaller.resolveLatestMavenUrl(db);
        var jarName = downloadUrl.substring(downloadUrl.lastIndexOf('/') + 1);
        var jar = FileSystemUtil.getJdbcliHome().resolve("drivers").resolve(jarName).toString();

        var profile = new Profile(
                name,
                dbName,
                new Profile.DriverConfig(db.driver().className(), jar),
                exampleUrl,
                new AuthConfig.Plain("DB_USER", "DB_PASSWORD"),
                Map.of(),
                new SafetyConfig(true, 1000, 30),
                Map.of("outputFormat", "json")
        );

        ProfileWriter.save(profile);

        LOGGER.info("Profile saved to {}", name);
    }

    @Command(name = "list", description = "List all saved connection profiles.", mixinStandardHelpOptions = true)
    public void list() {
        var profiles = ProfileLoader.loadAll();

        if (profiles.isEmpty()) {
            return;
        }

        var nameLen = profiles.stream().mapToInt(p -> p.name().length()).max().getAsInt();
        var fmt = "%-" + (nameLen + 2) + "s%s%n";

        for (var p : profiles) {
            System.out.printf(fmt, p.name(), p.dbId());
        }
    }

    @Command(name = "show", description = "Print the full JSON of a saved profile.", mixinStandardHelpOptions = true)
    public void show(@Option(names = "--profile", required = true, paramLabel = "<name>", description = "Profile name to display") String name) {
        var p = ProfileLoader.load(name);

        if (p == null) {
            throw new JdbcliException("Profile not found: " + name);
        }

        System.out.println(JsonUtil.MAPPER.writeValueAsString(p));
    }
}
