package com.askdb.db;

import com.askdb.nlp.GeneratedSql;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class RemoteDatabase {
    private static final Set<String> SYSTEM_SCHEMAS = Set.of(
            "information_schema",
            "pg_catalog",
            "mysql",
            "performance_schema",
            "sys"
    );

    private final String dbType;
    private final String host;
    private final String port;
    private final String databaseName;
    private final String username;
    private final String password;
    private final String jdbcUrl;

    public RemoteDatabase(String dbType, String host, String port, String databaseName, String username, String password) {
        this.dbType = normalizeDbType(dbType);
        this.host = requireValue(host, "Host");
        this.port = normalizePort(this.dbType, port);
        this.databaseName = requireValue(databaseName, "Database name");
        this.username = requireValue(username, "Username");
        this.password = password == null ? "" : password;
        loadDriver(this.dbType);
        this.jdbcUrl = buildJdbcUrl();
    }

    public void testConnection() throws SQLException {
        try (Connection connection = openConnection()) {
            connection.setReadOnly(true);
            if (!connection.isValid(5)) {
                throw new SQLException("Database connection could not be validated.");
            }
        }
    }

    public List<Map<String, Object>> schema() throws SQLException {
        List<Map<String, Object>> tables = new ArrayList<>();
        try (Connection connection = openConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            try (ResultSet resultSet = metaData.getTables(catalog, null, "%", new String[]{"TABLE", "VIEW"})) {
                while (resultSet.next() && tables.size() < 60) {
                    String schema = resultSet.getString("TABLE_SCHEM");
                    if (isSystemSchema(schema)) continue;

                    String tableName = resultSet.getString("TABLE_NAME");
                    String tableType = resultSet.getString("TABLE_TYPE");
                    List<String> columns = columns(metaData, catalog, schema, tableName);
                    tables.add(table(tableName, tableType == null ? "Remote table" : "Remote " + tableType.toLowerCase(Locale.ROOT), columns));
                }
            }
        }
        return tables;
    }

    public MockDatabase.QueryOutput execute(GeneratedSql generatedSql) throws SQLException {
        String sql = readOnlySql(generatedSql.sql());
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            connection.setReadOnly(true);
            statement.setQueryTimeout(30);
            statement.setMaxRows(500);
            try (ResultSet resultSet = statement.executeQuery(sql)) {
                return extractRows(resultSet);
            }
        }
    }

    public String mode() {
        return "remote";
    }

    private Connection openConnection() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", username);
        properties.setProperty("password", password);
        return DriverManager.getConnection(jdbcUrl, properties);
    }

    private List<String> columns(DatabaseMetaData metaData, String catalog, String schema, String tableName) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (ResultSet resultSet = metaData.getColumns(catalog, schema, tableName, "%")) {
            while (resultSet.next()) {
                String name = resultSet.getString("COLUMN_NAME");
                String type = resultSet.getString("TYPE_NAME");
                columns.add(type == null || type.isBlank() ? name : name + " " + type);
            }
        }
        return columns;
    }

    private MockDatabase.QueryOutput extractRows(ResultSet resultSet) throws SQLException {
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();
        List<String> columns = new ArrayList<>();
        for (int column = 1; column <= columnCount; column++) {
            String label = metaData.getColumnLabel(column);
            columns.add(label == null || label.isBlank() ? metaData.getColumnName(column) : label);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        while (resultSet.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int column = 1; column <= columnCount; column++) {
                row.put(columns.get(column - 1), normalizeValue(resultSet.getObject(column)));
            }
            rows.add(row);
        }
        return new MockDatabase.QueryOutput(columns, rows);
    }

    private Object normalizeValue(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal decimal) return decimal.stripTrailingZeros().toPlainString();
        if (value instanceof java.sql.Date date) return date.toLocalDate().toString();
        if (value instanceof java.sql.Time time) return time.toLocalTime().toString();
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant().toString();
        if (value instanceof TemporalAccessor) return value.toString();
        return value;
    }

    private String readOnlySql(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("Generated SQL is empty.");
        }

        String normalized = sql.trim();
        if (normalized.endsWith(";")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("select ") || lower.startsWith("with "))) {
            throw new IllegalArgumentException("Only read-only SELECT queries can run against remote databases.");
        }
        if (lower.contains(";")) {
            throw new IllegalArgumentException("Multiple SQL statements are not allowed.");
        }
        return normalized;
    }

    private Map<String, Object> table(String name, String description, List<String> columns) {
        Map<String, Object> table = new LinkedHashMap<>();
        table.put("name", name);
        table.put("description", description);
        table.put("columns", columns);
        return table;
    }

    private String buildJdbcUrl() {
        return switch (dbType) {
            case "PostgreSQL" -> "jdbc:postgresql://" + host + ":" + port + "/" + databaseName
                    + "?connectTimeout=5&socketTimeout=30&sslmode=prefer";
            case "MySQL" -> "jdbc:mysql://" + host + ":" + port + "/" + databaseName
                    + "?connectTimeout=5000&socketTimeout=30000&useSSL=true&allowPublicKeyRetrieval=true&serverTimezone=UTC";
            case "SQL Server" -> "jdbc:sqlserver://" + host + ":" + port
                    + ";databaseName=" + databaseName
                    + ";encrypt=true;trustServerCertificate=true;loginTimeout=5";
            default -> throw new IllegalArgumentException("Unsupported database type: " + dbType);
        };
    }

    private static void loadDriver(String dbType) {
        String className = switch (dbType) {
            case "PostgreSQL" -> "org.postgresql.Driver";
            case "MySQL" -> "com.mysql.cj.jdbc.Driver";
            case "SQL Server" -> "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            default -> throw new IllegalArgumentException("Unsupported database type: " + dbType);
        };

        try {
            Class.forName(className);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("JDBC driver for " + dbType + " is not available. Restart the backend so it can download drivers into backend/lib.");
        }
    }

    private static String normalizeDbType(String dbType) {
        String value = dbType == null ? "" : dbType.trim();
        if (value.equalsIgnoreCase("postgres") || value.equalsIgnoreCase("postgresql")) return "PostgreSQL";
        if (value.equalsIgnoreCase("mysql")) return "MySQL";
        if (value.equalsIgnoreCase("sqlserver") || value.equalsIgnoreCase("sql server")) return "SQL Server";
        return value;
    }

    private static String normalizePort(String dbType, String port) {
        String value = port == null ? "" : port.trim();
        if (!value.isBlank() && !"0".equals(value)) return value;
        return switch (dbType) {
            case "PostgreSQL" -> "5432";
            case "MySQL" -> "3306";
            case "SQL Server" -> "1433";
            default -> value;
        };
    }

    private static String requireValue(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.trim();
    }

    private static boolean isSystemSchema(String schema) {
        return schema != null && SYSTEM_SCHEMAS.contains(schema.toLowerCase(Locale.ROOT));
    }
}
