package com.askdb.nlp;

public interface SqlGenerator {
    GeneratedSql generate(String question, String databaseName, String previousSql);
}
