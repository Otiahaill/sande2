"""
Execute the MIGRATION_4_5 SQL against a real SQLite database.

The migration SQL is extracted verbatim from AppDatabase.kt, the legacy v4
schema is created from the @Entity declarations of the legacy tables, and the
whole thing is executed. Any SQL error (missing column, type mismatch, NOT
NULL violation, foreign key violation) raises here instead of crashing the
app on a real device at upgrade time.

Legacy rows are inserted first so the data-copy statements are exercised with
actual data, not just an empty table.
"""
import io
import os
import re
import sqlite3
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DB = os.path.join(
    ROOT, "app", "src", "main", "java", "com", "example", "sande", "data", "db",
    "AppDatabase.kt",
)

# The legacy (v4) tables, as they exist on disk for an installed v3/v4 app.
LEGACY_SCHEMA = """
CREATE TABLE products (
    id TEXT PRIMARY KEY NOT NULL,
    businessId TEXT NOT NULL,
    name TEXT NOT NULL,
    sku TEXT,
    categoryId TEXT,
    category TEXT,
    priceCents INTEGER NOT NULL,
    unitOfMeasure TEXT,
    conversionRatio INTEGER,
    isActive INTEGER NOT NULL DEFAULT 1,
    archivedAt INTEGER NOT NULL DEFAULT 0,
    deletedAt INTEGER NOT NULL DEFAULT 0,
    originalPriceCents INTEGER NOT NULL DEFAULT 0,
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL
);
CREATE TABLE businesses (
    id TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    createdAt INTEGER NOT NULL
);
CREATE TABLE product_categories (
    id TEXT PRIMARY KEY NOT NULL,
    businessId TEXT NOT NULL,
    name TEXT NOT NULL,
    createdAt INTEGER NOT NULL
);
CREATE TABLE movements (
    id TEXT PRIMARY KEY NOT NULL,
    productId TEXT NOT NULL,
    type TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    unitPriceCents INTEGER,
    referenceId TEXT,
    createdAt INTEGER NOT NULL,
    deviceId TEXT NOT NULL DEFAULT '',
    synced INTEGER NOT NULL DEFAULT 0,
    businessId TEXT NOT NULL DEFAULT ''
);
CREATE TABLE sale_headers (
    id TEXT PRIMARY KEY NOT NULL,
    businessId TEXT NOT NULL,
    receiptNumber TEXT NOT NULL,
    totalCents INTEGER NOT NULL,
    createdAt INTEGER NOT NULL,
    deviceId TEXT NOT NULL DEFAULT '',
    synced INTEGER NOT NULL DEFAULT 0,
    salespersonId TEXT NOT NULL DEFAULT '',
    salespersonRole TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'completed'
);
CREATE TABLE outbox (
    id TEXT PRIMARY KEY NOT NULL,
    entityType TEXT NOT NULL,
    entityId TEXT NOT NULL,
    operation TEXT NOT NULL,
    payload TEXT NOT NULL,
    createdAt INTEGER NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    synced INTEGER NOT NULL DEFAULT 0
);
"""

LEGACY_DATA = """
INSERT INTO products VALUES
    ('p1','biz1','Hammer','HM-1','cat1','Tools',15000,'piece',1,1,0,0,0,1700000000000,1700000000000),
    ('p2','biz1','Nail','NA-1','cat1','Tools',100,'piece',1,1,0,0,0,1700000000000,1700000000000);
INSERT INTO businesses VALUES ('biz1','Sunday Hardware',1700000000000);
INSERT INTO product_categories VALUES ('cat1','biz1','Tools',1700000000000);
INSERT INTO movements VALUES
    ('m1','p1','SALE',2,15000,'sh1',1700000000000,'dev1',0,'biz1'),
    ('m2','p1','RECEIVE',10,12000,NULL,1700000000000,'dev1',0,'biz1'),
    ('m3','p2','ADJUST',5,NULL,NULL,1700000000000,'dev1',0,'biz1');
INSERT INTO sale_headers VALUES
    ('sh1','biz1','SAL-ABC-00001',30000,1700000000000,'dev1',0,'owner1','owner','completed');
INSERT INTO outbox VALUES
    ('ob1','product','p1','create','{"name":"Hammer"}',1700000000000,0,0),
    ('ob2','sale_session','sh1','update','{}',1700000000001,2,1);
"""


def extract_migration_sql(marker="val MIGRATION_4_5", end_marker="val MIGRATION_5_6"):
    """Pull every execSQL statement out of a migration block.

    Kotlin string templates such as `$col` are not valid SQL, so each
    `listOf(...).forEach { col -> ... }` block is expanded by hand into the
    individual CREATE INDEX statements it produces.
    """
    src = io.open(DB, encoding="utf-8").read()
    start = src.index(marker)
    end = src.index(end_marker)
    block = src[start:end]

    # Expand the index loops first.
    def expand_loop(match):
        cols = [
            c.strip().strip('"')
            for c in match.group("cols").split(",")
            if c.strip()
        ]
        tmpl = match.group("tmpl")
        out = []
        for col in cols:
            out.append(tmpl.replace("$col", col))
        return "\n@@SPLIT@@\n".join(out)

    loop_re = re.compile(
        r"listOf\((?P<cols>[^)]*)\)\.forEach\s*\{\s*col\s*->\s*"
        r'database\.execSQL\("(?P<tmpl>[^"]*)"\)\s*\}',
        re.S,
    )
    block = loop_re.sub(expand_loop, block)

    statements = []
    # Multi-line triple-quoted statements.
    for m in re.finditer(r'execSQL\(\s*"""(.*?)"""\s*\)', block, re.S):
        statements.append(m.group(1))
    # Single-line statements.
    for m in re.finditer(r'execSQL\(\s*"((?:[^"\\]|\\.)*)"\s*\)', block):
        statements.append(m.group(1))
    return statements


def check_v2_to_v3():
    """Run MIGRATION_2_3 against a v2 schema.

    The published APK shipped a v3 database, so this path matters: the
    ALTER TABLE statements have to work on a database that predates the
    columns they add.
    """
    statements = extract_migration_sql("val MIGRATION_2_3", "val MIGRATION_3_4")
    con = sqlite3.connect(":memory:")
    cur = con.cursor()
    # A v2 database: products and sale_headers without the later columns.
    cur.executescript(
        """
        CREATE TABLE products (
            id TEXT PRIMARY KEY NOT NULL,
            businessId TEXT NOT NULL,
            name TEXT NOT NULL,
            sku TEXT,
            categoryId TEXT,
            category TEXT,
            priceCents INTEGER NOT NULL,
            unitOfMeasure TEXT,
            conversionRatio INTEGER,
            isActive INTEGER NOT NULL DEFAULT 1,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL
        );
        CREATE TABLE sale_headers (
            id TEXT PRIMARY KEY NOT NULL,
            businessId TEXT NOT NULL,
            receiptNumber TEXT NOT NULL,
            totalCents INTEGER NOT NULL,
            createdAt INTEGER NOT NULL,
            deviceId TEXT NOT NULL DEFAULT '',
            synced INTEGER NOT NULL DEFAULT 0
        );
        INSERT INTO products VALUES
            ('p1','biz1','Hammer','HM-1','cat1','Tools',15000,'piece',1,1,1700000000000,1700000000000);
        INSERT INTO sale_headers VALUES
            ('sh1','biz1','SAL-ABC-00001',30000,1700000000000,'dev1',0);
        """
    )

    failures = 0
    for stmt in statements:
        sql = stmt.strip()
        if not sql:
            continue
        try:
            cur.execute(sql)
        except Exception as e:
            failures += 1
            print("[MIGRATION_2_3 FAILED] %s" % " ".join(sql.split())[:78])
            print("   error: %s" % e)
    con.commit()

    if failures:
        return failures

    # The new columns must exist with the right defaults.
    cols = {r[1]: r for r in cur.execute("PRAGMA table_info(products)")}
    for name in ("archivedAt", "deletedAt", "originalPriceCents"):
        if name not in cols:
            print("[MIGRATION_2_3] products.%s was not added" % name)
            failures += 1
        elif cols[name][3] != 1:
            print(
                "[MIGRATION_2_3] products.%s is nullable but the entity is NOT NULL"
                % name
            )
            failures += 1

    shcols = {r[1]: r for r in cur.execute("PRAGMA table_info(sale_headers)")}
    for name in ("salespersonId", "salespersonRole", "status"):
        if name not in shcols:
            print("[MIGRATION_2_3] sale_headers.%s was not added" % name)
            failures += 1
        elif shcols[name][3] != 1:
            print(
                "[MIGRATION_2_3] sale_headers.%s is nullable but the entity is NOT NULL"
                % name
            )
            failures += 1

    # The existing rows must still be there.
    count = cur.execute("SELECT COUNT(*) FROM products").fetchone()[0]
    if count != 1:
        print("[MIGRATION_2_3] product rows lost (%d left)" % count)
        failures += 1
    return failures


def check_multi_device_counters():
    """Receipt counters restart per device.

    Two devices each with two sales: the counters have to be 1,2 for the
    first device and 1,2 for the second, not 1,2,3,4 across both.
    """
    statements = extract_migration_sql()
    con = sqlite3.connect(":memory:")
    con.execute("PRAGMA foreign_keys = ON")
    cur = con.cursor()
    cur.executescript(LEGACY_SCHEMA)
    cur.executescript(
        """
        INSERT INTO products VALUES
            ('p1','biz1','Hammer','HM-1','cat1','Tools',15000,'piece',1,1,0,0,0,1700000000000,1700000000000);
        INSERT INTO businesses VALUES ('biz1','Sunday Hardware',1700000000000);
        INSERT INTO product_categories VALUES ('cat1','biz1','Tools',1700000000000);
        INSERT INTO sale_headers VALUES
            ('sh1','biz1','SAL-ABC-00001',30000,1700000000000,'devA',0,'owner1','owner','completed'),
            ('sh2','biz1','SAL-ABC-00002',30000,1700000000100,'devA',0,'owner1','owner','completed'),
            ('sh3','biz1','SAL-XYZ-00001',30000,1700000000200,'devB',0,'owner1','owner','completed'),
            ('sh4','biz1','SAL-XYZ-00002',30000,1700000000300,'devB',0,'owner1','owner','completed');
        """
    )

    for stmt in statements:
        sql = stmt.strip()
        if not sql:
            continue
        try:
            cur.execute(sql)
        except Exception as e:
            print("[MULTI-DEVICE FAILED] %s" % " ".join(sql.split())[:78])
            print("   error: %s" % e)
            return 1
    con.commit()

    problems = []
    rows = cur.execute(
        "SELECT deviceCode, receiptNumber, counter FROM receipts ORDER BY deviceCode, counter"
    ).fetchall()
    expected = [
        ("devA", "SAL-ABC-00001", 1),
        ("devA", "SAL-ABC-00002", 2),
        ("devB", "SAL-XYZ-00001", 1),
        ("devB", "SAL-XYZ-00002", 2),
    ]
    if [tuple(r) for r in rows] != expected:
        problems.append(
            "receipt counters are %r, expected %r" % ([tuple(r) for r in rows], expected)
        )

    counters = cur.execute(
        "SELECT deviceCode, currentValue FROM counters "
        "WHERE businessId='biz1' AND counterType='receipt' ORDER BY deviceCode"
    ).fetchall()
    expected_counters = [("devA", 2), ("devB", 2)]
    if [tuple(r) for r in counters] != expected_counters:
        problems.append(
            "counters are %r, expected %r"
            % ([tuple(r) for r in counters], expected_counters)
        )

    for p in problems:
        print("[MULTI-DEVICE PROBLEM] %s" % p)
    return len(problems)


def check_v2_to_v6():
    """Run the whole upgrade chain against a v2 database.

    A published install has a v2/v3 database. This creates that schema
    exactly as Room v2 did, then applies MIGRATION_2_3, MIGRATION_3_4,
    MIGRATION_4_5 and MIGRATION_5_6 in order, and checks the resulting
    tables against the current entity declarations.
    """
    statements = []
    for start, end in (
        ("val MIGRATION_2_3", "val MIGRATION_3_4"),
        ("val MIGRATION_3_4", "val MIGRATION_4_5"),
        ("val MIGRATION_4_5", "val MIGRATION_5_6"),
    ):
        statements.extend(extract_migration_sql(start, end))

    con = sqlite3.connect(":memory:")
    con.execute("PRAGMA foreign_keys = ON")
    cur = con.cursor()
    # The v2 schema, as Room v2 created it from the v2 entities.
    cur.executescript(
        """
        CREATE TABLE products (
            id TEXT PRIMARY KEY NOT NULL,
            businessId TEXT NOT NULL,
            name TEXT NOT NULL,
            sku TEXT,
            categoryId TEXT,
            category TEXT,
            priceCents INTEGER NOT NULL,
            unitOfMeasure TEXT,
            conversionRatio INTEGER,
            isActive INTEGER NOT NULL DEFAULT 1,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL
        );
        CREATE INDEX IF NOT EXISTS index_products_businessId ON products(businessId);
        CREATE INDEX IF NOT EXISTS index_products_categoryId ON products(categoryId);
        CREATE INDEX IF NOT EXISTS index_products_isActive ON products(isActive);

        CREATE TABLE businesses (
            id TEXT PRIMARY KEY NOT NULL,
            name TEXT NOT NULL,
            createdAt INTEGER NOT NULL
        );
        CREATE TABLE product_categories (
            id TEXT PRIMARY KEY NOT NULL,
            businessId TEXT NOT NULL,
            name TEXT NOT NULL,
            isActive INTEGER NOT NULL DEFAULT 1,
            createdAt INTEGER NOT NULL
        );
        CREATE INDEX IF NOT EXISTS index_product_categories_businessId ON product_categories(businessId);
        CREATE INDEX IF NOT EXISTS index_product_categories_isActive ON product_categories(isActive);
        CREATE TABLE members (
            uid TEXT PRIMARY KEY NOT NULL,
            businessId TEXT NOT NULL,
            role TEXT NOT NULL,
            status TEXT NOT NULL,
            email TEXT,
            createdAt INTEGER NOT NULL
        );
        CREATE TABLE movements (
            id TEXT PRIMARY KEY NOT NULL,
            productId TEXT NOT NULL,
            type TEXT NOT NULL,
            quantity INTEGER NOT NULL,
            unitPriceCents INTEGER,
            referenceId TEXT,
            createdAt INTEGER NOT NULL,
            deviceId TEXT NOT NULL DEFAULT '',
            synced INTEGER NOT NULL DEFAULT 0,
            businessId TEXT NOT NULL DEFAULT '',
            FOREIGN KEY(productId) REFERENCES products(id) ON DELETE CASCADE
        );
        CREATE INDEX IF NOT EXISTS index_movements_productId ON movements(productId);
        CREATE INDEX IF NOT EXISTS index_movements_createdAt ON movements(createdAt);
        CREATE INDEX IF NOT EXISTS index_movements_synced ON movements(synced);
        CREATE INDEX IF NOT EXISTS index_movements_businessId ON movements(businessId);
        CREATE TABLE sale_headers (
            id TEXT PRIMARY KEY NOT NULL,
            businessId TEXT NOT NULL,
            receiptNumber TEXT NOT NULL,
            totalCents INTEGER NOT NULL,
            createdAt INTEGER NOT NULL,
            deviceId TEXT NOT NULL DEFAULT '',
            synced INTEGER NOT NULL DEFAULT 0
        );
        CREATE INDEX IF NOT EXISTS index_sale_headers_receiptNumber ON sale_headers(receiptNumber);
        CREATE INDEX IF NOT EXISTS index_sale_headers_createdAt ON sale_headers(createdAt);
        CREATE INDEX IF NOT EXISTS index_sale_headers_synced ON sale_headers(synced);
        CREATE INDEX IF NOT EXISTS index_sale_headers_businessId ON sale_headers(businessId);
        CREATE TABLE outbox (
            id TEXT PRIMARY KEY NOT NULL,
            entityType TEXT NOT NULL,
            entityId TEXT NOT NULL,
            operation TEXT NOT NULL,
            payload TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            attempts INTEGER NOT NULL DEFAULT 0,
            synced INTEGER NOT NULL DEFAULT 0
        );
        CREATE INDEX IF NOT EXISTS index_outbox_createdAt ON outbox(createdAt);
        CREATE INDEX IF NOT EXISTS index_outbox_synced ON outbox(synced);

        INSERT INTO products VALUES
            ('p1','biz1','Hammer','HM-1','cat1','Tools',15000,'piece',1,1,1700000000000,1700000000000);
        INSERT INTO businesses VALUES ('biz1','Sunday Hardware',1700000000000);
        INSERT INTO product_categories VALUES ('cat1','biz1','Tools',1,1700000000000);
        INSERT INTO members VALUES ('u1','biz1','owner','active','a@b.c',1700000000000);
        INSERT INTO movements VALUES
            ('m1','p1','SALE',2,15000,'sh1',1700000000000,'dev1',0,'biz1');
        INSERT INTO sale_headers VALUES
            ('sh1','biz1','SAL-ABC-00001',30000,1700000000000,'dev1',0);
        INSERT INTO outbox VALUES
            ('ob1','product','p1','create','{}',1700000000000,0,0);
        """
    )

    failures = 0
    for stmt in statements:
        sql = stmt.strip()
        if not sql:
            continue
        try:
            cur.execute(sql)
        except Exception as e:
            failures += 1
            print("[CHAIN FAILED] %s" % " ".join(sql.split())[:78])
            print("   error: %s" % e)
    con.commit()
    if failures:
        return failures

    problems = []

    # Every current entity table must exist after the chain.
    ent_dir = os.path.join(
        ROOT, "app", "src", "main", "java", "com", "example", "sande", "data",
        "entities",
    )
    for fname in sorted(os.listdir(ent_dir)):
        if not fname.endswith(".kt"):
            continue
        src = io.open(os.path.join(ent_dir, fname), encoding="utf-8").read()
        tm = re.search(r'tableName\s*=\s*"([^"]+)"', src)
        if not tm:
            continue
        table = tm.group(1)
        exists = cur.execute(
            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?",
            (table,),
        ).fetchone()[0]
        if not exists:
            problems.append("table %s does not exist after the v2->v6 chain" % table)

    # The legacy rows must have survived.
    if cur.execute("SELECT COUNT(*) FROM products").fetchone()[0] != 1:
        problems.append("product rows lost during the chain")
    if cur.execute("SELECT COUNT(*) FROM members").fetchone()[0] != 1:
        problems.append("member rows lost during the chain")

    # The sale must have become a session plus a line item.
    sessions = cur.execute("SELECT COUNT(*) FROM sale_sessions").fetchone()[0]
    if sessions != 1:
        problems.append("expected 1 migrated sale_session, found %d" % sessions)
    items = cur.execute("SELECT COUNT(*) FROM sale_items").fetchone()[0]
    if items != 1:
        problems.append("expected 1 migrated sale_item, found %d" % items)

    # Every legacy sale must have become a receipt.
    receipts = cur.execute("SELECT COUNT(*) FROM receipts").fetchone()[0]
    if receipts != 1:
        problems.append("expected 1 migrated receipt, found %d" % receipts)

    # The receipt has to point at a migrated sale session.
    dangling = cur.execute(
        "SELECT COUNT(*) FROM receipts r "
        "LEFT JOIN sale_sessions s ON s.id = r.sessionId "
        "WHERE s.id IS NULL"
    ).fetchone()[0]
    if dangling:
        problems.append("%d receipt(s) point at a missing sale_session" % dangling)

    # The per-device receipt counter restarts at 1 for each device.
    bad_counter = cur.execute(
        "SELECT COUNT(*) FROM receipts WHERE counter < 1"
    ).fetchone()[0]
    if bad_counter:
        problems.append("%d receipt(s) have a counter below 1" % bad_counter)

    # No dangling foreign keys anywhere.
    bad = cur.execute("SELECT COUNT(*) FROM pragma_foreign_key_check").fetchone()[0]
    if bad:
        problems.append("%d foreign key violation(s) after the chain" % bad)

    for p in problems:
        print("[CHAIN PROBLEM] %s" % p)
    return len(problems)


def main():
    statements = extract_migration_sql()
    if not statements:
        print("FAIL: no migration SQL found")
        return 1

    con = sqlite3.connect(":memory:")
    con.execute("PRAGMA foreign_keys = ON")
    cur = con.cursor()
    cur.executescript(LEGACY_SCHEMA)
    cur.executescript(LEGACY_DATA)

    print("Executing %d migration statements..." % len(statements))
    failures = 0
    for i, stmt in enumerate(statements, 1):
        sql = stmt.strip()
        if not sql:
            continue
        label = " ".join(sql.split())[:78]
        try:
            cur.execute(sql)
        except Exception as e:
            failures += 1
            print("")
            print("STATEMENT %d FAILED: %s" % (i, label))
            print("   error: %s" % e)
    con.commit()

    print("")
    if failures:
        print("RESULT: %d statement(s) failed" % failures)
        return 1

    # Now check the data landed correctly.
    print("Verifying migrated data...")
    problems = []

    def scalar(sql):
        return cur.execute(sql).fetchone()[0]

    # movementType must be the enum NAME, not a cast integer.
    rows = cur.execute(
        "SELECT movementType, quantityDelta FROM stock_movements ORDER BY id"
    ).fetchall()
    for movement_type, delta in rows:
        if movement_type not in ("SALE", "RECEIVE", "ADJUST", "VOID"):
            problems.append(
                "stock_movements.movementType = %r (expected an enum name)" % movement_type
            )
    sale_delta = cur.execute(
        "SELECT quantityDelta FROM stock_movements WHERE movementType='SALE'"
    ).fetchone()
    if sale_delta and sale_delta[0] != -2:
        problems.append(
            "SALE quantityDelta = %r (expected -2)" % (sale_delta[0],)
        )
    receive_delta = cur.execute(
        "SELECT quantityDelta FROM stock_movements WHERE movementType='RECEIVE'"
    ).fetchone()
    if receive_delta and receive_delta[0] != 10:
        problems.append(
            "RECEIVE quantityDelta = %r (expected 10)" % (receive_delta[0],)
        )

    # outbox -> outbox_entries column mapping.
    ob = cur.execute(
        "SELECT businessId, payloadJson, attemptCount FROM outbox_entries "
        "WHERE id='ob2'"
    ).fetchone()
    if not ob:
        problems.append("outbox row ob2 was not migrated")
    else:
        if ob[0] != "biz1":
            problems.append("outbox_entries.businessId = %r (expected biz1)" % ob[0])
        if ob[1] != "{}":
            problems.append("outbox_entries.payloadJson = %r (expected {})" % ob[1])
        if ob[2] != 2:
            problems.append("outbox_entries.attemptCount = %r (expected 2)" % ob[2])

    # sale_items NOT NULL columns must be populated.
    for row in cur.execute(
        "SELECT productName, categoryName, unitPriceCents, totalCents FROM sale_items"
    ):
        if not row[0] or row[1] is None or row[2] is None or row[3] is None:
            problems.append("sale_items row has a NULL in a NOT NULL column: %r" % (row,))

    # The receipt counter must be seeded from the legacy sale headers.
    counter = cur.execute(
        "SELECT currentValue FROM counters WHERE counterType='receipt'"
    ).fetchone()
    if not counter or counter[0] != 1:
        problems.append("counters.currentValue = %r (expected 1)" % (counter,))

    # Every legacy sale must have become a receipt, numbered per device.
    receipt_count = scalar("SELECT COUNT(*) FROM receipts")
    if receipt_count != 1:
        problems.append(
            "expected 1 migrated receipt, found %d" % receipt_count
        )
    rc = cur.execute(
        "SELECT receiptNumber, deviceCode, counter, sessionId FROM receipts"
    ).fetchone()
    if rc:
        if rc[0] != "SAL-ABC-00001":
            problems.append("receipts.receiptNumber = %r (expected SAL-ABC-00001)" % rc[0])
        if rc[2] != 1:
            problems.append("receipts.counter = %r (expected 1)" % rc[2])
        # The receipt has to point at the migrated sale session.
        sess = cur.execute(
            "SELECT COUNT(*) FROM sale_sessions WHERE id=?", (rc[3],)
        ).fetchone()[0]
        if sess != 1:
            problems.append(
                "receipts.sessionId %r does not match a migrated sale_session" % rc[3]
            )

    # A business profile must exist for the legacy business.
    prof = cur.execute(
        "SELECT name FROM business_profiles WHERE id='biz1'"
    ).fetchone()
    if not prof:
        problems.append("business_profiles row for biz1 was not created")

    # Foreign key integrity across the new tables.
    for pragma in ("stock_movements", "sale_items", "outbox_entries"):
        bad = scalar("SELECT COUNT(*) FROM pragma_foreign_key_check")
        if bad:
            problems.append("foreign key violations after migration: %d" % bad)
            break

    print("")
    for p in problems:
        print("[DATA PROBLEM] %s" % p)
    if problems:
        print("RESULT: %d problem(s)" % len(problems))
        return 1

    print("Checking MIGRATION_2_3 (v2 -> v3)...")
    v23 = check_v2_to_v3()
    if v23:
        print("RESULT: %d problem(s) in MIGRATION_2_3" % v23)
        return 1

    print("Checking the full v2 -> v6 chain...")
    chain = check_v2_to_v6()
    if chain:
        print("RESULT: %d problem(s) in the v2->v6 chain" % chain)
        return 1

    print("Checking per-device receipt counters...")
    multi = check_multi_device_counters()
    if multi:
        print("RESULT: %d problem(s) with multi-device counters" % multi)
        return 1

    print("")
    print("RESULT: clean")
    return 0


if __name__ == "__main__":
    sys.exit(main())
