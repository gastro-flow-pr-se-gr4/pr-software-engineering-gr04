package at.gastroflow.backend.demo.model;

/** Eine Zeile einer Rezeptur: welche Zutat in welcher Menge (Basiseinheit) pro Portion. */
public record RecipeItem(int ingredientId, long amount) {
}
