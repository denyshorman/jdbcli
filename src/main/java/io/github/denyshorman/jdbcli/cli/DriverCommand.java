package io.github.denyshorman.jdbcli.cli;

import io.github.denyshorman.jdbcli.descriptor.DbDescriptorLoader;
import io.github.denyshorman.jdbcli.driver.DriverInstaller;
import io.github.denyshorman.jdbcli.exception.JdbcliException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "driver",
        description = {
                "Manage JDBC drivers.",
                "",
                "Drivers are downloaded on demand and stored in ~/.jdbcli/drivers/.",
                "A profile points to the local JAR so jdbcli can load it at query time.",
        },
        mixinStandardHelpOptions = true
)
public class DriverCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(DriverCommand.class);

    @Command(
            name = "install",
            description = {
                    "Download and cache the JDBC driver for a database descriptor.",
                    "",
                    "By default the driver URL is taken from the descriptor (either a direct",
                    "URL or the latest version resolved from Maven Central).",
                    "Use --url to override.",
                    "",
                    "The JAR is saved to ~/.jdbcli/drivers/ and re-used on subsequent runs.",
            },
            mixinStandardHelpOptions = true
    )
    public void install(
            @Option(names = "--db", required = true, paramLabel = "<id>", description = "Descriptor ID to install the driver for (e.g. postgres)")
            String dbName,

            @Option(names = "--url", paramLabel = "<url>", description = "Override the download URL (direct link to the JAR file)")
            @Nullable
            String url
    ) throws Exception {
        var db = DbDescriptorLoader.load(dbName);

        if (db == null) {
            throw new JdbcliException("DB not found: " + dbName);
        }

        var jar = DriverInstaller.install(db, url);

        LOGGER.info("Installed driver to {}", jar);
    }
}
