package com.askdb.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record DatabaseConnection(
        String dbType,
        String host,
        String port,
        String databaseName,
        String username,
        String mode,
        Instant connectedAt
) {
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("dbType", dbType);
        map.put("host", host);
        map.put("port", port);
        map.put("databaseName", databaseName);
        map.put("username", username);
        map.put("mode", mode);
        map.put("connectedAt", connectedAt.toString());
        return map;
    }
}
