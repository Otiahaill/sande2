"""Apply the generated row types to the D1 queries.

Every `SELECT` in the backend now gets the matching row interface as the
type argument of `.first<T>()` / `.all<T>()`, so the rows are typed instead of
being `Record<string, unknown>`.
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BACKEND = os.path.join(ROOT, "backend", "src")
ROWS = os.path.join(BACKEND, "db", "rows.ts")

# Table -> interface name, read from the generated file.
def load_names():
    src = io.open(ROWS, encoding="utf-8").read()
    names = {}
    for m in re.finditer(
        r"export interface (\w+)\s*\{([^}]*)\}", src, re.S
    ):
        iface, body = m.group(1), m.group(2)
        # The first column is the primary key; the table name is not in the
        # interface, so the mapping comes from the generator's table list.
        names[iface] = iface
    return names


# The table-to-interface mapping, kept in sync with tools/gen_row_types.py.
TABLE_TO_IFACE = {
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
    "business_profiles": "BusinessProfileRow",
    "device_registrations": "DeviceRegistrationRow",
    "membership_mirrors": "MembershipMirrorRow",
}

SELECT_RE = re.compile(r"SELECT\s+[\s\S]*?\s+FROM\s+(\w+)", re.IGNORECASE)


def table_of(statement):
    """The table a statement selects from, or None."""
    m = SELECT_RE.search(statement)
    if not m:
        return None
    table = m.group(1)
    return TABLE_TO_IFACE.get(table)


def rewrite_statement(chunk):
    """Adds the type argument to .first() / .all() in one statement."""
    iface = table_of(chunk)
    if not iface:
        return chunk
    # Don't type it twice.
    if re.search(r"\.first<\w+>\(\)", chunk) or re.search(r"\.all<\w+>\(\)", chunk):
        return chunk
    chunk = re.sub(r"\.first\(\)", ".first<%s>()" % iface, chunk)
    chunk = re.sub(r"\.all\(\)", ".all<%s>()" % iface, chunk)
    # The rows() helper takes the same type argument.
    chunk = re.sub(
        r"rows\(([\s\S]*?\.all<\w+>\(\))\)", r"rows<%s>(\1)" % iface, chunk
    )
    return chunk


def main():
    changed = 0
    total = 0
    for dirpath, _dirs, names in os.walk(BACKEND):
        if "node_modules" in dirpath:
            continue
        for name in sorted(names):
            if not name.endswith(".ts") or name.endswith(".test.ts"):
                continue
            path = os.path.join(dirpath, name)
            src = io.open(path, encoding="utf-8").read()
            if ".first()" not in src and ".all()" not in src:
                continue
            out = []
            count = 0
            for chunk in src.split(";"):
                new = rewrite_statement(chunk)
                if new != chunk:
                    count += 1
                out.append(new)
            new_src = ";".join(out)
            if new_src != src:
                io.open(path, "w", encoding="utf-8").write(new_src)
                changed += 1
                total += count
                print("typed %d statement(s) in %s" % (count, name))
    print("\n%d file(s), %d statement(s)" % (changed, total))
    return 0


if __name__ == "__main__":
    sys.exit(main())
