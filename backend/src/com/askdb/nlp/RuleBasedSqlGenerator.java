package com.askdb.nlp;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RuleBasedSqlGenerator implements SqlGenerator {
    private static final Pattern TOP_PATTERN = Pattern.compile("top\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DAYS_PATTERN = Pattern.compile("(\\d+)\\s+days?", Pattern.CASE_INSENSITIVE);

    @Override
    public GeneratedSql generate(String question, String databaseName, String previousSql) {
        String normalized = question == null ? "" : question.toLowerCase();
        int limit = extractNumber(TOP_PATTERN, question, 10);
        int days = extractNumber(DAYS_PATTERN, question, 30);

        if (containsAny(normalized, "haven't ordered", "have not ordered", "inactive", "no orders", "not ordered")) {
            String sql = "SELECT c.id AS customer_id, c.name AS customer_name, c.region,\n" +
                    "       SUM(oi.quantity * oi.unit_price) AS revenue,\n" +
                    "       MAX(o.order_date) AS last_order_date,\n" +
                    "       DATEDIFF('day', MAX(o.order_date), CURRENT_DATE) AS days_since_last_order\n" +
                    "FROM customers c\n" +
                    "JOIN orders o ON o.customer_id = c.id\n" +
                    "JOIN order_items oi ON oi.order_id = o.id\n" +
                    "WHERE o.status <> 'CANCELLED'\n" +
                    "GROUP BY c.id, c.name, c.region\n" +
                    "HAVING MAX(o.order_date) < CURRENT_DATE - INTERVAL '" + days + " days'\n" +
                    "ORDER BY revenue DESC\n" +
                    "LIMIT " + limit + ";";
            return new GeneratedSql("inactive_customers_by_revenue", "Top customers by revenue with no orders in " + days + " days", sql, limit, days, "");
        }

        if (containsAny(normalized, "product", "products", "sku", "category")) {
            String sql = "SELECT p.id AS product_id, p.name AS product_name, p.category,\n" +
                    "       SUM(oi.quantity) AS units_sold,\n" +
                    "       SUM(oi.quantity * oi.unit_price) AS revenue\n" +
                    "FROM products p\n" +
                    "JOIN order_items oi ON oi.product_id = p.id\n" +
                    "JOIN orders o ON o.id = oi.order_id\n" +
                    "WHERE o.status <> 'CANCELLED'\n" +
                    "GROUP BY p.id, p.name, p.category\n" +
                    "ORDER BY revenue DESC\n" +
                    "LIMIT " + limit + ";";
            return new GeneratedSql("product_revenue", "Products ranked by revenue", sql, limit, days, "");
        }

        if (containsAny(normalized, "recent orders", "latest orders", "orders")) {
            String status = extractStatus(normalized);
            String where = status.isBlank() ? "" : "WHERE o.status = '" + status + "'\n";
            String sql = "SELECT o.id AS order_id, c.name AS customer_name, o.order_date, o.status,\n" +
                    "       SUM(oi.quantity * oi.unit_price) AS order_total\n" +
                    "FROM orders o\n" +
                    "JOIN customers c ON c.id = o.customer_id\n" +
                    "JOIN order_items oi ON oi.order_id = o.id\n" +
                    where +
                    "GROUP BY o.id, c.name, o.order_date, o.status\n" +
                    "ORDER BY o.order_date DESC\n" +
                    "LIMIT " + limit + ";";
            return new GeneratedSql("recent_orders", "Recent orders" + (status.isBlank() ? "" : " with status " + status), sql, limit, days, status);
        }

        if (containsAny(normalized, "revenue", "top customers", "best customers", "customers by revenue")) {
            String sql = "SELECT c.id AS customer_id, c.name AS customer_name, c.region,\n" +
                    "       COUNT(DISTINCT o.id) AS orders,\n" +
                    "       SUM(oi.quantity * oi.unit_price) AS revenue\n" +
                    "FROM customers c\n" +
                    "JOIN orders o ON o.customer_id = c.id\n" +
                    "JOIN order_items oi ON oi.order_id = o.id\n" +
                    "WHERE o.status <> 'CANCELLED'\n" +
                    "GROUP BY c.id, c.name, c.region\n" +
                    "ORDER BY revenue DESC\n" +
                    "LIMIT " + limit + ";";
            return new GeneratedSql("customers_by_revenue", "Customers ranked by revenue", sql, limit, days, "");
        }

        String sql = "SELECT c.id AS customer_id, c.name AS customer_name, c.email, c.region\n" +
                "FROM customers c\n" +
                "ORDER BY c.name ASC\n" +
                "LIMIT " + limit + ";";
        return new GeneratedSql("customers", "Customer list", sql, limit, days, "");
    }

    private int extractNumber(Pattern pattern, String question, int fallback) {
        if (question == null) return fallback;
        Matcher matcher = pattern.matcher(question);
        if (matcher.find()) {
            try {
                return Math.max(1, Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }

    private String extractStatus(String normalized) {
        if (normalized.contains("cancelled") || normalized.contains("canceled")) return "CANCELLED";
        if (normalized.contains("shipped")) return "SHIPPED";
        if (normalized.contains("pending")) return "PENDING";
        if (normalized.contains("paid")) return "PAID";
        return "";
    }
}
