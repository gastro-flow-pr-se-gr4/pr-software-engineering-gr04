package at.gastroflow.backend.api;

import at.gastroflow.backend.dao.CourseDAO;
import at.gastroflow.backend.dao.OrderDAO;
import at.gastroflow.backend.dto.BillDTO;
import at.gastroflow.backend.dto.CreateOrderRequest;
import at.gastroflow.backend.dto.OrderDTO;
import at.gastroflow.backend.mapper.OrderMapper;
import at.gastroflow.backend.model.Order;
import at.gastroflow.backend.model.Course;
import at.gastroflow.backend.model.OrderStatus;
import at.gastroflow.backend.service.CourseService;
import at.gastroflow.backend.service.KitchenService;
import at.gastroflow.backend.service.OrderService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApiServer {

    public static final int DEFAULT_PORT = 8090;
    public static final String HOST = "127.0.0.1";

    private static final Pattern ORDER = Pattern.compile("/api/orders/([0-9a-fA-F-]{36})");
    private static final Pattern ORDER_ACTION = Pattern.compile("/api/orders/([0-9a-fA-F-]{36})/(status|cancel)");
    private static final Pattern TABLE_ACTION = Pattern.compile("/api/tables/(\\d+)/(bill|pay)");
    private static final Pattern KITCHEN_ACTION = Pattern.compile("/api/kitchen/(\\d+)/(next|ready|close)");

    private final OrderService orderService;
    private final KitchenService kitchenService;
    private final CourseService courseService;
    private final OrderDAO schema;
    private final OrderMapper mapper = new OrderMapper();
    private HttpServer server;
    private volatile boolean tablesReady = false;

    public ApiServer() {
        this(new OrderDAO(), new CourseDAO());
    }

    public ApiServer(OrderDAO orderDAO, CourseDAO courseDAO) {
        this.courseService = new CourseService(courseDAO);
        this.orderService = new OrderService(orderDAO, courseService);
        this.kitchenService = new KitchenService(orderService);
        this.schema = orderDAO;
    }

    private void ensureTables() throws SQLException {
        if (!tablesReady && schema != null) {
            synchronized (this) {
                if (!tablesReady) {
                    schema.createTables();
                    tablesReady = true;
                }
            }
        }
    }

    public synchronized int start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(HOST, port), 0);
        server.createContext("/api", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "gastroflow-api");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
        return server.getAddress().getPort();
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public static void main(String[] args) throws Exception {
        int port = new ApiServer().start(DEFAULT_PORT);
        System.out.println("GastroFlow REST-API läuft auf http://" + HOST + ":" + port + "/api");
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath().replaceAll("/+$", "");
            Matcher matcher;

            if (path.equals("/api/health") && method.equals("GET")) {
                send(exchange, 200, Map.of("status", "ok"));
                return;
            }
            ensureTables();

            if (path.equals("/api/orders") && method.equals("GET")) {
                send(exchange, 200, listOrders(query(exchange.getRequestURI())));

            } else if (path.equals("/api/orders") && method.equals("POST")) {
                CreateOrderRequest request = read(exchange, CreateOrderRequest.class);
                send(exchange, 201, toDTOs(orderService.createOrder(request)));

            } else if ((matcher = ORDER.matcher(path)).matches() && method.equals("GET")) {
                send(exchange, 200, mapper.toDTO(orderService.getOrder(UUID.fromString(matcher.group(1)))));

            } else if ((matcher = ORDER_ACTION.matcher(path)).matches() && method.equals("POST")) {
                UUID id = UUID.fromString(matcher.group(1));
                Order order;
                if (matcher.group(2).equals("status")) {
                    JsonNode body = read(exchange, JsonNode.class);
                    order = orderService.changeStatus(id, parseStatus(body == null ? null : body.path("status").asText(null)));
                } else {
                    order = orderService.cancel(id);
                }
                send(exchange, 200, mapper.toDTO(order));

            } else if ((matcher = TABLE_ACTION.matcher(path)).matches()) {
                int table = Integer.parseInt(matcher.group(1));
                if (matcher.group(2).equals("bill") && method.equals("GET")) {
                    send(exchange, 200, new BillDTO(table, orderService.getAmountDue(table),
                            toDTOs(orderService.getActiveOrders(table))));
                } else if (matcher.group(2).equals("pay") && method.equals("POST")) {
                    send(exchange, 200, Map.of("table", table, "paid", orderService.payTable(table)));
                } else {
                    error(exchange, 405, "Diese Methode ist hier nicht erlaubt.");
                }

            } else if (path.equals("/api/kitchen") && method.equals("GET")) {
                send(exchange, 200, kitchenService.getTables());

            } else if ((matcher = KITCHEN_ACTION.matcher(path)).matches() && method.equals("POST")) {
                int table = Integer.parseInt(matcher.group(1));
                switch (matcher.group(2)) {
                    case "next" -> kitchenService.startNextCourse(table);
                    case "ready" -> kitchenService.markCourseReady(table);
                    default -> kitchenService.closeTable(table);
                }
                send(exchange, 200, kitchenService.getTable(table));

            } else if (path.equals("/api/courses/lookup") && method.equals("POST")) {
                JsonNode body = read(exchange, JsonNode.class);
                Map<String, Course> result = new java.util.LinkedHashMap<>();
                if (body != null) {
                    for (JsonNode category : body.path("categories")) {
                        result.put(category.asText(), courseService.getCourse(category.asText()));
                    }
                }
                send(exchange, 200, result);

            } else if (path.equals("/api/courses") && method.equals("POST")) {
                JsonNode body = read(exchange, JsonNode.class);
                if (body == null) {
                    throw new IllegalArgumentException("Bitte Kategorie und Gang angeben.");
                }
                courseService.setCourse(body.path("category").asText(null), parseCourse(body.path("course").asText(null)));
                send(exchange, 200, Map.of("ok", true));

            } else if (path.equals("/api/courses/rename") && method.equals("POST")) {
                JsonNode body = read(exchange, JsonNode.class);
                if (body == null || body.path("from").isMissingNode() || body.path("to").isMissingNode()) {
                    throw new IllegalArgumentException("Bitte alten und neuen Namen angeben.");
                }
                courseService.renameCategory(body.path("from").asText(), body.path("to").asText());
                send(exchange, 200, Map.of("ok", true));

            } else {
                error(exchange, 404, "Unbekannte Adresse: " + method + " " + path);
            }

        } catch (IllegalArgumentException e) {
            error(exchange, 400, e.getMessage());
        } catch (NoSuchElementException e) {
            error(exchange, 404, e.getMessage());
        } catch (IllegalStateException e) {
            error(exchange, 409, e.getMessage());
        } catch (SQLException e) {
            error(exchange, 503, "Datenbank nicht erreichbar. Läuft Docker (docker compose up -d)? " + e.getMessage());
        } catch (RuntimeException e) {
            error(exchange, 500, "Interner Fehler: " + e);
        } finally {
            exchange.close();
        }
    }

    private List<OrderDTO> listOrders(Map<String, String> query) throws SQLException {
        List<Order> orders;
        if ("true".equals(query.get("active"))) {
            orders = orderService.getActiveOrders();
        } else if ("true".equals(query.get("open"))) {
            orders = orderService.getOpenOrders();
        } else if (query.containsKey("date")) {
            orders = orderService.getOrdersOn(parseDate(query.get("date")));
        } else {
            orders = orderService.getAllOrders();
        }
        if (query.containsKey("table")) {
            int table = parseInt(query.get("table"));
            Predicate<Order> sameTable = order -> order.getTableNumber() == table;
            orders = orders.stream().filter(sameTable).toList();
        }
        return toDTOs(orders);
    }

    private List<OrderDTO> toDTOs(List<Order> orders) {
        return orders.stream().map(mapper::toDTO).toList();
    }

    private static OrderStatus parseStatus(String text) {
        try {
            return OrderStatus.valueOf(text);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Unbekannter Status: " + text);
        }
    }

    private static Course parseCourse(String text) {
        try {
            return Course.valueOf(text);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Unbekannter Gang: " + text);
        }
    }

    private static LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Ungültiges Datum: " + text + " (Format 2026-11-26)");
        }
    }

    private static int parseInt(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Ungültige Zahl: " + text);
        }
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> result = new HashMap<>();
        String raw = uri.getRawQuery();
        if (raw == null) {
            return result;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                result.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return result;
    }

    private static <T> T read(HttpExchange exchange, Class<T> type) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        if (body.length == 0) {
            return null;
        }
        try {
            return Json.MAPPER.readValue(body, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Ungültige Anfrage (JSON): " + e.getOriginalMessage());
        }
    }

    private static void send(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] json = Json.MAPPER.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, json.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(json);
        }
    }

    private static void error(HttpExchange exchange, int status, String message) throws IOException {
        send(exchange, status, Map.of("error", message == null ? "Unbekannter Fehler" : message));
    }
}
