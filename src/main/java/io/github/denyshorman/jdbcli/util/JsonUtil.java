package io.github.denyshorman.jdbcli.util;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.SerializationFeature;

public class JsonUtil {
    public static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();
}
