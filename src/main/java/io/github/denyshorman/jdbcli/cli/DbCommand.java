package io.github.denyshorman.jdbcli.cli;

import io.github.denyshorman.jdbcli.config.DbDescriptor;
import io.github.denyshorman.jdbcli.descriptor.DbDescriptorLoader;
import io.github.denyshorman.jdbcli.exception.JdbcliException;
import io.github.denyshorman.jdbcli.util.FileSystemUtil;
import io.github.denyshorman.jdbcli.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.util.Map;

@Command(
        name = "db",
        description = {
                "Manage database descriptors.",
                "",
                "A descriptor is a JSON file that identifies a database driver and provides",
                "an example JDBC URL. jdbcli uses it to download the driver and pre-fill a",
                "profile template.",
                "",
                "Built-in descriptors (postgres, sqlserver, oracle, mongodb) are bundled",
                "with jdbcli. Add or override descriptors by placing JSON files in",
                "~/.jdbcli/dbs/.",
        },
        mixinStandardHelpOptions = true
)
public class DbCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(DbCommand.class);

    @Command(name = "list", description = "List all available database descriptors (built-in and user-defined).", mixinStandardHelpOptions = true)
    public void list() {
        var dbs = DbDescriptorLoader.loadAll();

        if (dbs.isEmpty()) {
            return;
        }

        var idLen = dbs.stream().mapToInt(d -> d.id().length()).max().getAsInt();
        var nameLen = dbs.stream().mapToInt(d -> d.displayName().length()).max().getAsInt();
        var fmt = "%-" + (idLen + 2) + "s%-" + (nameLen + 2) + "s%s%n";

        for (var db : dbs) {
            System.out.printf(fmt, db.id(), db.displayName(), db.driver().className());
        }
    }

    @Command(
            name = "show",
            description = "Print the full JSON of a single database descriptor.",
            mixinStandardHelpOptions = true
    )
    public void show(@Parameters(index = "0", paramLabel = "<id>", description = "Descriptor ID (e.g. postgres, sqlserver, oracle, mongodb)") String dbName) {
        var db = DbDescriptorLoader.load(dbName);

        if (db == null) {
            throw new JdbcliException("Database not found: " + dbName);
        }

        System.out.println(JsonUtil.MAPPER.writeValueAsString(db));
    }

    @Command(
            name = "init",
            description = {
                    "Create a custom descriptor template at ~/.jdbcli/dbs/<name>.json.",
                    "",
                    "Edit the generated file to supply the real driver class and Maven",
                    "coordinates (or a direct download URL), then run 'driver install'.",
            },
            mixinStandardHelpOptions = true
    )
    public void init(
            @Option(
                    names = "--name",
                    required = true,
                    paramLabel = "<name>",
                    description = "Unique ID for the new descriptor (used in all other commands, e.g. mydb)"
            )
            String name
    ) throws Exception {
        var db = new DbDescriptor(
                name,
                "Custom DB",
                new DbDescriptor.DriverInfo("com.example", "my-driver", "1.0.0", null, null, "com.example.Driver"),
                Map.of(
                        "jdbcUrl", "jdbc:example://localhost:1234/mydb",
                        "testQuery", "SELECT 1"
                )
        );

        var dbsDir = FileSystemUtil.getJdbcliHome().resolve("dbs");
        Files.createDirectories(dbsDir);

        var file = dbsDir.resolve(name + ".json");
        Files.writeString(file, JsonUtil.MAPPER.writeValueAsString(db));

        LOGGER.info("Created boilerplate descriptor at {}", file);
    }
}
