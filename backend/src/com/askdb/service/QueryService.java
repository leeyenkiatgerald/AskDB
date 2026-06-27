package com.askdb.service;

import com.askdb.db.MockDatabase;
import com.askdb.db.RemoteDatabase;
import com.askdb.nlp.GeneratedSql;
import com.askdb.nlp.OpenAiSqlGenerator;
import com.askdb.nlp.RuleBasedSqlGenerator;
import com.askdb.nlp.SqlGenerator;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class QueryService {
    private final MockDatabase database;
    private final SqlGenerator ruleBasedSqlGenerator;
    private final SqlGenerator aiSqlGenerator;
    private final List<QueryResult> history = new ArrayList<>();
    private final AtomicInteger idCounter = new AtomicInteger(1);
    private RemoteDatabase remoteDatabase;
    private DatabaseConnection activeConnection;
    private List<Map<String, Object>> activeSchema;

    public QueryService(MockDatabase database) {
        this.database = database;
        this.ruleBasedSqlGenerator = new RuleBasedSqlGenerator();
        this.aiSqlGenerator = new OpenAiSqlGenerator(ruleBasedSqlGenerator);
        this.activeSchema = database.schema();
        this.activeConnection = new DatabaseConnection(
                "MockDB",
                "localhost",
                "0",
                "AskDB Demo Warehouse",
                "demo_manager",
                "mock",
                Instant.now()
        );
    }

    public synchronized DatabaseConnection connect(String dbType, String host, String port, String databaseName, String username, String password) throws SQLException {
        if (dbType == null || dbType.isBlank() || "MockDB".equalsIgnoreCase(dbType.trim())) {
            remoteDatabase = null;
            activeSchema = database.schema();
            activeConnection = new DatabaseConnection(
                    "MockDB",
                    host == null || host.isBlank() ? "localhost" : host.trim(),
                    port == null || port.isBlank() ? "0" : port.trim(),
                    databaseName == null || databaseName.isBlank() ? "AskDB Demo Warehouse" : databaseName.trim(),
                    username == null || username.isBlank() ? "demo_manager" : username.trim(),
                    "mock",
                    Instant.now()
            );
            return activeConnection;
        }

        RemoteDatabase candidate = new RemoteDatabase(dbType, host, port, databaseName, username, password);
        candidate.testConnection();
        List<Map<String, Object>> candidateSchema = candidate.schema();
        remoteDatabase = candidate;
        activeSchema = candidateSchema;
        activeConnection = new DatabaseConnection(
                dbType.trim(),
                host.trim(),
                port == null || port.isBlank() || "0".equals(port.trim()) ? defaultPort(dbType) : port.trim(),
                databaseName.trim(),
                username.trim(),
                candidate.mode(),
                Instant.now()
        );
        return activeConnection;
    }

    public synchronized List<Map<String, Object>> schema() throws SQLException {
        return activeSchema;
    }

    public synchronized QueryResult ask(String question, String databaseName, String previousSql) throws SQLException {
        SqlGenerator generator = remoteDatabase == null ? ruleBasedSqlGenerator : aiSqlGenerator;
        GeneratedSql generatedSql = generator.generate(question, activeConnection.databaseName(), previousSql, activeConnection.dbType(), activeSchema);
        MockDatabase.QueryOutput output = remoteDatabase == null
                ? database.execute(generatedSql)
                : remoteDatabase.execute(generatedSql);
        QueryResult result = new QueryResult(
                idCounter.getAndIncrement(),
                Instant.now().toString(),
                activeConnection.databaseName(),
                question,
                generatedSql.summary(),
                generatedSql.sql(),
                output.columns(),
                output.rows()
        );

        history.add(0, result);
        while (history.size() > 25) {
            history.remove(history.size() - 1);
        }
        return result;
    }

    public synchronized List<QueryResult> history() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    public synchronized DatabaseConnection activeConnection() {
        return activeConnection;
    }

    private String defaultPort(String dbType) {
        String value = dbType == null ? "" : dbType.trim();
        if (value.equalsIgnoreCase("PostgreSQL")) return "5432";
        if (value.equalsIgnoreCase("MySQL")) return "3306";
        if (value.equalsIgnoreCase("SQL Server")) return "1433";
        return "0";
    }
}
