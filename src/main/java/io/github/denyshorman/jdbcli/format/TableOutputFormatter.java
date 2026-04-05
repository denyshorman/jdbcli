package io.github.denyshorman.jdbcli.format;

import java.util.List;
import java.util.Map;

public class TableOutputFormatter implements OutputFormatter {
    @Override
    public void print(String profileName, List<Map<String, Object>> columns, List<Map<String, Object>> rows) {
        var colNames = columns.stream().map(c -> c.get("name").toString()).toList();
        var lengths = new int[colNames.size()];

        for (var i = 0; i < colNames.size(); i++) {
            lengths[i] = colNames.get(i).length();
        }

        for (var row : rows) {
            for (int i = 0; i < colNames.size(); i++) {
                var val = row.get(colNames.get(i));
                var str = val == null ? "NULL" : val.toString();
                if (str.length() > lengths[i]) lengths[i] = str.length();
            }
        }

        var formatStr = new StringBuilder();

        for (var len : lengths) {
            formatStr.append("%-").append(len + 2).append("s");
        }

        System.out.printf(formatStr + "%n", colNames.toArray());

        var separator = new StringBuilder();

        for (var len : lengths) {
            separator.repeat("-", len + 2);
        }

        System.out.println(separator);

        for (var row : rows) {
            var vals = colNames.stream().map(c -> {
                var val = row.get(c);
                return val == null ? "NULL" : val.toString();
            }).toArray();

            System.out.printf(formatStr + "%n", vals);
        }

        System.out.println("(" + rows.size() + (rows.size() == 1 ? " row)" : " rows)"));
    }
}
