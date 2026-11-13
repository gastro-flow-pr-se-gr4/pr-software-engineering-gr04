package at.gastroflow.backend.service;

import at.gastroflow.backend.model.Ingredient;
import at.gastroflow.backend.model.MovementType;
import at.gastroflow.backend.model.Order;
import at.gastroflow.backend.model.OrderItem;
import at.gastroflow.backend.model.Product;
import at.gastroflow.backend.model.RecipeItem;
import at.gastroflow.backend.model.RestaurantSettings;
import at.gastroflow.backend.model.StockMovement;
import at.gastroflow.backend.model.StockStatus;
import at.gastroflow.backend.model.Unit;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Lagerverwaltung: Zutaten, Rezepturen, automatische Buchungen und Lagerhistorie.
 *
 * Regeln für Bestellungen:
 * - Jede Bestellung wird höchstens einmal gebucht.
 * - Gebucht wird alles oder nichts: reicht eine Zutat nicht, wird gar nichts abgezogen
 *   (der Bestand kann nie negativ werden).
 * - Storno vor der Zubereitung bucht die Zutaten zurück, danach nicht mehr.
 */
public class InventoryService {

    public static final List<String> CATEGORIES = List.of(
            "Fleisch & Fisch", "Obst & Gemüse", "Milchprodukte & Eier", "Backwaren",
            "Trockenware", "Tiefkühlware", "Getränke", "Sonstiges");

    private static final List<Ingredient> INGREDIENTS = new ArrayList<>();
    private static final List<StockMovement> MOVEMENTS = new ArrayList<>();
    private static final Map<Integer, Map<Integer, Long>> BOOKINGS = new HashMap<>(); // Bestellnr. → (Zutat → Menge)
    private static final Set<Integer> HANDLED_ORDERS = new HashSet<>();
    private static final Set<Integer> REVERSED_ORDERS = new HashSet<>();
    private static final Map<Integer, String> PROBLEMS = new HashMap<>();

    private static InventoryStore store;
    private static int nextId = 1;

    // ---------- Laden und Speichern ----------

    /** Lädt das Lager beim ersten Zugriff aus der Standard-Datei. */
    public static synchronized void ensureLoaded() {
        if (store == null) {
            init(InventoryStore.defaultFile(), true);
        }
    }

    /** Lädt das Lager aus der Datei; gibt es sie noch nicht, werden Beispieldaten angelegt. */
    public static synchronized void init(Path file, boolean saveInBackground) {
        INGREDIENTS.clear();
        MOVEMENTS.clear();
        BOOKINGS.clear();
        HANDLED_ORDERS.clear();
        REVERSED_ORDERS.clear();
        PROBLEMS.clear();
        nextId = 1;
        store = new InventoryStore(file, saveInBackground);

        Optional<InventoryStore.Data> data = store.load();
        if (data.isPresent()) {
            nextId = data.get().nextId();
            INGREDIENTS.addAll(data.get().ingredients());
            MOVEMENTS.addAll(data.get().movements());
            for (Product product : MenuService.getProducts()) {
                product.setRecipe(data.get().recipes().getOrDefault(product.getName(), List.of()));
            }
        } else {
            seedDemo();
        }
        save();
    }

    public static synchronized Path getStorageFile() {
        ensureLoaded();
        return store.getFile();
    }

    private static void save() {
        Map<String, List<RecipeItem>> recipes = new LinkedHashMap<>();
        for (Product product : MenuService.getProducts()) {
            if (!product.getRecipe().isEmpty()) {
                recipes.put(product.getName(), product.getRecipe());
            }
        }
        store.save(InventoryStore.serialize(nextId, INGREDIENTS, MOVEMENTS, recipes));
    }

    // ---------- Zutaten ----------

    public static synchronized List<Ingredient> getIngredients(boolean includeArchived) {
        ensureLoaded();
        return INGREDIENTS.stream()
                .filter(i -> includeArchived || !i.isArchived())
                .sorted(Comparator.comparing(Ingredient::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public static synchronized Optional<Ingredient> findIngredient(int id) {
        ensureLoaded();
        return INGREDIENTS.stream().filter(i -> i.getId() == id).findFirst();
    }

    public static synchronized Ingredient addIngredient(String name, String category, Unit unit,
                                                       long stock, long minStock) {
        ensureLoaded();
        String clean = checkName(name, null);
        checkCategory(category);
        if (stock < 0 || minStock < 0) {
            throw new IllegalArgumentException("Bestand und Mindestbestand dürfen nicht negativ sein.");
        }
        Ingredient ingredient = new Ingredient(nextId++, clean, category.trim(), unit, stock, minStock,
                false, LocalDateTime.now());
        INGREDIENTS.add(ingredient);
        if (stock > 0) {
            record(ingredient, stock, MovementType.ANFANGSBESTAND, "Neu angelegt");
        }
        save();
        return ingredient;
    }

    public static synchronized void updateIngredient(Ingredient ingredient, String name, String category,
                                                     Unit unit, long minStock) {
        String clean = checkName(name, ingredient);
        checkCategory(category);
        if (!ingredient.getUnit().isCompatible(unit)) {
            throw new IllegalArgumentException("Die Einheit muss zur bisherigen passen ("
                    + ingredient.getUnit().compatibleUnits() + ").");
        }
        if (minStock < 0) {
            throw new IllegalArgumentException("Der Mindestbestand darf nicht negativ sein.");
        }
        ingredient.setName(clean);
        ingredient.setCategory(category.trim());
        ingredient.setUnit(unit);
        ingredient.setMinStock(minStock);
        ingredient.setLastUpdated(LocalDateTime.now());
        save();
    }

    /** Wareneingang: Bestand erhöhen. */
    public static synchronized void receive(Ingredient ingredient, long amount, String reason) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Bitte eine Menge größer als 0 eingeben.");
        }
        ingredient.setStock(ingredient.getStock() + amount);
        ingredient.setLastUpdated(LocalDateTime.now());
        record(ingredient, amount, MovementType.WARENEINGANG,
                reason == null || reason.isBlank() ? "Wareneingang" : reason.trim());
        save();
    }

    /** Manuelle Korrektur auf einen neuen Bestand (z. B. nach Inventur oder Verderb). */
    public static synchronized void correct(Ingredient ingredient, long newStock, String reason) {
        if (newStock < 0) {
            throw new IllegalArgumentException("Der Bestand darf nicht negativ sein.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Bitte einen Grund für die Korrektur angeben.");
        }
        long delta = newStock - ingredient.getStock();
        if (delta == 0) {
            throw new IllegalArgumentException("Der Bestand ist bereits "
                    + ingredient.getUnit().format(newStock) + ".");
        }
        ingredient.setStock(newStock);
        ingredient.setLastUpdated(LocalDateTime.now());
        record(ingredient, delta, MovementType.KORREKTUR, reason.trim());
        save();
    }

    public static synchronized void setArchived(Ingredient ingredient, boolean archived) {
        ingredient.setArchived(archived);
        ingredient.setLastUpdated(LocalDateTime.now());
        save();
    }

    /** Löschen ist nur möglich, wenn die Zutat in keiner Rezeptur vorkommt. */
    public static synchronized void deleteIngredient(Ingredient ingredient) {
        List<Product> products = usedIn(ingredient);
        if (!products.isEmpty()) {
            throw new IllegalStateException("„" + ingredient.getName() + "“ wird noch in Rezepturen verwendet ("
                    + String.join(", ", products.stream().map(Product::getName).toList())
                    + "). Bitte archivieren oder zuerst aus den Rezepturen entfernen.");
        }
        INGREDIENTS.remove(ingredient);
        save();
    }

    /** Produkte, deren Rezeptur diese Zutat enthält. */
    public static synchronized List<Product> usedIn(Ingredient ingredient) {
        return MenuService.getProducts().stream()
                .filter(p -> p.getRecipe().stream().anyMatch(r -> r.ingredientId() == ingredient.getId()))
                .toList();
    }

    private static String checkName(String name, Ingredient ignore) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Bitte einen Namen für die Zutat eingeben.");
        }
        String clean = name.trim();
        for (Ingredient other : INGREDIENTS) {
            if (other != ignore && other.getName().equalsIgnoreCase(clean)) {
                throw new IllegalArgumentException("Die Zutat „" + clean + "“ gibt es schon.");
            }
        }
        return clean;
    }

    private static void checkCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Bitte eine Kategorie auswählen.");
        }
    }

    // ---------- Rezepturen ----------

    public static synchronized void setRecipe(Product product, List<RecipeItem> items) {
        ensureLoaded();
        Set<Integer> seen = new HashSet<>();
        for (RecipeItem item : items) {
            Ingredient ingredient = findIngredient(item.ingredientId())
                    .orElseThrow(() -> new IllegalArgumentException("Eine Zutat der Rezeptur gibt es nicht mehr."));
            if (item.amount() <= 0) {
                throw new IllegalArgumentException("Die Menge für „" + ingredient.getName() + "“ muss größer als 0 sein.");
            }
            if (!seen.add(item.ingredientId())) {
                throw new IllegalArgumentException("„" + ingredient.getName() + "“ steht doppelt in der Rezeptur.");
            }
        }
        product.setRecipe(items);
        save();
    }

    /** Speichert nach Änderungen an Produkten (z. B. neuer Name), damit die Rezepturen erhalten bleiben. */
    public static synchronized void persist() {
        ensureLoaded();
        save();
    }

    /**
     * Wie viele Portionen eines Gerichts mit dem aktuellen Lager möglich sind (knappste Zutat).
     * null = keine Rezeptur hinterlegt. Jede Zahl ist eine Schätzung für dieses Gericht allein.
     */
    public static synchronized Integer portionsFor(Product product) {
        ensureLoaded();
        if (product.getRecipe().isEmpty()) {
            return null;
        }
        long portions = Long.MAX_VALUE;
        for (RecipeItem item : product.getRecipe()) {
            Optional<Ingredient> ingredient = findIngredient(item.ingredientId());
            long possible = ingredient.isEmpty() || ingredient.get().isArchived()
                    ? 0 : ingredient.get().getStock() / item.amount();
            portions = Math.min(portions, possible);
        }
        return (int) Math.min(portions, Integer.MAX_VALUE);
    }

    // ---------- Status und Warnungen ----------

    public static synchronized StockStatus statusOf(Ingredient ingredient) {
        if (ingredient.isArchived() || ingredient.getStock() <= 0
                || ingredient.getStock() < largestPortionNeed(ingredient)) {
            return StockStatus.NICHT_VERFUEGBAR;
        }
        long threshold = Math.round(ingredient.getMinStock()
                * RestaurantSettings.get().getLowStockPercent() / 100.0);
        return ingredient.getStock() <= threshold ? StockStatus.NIEDRIG : StockStatus.AUSREICHEND;
    }

    // Größte Menge, die ein einzelnes Gericht von dieser Zutat braucht
    private static long largestPortionNeed(Ingredient ingredient) {
        long max = 0;
        for (Product product : MenuService.getProducts()) {
            for (RecipeItem item : product.getRecipe()) {
                if (item.ingredientId() == ingredient.getId()) {
                    max = Math.max(max, item.amount());
                }
            }
        }
        return max;
    }

    /** Aktive Zutaten mit niedrigem oder fehlendem Bestand (Rot zuerst). */
    public static synchronized List<Ingredient> getWarnings() {
        return getIngredients(false).stream()
                .filter(i -> statusOf(i) != StockStatus.AUSREICHEND)
                .sorted(Comparator.comparing((Ingredient i) -> statusOf(i) != StockStatus.NICHT_VERFUEGBAR))
                .toList();
    }

    public static synchronized String warningText(Ingredient ingredient) {
        String amount = ingredient.getUnit().format(ingredient.getStock());
        if (statusOf(ingredient) == StockStatus.NICHT_VERFUEGBAR) {
            return "Achtung: " + ingredient.getName() + " ist nicht ausreichend vorhanden (" + amount + ").";
        }
        return "Achtung: Bestand von " + ingredient.getName() + " ist knapp. Nur noch " + amount + " verfügbar.";
    }

    // ---------- Buchungen durch Bestellungen ----------

    /** Benötigte Zutaten für Bestellpositionen (Zutat → Menge in Basiseinheit). */
    public static synchronized Map<Integer, Long> requiredFor(List<OrderItem> items) {
        Map<Integer, Long> need = new LinkedHashMap<>();
        for (OrderItem item : items) {
            Product product = MenuService.findByName(item.getProductName());
            if (product == null) {
                continue;
            }
            for (RecipeItem recipeItem : product.getRecipe()) {
                need.merge(recipeItem.ingredientId(), recipeItem.amount() * item.getQuantity(), Long::sum);
            }
        }
        return need;
    }

    /**
     * Zieht die Zutaten einer Bestellung vom Lager ab – genau einmal und nur, wenn alles reicht.
     * Gibt true zurück, wenn gebucht wurde (oder nichts zu buchen war).
     */
    public static synchronized boolean bookOrder(Order order) {
        ensureLoaded();
        if (!HANDLED_ORDERS.add(order.getNumber())) {
            return BOOKINGS.containsKey(order.getNumber());
        }
        Map<Integer, Long> need = requiredFor(order.getItems());

        // 1. Prüfen, ob alle Zutaten reichen
        List<String> missing = new ArrayList<>();
        for (Map.Entry<Integer, Long> entry : need.entrySet()) {
            Optional<Ingredient> ingredient = findIngredient(entry.getKey());
            if (ingredient.isEmpty() || ingredient.get().isArchived()
                    || ingredient.get().getStock() < entry.getValue()) {
                String name = ingredient.map(Ingredient::getName).orElse("Unbekannte Zutat");
                String detail = ingredient.map(i -> " (benötigt " + i.getUnit().format(entry.getValue())
                        + ", vorhanden " + i.getUnit().format(i.getStock()) + ")").orElse("");
                missing.add(name + detail);
            }
        }
        if (!missing.isEmpty()) {
            PROBLEMS.put(order.getNumber(), "Nicht vom Lager abgebucht – zu wenig Bestand: "
                    + String.join(", ", missing));
            return false;
        }

        // 2. Alles auf einmal abbuchen
        for (Map.Entry<Integer, Long> entry : need.entrySet()) {
            Ingredient ingredient = findIngredient(entry.getKey()).orElseThrow();
            ingredient.setStock(ingredient.getStock() - entry.getValue());
            ingredient.setLastUpdated(LocalDateTime.now());
            record(ingredient, -entry.getValue(), MovementType.BESTELLUNG, "Bestellung #" + order.getNumber());
        }
        BOOKINGS.put(order.getNumber(), need);
        save();
        return true;
    }

    /**
     * Storno: bucht die Zutaten zurück, wenn sie noch nicht verbraucht wurden.
     * Gibt true zurück, wenn zurückgebucht wurde.
     */
    public static synchronized boolean reverseOrder(Order order, boolean alreadyPrepared) {
        Map<Integer, Long> booked = BOOKINGS.get(order.getNumber());
        if (booked == null || alreadyPrepared || !REVERSED_ORDERS.add(order.getNumber())) {
            return false;
        }
        for (Map.Entry<Integer, Long> entry : booked.entrySet()) {
            findIngredient(entry.getKey()).ifPresent(ingredient -> {
                ingredient.setStock(ingredient.getStock() + entry.getValue());
                ingredient.setLastUpdated(LocalDateTime.now());
                record(ingredient, entry.getValue(), MovementType.RUECKBUCHUNG, "Storno Bestellung #" + order.getNumber());
            });
        }
        save();
        return true;
    }

    public static synchronized boolean isBooked(Order order) {
        return BOOKINGS.containsKey(order.getNumber()) && !REVERSED_ORDERS.contains(order.getNumber());
    }

    /** Hinweis, falls eine Bestellung nicht gebucht werden konnte. */
    public static synchronized Optional<String> problemFor(Order order) {
        return Optional.ofNullable(PROBLEMS.get(order.getNumber()));
    }

    /** Zutaten dieser Bestellung, deren Bestand knapp oder nicht ausreichend ist. */
    public static synchronized List<Ingredient> scarceIngredientsFor(Order order) {
        ensureLoaded();
        List<Ingredient> scarce = new ArrayList<>();
        for (Integer id : requiredFor(order.getItems()).keySet()) {
            findIngredient(id).filter(i -> statusOf(i) != StockStatus.AUSREICHEND).ifPresent(scarce::add);
        }
        return scarce;
    }

    // ---------- Lagerhistorie ----------

    /** Alle Buchungen, neueste zuerst. */
    public static synchronized List<StockMovement> getMovements() {
        ensureLoaded();
        List<StockMovement> list = new ArrayList<>(MOVEMENTS);
        list.sort(Comparator.comparing(StockMovement::time).reversed());
        return list;
    }

    public static synchronized long movementsOn(LocalDate date) {
        return getMovements().stream().filter(m -> m.time().toLocalDate().equals(date)).count();
    }

    private static void record(Ingredient ingredient, long delta, MovementType type, String reason) {
        MOVEMENTS.add(new StockMovement(LocalDateTime.now(), ingredient.getId(), ingredient.getName(),
                delta, ingredient.getUnit(), type, reason));
    }

    // ---------- Beispieldaten (nur beim allerersten Start) ----------

    private static void seedDemo() {
        Ingredient rind = seed("Rindfleisch", "Fleisch & Fisch", Unit.KILOGRAMM, 8000, 3000);
        Ingredient broetchen = seed("Burgerbrötchen", "Backwaren", Unit.STUECK, 12, 15);
        Ingredient tomaten = seed("Tomaten", "Obst & Gemüse", Unit.KILOGRAMM, 2000, 3000);
        Ingredient kaese = seed("Käse", "Milchprodukte & Eier", Unit.KILOGRAMM, 5000, 2000);
        seed("Pommes", "Tiefkühlware", Unit.KILOGRAMM, 1000, 4000);
        Ingredient salat = seed("Salat", "Obst & Gemüse", Unit.KILOGRAMM, 4000, 2000);
        Ingredient huhn = seed("Hähnchenbrust", "Fleisch & Fisch", Unit.KILOGRAMM, 6000, 2000);
        Ingredient kalb = seed("Kalbfleisch", "Fleisch & Fisch", Unit.KILOGRAMM, 4000, 1500);
        Ingredient lachs = seed("Lachsfilet", "Fleisch & Fisch", Unit.KILOGRAMM, 3000, 1000);
        Ingredient mehl = seed("Mehl", "Trockenware", Unit.KILOGRAMM, 10000, 3000);
        Ingredient eier = seed("Eier", "Milchprodukte & Eier", Unit.STUECK, 60, 20);
        Ingredient panier = seed("Paniermehl", "Trockenware", Unit.KILOGRAMM, 2000, 500);
        Ingredient teig = seed("Pizzateig", "Backwaren", Unit.STUECK, 30, 10);
        Ingredient sauce = seed("Tomatensauce", "Trockenware", Unit.LITER, 5000, 1500);
        Ingredient mozzarella = seed("Mozzarella", "Milchprodukte & Eier", Unit.KILOGRAMM, 4000, 1500);
        Ingredient salami = seed("Salami", "Fleisch & Fisch", Unit.KILOGRAMM, 1500, 500);
        Ingredient spaghetti = seed("Spaghetti", "Trockenware", Unit.KILOGRAMM, 5000, 1500);
        Ingredient speck = seed("Speck", "Fleisch & Fisch", Unit.KILOGRAMM, 1500, 500);
        Ingredient parmesan = seed("Parmesan", "Milchprodukte & Eier", Unit.KILOGRAMM, 1000, 300);
        Ingredient baguette = seed("Baguette", "Backwaren", Unit.STUECK, 15, 5);
        Ingredient knoblauchbutter = seed("Knoblauchbutter", "Milchprodukte & Eier", Unit.KILOGRAMM, 1000, 300);
        Ingredient butter = seed("Butter", "Milchprodukte & Eier", Unit.KILOGRAMM, 2000, 500);
        Ingredient milch = seed("Milch", "Milchprodukte & Eier", Unit.LITER, 12000, 4000);
        Ingredient schokolade = seed("Schokolade", "Trockenware", Unit.KILOGRAMM, 1500, 500);
        Ingredient mascarpone = seed("Mascarpone", "Milchprodukte & Eier", Unit.KILOGRAMM, 1500, 500);
        Ingredient biskuits = seed("Löffelbiskuits", "Backwaren", Unit.STUECK, 80, 30);
        Ingredient kaffee = seed("Kaffeebohnen", "Getränke", Unit.KILOGRAMM, 3000, 1000);
        Ingredient cola = seed("Coca-Cola 0,33 l", "Getränke", Unit.STUECK, 48, 24);
        Ingredient fanta = seed("Fanta 0,33 l", "Getränke", Unit.STUECK, 30, 24);
        Ingredient wasser = seed("Mineralwasser 0,5 l", "Getränke", Unit.STUECK, 60, 24);
        Ingredient apfelsaft = seed("Apfelsaft", "Getränke", Unit.LITER, 10000, 3000);

        recipe("Bruschetta", item(baguette, 1), item(tomaten, 80));
        recipe("Caesar Salad", item(salat, 120), item(huhn, 80), item(parmesan, 20));
        recipe("Knoblauchbrot", item(baguette, 1), item(knoblauchbutter, 30));
        recipe("Wiener Schnitzel", item(kalb, 180), item(eier, 1), item(mehl, 30), item(panier, 50));
        recipe("Gegrilltes Hähnchen", item(huhn, 250), item(salat, 50));
        recipe("Lachsfilet", item(lachs, 200), item(butter, 20));
        recipe("Pizza Margherita", item(teig, 1), item(sauce, 80), item(mozzarella, 125));
        recipe("Pizza Salami", item(teig, 1), item(sauce, 80), item(mozzarella, 100), item(salami, 60));
        recipe("Spaghetti Carbonara", item(spaghetti, 150), item(speck, 60), item(eier, 2), item(parmesan, 30));
        recipe("Classic Burger", item(broetchen, 1), item(rind, 180), item(kaese, 30), item(tomaten, 50), item(salat, 20));
        recipe("Cheeseburger", item(broetchen, 1), item(rind, 180), item(kaese, 60), item(salat, 20));
        recipe("Crispy Chicken Burger", item(broetchen, 1), item(huhn, 150), item(panier, 30), item(salat, 20));
        recipe("Kaiserschmarrn", item(mehl, 100), item(eier, 2), item(milch, 150), item(butter, 20));
        recipe("Schokoladenkuchen", item(schokolade, 60), item(mehl, 40), item(eier, 1), item(butter, 30));
        recipe("Tiramisu", item(mascarpone, 100), item(biskuits, 4), item(kaffee, 10), item(eier, 1));
        recipe("Coca-Cola 0,33 l", item(cola, 1));
        recipe("Fanta 0,33 l", item(fanta, 1));
        recipe("Mineralwasser 0,5 l", item(wasser, 1));
        recipe("Apfelsaft 0,25 l", item(apfelsaft, 250));
        recipe("Espresso", item(kaffee, 8));
        recipe("Cappuccino", item(kaffee, 8), item(milch, 150));
        recipe("Latte Macchiato", item(kaffee, 8), item(milch, 250));
    }

    private static Ingredient seed(String name, String category, Unit unit, long stock, long minStock) {
        Ingredient ingredient = new Ingredient(nextId++, name, category, unit, stock, minStock,
                false, LocalDateTime.now());
        INGREDIENTS.add(ingredient);
        record(ingredient, stock, MovementType.ANFANGSBESTAND, "Startbestand (Demo)");
        return ingredient;
    }

    private static void recipe(String productName, RecipeItem... items) {
        Product product = MenuService.findByName(productName);
        if (product != null) {
            product.setRecipe(List.of(items));
        }
    }

    private static RecipeItem item(Ingredient ingredient, long amount) {
        return new RecipeItem(ingredient.getId(), amount);
    }
}
