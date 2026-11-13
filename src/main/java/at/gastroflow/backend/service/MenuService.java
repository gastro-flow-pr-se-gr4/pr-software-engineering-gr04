package at.gastroflow.backend.service;

import at.gastroflow.backend.model.Product;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Speisekarte: Kategorien und Produkte (gemeinsame Datenquelle).
 * Das Service-Dashboard soll seine Produktauswahl aus getAvailableProducts() holen.
 */
public class MenuService {

    private static final List<String> CATEGORIES = new ArrayList<>();
    private static final List<Product> PRODUCTS = new ArrayList<>();
    private static int nextId = 1;

    // Start-Speisekarte (Allergene nach österreichischem Code, z. B. A = Gluten, G = Milch)
    static {
        seed("Vorspeisen", "Bruschetta", 7.90, "A");
        seed("Vorspeisen", "Caesar Salad", 9.90, "A, C, D, G, M");
        seed("Vorspeisen", "Knoblauchbrot", 5.50, "A, G");

        seed("Hauptgerichte", "Wiener Schnitzel", 18.90, "A, C");
        seed("Hauptgerichte", "Gegrilltes Hähnchen", 16.90, "L");
        seed("Hauptgerichte", "Lachsfilet", 21.90, "D, G");

        seed("Pizza & Pasta", "Pizza Margherita", 11.90, "A, G");
        seed("Pizza & Pasta", "Pizza Salami", 13.90, "A, G");
        seed("Pizza & Pasta", "Spaghetti Carbonara", 14.90, "A, C, G");

        seed("Burger", "Classic Burger", 14.90, "A, C, M");
        seed("Burger", "Cheeseburger", 15.90, "A, C, G, M");
        seed("Burger", "Crispy Chicken Burger", 14.50, "A, C, M");

        seed("Desserts", "Kaiserschmarrn", 9.90, "A, C, G");
        seed("Desserts", "Schokoladenkuchen", 7.50, "A, C, G");
        seed("Desserts", "Tiramisu", 6.90, "A, C, G");

        seed("Getränke", "Coca-Cola 0,33 l", 3.50, "");
        seed("Getränke", "Fanta 0,33 l", 3.50, "");
        seed("Getränke", "Mineralwasser 0,5 l", 2.90, "");
        seed("Getränke", "Apfelsaft 0,25 l", 3.20, "");

        seed("Kaffee & Tee", "Espresso", 2.50, "");
        seed("Kaffee & Tee", "Cappuccino", 3.90, "G");
        seed("Kaffee & Tee", "Latte Macchiato", 4.50, "G");
    }

    private static void seed(String category, String name, double price, String allergens) {
        if (!CATEGORIES.contains(category)) {
            CATEGORIES.add(category);
        }
        PRODUCTS.add(new Product(nextId++, name, price, category, allergens, null, true));
    }

    // ---------- Kategorien ----------

    public static List<String> getCategories() {
        return Collections.unmodifiableList(CATEGORIES);
    }

    public static void addCategory(String name) {
        String clean = checkCategoryName(name, null);
        CATEGORIES.add(clean);
    }

    public static void renameCategory(String oldName, String newName) {
        String clean = checkCategoryName(newName, oldName);
        int index = CATEGORIES.indexOf(oldName);
        if (index < 0) {
            throw new IllegalArgumentException("Diese Kategorie gibt es nicht mehr.");
        }
        CATEGORIES.set(index, clean);
        for (Product product : PRODUCTS) {
            if (product.getCategory().equals(oldName)) {
                product.setCategory(clean);
            }
        }
    }

    public static void deleteCategory(String name) {
        if (getProductsInCategory(name).size() > 0) {
            throw new IllegalStateException(
                    "Die Kategorie enthält noch Produkte. Bitte zuerst die Produkte löschen oder verschieben.");
        }
        CATEGORIES.remove(name);
    }

    private static String checkCategoryName(String name, String ignore) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Bitte einen Namen für die Kategorie eingeben.");
        }
        String clean = name.trim();
        for (String category : CATEGORIES) {
            if (category.equalsIgnoreCase(clean) && !category.equals(ignore)) {
                throw new IllegalArgumentException("Die Kategorie „" + clean + "“ gibt es schon.");
            }
        }
        return clean;
    }

    // ---------- Produkte ----------

    public static List<Product> getProducts() {
        return Collections.unmodifiableList(PRODUCTS);
    }

    public static List<Product> getProductsInCategory(String category) {
        return PRODUCTS.stream().filter(p -> p.getCategory().equals(category)).toList();
    }

    public static Product findByName(String name) {
        return PRODUCTS.stream().filter(p -> p.getName().equals(name)).findFirst().orElse(null);
    }

    /** Nur Produkte, die neu bestellt werden dürfen (für das Service-Dashboard). */
    public static List<Product> getAvailableProducts() {
        return PRODUCTS.stream().filter(Product::isAvailable).toList();
    }

    public static Product addProduct(String name, double price, String category,
                                     String allergens, String imagePath, boolean available) {
        checkProduct(name, price, category, null);
        Product product = new Product(nextId++, name.trim(), price, category,
                clean(allergens), imagePath, available);
        PRODUCTS.add(product);
        return product;
    }

    public static void updateProduct(Product product, String name, double price, String category,
                                     String allergens, String imagePath, boolean available) {
        checkProduct(name, price, category, product);
        product.setName(name.trim());
        product.setPrice(price);
        product.setCategory(category);
        product.setAllergens(clean(allergens));
        product.setImagePath(imagePath);
        product.setAvailable(available);
    }

    public static void setAvailable(Product product, boolean available) {
        product.setAvailable(available);
    }

    public static void deleteProduct(Product product) {
        PRODUCTS.remove(product);
    }

    private static void checkProduct(String name, double price, String category, Product ignore) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Bitte einen Produktnamen eingeben.");
        }
        if (price <= 0) {
            throw new IllegalArgumentException("Der Preis muss größer als 0 sein.");
        }
        if (category == null || !CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("Bitte eine Kategorie auswählen.");
        }
        for (Product product : PRODUCTS) {
            if (product != ignore && product.getName().equalsIgnoreCase(name.trim())) {
                throw new IllegalArgumentException("Ein Produkt mit diesem Namen gibt es schon.");
            }
        }
    }

    private static String clean(String text) {
        return text == null ? "" : text.trim();
    }
}
