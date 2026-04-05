package io.github.denyshorman.jdbcli.format;

import java.util.List;
import java.util.Map;

public class CsvOutputFormatter implements OutputFormatter {
    @Override
    public void print(String profileName, List<Map<String, Object>> columns, List<Map<String, Object>> rows) {
        var colNames = columns.stream().map(c -> c.get("name").toString()).toList();
        System.out.println(String.join(",", colNames));

        for (var row : rows) {
            var vals = colNames.stream().map(c -> {
                var val = row.get(c);
                if (val == null) return "";
                var str = val.toString().replace("\"", "\"\"");
                if (str.contains(",") || str.contains("\n") || str.contains("\"")) {
                    return "\"" + str + "\"";
                }
                return str;
            }).toList();

            System.out.println(String.join(",", vals));
        }
    }
}

