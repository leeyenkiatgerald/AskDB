package com.askdb.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record QueryResult(
        int id,
        String createdAt,
        String databaseName,
        String question,
        String summary,
        String sql,
        List<String> columns,
        List<Map<String, Object>> rows
) {
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("createdAt", createdAt);
        map.put("databaseName", databaseName);
        map.put("question", question);
        map.put("summary", summary);
        map.put("sql", sql);
        map.put("columns", columns);
        map.put("rows", rows);
        map.put("rowCount", rows.size());
        return map;
    }
}
