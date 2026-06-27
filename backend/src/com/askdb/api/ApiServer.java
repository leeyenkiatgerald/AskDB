package com.askdb.api;

import com.askdb.db.MockDatabase;
import com.askdb.service.DatabaseConnection;
import com.askdb.service.QueryResult;
import com.askdb.service.QueryService;
import com.askdb.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class ApiServer {
    private final int port;
    private final HttpServer server;
    private final MockDatabase database;
    private final QueryService queryService;

    public ApiServer(int port) throws IOException {
        this.port = port;
        this.database = new MockDatabase();
        this.queryService = new QueryService(database);
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.createContext("/api/health", this::handleHealth);
        this.server.createContext("/api/schema", this::handleSchema);
        this.server.createContext("/api/history", this::handleHistory);
        this.server.createContext("/api/connect", this::handleConnect);
        this.server.createContext("/api/query", this::handleQuery);
        this.server.createContext("/", this::handleNotFound);
        this.server.setExecutor(Executors.newFixedThreadPool(8));
    }

    public void start() {
        server.start();
        System.out.println("AskDB backend running at http://localhost:" + port);
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (preflight(exchange)) return;
        if (!requireMethod(exchange, "GET")) return;
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("service", "AskDB Backend");
        response.put("message", "Ready to translate plain English into SQL");
        response.put("timestamp", Instant.now().toString());
        sendJson(exchange, 200, response);
    }

    private void handleSchema(HttpExchange exchange) throws IOException {
        if (preflight(exchange)) return;
        if (!requireMethod(exchange, "GET")) return;
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            response.put("connection", queryService.activeConnection().toMap());
            response.put("tables", queryService.schema());
        } catch (SQLException exception) {
            sendError(exchange, 502, "Connected database is reachable, but schema could not be loaded: " + exception.getMessage());
            return;
        }
        sendJson(exchange, 200, response);
    }

    private void handleHistory(HttpExchange exchange) throws IOException {
        if (preflight(exchange)) return;
        if (!requireMethod(exchange, "GET")) return;
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", queryService.history().stream().map(QueryResult::toMap).toList());
        sendJson(exchange, 200, response);
    }

    private void handleConnect(HttpExchange exchange) throws IOException {
        if (preflight(exchange)) return;
        if (!requireMethod(exchange, "POST")) return;
        String body = readBody(exchange);

        String dbType = Json.extractString(body, "dbType", "MockDB");
        String host = Json.extractString(body, "host", "localhost");
        String portValue = Json.extractString(body, "port", "0");
        String databaseName = Json.extractString(body, "databaseName", "AskDB Demo Warehouse");
        String username = Json.extractString(body, "username", "demo_manager");
        String password = Json.extractString(body, "password", "");

        Map<String, Object> response = new LinkedHashMap<>();
        try {
            DatabaseConnection connection = queryService.connect(dbType, host, portValue, databaseName, username, password);
            response.put("ok", true);
            response.put("message", connection.mode().equals("mock")
                    ? "Connected to mock database."
                    : "Connected to remote " + connection.dbType() + " database.");
            response.put("connection", connection.toMap());
            response.put("tables", queryService.schema());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            sendError(exchange, 400, exception.getMessage());
            return;
        } catch (SQLException exception) {
            sendError(exchange, 502, "Could not connect to remote database: " + exception.getMessage());
            return;
        }
        sendJson(exchange, 200, response);
    }

    private void handleQuery(HttpExchange exchange) throws IOException {
        if (preflight(exchange)) return;
        if (!requireMethod(exchange, "POST")) return;
        String body = readBody(exchange);
        String question = Json.extractString(body, "question", "").trim();
        String databaseName = Json.extractString(body, "databaseName", "AskDB Demo Warehouse");
        String previousSql = Json.extractString(body, "previousSql", "");

        if (question.isBlank()) {
            sendError(exchange, 400, "Question is required.");
            return;
        }

        try {
            QueryResult result = queryService.ask(question, databaseName, previousSql);
            sendJson(exchange, 200, result.toMap());
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, exception.getMessage());
        } catch (SQLException exception) {
            sendError(exchange, 502, "Remote query failed: " + exception.getMessage());
        }
    }

    private void handleNotFound(HttpExchange exchange) throws IOException {
        if (preflight(exchange)) return;
        sendError(exchange, 404, "Route not found.");
    }

    private boolean preflight(HttpExchange exchange) throws IOException {
        addCors(exchange);
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    private boolean requireMethod(HttpExchange exchange, String method) throws IOException {
        if (!method.equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed. Use " + method + ".");
            return false;
        }
        return true;
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream inputStream = exchange.getRequestBody()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", false);
        response.put("error", message);
        sendJson(exchange, statusCode, response);
    }

    private void sendJson(HttpExchange exchange, int statusCode, Object payload) throws IOException {
        addCors(exchange);
        byte[] bytes = Json.stringify(payload).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private void addCors(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }
}
