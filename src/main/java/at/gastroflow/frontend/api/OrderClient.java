package at.gastroflow.frontend.api;

import at.gastroflow.backend.api.ApiServer;
import at.gastroflow.backend.api.Json;
import at.gastroflow.backend.dto.BillDTO;
import at.gastroflow.backend.dto.CreateOrderRequest;
import at.gastroflow.backend.dto.OrderDTO;
import at.gastroflow.backend.model.OrderStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Spricht mit der REST-API für Bestellungen (#75).
 * Läuft die API noch nicht, wird sie beim ersten Aufruf in der App mitgestartet,
 * damit "mvn javafx:run" wie bisher reicht. Die Datenbank (Docker) muss laufen.
 */
public final class OrderClient {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private static final TypeReference<List<OrderDTO>> ORDER_LIST = new TypeReference<>() { };

    private static String baseUrl = System.getProperty("gastroflow.api",
            "http://" + ApiServer.HOST + ":" + ApiServer.DEFAULT_PORT + "/api");
    private static boolean checked = false;
    private static ApiServer embedded;

    private OrderClient() {
    }

    /** Nur für Tests: eine andere API-Adresse verwenden. */
    public static synchronized void useBaseUrl(String url) {
        baseUrl = url;
        checked = true;
    }

    // ---------- Bestellungen ----------

    public static OrderDTO create(CreateOrderRequest request) {
        return read(post("/orders", request), OrderDTO.class);
    }

    public static List<OrderDTO> openOrders() {
        return read(get("/orders?open=true"), ORDER_LIST);
    }

    /** Alles, was noch läuft: nicht serviert oder nicht bezahlt. */
    public static List<OrderDTO> activeOrders() {
        return read(get("/orders?active=true"), ORDER_LIST);
    }

    public static List<OrderDTO> ordersOn(LocalDate date) {
        return read(get("/orders?date=" + date), ORDER_LIST);
    }

    public static OrderDTO changeStatus(UUID id, OrderStatus status) {
        return read(post("/orders/" + id + "/status", Map.of("status", status.name())), OrderDTO.class);
    }

    public static OrderDTO cancel(UUID id) {
        return read(post("/orders/" + id + "/cancel", null), OrderDTO.class);
    }

    // ---------- Tische ----------

    public static BillDTO bill(int table) {
        return read(get("/tables/" + table + "/bill"), BillDTO.class);
    }

    public static boolean isTableOccupied(int table) {
        return !bill(table).orders().isEmpty();
    }

    /** Kassiert den Tisch ab und gibt den bezahlten Betrag zurück. */
    public static BigDecimal pay(int table) {
        return read(post("/tables/" + table + "/pay", null), JsonNode.class).get("paid").decimalValue();
    }

    // ---------- HTTP ----------

    private static HttpResponse<byte[]> get(String path) {
        return send(HttpRequest.newBuilder(URI.create(base() + path)).GET());
    }

    private static HttpResponse<byte[]> post(String path, Object body) {
        try {
            HttpRequest.BodyPublisher publisher = body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofByteArray(Json.MAPPER.writeValueAsBytes(body));
            return send(HttpRequest.newBuilder(URI.create(base() + path))
                    .header("Content-Type", "application/json").POST(publisher));
        } catch (IOException e) {
            throw new ApiException(0, "Anfrage konnte nicht erstellt werden: " + e.getMessage());
        }
    }

    private static HttpResponse<byte[]> send(HttpRequest.Builder builder) {
        try {
            HttpResponse<byte[]> response = HTTP.send(builder.timeout(Duration.ofSeconds(10)).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                String message;
                try {
                    message = Json.MAPPER.readTree(response.body()).path("error").asText("Fehler " + response.statusCode());
                } catch (IOException e) {
                    message = "Fehler " + response.statusCode();
                }
                throw new ApiException(response.statusCode(), message);
            }
            return response;
        } catch (IOException e) {
            throw new ApiException(0, "Das Backend ist nicht erreichbar (" + base() + ").");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, "Anfrage abgebrochen.");
        }
    }

    private static <T> T read(HttpResponse<byte[]> response, Class<T> type) {
        try {
            return Json.MAPPER.readValue(response.body(), type);
        } catch (IOException e) {
            throw new ApiException(0, "Antwort des Backends nicht lesbar: " + e.getMessage());
        }
    }

    private static <T> T read(HttpResponse<byte[]> response, TypeReference<T> type) {
        try {
            return Json.MAPPER.readValue(response.body(), type);
        } catch (IOException e) {
            throw new ApiException(0, "Antwort des Backends nicht lesbar: " + e.getMessage());
        }
    }

    // Beim ersten Aufruf prüfen, ob die API läuft – sonst in dieser App starten
    private static synchronized String base() {
        if (!checked) {
            checked = true;
            if (!isReachable() && baseUrl.startsWith("http://" + ApiServer.HOST + ":" + ApiServer.DEFAULT_PORT)) {
                try {
                    embedded = new ApiServer();
                    embedded.start(ApiServer.DEFAULT_PORT);
                } catch (IOException e) {
                    embedded = null; // Port belegt o. Ä. – die Fehlermeldung kommt beim Aufruf
                }
            }
        }
        return baseUrl;
    }

    private static boolean isReachable() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/health"))
                    .timeout(Duration.ofSeconds(1)).GET().build();
            return HTTP.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
