package at.gastroflow.frontend.api;

import at.gastroflow.backend.api.Json;
import at.gastroflow.backend.dto.ProductDTO;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class ProductClient {

    private final String url = "http://127.0.0.1:8090/api/products";
    private final HttpClient client = HttpClient.newHttpClient();

    public List<ProductDTO> getAllProducts() throws IOException, InterruptedException {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Fehler beim Laden der Produkte!");
        }

        ProductDTO[] products = Json.MAPPER.readValue(response.body(), ProductDTO[].class);

        List<ProductDTO> productList = new ArrayList<>();

        for (ProductDTO product : products) {
            productList.add(product);
        }

        return productList;
    }
}
