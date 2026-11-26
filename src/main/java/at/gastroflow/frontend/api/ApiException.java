package at.gastroflow.frontend.api;

/** Fehler von der REST-API mit verständlicher Meldung (z. B. "Die Bestellung ist leer."). */
public class ApiException extends IllegalStateException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    /** HTTP-Status: 400 ungültig, 404 nicht gefunden, 409 nicht erlaubt, 503 Datenbank weg, 0 = API nicht erreichbar. */
    public int getStatus() {
        return status;
    }
}
