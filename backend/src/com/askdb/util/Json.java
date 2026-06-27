package com.askdb.util;

import java.util.Iterator;
import java.util.Map;

public final class Json {
    private Json() {
    }

    public static String extractString(String json, String key, String fallback) {
        if (json == null || key == null) return fallback;
        String quotedKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(quotedKey);
        if (keyIndex < 0) return fallback;
        int colonIndex = json.indexOf(':', keyIndex + quotedKey.length());
        if (colonIndex < 0) return fallback;
        int valueIndex = colonIndex + 1;
        while (valueIndex < json.length() && Character.isWhitespace(json.charAt(valueIndex))) {
            valueIndex++;
        }
        if (valueIndex >= json.length()) return fallback;

        if (json.charAt(valueIndex) == '"') {
            StringBuilder builder = new StringBuilder();
            boolean escaping = false;
            for (int i = valueIndex + 1; i < json.length(); i++) {
                char current = json.charAt(i);
                if (escaping) {
                    builder.append(unescapeChar(current));
                    escaping = false;
                } else if (current == '\\') {
                    escaping = true;
                } else if (current == '"') {
                    return builder.toString();
                } else {
                    builder.append(current);
                }
            }
            return fallback;
        }

        int endIndex = valueIndex;
        while (endIndex < json.length() && json.charAt(endIndex) != ',' && json.charAt(endIndex) != '}') {
            endIndex++;
        }
        String raw = json.substring(valueIndex, endIndex).trim();
        return raw.isBlank() || "null".equals(raw) ? fallback : raw;
    }

    public static String stringify(Object value) {
        if (value == null) return "null";
        if (value instanceof String string) return quote(string);
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) return object(map);
        if (value instanceof Iterable<?> iterable) return array(iterable);
        return quote(value.toString());
    }

    private static String object(Map<?, ?> map) {
        StringBuilder builder = new StringBuilder("{");
        Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<?, ?> entry = iterator.next();
            builder.append(quote(String.valueOf(entry.getKey()))).append(':').append(stringify(entry.getValue()));
            if (iterator.hasNext()) builder.append(',');
        }
        return builder.append('}').toString();
    }

    private static String array(Iterable<?> iterable) {
        StringBuilder builder = new StringBuilder("[");
        Iterator<?> iterator = iterable.iterator();
        while (iterator.hasNext()) {
            builder.append(stringify(iterator.next()));
            if (iterator.hasNext()) builder.append(',');
        }
        return builder.append(']').toString();
    }

    private static String quote(String value) {
        StringBuilder builder = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            switch (current) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (current < 32) {
                        builder.append(String.format("\\u%04x", (int) current));
                    } else {
                        builder.append(current);
                    }
                }
            }
        }
        return builder.append('"').toString();
    }

    private static char unescapeChar(char current) {
        return switch (current) {
            case '"' -> '"';
            case '\\' -> '\\';
            case '/' -> '/';
            case 'b' -> '\b';
            case 'f' -> '\f';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            default -> current;
        };
    }
}
