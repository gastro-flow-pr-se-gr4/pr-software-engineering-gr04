package at.gastroflow.backend.api;

import at.gastroflow.backend.dto.CreateOrderRequest;
import at.gastroflow.backend.dto.OrderDTO;
import at.gastroflow.backend.dto.OrderItemDTO;
import at.gastroflow.backend.model.OrderStatus;
import at.gastroflow.backend.service.InMemoryOrderDAO;
import at.gastroflow.backend.service.InMemoryCourseDAO;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiServerTest {

    private ApiServer server;
    private String base;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() throws Exception {
        server = new ApiServer(new InMemoryOrderDAO(), new InMemoryCourseDAO());
        base = "http://127.0.0.1:" + server.start(0) + "/api";
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private HttpResponse<String> call(String method, String path, Object body) throws Exception {
        HttpRequest.BodyPublisher publisher = body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(Json.MAPPER.writeValueAsBytes(body));
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/json").method(method, publisher).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private OrderDTO createBurger(int table) throws Exception {
        HttpResponse<String> response = call("POST", "/orders", new CreateOrderRequest(UUID.randomUUID(), table,
                "Service", 1, List.of(new OrderItemDTO("Classic Burger", "Burger", new BigDecimal("14.90"), 2, "ohne Zwiebeln"))));
        assertEquals(201, response.statusCode(), response.body());
        return Json.MAPPER.readValue(response.body(), OrderDTO[].class)[0];
    }

    @Test
    void healthAnswers() throws Exception {
        assertEquals(200, call("GET", "/health", null).statusCode());
    }

    @Test
    void orderRoundTripOverHttp() throws Exception {
        OrderDTO order = createBurger(4);
        assertEquals(new BigDecimal("29.80"), order.total());
        assertEquals("2× Classic Burger: ohne Zwiebeln", order.note());

        HttpResponse<String> list = call("GET", "/orders?open=true&table=4", null);
        assertEquals(1, Json.MAPPER.readTree(list.body()).size());

        HttpResponse<String> step = call("POST", "/orders/" + order.id() + "/status", java.util.Map.of("status", "IN_ZUBEREITUNG"));
        assertEquals(OrderStatus.IN_ZUBEREITUNG, Json.MAPPER.readValue(step.body(), OrderDTO.class).status());

        JsonNode bill = Json.MAPPER.readTree(call("GET", "/tables/4/bill", null).body());
        assertEquals(0, new BigDecimal("29.80").compareTo(bill.get("amountDue").decimalValue()));

        assertEquals(200, call("POST", "/tables/4/pay", null).statusCode());
        assertEquals(409, call("POST", "/tables/4/pay", null).statusCode());
    }

    @Test
    void kitchenAndCoursesOverHttp() throws Exception {
        call("POST", "/courses", java.util.Map.of("category", "Burger", "course", "VORSPEISE"));
        JsonNode lookup = Json.MAPPER.readTree(call("POST", "/courses/lookup",
                java.util.Map.of("categories", List.of("Burger", "Desserts"))).body());
        assertEquals("VORSPEISE", lookup.get("Burger").asText());
        assertEquals("NACHSPEISE", lookup.get("Desserts").asText());

        createBurger(6);
        JsonNode tables = Json.MAPPER.readTree(call("GET", "/kitchen", null).body());
        assertEquals(1, tables.size());
        assertEquals("NEU", tables.get(0).get("stage").asText());

        JsonNode card = Json.MAPPER.readTree(call("POST", "/kitchen/6/next", null).body());
        assertEquals("IN_ZUBEREITUNG", card.get("stage").asText());
        card = Json.MAPPER.readTree(call("POST", "/kitchen/6/ready", null).body());
        assertTrue(card.get("canClose").asBoolean());
        assertEquals(409, call("POST", "/kitchen/6/next", null).statusCode());
        assertEquals(200, call("POST", "/kitchen/6/close", null).statusCode());
        assertEquals(0, Json.MAPPER.readTree(call("GET", "/kitchen", null).body()).size());
    }

    @Test
    void errorsComeBackAsReadableJson() throws Exception {
        HttpResponse<String> empty = call("POST", "/orders", new CreateOrderRequest(UUID.randomUUID(), 1, "Service", 1, List.of()));
        assertEquals(400, empty.statusCode());
        assertTrue(Json.MAPPER.readTree(empty.body()).get("error").asText().contains("leer"));

        OrderDTO order = createBurger(2);
        HttpResponse<String> skip = call("POST", "/orders/" + order.id() + "/status", java.util.Map.of("status", "FERTIG"));
        assertEquals(409, skip.statusCode());

        assertEquals(404, call("GET", "/orders/" + UUID.randomUUID(), null).statusCode());
        assertEquals(404, call("GET", "/gibtsnicht", null).statusCode());
        assertEquals(400, call("POST", "/orders", "kein json").statusCode());
    }
}
