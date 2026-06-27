package com.askdb.nlp;

import com.askdb.util.Json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OpenAiSqlGenerator implements SqlGenerator {
    private final SqlGenerator fallback;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;

    public OpenAiSqlGenerator(SqlGenerator fallback) {
        this.fallback = fallback;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.apiKey = System.getenv("OPENAI_API_KEY");
        this.model = System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o-mini");
    }

    @Override
    public GeneratedSql generate(String question, String databaseName, String previousSql, String dbType, List<Map<String, Object>> schema) {
        if (apiKey == null || apiKey.isBlank()) {
            return fallback.generate(question, databaseName, previousSql, dbType, schema);
        }

        try {
            AiSql aiSql = requestSql(question, databaseName, previousSql, dbType, schema);
            int limit = extractLimit(question);
            return new GeneratedSql("ai_sql", aiSql.summary(), aiSql.sql(), limit, 30, "");
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("AI SQL generation failed: " + exception.getMessage(), exception);
        }
    }

    private AiSql requestSql(String question, String databaseName, String previousSql, String dbType, List<Map<String, Object>> schema) throws IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("store", false);
        body.put("temperature", 0);
        body.put("instructions", instructions(dbType));
        body.put("input", prompt(question, databaseName, previousSql, schema));
        body.put("text", structuredOutputFormat());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/responses"))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String error = Json.extractString(response.body(), "message", response.body());
            throw new IllegalStateException("OpenAI API returned " + response.statusCode() + ": " + error);
        }

        String outputText = Json.extractString(response.body(), "text", "");
        if (outputText.isBlank()) {
            throw new IllegalStateException("OpenAI API response did not include generated SQL.");
        }

        String sql = Json.extractString(outputText, "sql", "").trim();
        String summary = Json.extractString(outputText, "summary", "Generated SQL").trim();
        if (sql.isBlank()) {
            throw new IllegalStateException("OpenAI API returned an empty SQL query.");
        }
        validateReadOnly(sql);
        return new AiSql(summary.isBlank() ? "Generated SQL" : summary, sql);
    }

    private String instructions(String dbType) {
        return "You translate business questions into safe read-only SQL for " + dbType + ". "
                + "Use only the provided schema. Return exactly one SQL statement. "
                + "Only generate SELECT or WITH queries. Never generate INSERT, UPDATE, DELETE, DROP, ALTER, CREATE, TRUNCATE, GRANT, REVOKE, COPY, CALL, EXEC, or multiple statements. "
                + "Prefer clear aliases. Use the SQL dialect for the database type. "
                + "For PostgreSQL use LIMIT for top-N results and CURRENT_DATE - INTERVAL 'N days' for date windows.";
    }

    private String prompt(String question, String databaseName, String previousSql, List<Map<String, Object>> schema) {
        return "Database: " + databaseName + "\n"
                + "Schema:\n" + schemaText(schema) + "\n\n"
                + "Previous SQL, if relevant:\n" + (previousSql == null || previousSql.isBlank() ? "(none)" : previousSql) + "\n\n"
                + "User question:\n" + question;
    }

    private String schemaText(List<Map<String, Object>> schema) {
        if (schema == null || schema.isEmpty()) {
            return "(no schema available)";
        }

        StringBuilder builder = new StringBuilder();
        for (Map<String, Object> table : schema) {
            builder.append("- ").append(table.getOrDefault("name", "unknown_table")).append(": ");
            Object columns = table.get("columns");
            if (columns instanceof Iterable<?> iterable) {
                boolean first = true;
                for (Object column : iterable) {
                    if (!first) builder.append(", ");
                    builder.append(column);
                    first = false;
                }
            } else {
                builder.append(columns == null ? "(no columns)" : columns);
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    private Map<String, Object> structuredOutputFormat() {
        Map<String, Object> sqlProperty = new LinkedHashMap<>();
        sqlProperty.put("type", "string");
        sqlProperty.put("description", "A single read-only SQL query.");

        Map<String, Object> summaryProperty = new LinkedHashMap<>();
        summaryProperty.put("type", "string");
        summaryProperty.put("description", "A short user-facing description of what the SQL returns.");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("summary", summaryProperty);
        properties.put("sql", sqlProperty);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("summary", "sql"));
        schema.put("additionalProperties", false);

        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.put("name", "sql_query");
        format.put("strict", true);
        format.put("schema", schema);

        Map<String, Object> text = new LinkedHashMap<>();
        text.put("format", format);
        return text;
    }

    private void validateReadOnly(String sql) {
        String normalized = sql.trim();
        String lower = normalized.toLowerCase();
        if (!(lower.startsWith("select ") || lower.startsWith("with "))) {
            throw new IllegalStateException("AI generated non-read-only SQL.");
        }
        if (normalized.endsWith(";")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.contains(";")) {
            throw new IllegalStateException("AI generated multiple SQL statements.");
        }
    }

    private int extractLimit(String question) {
        if (question == null) return 10;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("top\\s+(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(question);
        if (!matcher.find()) return 10;
        try {
            return Math.max(1, Integer.parseInt(matcher.group(1)));
        } catch (NumberFormatException exception) {
            return 10;
        }
    }

    private record AiSql(String summary, String sql) {
    }
}
