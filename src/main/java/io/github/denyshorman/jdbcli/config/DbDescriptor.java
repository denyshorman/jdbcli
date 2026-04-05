package io.github.denyshorman.jdbcli.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DbDescriptor(
        String id,
        String displayName,
        DriverInfo driver,
        Map<String, String> examples
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DriverInfo(
            @Nullable String groupId,
            @Nullable String artifactId,
            @Nullable String defaultVersion,
            @Nullable String classifier,
            @Nullable String url,
            @JsonProperty("class") String className
    ) {
    }
}
