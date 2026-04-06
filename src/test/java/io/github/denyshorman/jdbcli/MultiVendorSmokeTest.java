package io.github.denyshorman.jdbcli;

import io.github.denyshorman.jdbcli.config.AuthConfig;
import io.github.denyshorman.jdbcli.config.DbDescriptor;
import io.github.denyshorman.jdbcli.config.Profile;
import io.github.denyshorman.jdbcli.config.SafetyConfig;
import io.github.denyshorman.jdbcli.descriptor.DbDescriptorLoader;
import io.github.denyshorman.jdbcli.driver.DriverInstaller;
import io.github.denyshorman.jdbcli.execution.JdbcExecutor;
import io.github.denyshorman.jdbcli.format.JsonOutputFormatter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
public class MultiVendorSmokeTest {
    @Container
    public static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    public static MSSQLServerContainer<?> mssql = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest").acceptLicense();

    @Container
    public static OracleContainer oracle = new OracleContainer("gvenzl/oracle-xe:21-slim-faststart");

    @Container
    public static MongoDBContainer mongo = new MongoDBContainer(DockerImageName.parse("mongodb/mongodb-enterprise-server:6.0.12-ubuntu2204").asCompatibleSubstituteFor("mongo"));

    @TempDir
    static Path JDBCLI_HOME;

    @BeforeAll
    public static void setJdbcliHome() {
        System.setProperty("jdbcli.home", JDBCLI_HOME.toString());
    }

    @AfterAll
    static void clearJdbcliHome() {
        System.clearProperty("jdbcli.home");
    }

    private void runQueryTest(String dbId, String url, String user, String password) throws Exception {
        var db = (DbDescriptor) null;

        for (var d : DbDescriptorLoader.loadAll()) {
            if (d.id().equals(dbId)) {
                db = d;
            }
        }

        if (db == null) {
            throw new RuntimeException("DB Descriptor not found: " + dbId);
        }

        var driverJar = DriverInstaller.install(db, null);

        var p = new Profile(
                dbId + "-test",
                dbId,
                new Profile.DriverConfig(db.driver().className(), driverJar.toAbsolutePath().toString()),
                url,
                new AuthConfig.Plain(user, password),
                Map.of(),
                new SafetyConfig(true, 100, 30),
                Map.of()
        );

        var originalOut = System.out;
        var bos = new ByteArrayOutputStream();

        System.setOut(new PrintStream(bos));

        try {
            JdbcExecutor.execute(p, db.examples().get("testQuery"), new JsonOutputFormatter());
        } finally {
            System.setOut(originalOut);
        }

        var output = bos.toString();

        assertTrue(output.contains("\"rowCount\":") || output.contains("\"rowCount\" :"), "Output should contain rowCount");
    }

    @Test
    public void testPostgres() throws Exception {
        runQueryTest("postgres", postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    @Test
    public void testSqlServer() throws Exception {
        runQueryTest("sqlserver", mssql.getJdbcUrl() + ";encrypt=false;trustServerCertificate=true;", mssql.getUsername(), mssql.getPassword());
    }

    @Test
    public void testOracle() throws Exception {
        runQueryTest("oracle", oracle.getJdbcUrl(), oracle.getUsername(), oracle.getPassword());
    }

    @Test
    public void testMongo() throws Exception {
        runQueryTest("mongodb", "jdbc:mongodb://" + mongo.getHost() + ":" + mongo.getMappedPort(27017) + "/admin", null, null);
    }
}
