package io.github.denyshorman.jdbcli.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = AuthConfig.Env.class, name = "env"),
        @JsonSubTypes.Type(value = AuthConfig.Plain.class, name = "plain")
})
public sealed interface AuthConfig permits AuthConfig.Plain, AuthConfig.Env {
    record Env(
            String usernameEnv,
            String passwordEnv
    ) implements AuthConfig {
    }

    record Plain(
            String username,
            String password
    ) implements AuthConfig {
    }
}
