package com.askdb.nlp;

public record GeneratedSql(
        String intent,
        String summary,
        String sql,
        int limit,
        int days,
        String status
) {
}
