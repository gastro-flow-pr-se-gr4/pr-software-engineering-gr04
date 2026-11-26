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
| POST | `/api/orders` | Bestellung anlegen |
| POST | `/api/orders/{id}/status` | Nächster Schritt, Body `{"status":"IN_ZUBEREITUNG"}` |
| POST | `/api/orders/{id}/cancel` | Stornieren |
| GET | `/api/tables/{nr}/bill` | Rechnung eines Tisches |
| POST | `/api/tables/{nr}/pay` | Tisch abkassieren |
