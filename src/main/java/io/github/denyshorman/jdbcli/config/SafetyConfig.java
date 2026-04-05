package io.github.denyshorman.jdbcli.config;

import org.jspecify.annotations.Nullable;

public record SafetyConfig(
        @Nullable Boolean readOnly,
        @Nullable Integer maxRows,
        @Nullable Integer timeoutSeconds
) {
}
