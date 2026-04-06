package io.github.denyshorman.jdbcli;

import io.github.denyshorman.jdbcli.cli.MainCommand;
import io.github.denyshorman.jdbcli.config.AuthConfig;
import io.github.denyshorman.jdbcli.config.Profile;
import io.github.denyshorman.jdbcli.config.SafetyConfig;
import io.github.denyshorman.jdbcli.descriptor.DbDescriptorLoader;
import io.github.denyshorman.jdbcli.profile.ProfileWriter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end scenario tests that simulate the real user workflow:
 * <pre>
 *   1. db list       — discover available database types
 *   2. db show       — inspect a descriptor
 *   3. db init       — scaffold a custom descriptor
 *   4. driver install — download the JDBC driver
 *   5. profile init   — create a connection profile template
 *   6. profile edit   — (simulated) fill in the real URL and credentials
 *   7. profile list  — verify the profile is saved
 *   8. profile show  — inspect the saved profile
 *   9. query CREATE  — DDL statement
 *  10. query INSERT  — DML that returns rowsAffected
 *  11. query SELECT  — result set, JSON / CSV / table formats
 *  12. query UPDATE  — DML with rowsAffected
 *  13. query DELETE  — DML with rowsAffected
 *  14. query DROP    — DDL cleanup
 *  15. query --file  — run SQL from a file
 *  16. errors        — unknown profile, missing JAR, invalid SQL
 * </pre>
 *
 * Tests are ordered and share one Postgres container and one temp jdbcli home directory.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CliWorkflowTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @TempDir
    static Path JDBCLI_HOME;

    //#region Lifecycle
    @BeforeAll
    static void setJdbcliHome() {
        System.setProperty("jdbcli.home", JDBCLI_HOME.toString());
    }

    @AfterAll
    static void clearJdbcliHome() {
        System.clearProperty("jdbcli.home");
    }
    //#endregion

    //#region Helpers
    private CliResult run(String... args) {
        var bos = new ByteArrayOutputStream();
        var original = System.out;

        System.setOut(new PrintStream(bos));

        int exitCode;

        try {
            exitCode = new CommandLine(new MainCommand()).execute(args);
        } finally {
            System.setOut(original);
        }

        return new CliResult(exitCode, bos.toString());
    }

    record CliResult(int exitCode, String stdout) {
    }
    //#endregion

    //#region Steps

    //#region db list
    @Test
    @Order(1)
    void dbList_showsBuiltInDescriptors() {
        var result = run("db", "list");
        assertEquals(0, result.exitCode(), "db list should succeed");
        assertTrue(result.stdout().contains("postgres"), "should list postgres.  Got:\n" + result.stdout());
        assertTrue(result.stdout().contains("sqlserver"), "should list sqlserver");
        assertTrue(result.stdout().contains("oracle"), "should list oracle");
        assertTrue(result.stdout().contains("mongodb"), "should list mongodb");
    }
    //#endregion

    //#region db show
    @Test
    @Order(2)
    void dbShow_displaysDescriptorAsJson() {
        var result = run("db", "show", "postgres");
        assertEquals(0, result.exitCode(), "db show should succeed");
        assertTrue(result.stdout().contains("org.postgresql.Driver"), "descriptor JSON should include driver class. Got:\n" + result.stdout());
        assertTrue(result.stdout().contains("postgresql"), "descriptor JSON should mention postgresql");
    }

    @Test
    @Order(3)
    void dbShow_unknownDb_returnsExitCode1() {
        var result = run("db", "show", "nonexistent-db-xyz");
        assertEquals(1, result.exitCode(), "unknown db should return exit code 1");
        assertFalse(result.stdout().contains("className"), "unknown db should not print a descriptor. Got:\n" + result.stdout());
    }
    //#endregion

    //#region db init
    @Test
    @Order(4)
    void dbInit_createsCustomDescriptorFile() throws Exception {
        var result = run("db", "init", "--name", "mydb");
        assertEquals(0, result.exitCode(), "db init should succeed");

        var descriptorFile = JDBCLI_HOME.resolve("dbs/mydb.json");
        assertTrue(Files.exists(descriptorFile), "descriptor file should be created at " + descriptorFile);

        var content = Files.readString(descriptorFile);
        assertTrue(content.contains("mydb"), "descriptor JSON should contain the chosen name");
        assertTrue(content.contains("\"class\""), "descriptor JSON should contain a driver class field");
    }
    //#endregion

    //#region driver install
    @Test
    @Order(5)
    void driverInstall_downloadsPostgresJar() throws Exception {
        var result = run("driver", "install", "--db", "postgres");
        assertEquals(0, result.exitCode(), "driver install should succeed");

        var driversDir = JDBCLI_HOME.resolve("drivers");
        assertTrue(Files.exists(driversDir), "drivers directory should be created");

        try (var drivers = Files.list(driversDir)) {
            var jars = drivers.filter(p -> p.toString().endsWith(".jar")).toList();
            assertFalse(jars.isEmpty(), "at least one JAR should be present after install");
        }
    }
    //#endregion

    //#region profile init
    @Test
    @Order(6)
    void profileInit_createsTemplateProfileFile() throws Exception {
        var result = run("profile", "init", "--db", "postgres", "--name", "pg-test");
        assertEquals(0, result.exitCode(), "profile init should succeed");

        var profileFile = JDBCLI_HOME.resolve("profiles/pg-test.json");
        assertTrue(Files.exists(profileFile), "profile file should be created at " + profileFile);

        var content = Files.readString(profileFile);
        assertTrue(content.contains("pg-test"), "profile JSON should contain the chosen name");
        assertTrue(content.contains("postgres"), "profile JSON should reference the db id");
        assertTrue(content.contains("jdbcUrl"), "profile JSON should contain a jdbcUrl field");
    }
    //#endregion

    //#region simulate user editing the profile

    /**
     * After {@code profile init} the user is expected to open the generated JSON and
     * fill in the real JDBC URL and credentials.  This step simulates that edit
     * programmatically so subsequent steps can run real queries.
     */
    @Test
    @Order(7)
    void profileEdit_fillsInRealConnectionDetails() throws Exception {
        var driversDir = JDBCLI_HOME.resolve("drivers");
        var driverJar = (Path) null;

        try (var drivers = Files.list(driversDir)) {
            driverJar = drivers
                    .filter(p -> p.toString().endsWith(".jar"))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No driver JAR found in " + driversDir));
        }

        var db = DbDescriptorLoader.load("postgres");
        assertNotNull(db, "postgres descriptor must exist");

        ProfileWriter.save(new Profile(
                "pg-test",
                "postgres",
                new Profile.DriverConfig(db.driver().className(), driverJar.toString()),
                POSTGRES.getJdbcUrl(),
                new AuthConfig.Plain(POSTGRES.getUsername(), POSTGRES.getPassword()),
                Map.of(),
                new SafetyConfig(false, 100, 30),
                Map.of()
        ));

        var content = Files.readString(JDBCLI_HOME.resolve("profiles/pg-test.json"));
        assertTrue(content.contains(POSTGRES.getJdbcUrl()), "patched profile should contain the real JDBC URL");
    }
    //#endregion

    //#region profile list / show
    @Test
    @Order(8)
    void profileList_showsCreatedProfile() {
        var result = run("profile", "list");
        assertEquals(0, result.exitCode(), "profile list should succeed");
        assertTrue(result.stdout().contains("pg-test"), "profile list should show pg-test. Got:\n" + result.stdout());
        assertTrue(result.stdout().contains("postgres"), "profile list should show the db id");
    }

    @Test
    @Order(9)
    void profileShow_displaysProfileJson() {
        var result = run("profile", "show", "--profile", "pg-test");
        assertEquals(0, result.exitCode(), "profile show should succeed");
        assertTrue(result.stdout().contains("pg-test"), "profile show should contain profile name. Got:\n" + result.stdout());
        assertTrue(result.stdout().contains("jdbcUrl"), "profile show should contain jdbcUrl field");
    }
    //#endregion

    //#region DDL: CREATE TABLE
    @Test
    @Order(10)
    void query_createTable_succeeds() {
        var result = run("query", "--profile", "pg-test", "--sql", "CREATE TABLE scenario_test (id SERIAL PRIMARY KEY, name TEXT, value INT)");
        assertEquals(0, result.exitCode(), "CREATE TABLE should succeed");
        assertTrue(result.stdout().contains("rowsAffected"), "DDL should return rowsAffected output. Got:\n" + result.stdout());
    }
    //#endregion

    //#region DML: INSERT
    @Test
    @Order(11)
    void query_insert_returnsRowsAffected() {
        var result = run("query", "--profile", "pg-test", "--sql", "INSERT INTO scenario_test (name, value) VALUES ('hello', 42), ('world', 99)");
        assertEquals(0, result.exitCode(), "INSERT should succeed");
        assertTrue(result.stdout().contains("rowsAffected"), "INSERT output should contain rowsAffected. Got:\n" + result.stdout());
        assertTrue(result.stdout().contains("2"), "INSERT should report 2 rows affected");
    }
    //#endregion

    //#region SELECT (all three output formats)
    @Test
    @Order(12)
    void query_select_jsonFormat_returnsStructuredResult() {
        var result = run("query", "--profile", "pg-test", "--sql", "SELECT * FROM scenario_test ORDER BY id", "--format", "json");
        assertEquals(0, result.exitCode(), "SELECT json should succeed");
        assertTrue(result.stdout().contains("rowCount"), "JSON output should have rowCount. Got:\n" + result.stdout());
        assertTrue(result.stdout().contains("columns"), "JSON output should have columns");
        assertTrue(result.stdout().contains("hello"), "JSON output should contain inserted data");
        assertTrue(result.stdout().contains("42"), "JSON output should contain inserted value");
    }

    @Test
    @Order(13)
    void query_select_csvFormat_hasHeaderAndRows() {
        var result = run("query", "--profile", "pg-test", "--sql", "SELECT * FROM scenario_test ORDER BY id", "--format", "csv");
        assertEquals(0, result.exitCode(), "SELECT csv should succeed");

        var lines = result.stdout().strip().lines().toList();
        assertFalse(lines.isEmpty(), "CSV should have at least a header row");

        var header = lines.getFirst().toLowerCase();
        assertTrue(header.contains("name"), "CSV header should contain 'name'. Got: " + header);
        assertTrue(header.contains("value"), "CSV header should contain 'value'");

        var data = result.stdout();
        assertTrue(data.contains("hello"), "CSV data should contain 'hello'");
    }

    @Test
    @Order(14)
    void query_select_tableFormat_hasHeaderAndRows() {
        var result = run("query", "--profile", "pg-test", "--sql", "SELECT * FROM scenario_test ORDER BY id", "--format", "table");
        assertEquals(0, result.exitCode(), "SELECT table should succeed");

        var out = result.stdout();
        assertTrue(out.contains("name"), "table output should contain 'name' column. Got:\n" + out);
        assertTrue(out.contains("hello"), "table output should contain data rows");
    }
    //#endregion

    //#region DML: UPDATE
    @Test
    @Order(15)
    void query_update_returnsRowsAffected() {
        var result = run("query", "--profile", "pg-test", "--sql", "UPDATE scenario_test SET value = 100 WHERE name = 'hello'");
        assertEquals(0, result.exitCode(), "UPDATE should succeed");
        assertTrue(result.stdout().contains("rowsAffected"), "UPDATE output should contain rowsAffected. Got:\n" + result.stdout());
        assertTrue(result.stdout().contains("1"), "UPDATE should report 1 row affected");
    }
    //#endregion

    //#region DML: DELETE
    @Test
    @Order(16)
    void query_delete_returnsRowsAffected() {
        var result = run("query", "--profile", "pg-test", "--sql", "DELETE FROM scenario_test WHERE name = 'world'");
        assertEquals(0, result.exitCode(), "DELETE should succeed");
        assertTrue(result.stdout().contains("rowsAffected"), "DELETE output should contain rowsAffected. Got:\n" + result.stdout());
    }
    //#endregion

    //#region DDL: DROP TABLE (cleanup)
    @Test
    @Order(17)
    void query_dropTable_succeeds() {
        var result = run("query", "--profile", "pg-test", "--sql", "DROP TABLE scenario_test");
        assertEquals(0, result.exitCode(), "DROP TABLE should succeed");
    }
    //#endregion

    //#region query from file (--file flag)
    @Test
    @Order(18)
    void query_fromFile_executesCorrectly() throws Exception {
        var sqlFile = Files.createTempFile("scenario_", ".sql");
        try {
            Files.writeString(sqlFile, "SELECT 1 AS result");
            var result = run("query", "--profile", "pg-test", "--file", sqlFile.toString());
            assertEquals(0, result.exitCode(), "query --file should succeed");
            assertTrue(result.stdout().contains("rowCount"), "output should contain rowCount. Got:\n" + result.stdout());
            assertTrue(result.stdout().contains("result"), "output should contain the aliased column 'result'");
        } finally {
            Files.deleteIfExists(sqlFile);
        }
    }
    //#endregion

    //#region Error scenarios
    @Test
    @Order(19)
    void errorScenario_unknownProfile_returnsExitCode1() {
        var result = run("query", "--profile", "nonexistent-profile-xyz", "--sql", "SELECT 1");
        assertEquals(1, result.exitCode(), "unknown profile should return exit code 1");
    }

    @Test
    @Order(20)
    void errorScenario_missingDriverJar_returnsExitCode1() {
        ProfileWriter.save(new Profile(
                "no-jar-profile",
                "postgres",
                new Profile.DriverConfig("org.postgresql.Driver", "/nonexistent/path/driver.jar"),
                "jdbc:postgresql://localhost/test",
                new AuthConfig.Plain("user", "pass"),
                Map.of(),
                new SafetyConfig(false, 100, 30),
                Map.of()
        ));

        var result = run("query", "--profile", "no-jar-profile", "--sql", "SELECT 1");
        assertEquals(1, result.exitCode(), "missing driver JAR should return exit code 1");
    }

    @Test
    @Order(21)
    void errorScenario_invalidSql_returnsExitCode1() {
        var result = run("query", "--profile", "pg-test", "--sql", "SELECT * FROM nonexistent_table_xyz_that_does_not_exist");
        assertEquals(1, result.exitCode(), "invalid SQL (unknown table) should return exit code 1");
    }

    @Test
    @Order(22)
    void errorScenario_missingRequiredOption_returnsExitCode2() {
        // picocli returns exit code 2 for usage errors (missing required option)
        var result = run("query", "--sql", "SELECT 1"); // missing --profile
        assertEquals(2, result.exitCode(), "missing --profile should return picocli usage error code 2");
    }
    //#endregion

    //#endregion
}
