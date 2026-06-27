package com.askdb.service;

import com.askdb.db.MockDatabase;
import com.askdb.nlp.GeneratedSql;
import com.askdb.nlp.RuleBasedSqlGenerator;
import com.askdb.nlp.SqlGenerator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class QueryService {
    private final MockDatabase database;
    private final SqlGenerator sqlGenerator;
    private final List<QueryResult> history = new ArrayList<>();
    private final AtomicInteger idCounter = new AtomicInteger(1);

    public QueryService(MockDatabase database) {
        this.database = database;
        this.sqlGenerator = new RuleBasedSqlGenerator();
    }

    public synchronized QueryResult ask(String question, String databaseName, String previousSql) {
        GeneratedSql generatedSql = sqlGenerator.generate(question, databaseName, previousSql);
        MockDatabase.QueryOutput output = database.execute(generatedSql);
        QueryResult result = new QueryResult(
                idCounter.getAndIncrement(),
                Instant.now().toString(),
                databaseName,
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
}
