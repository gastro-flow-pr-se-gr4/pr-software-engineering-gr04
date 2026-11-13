package at.gastroflow.backend.service;

import at.gastroflow.backend.model.Ingredient;
import at.gastroflow.backend.model.MovementType;
import at.gastroflow.backend.model.RecipeItem;
import at.gastroflow.backend.model.StockMovement;
import at.gastroflow.backend.model.Unit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Speichert Zutaten, Lagerhistorie und Rezepturen dauerhaft in einer Textdatei,
 * solange GastroFlow noch keine Datenbank hat.
 * Geschrieben wird in eine temporäre Datei, die dann in einem Schritt ersetzt wird,
 * damit die Datei nie halb geschrieben ist. Das Schreiben läuft im Hintergrund,
 * damit die Oberfläche nicht hängt.
 */
public final class InventoryStore {

    /** Geladene Daten. Rezepturen sind nach Produktnamen abgelegt. */
    public record Data(int nextId, List<Ingredient> ingredients, List<StockMovement> movements,
                       Map<String, List<RecipeItem>> recipes) {
    }

    private static final String HEADER = "GASTROFLOW-LAGER 1";

    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "gastroflow-lager-speichern");
        thread.setDaemon(true);
        return thread;
    });

    private final Path file;
    private final boolean inBackground;

    public InventoryStore(Path file, boolean inBackground) {
        this.file = file;
        this.inBackground = inBackground;
    }

    public static Path defaultFile() {
        return Path.of(System.getProperty("user.home"), ".gastroflow", "lager.txt");
    }

    public Path getFile() {
        return file;
    }

    /** Lädt die Datei. Leer, wenn es sie noch nicht gibt oder sie defekt ist. */
    public Optional<Data> load() {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(parse(Files.readAllLines(file, StandardCharsets.UTF_8)));
        } catch (IOException | RuntimeException e) {
            // Defekte Datei zur Sicherheit aufheben, statt sie zu überschreiben
            try {
                Files.move(file, file.resolveSibling(file.getFileName() + ".defekt"),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // nichts weiter zu tun
            }
            System.err.println("Lagerdatei konnte nicht gelesen werden: " + e.getMessage());
            return Optional.empty();
        }
    }

    public void save(String text) {
        if (inBackground) {
            WRITER.submit(() -> write(text));
        } else {
            write(text);
        }
    }

    private void write(String text) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, text, StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("Lager konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    // ---------- Dateiformat (eine Zeile pro Eintrag, Felder mit Tabulator getrennt) ----------

    public static String serialize(int nextId, List<Ingredient> ingredients, List<StockMovement> movements,
                                   Map<String, List<RecipeItem>> recipes) {
        StringBuilder out = new StringBuilder(HEADER).append('\n');
        out.append("NEXT\t").append(nextId).append('\n');
        for (Ingredient i : ingredients) {
            line(out, "I", i.getId(), i.getName(), i.getCategory(), i.getUnit().name(), i.getStock(),
                    i.getMinStock(), i.isArchived(), i.getLastUpdated());
        }
        for (StockMovement m : movements) {
            line(out, "M", m.time(), m.ingredientId(), m.ingredientName(), m.delta(), m.unit().name(),
                    m.type().name(), m.reason());
        }
        recipes.forEach((product, items) -> {
            for (RecipeItem item : items) {
                line(out, "R", product, item.ingredientId(), item.amount());
            }
        });
        return out.toString();
    }

    static Data parse(List<String> lines) {
        if (lines.isEmpty() || !lines.get(0).equals(HEADER)) {
            throw new IllegalArgumentException("Unbekanntes Dateiformat");
        }
        int nextId = 1;
        List<Ingredient> ingredients = new ArrayList<>();
        List<StockMovement> movements = new ArrayList<>();
        Map<String, List<RecipeItem>> recipes = new LinkedHashMap<>();

        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            String[] f = line.split("\t", -1);
            for (int i = 0; i < f.length; i++) {
                f[i] = unescape(f[i]);
            }
            switch (f[0]) {
                case "NEXT" -> nextId = Integer.parseInt(f[1]);
                case "I" -> ingredients.add(new Ingredient(Integer.parseInt(f[1]), f[2], f[3], Unit.valueOf(f[4]),
                        Long.parseLong(f[5]), Long.parseLong(f[6]), Boolean.parseBoolean(f[7]),
                        LocalDateTime.parse(f[8])));
                case "M" -> movements.add(new StockMovement(LocalDateTime.parse(f[1]), Integer.parseInt(f[2]), f[3],
                        Long.parseLong(f[4]), Unit.valueOf(f[5]), MovementType.valueOf(f[6]), f[7]));
                case "R" -> recipes.computeIfAbsent(f[1], key -> new ArrayList<>())
                        .add(new RecipeItem(Integer.parseInt(f[2]), Long.parseLong(f[3])));
                default -> throw new IllegalArgumentException("Unbekannte Zeile: " + f[0]);
            }
        }
        return new Data(nextId, ingredients, movements, recipes);
    }

    private static void line(StringBuilder out, String type, Object... fields) {
        out.append(type);
        for (Object field : fields) {
            out.append('\t').append(escape(String.valueOf(field)));
        }
        out.append('\n');
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
    }

    private static String unescape(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(++i);
                out.append(next == 't' ? '\t' : next == 'n' ? '\n' : next);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
