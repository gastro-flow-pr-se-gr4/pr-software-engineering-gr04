package at.gastroflow.backend.model;

/** Eine Zeile einer Rezeptur: welche Zutat in welcher Menge (Basiseinheit) pro Portion. */
public record RecipeItem(int ingredientId, long amount) {
}
