package at.gastroflow.backend.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Ein Produkt der Speisekarte. */
public class Product {

    private final int id;
    private String name;
    private double price;
    private String category;
    private String allergens;
    private String imagePath;
    private boolean available;
    private List<RecipeItem> recipe = new ArrayList<>();

    public Product(int id, String name, double price, String category,
                   String allergens, String imagePath, boolean available) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.category = category;
        this.allergens = allergens;
        this.imagePath = imagePath;
        this.available = available;
    }

    public int getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getAllergens() { return allergens; }
    public void setAllergens(String allergens) { this.allergens = allergens; }

    /** Bild als Datei-URL, oder null wenn kein Bild hinterlegt ist. */
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    /** Rezeptur: Zutaten und Mengen für eine Portion (leer = keine Rezeptur hinterlegt). */
    public List<RecipeItem> getRecipe() { return Collections.unmodifiableList(recipe); }
    public void setRecipe(List<RecipeItem> recipe) { this.recipe = new ArrayList<>(recipe); }

    @Override
    public String toString() {
        return name + String.format(" (%.2f €)", price);
    }
}
