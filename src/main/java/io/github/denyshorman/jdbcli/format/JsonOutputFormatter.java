package io.github.denyshorman.jdbcli.format;

import io.github.denyshorman.jdbcli.util.JsonUtil;

import java.util.List;
import java.util.Map;

public class JsonOutputFormatter implements OutputFormatter {
    @Override
    public void print(String profileName, List<Map<String, Object>> columns, List<Map<String, Object>> rows) {
        var result = new QueryResult(profileName, rows.size(), columns, rows);
        System.out.println(JsonUtil.MAPPER.writeValueAsString(result));
    }

    private record QueryResult(
            String profile,
            int rowCount,
            List<Map<String, Object>> columns,
            List<Map<String, Object>> rows
    ) {
    }
}
