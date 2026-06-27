package com.askdb.nlp;

import java.util.List;
import java.util.Map;

public interface SqlGenerator {
    GeneratedSql generate(String question, String databaseName, String previousSql, String dbType, List<Map<String, Object>> schema);
}
