package io.github.denyshorman.jdbcli.config;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

public record Profile(
        String name,
        String dbId,
        DriverConfig driver,
        String jdbcUrl,
        @Nullable AuthConfig auth,
        @Nullable Map<String, String> properties,
        @Nullable SafetyConfig safety,
        @Nullable Map<String, String> defaults
) {
    public record DriverConfig(
            @JsonProperty("class") String className,
            String jar
    ) {
    }
}
