import org.jspecify.annotations.NullMarked;

@NullMarked
module io.github.denyshorman.jdbcli {
    requires java.sql;
    requires java.net.http;
    requires org.slf4j;
    requires org.jspecify;
    requires info.picocli;
    requires tools.jackson.databind;

    opens io.github.denyshorman.jdbcli.cli to info.picocli;

    exports io.github.denyshorman.jdbcli.cli;
    exports io.github.denyshorman.jdbcli.config to tools.jackson.databind;
}
