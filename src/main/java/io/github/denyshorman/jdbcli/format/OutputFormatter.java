package io.github.denyshorman.jdbcli.format;

import java.util.List;
import java.util.Map;

public interface OutputFormatter {
    void print(
            String profileName,
            List<Map<String, Object>> columns,
            List<Map<String, Object>> rows
    ) throws Exception;
}
