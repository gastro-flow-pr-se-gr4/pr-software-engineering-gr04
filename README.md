# pr-software-engineering-gr04
## Starten

1. Datenbank starten (Docker Desktop muss laufen): `docker compose up -d`
2. App starten: `mvn javafx:run`

Die REST-API für Bestellungen (`http://127.0.0.1:8090/api`) startet automatisch mit der App
und legt die Tabellen aus `backend/src/main/resources/db/orders.sql` beim ersten Zugriff an.
Ohne laufende Datenbank zeigen Service, Küche und Leitung einen Hinweis statt der Bestellungen.

## REST-API Bestellungen (#75)

| Methode | Adresse | Zweck |
|---|---|---|
| GET | `/api/orders` | Bestellungen (Filter `?open=true`, `?active=true`, `?date=2026-11-26`, `?table=3`) |
| POST | `/api/orders` | Bestellung anlegen (wird nach Gängen aufgeteilt) |
| POST | `/api/orders/{id}/status` | Nächster Schritt, Body `{"status":"IN_ZUBEREITUNG"}` |
| POST | `/api/orders/{id}/cancel` | Stornieren |
| GET | `/api/tables/{nr}/bill` | Rechnung eines Tisches |
| POST | `/api/tables/{nr}/pay` | Tisch abkassieren |
| GET | `/api/kitchen` | Tischkarten der Küche, Gang für Gang |
| POST | `/api/kitchen/{nr}/next` | Nächsten Gang starten (direkt in Zubereitung) |
| POST | `/api/kitchen/{nr}/ready` | Aktueller Gang ist fertig |
| POST | `/api/kitchen/{nr}/close` | Küche schließt den Tisch ab |
| POST | `/api/courses/lookup` | Gang zu Kategorien, Body `{"categories":["Desserts"]}` |
| POST | `/api/courses` | Gang einer Kategorie setzen, Body `{"category":"Desserts","course":"NACHSPEISE"}` |
