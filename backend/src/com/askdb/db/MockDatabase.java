package com.askdb.db;

import com.askdb.nlp.GeneratedSql;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class MockDatabase {
    private final List<Customer> customers;
    private final List<Product> products;
    private final List<Order> orders;
    private final List<OrderItem> orderItems;

    public MockDatabase() {
        LocalDate today = LocalDate.now();
        customers = List.of(
                new Customer(1, "Acme Retail", "ops@acmeretail.example", "West"),
                new Customer(2, "Northstar Grocery", "buyers@northstar.example", "North"),
                new Customer(3, "Bluebird Office", "finance@bluebird.example", "East"),
                new Customer(4, "Summit Outfitters", "orders@summit.example", "Mountain"),
                new Customer(5, "Harbor Foods", "supply@harbor.example", "South"),
                new Customer(6, "Metro Fitness", "team@metrofitness.example", "Central"),
                new Customer(7, "Evergreen Schools", "procurement@evergreen.example", "West"),
                new Customer(8, "Pioneer Labs", "labops@pioneer.example", "East"),
                new Customer(9, "Sunrise Hotels", "purchasing@sunrise.example", "South"),
                new Customer(10, "Canyon Bikes", "admin@canyon.example", "Mountain"),
                new Customer(11, "Lakeside Pharmacy", "inventory@lakeside.example", "North"),
                new Customer(12, "Urban Books", "stock@urbanbooks.example", "Central")
        );

        products = List.of(
                new Product(1, "Analytics Seat", "Software", 99.00),
                new Product(2, "Premium Support", "Service", 249.00),
                new Product(3, "Data Connector", "Software", 149.00),
                new Product(4, "Training Workshop", "Service", 899.00),
                new Product(5, "Usage Bundle", "Software", 399.00),
                new Product(6, "Implementation Package", "Service", 1299.00)
        );

        orders = List.of(
                new Order(1001, 1, today.minusDays(72), "SHIPPED"),
                new Order(1002, 1, today.minusDays(43), "PAID"),
                new Order(1003, 2, today.minusDays(12), "SHIPPED"),
                new Order(1004, 2, today.minusDays(3), "PENDING"),
                new Order(1005, 3, today.minusDays(88), "SHIPPED"),
                new Order(1006, 3, today.minusDays(40), "SHIPPED"),
                new Order(1007, 4, today.minusDays(21), "PAID"),
                new Order(1008, 5, today.minusDays(55), "SHIPPED"),
                new Order(1009, 6, today.minusDays(5), "PAID"),
                new Order(1010, 7, today.minusDays(64), "SHIPPED"),
                new Order(1011, 8, today.minusDays(31), "PAID"),
                new Order(1012, 9, today.minusDays(96), "CANCELLED"),
                new Order(1013, 9, today.minusDays(38), "SHIPPED"),
                new Order(1014, 10, today.minusDays(13), "PAID"),
                new Order(1015, 11, today.minusDays(47), "SHIPPED"),
                new Order(1016, 12, today.minusDays(9), "PENDING")
        );

        orderItems = List.of(
                new OrderItem(1, 1001, 1, 45, 99.00),
                new OrderItem(2, 1001, 2, 8, 249.00),
                new OrderItem(3, 1002, 5, 14, 399.00),
                new OrderItem(4, 1003, 1, 18, 99.00),
                new OrderItem(5, 1003, 3, 9, 149.00),
                new OrderItem(6, 1004, 2, 4, 249.00),
                new OrderItem(7, 1005, 6, 7, 1299.00),
                new OrderItem(8, 1006, 4, 2, 899.00),
                new OrderItem(9, 1006, 5, 11, 399.00),
                new OrderItem(10, 1007, 3, 19, 149.00),
                new OrderItem(11, 1008, 1, 35, 99.00),
                new OrderItem(12, 1008, 6, 4, 1299.00),
                new OrderItem(13, 1009, 5, 6, 399.00),
                new OrderItem(14, 1010, 4, 5, 899.00),
                new OrderItem(15, 1010, 2, 12, 249.00),
                new OrderItem(16, 1011, 6, 3, 1299.00),
                new OrderItem(17, 1012, 1, 8, 99.00),
                new OrderItem(18, 1013, 3, 15, 149.00),
                new OrderItem(19, 1013, 5, 8, 399.00),
                new OrderItem(20, 1014, 1, 12, 99.00),
                new OrderItem(21, 1015, 2, 6, 249.00),
                new OrderItem(22, 1015, 6, 5, 1299.00),
                new OrderItem(23, 1016, 3, 10, 149.00),
                new OrderItem(24, 1016, 5, 3, 399.00)
        );
    }

    public List<Map<String, Object>> schema() {
        List<Map<String, Object>> tables = new ArrayList<>();
        tables.add(table("customers", "Business accounts and regional ownership", List.of("id", "name", "email", "region")));
        tables.add(table("orders", "Order header records tied to customers", List.of("id", "customer_id", "order_date", "status")));
        tables.add(table("order_items", "Line items connecting orders and products", List.of("id", "order_id", "product_id", "quantity", "unit_price")));
        tables.add(table("products", "Products and services sold", List.of("id", "name", "category", "unit_price")));
        return tables;
    }

    public QueryOutput execute(GeneratedSql generatedSql) {
        return switch (generatedSql.intent()) {
            case "inactive_customers_by_revenue" -> inactiveCustomersByRevenue(generatedSql.days(), generatedSql.limit());
            case "customers_by_revenue" -> customersByRevenue(generatedSql.limit());
            case "product_revenue" -> productRevenue(generatedSql.limit());
            case "recent_orders" -> recentOrders(generatedSql.limit(), generatedSql.status());
            default -> customers(generatedSql.limit());
        };
    }

    private QueryOutput inactiveCustomersByRevenue(int days, int limit) {
        LocalDate cutoff = LocalDate.now().minusDays(days);
        Set<Integer> validOrderIds = orders.stream()
                .filter(order -> !"CANCELLED".equals(order.status()))
                .map(Order::id)
                .collect(Collectors.toSet());

        List<Map<String, Object>> rows = customerMetrics().stream()
                .filter(row -> row.lastOrderDate() != null && row.lastOrderDate().isBefore(cutoff))
                .sorted(Comparator.comparing(CustomerMetric::revenue).reversed())
                .limit(limit)
                .map(row -> {
                    Map<String, Object> map = baseCustomerMetricRow(row);
                    map.put("days_since_last_order", ChronoUnit.DAYS.between(row.lastOrderDate(), LocalDate.now()));
                    return map;
                })
                .filter(row -> validOrderIds.size() > 0)
                .toList();
        return new QueryOutput(List.of("customer_id", "customer_name", "region", "revenue", "last_order_date", "days_since_last_order", "orders"), rows);
    }

    private QueryOutput customersByRevenue(int limit) {
        List<Map<String, Object>> rows = customerMetrics().stream()
                .sorted(Comparator.comparing(CustomerMetric::revenue).reversed())
                .limit(limit)
                .map(this::baseCustomerMetricRow)
                .toList();
        return new QueryOutput(List.of("customer_id", "customer_name", "region", "revenue", "last_order_date", "orders"), rows);
    }

    private QueryOutput productRevenue(int limit) {
        Map<Integer, ProductMetric> metrics = new HashMap<>();
        Map<Integer, Order> orderById = orders.stream().collect(Collectors.toMap(Order::id, order -> order));

        for (OrderItem item : orderItems) {
            Order order = orderById.get(item.orderId());
            if (order == null || "CANCELLED".equals(order.status())) continue;
            Product product = productById(item.productId());
            ProductMetric metric = metrics.computeIfAbsent(product.id(), id -> new ProductMetric(product, 0, 0));
            metric.units += item.quantity();
            metric.revenue += item.quantity() * item.unitPrice();
        }

        List<Map<String, Object>> rows = metrics.values().stream()
                .sorted(Comparator.comparing(ProductMetric::revenue).reversed())
                .limit(limit)
                .map(metric -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("product_id", metric.product.id());
                    map.put("product_name", metric.product.name());
                    map.put("category", metric.product.category());
                    map.put("units_sold", metric.units);
                    map.put("revenue", money(metric.revenue));
                    return map;
                })
                .toList();
        return new QueryOutput(List.of("product_id", "product_name", "category", "units_sold", "revenue"), rows);
    }

    private QueryOutput recentOrders(int limit, String status) {
        Map<Integer, Customer> customerById = customers.stream().collect(Collectors.toMap(Customer::id, customer -> customer));
        List<Map<String, Object>> rows = orders.stream()
                .filter(order -> status == null || status.isBlank() || Objects.equals(order.status(), status))
                .sorted(Comparator.comparing(Order::orderDate).reversed())
                .limit(limit)
                .map(order -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("order_id", order.id());
                    map.put("customer_name", customerById.get(order.customerId()).name());
                    map.put("order_date", order.orderDate().toString());
                    map.put("status", order.status());
                    map.put("order_total", money(orderTotal(order.id())));
                    return map;
                })
                .toList();
        return new QueryOutput(List.of("order_id", "customer_name", "order_date", "status", "order_total"), rows);
    }

    private QueryOutput customers(int limit) {
        List<Map<String, Object>> rows = customers.stream()
                .sorted(Comparator.comparing(Customer::name))
                .limit(limit)
                .map(customer -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("customer_id", customer.id());
                    map.put("customer_name", customer.name());
                    map.put("email", customer.email());
                    map.put("region", customer.region());
                    return map;
                })
                .toList();
        return new QueryOutput(List.of("customer_id", "customer_name", "email", "region"), rows);
    }

    private List<CustomerMetric> customerMetrics() {
        Map<Integer, Customer> customerById = customers.stream().collect(Collectors.toMap(Customer::id, customer -> customer));
        Map<Integer, CustomerMetric> metrics = new HashMap<>();
        Map<Integer, Order> orderById = orders.stream().collect(Collectors.toMap(Order::id, order -> order));

        for (Order order : orders) {
            if ("CANCELLED".equals(order.status())) continue;
            Customer customer = customerById.get(order.customerId());
            metrics.computeIfAbsent(customer.id(), id -> new CustomerMetric(customer, 0, null, 0));
            CustomerMetric metric = metrics.get(customer.id());
            metric.orders += 1;
            if (metric.lastOrderDate == null || order.orderDate().isAfter(metric.lastOrderDate)) {
                metric.lastOrderDate = order.orderDate();
            }
        }

        for (OrderItem item : orderItems) {
            Order order = orderById.get(item.orderId());
            if (order == null || "CANCELLED".equals(order.status())) continue;
            Customer customer = customerById.get(order.customerId());
            CustomerMetric metric = metrics.computeIfAbsent(customer.id(), id -> new CustomerMetric(customer, 0, null, 0));
            metric.revenue += item.quantity() * item.unitPrice();
        }

        return new ArrayList<>(metrics.values());
    }

    private Map<String, Object> baseCustomerMetricRow(CustomerMetric metric) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("customer_id", metric.customer.id());
        map.put("customer_name", metric.customer.name());
        map.put("region", metric.customer.region());
        map.put("revenue", money(metric.revenue()));
        map.put("last_order_date", metric.lastOrderDate() == null ? "" : metric.lastOrderDate().toString());
        map.put("orders", metric.orders());
        return map;
    }

    private double orderTotal(int orderId) {
        return orderItems.stream()
                .filter(item -> item.orderId() == orderId)
                .mapToDouble(item -> item.quantity() * item.unitPrice())
                .sum();
    }

    private Product productById(int productId) {
        return products.stream()
                .filter(product -> product.id() == productId)
                .findFirst()
                .orElseThrow();
    }

    private Map<String, Object> table(String name, String description, List<String> columns) {
        Map<String, Object> table = new LinkedHashMap<>();
        table.put("name", name);
        table.put("description", description);
        table.put("columns", columns);
        return table;
    }

    private double money(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record QueryOutput(List<String> columns, List<Map<String, Object>> rows) {
    }

    private record Customer(int id, String name, String email, String region) {
    }

    private record Product(int id, String name, String category, double unitPrice) {
    }

    private record Order(int id, int customerId, LocalDate orderDate, String status) {
    }

    private record OrderItem(int id, int orderId, int productId, int quantity, double unitPrice) {
    }

    private static class CustomerMetric {
        private final Customer customer;
        private double revenue;
        private LocalDate lastOrderDate;
        private int orders;

        private CustomerMetric(Customer customer, double revenue, LocalDate lastOrderDate, int orders) {
            this.customer = customer;
            this.revenue = revenue;
            this.lastOrderDate = lastOrderDate;
            this.orders = orders;
        }

        public Customer customer() {
            return customer;
        }

        public double revenue() {
            return revenue;
        }

        public LocalDate lastOrderDate() {
            return lastOrderDate;
        }

        public int orders() {
            return orders;
        }
    }

    private static class ProductMetric {
        private final Product product;
        private int units;
        private double revenue;

        private ProductMetric(Product product, int units, double revenue) {
            this.product = product;
            this.units = units;
            this.revenue = revenue;
        }

        public double revenue() {
            return revenue;
        }
    }
}
