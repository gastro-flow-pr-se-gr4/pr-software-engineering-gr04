package at.gastroflow.backend.demo.model;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum Unit {

    STUECK("Stück", "Stück", "ANZAHL", 1),
    GRAMM("Gramm", "g", "MASSE", 1),
    KILOGRAMM("Kilogramm", "kg", "MASSE", 1000),
    MILLILITER("Milliliter", "ml", "VOLUMEN", 1),
    LITER("Liter", "l", "VOLUMEN", 1000);

    private final String label;
    private final String symbol;
    private final String dimension;
    private final long factor;

    Unit(String label, String symbol, String dimension, long factor) {
        this.label = label;
        this.symbol = symbol;
        this.dimension = dimension;
        this.factor = factor;
    }

    public String getLabel() { return label; }

    public String getSymbol() { return symbol; }

    public boolean isCompatible(Unit other) {
        return dimension.equals(other.dimension);
    }

    public List<Unit> compatibleUnits() {
        return Arrays.stream(values()).filter(this::isCompatible).toList();
    }

    public Unit baseUnit() {
        return switch (dimension) {
            case "MASSE" -> GRAMM;
            case "VOLUMEN" -> MILLILITER;
            default -> STUECK;
        };
    }

    public long toBase(double value) {
        return Math.round(value * factor);
    }

    public double fromBase(long base) {
        return base / (double) factor;
    }

    public String formatNumber(long base) {
        DecimalFormat format = new DecimalFormat("#,##0.###", new DecimalFormatSymbols(Locale.GERMANY));
        return format.format(fromBase(base));
    }

    public String format(long base) {
        return formatNumber(base) + " " + symbol;
    }

    @Override
    public String toString() {
        return label;
    }
}
