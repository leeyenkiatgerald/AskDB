package com.askdb.nlp;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RuleBasedSqlGenerator implements SqlGenerator {
    private static final Pattern TOP_PATTERN = Pattern.compile("top\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DAYS_PATTERN = Pattern.compile("(\\d+)\\s+days?", Pattern.CASE_INSENSITIVE);

    @Override
    public GeneratedSql generate(String question, String databaseName, String previousSql, String dbType, List<Map<String, Object>> schema) {
        String normalized = question == null ? "" : question.toLowerCase();
        int limit = extractNumber(TOP_PATTERN, question, 10);
        int days = extractNumber(DAYS_PATTERN, question, 30);

        if (hasTable(schema, "car_sales")) {
            return generateCarSalesSql(normalized, dbType, limit, days);
        }

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

    private GeneratedSql generateCarSalesSql(String normalized, String dbType, int limit, int days) {
        String limitClause = limitClause(dbType, limit);
        String topPrefix = topPrefix(dbType, limit);
        String daysSinceLastSale = daysSinceLastSaleSql(dbType);
        String staleSalePredicate = staleDatePredicate(dbType, days);

        if (containsAny(normalized, "haven't ordered", "have not ordered", "inactive", "no orders", "not ordered", "haven't bought", "not bought")) {
            String sql = "SELECT " + topPrefix + "customer_name,\n" +
                    "       COUNT(*) AS purchases,\n" +
                    "       SUM(price) AS revenue,\n" +
                    "       MAX(sale_date) AS last_purchase_date,\n" +
                    "       " + daysSinceLastSale + " AS days_since_last_purchase\n" +
                    "FROM car_sales\n" +
                    "GROUP BY customer_name\n" +
                    "HAVING MAX(sale_date) < " + staleSalePredicate + "\n" +
                    "ORDER BY revenue DESC" + limitClause + ";";
            return new GeneratedSql("car_sales_inactive_customers", "Top car customers by revenue with no purchases in " + days + " days", sql, limit, days, "");
        }

        if (containsAny(normalized, "revenue", "top customers", "best customers", "customers by revenue", "customer")) {
            String sql = "SELECT " + topPrefix + "customer_name,\n" +
                    "       COUNT(*) AS purchases,\n" +
                    "       SUM(price) AS revenue,\n" +
                    "       MAX(sale_date) AS last_purchase_date\n" +
                    "FROM car_sales\n" +
                    "GROUP BY customer_name\n" +
                    "ORDER BY revenue DESC" + limitClause + ";";
            return new GeneratedSql("car_sales_customers_by_revenue", "Car customers ranked by revenue", sql, limit, days, "");
        }

        if (containsAny(normalized, "recent", "latest", "sales", "orders", "purchases")) {
            String sql = "SELECT " + topPrefix + "car_id, sale_date, customer_name, company, model, body_style, dealer_name, dealer_region, price\n" +
                    "FROM car_sales\n" +
                    "ORDER BY sale_date DESC, car_id DESC" + limitClause + ";";
            return new GeneratedSql("car_sales_recent_sales", "Recent car sales", sql, limit, days, "");
        }

        if (containsAny(normalized, "car", "cars", "model", "models", "company", "brand", "product", "products")) {
            String sql = "SELECT " + topPrefix + "company, model,\n" +
                    "       COUNT(*) AS units_sold,\n" +
                    "       SUM(price) AS revenue,\n" +
                    "       ROUND(AVG(price), 2) AS average_price\n" +
                    "FROM car_sales\n" +
                    "GROUP BY company, model\n" +
                    "ORDER BY revenue DESC" + limitClause + ";";
            return new GeneratedSql("car_sales_models_by_revenue", "Car models ranked by revenue", sql, limit, days, "");
        }

        if (containsAny(normalized, "region", "dealer region", "location")) {
            String sql = "SELECT " + topPrefix + "dealer_region,\n" +
                    "       COUNT(*) AS sales,\n" +
                    "       SUM(price) AS revenue,\n" +
                    "       ROUND(AVG(price), 2) AS average_price\n" +
                    "FROM car_sales\n" +
                    "GROUP BY dealer_region\n" +
                    "ORDER BY revenue DESC" + limitClause + ";";
            return new GeneratedSql("car_sales_regions_by_revenue", "Dealer regions ranked by revenue", sql, limit, days, "");
        }

        String sql = "SELECT " + topPrefix + "car_id, sale_date, customer_name, company, model, price, dealer_region\n" +
                "FROM car_sales\n" +
                "ORDER BY sale_date DESC, car_id DESC" + limitClause + ";";
        return new GeneratedSql("car_sales_list", "Car sales list", sql, limit, days, "");
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

    private boolean hasTable(List<Map<String, Object>> schema, String tableName) {
        if (schema == null) return false;
        for (Map<String, Object> table : schema) {
            Object name = table.get("name");
            if (name != null && tableName.equalsIgnoreCase(name.toString())) return true;
        }
        return false;
    }

    private String topPrefix(String dbType, int limit) {
        return isSqlServer(dbType) ? "TOP " + limit + " " : "";
    }

    private String limitClause(String dbType, int limit) {
        return isSqlServer(dbType) ? "" : "\nLIMIT " + limit;
    }

    private String daysSinceLastSaleSql(String dbType) {
        if (isSqlServer(dbType)) return "DATEDIFF(day, MAX(sale_date), GETDATE())";
        if (isMySql(dbType)) return "DATEDIFF(CURRENT_DATE, MAX(sale_date))";
        return "CURRENT_DATE - MAX(sale_date)";
    }

    private String staleDatePredicate(String dbType, int days) {
        if (isSqlServer(dbType)) return "DATEADD(day, -" + days + ", CAST(GETDATE() AS date))";
        if (isMySql(dbType)) return "CURRENT_DATE - INTERVAL " + days + " DAY";
        return "CURRENT_DATE - INTERVAL '" + days + " days'";
    }

    private boolean isSqlServer(String dbType) {
        return dbType != null && dbType.toLowerCase(Locale.ROOT).contains("sql server");
    }

    private boolean isMySql(String dbType) {
        return dbType != null && dbType.toLowerCase(Locale.ROOT).contains("mysql");
    }
}
