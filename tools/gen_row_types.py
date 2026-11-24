"""Generate TypeScript row interfaces from the D1 SQL migrations.

D1 returns rows as `Record<string, unknown>`, so every property access on a
row needs either a cast or an index signature. Typing the rows properly
removes the `noPropertyAccessFromIndexSignature` errors and makes the queries
self-documenting.

This parses the CREATE TABLE statements in backend/migrations/*.sql and writes
backend/src/db/rows.ts.
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MIGRATIONS = os.path.join(ROOT, "backend", "migrations")
TARGET = os.path.join(ROOT, "backend", "src", "db", "rows.ts")

# Table name -> TypeScript interface name.
NAMES = {
    "businesses": "BusinessRow",
    "accounts": "AccountRow",
    "memberships": "MembershipRow",
    "invitations": "InvitationRow",
    "devices": "DeviceRow",
    "refresh_tokens": "RefreshTokenRow",
    "recovery_codes": "RecoveryCodeRow",
    "sync_batches": "SyncBatchRow",
    "sync_exceptions": "SyncExceptionRow",
    "change_log": "ChangeLogRow",
    "backups": "BackupRow",
    "app_versions": "AppVersionRow",
    "platform_admins": "PlatformAdminRow",
    "support_access_grants": "SupportAccessGrantRow",
    "products": "ProductRow",
    "product_categories": "ProductCategoryRow",
    "product_price_history": "ProductPriceHistoryRow",
    "members": "MemberRow",
    "sale_sessions": "SaleSessionRow",
    "sale_items": "SaleItemRow",
    "stock_movements": "StockMovementRow",
    "receipts": "ReceiptRow",
    "counters": "CounterRow",
    "draft_sales": "DraftSaleRow",
    "audit_events": "AuditEventRow",
    "outbox": "OutboxRow",
    "outbox_entries": "OutboxEntryRow",
    "sync_cursors": "SyncCursorRow",
}


def sql_type_to_ts(sql_type, not_null):
    t = sql_type.upper()
    if "INT" in t:
        return "number"
    if "REAL" in t or "FLOA" in t or "DOUB" in t:
        return "number"
    if "BLOB" in t:
        return "ArrayBuffer"
    return "string"


def parse_tables():
    tables = {}
    for name in sorted(os.listdir(MIGRATIONS)):
        if not name.endswith(".sql"):
            continue
        src = io.open(os.path.join(MIGRATIONS, name), encoding="utf-8").read()
        for m in re.finditer(
            r"CREATE TABLE IF NOT EXISTS (\w+)\s*\((.*?)\n\);", src, re.S
        ):
            table, body = m.group(1), m.group(2)
            cols = []
            for line in body.split("\n"):
                line = line.strip().rstrip(",")
                cm = re.match(
                    r"(\w+)\s+([A-Z]+)(\s+NOT NULL)?(\s+DEFAULT\s+[^\s,]+)?", line
                )
                if not cm:
                    continue
                col, sql_type, not_null = cm.group(1), cm.group(2), cm.group(3)
                cols.append(
                    (col, sql_type_to_ts(sql_type, bool(not_null)), bool(not_null))
                )
            tables[table] = cols
    return tables


def main():
    tables = parse_tables()
    out = [
        "/**",
        " * Row shapes for the D1 tables.",
        " *",
        " * D1 returns every row as `Record<string, unknown>`. These interfaces",
        " * describe the actual columns so the query code is type checked.",
        " *",
        " * Generated from backend/migrations/*.sql by tools/gen_row_types.py.",
        " */",
        "",
    ]
    for table, cols in sorted(tables.items()):
        name = NAMES.get(table)
        if not name:
            print("  (no interface name for table %s - skipped)" % table)
            continue
        out.append("export interface %s {" % name)
        for col, ts, _not_null in cols:
            out.append("  %s: %s;" % (col, ts))
        out.append("}")
        out.append("")

    io.open(TARGET, "w", encoding="utf-8").write("\n".join(out))
    print("wrote %d interfaces to %s" % (len(tables), TARGET))
    return 0


if __name__ == "__main__":
    sys.exit(main())
