package io.github.denyshorman.jdbcli.exception;

public class JdbcliException extends RuntimeException {
    public JdbcliException(String message) {
        super(message);
    }

    public JdbcliException(String message, Throwable cause) {
        super(message, cause);
    }
}
