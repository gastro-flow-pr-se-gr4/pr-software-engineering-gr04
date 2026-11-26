-- Bestellungen (#74). Wird beim Start der REST-API automatisch ausgeführt,
-- vorhandene Tabellen und Daten bleiben dabei erhalten.

CREATE SEQUENCE IF NOT EXISTS order_number_seq START 10001;

CREATE TABLE IF NOT EXISTS orders (
    id           UUID PRIMARY KEY,
    number       INT NOT NULL UNIQUE DEFAULT nextval('order_number_seq'),
    table_number INT NOT NULL CHECK (table_number >= 1),
    waiter       VARCHAR(100) NOT NULL,
    created_at   TIMESTAMP NOT NULL,
    guests       INT NOT NULL DEFAULT 1 CHECK (guests >= 0),
    status       VARCHAR(20) NOT NULL,
    paid_at      TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS order_items (
    id           UUID PRIMARY KEY,
    order_id     UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    position     INT NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    category     VARCHAR(100) NOT NULL DEFAULT '',
    unit_price   NUMERIC(10, 2) NOT NULL CHECK (unit_price >= 0),
    quantity     INT NOT NULL CHECK (quantity BETWEEN 1 AND 99),
    note         VARCHAR(500) NOT NULL DEFAULT ''
);

CREATE INDEX IF NOT EXISTS orders_table_idx ON orders (table_number);
CREATE INDEX IF NOT EXISTS order_items_order_idx ON order_items (order_id);

-- Gänge (#84): jede Bestellung gehört zu einem Gang, die Küche kann einen Tisch abschließen.
-- submission_id fasst die Teile eines Absendens zusammen (und verhindert doppeltes Speichern).
ALTER TABLE orders ADD COLUMN IF NOT EXISTS course VARCHAR(20) NOT NULL DEFAULT 'HAUPTSPEISE';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS kitchen_closed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS submission_id UUID NULL;
CREATE INDEX IF NOT EXISTS orders_submission_idx ON orders (submission_id);

-- Gang pro Kategorie der Speisekarte (von der Leitung änderbar)
CREATE TABLE IF NOT EXISTS category_courses (
    category VARCHAR(100) PRIMARY KEY,
    course   VARCHAR(20) NOT NULL
);
