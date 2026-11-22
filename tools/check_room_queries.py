#!/usr/bin/env python3
"""Check that every column referenced in a Room @Query exists on the table.

Room's KSP processor validates every @Query against the entity schema at
compile time and fails with:

    There is a problem with the query: [SQLITE_ERROR] SQL error or missing
    database (no such column: <name>)

This script extracts the table + columns from each @Query and compares them
against the @Entity column lists, so a bad column is found without a build.

Run:  python3 tools/check_room_queries.py
Exit code 0 = clean, 1 = problems found.
"""
import os
import re
import sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(ROOT, "app", "src")
SOURCESETS = [("main", os.path.join(APP, "main")),
              ("test", os.path.join(APP, "test")),
              ("androidTest", os.path.join(APP, "androidTest"))]

ENTITY_DIR = os.path.join(APP, "main", "java", "com", "example", "sande", "data", "entities")

QUERY_RE = re.compile(r'@Query\(\s*"((?:[^"\\]|\\.)*)"\s*\)', re.S)
TABLE_RE = re.compile(r"tableName\s*=\s*\"([^\"]+)\"")

# A @Query body: capture the FROM/JOIN tables and the bare column identifiers.
SQL_KEYWORDS = {
    "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "NULL", "AS", "IN", "IS",
    "LIKE", "BETWEEN", "ORDER", "BY", "GROUP", "HAVING", "LIMIT", "OFFSET",
    "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "ON", "CONFLICT",
    "REPLACE", "IGNORE", "CASE", "WHEN", "THEN", "ELSE", "END", "JOIN",
    "LEFT", "RIGHT", "INNER", "OUTER", "CROSS", "DISTINCT", "ALL", "UNION",
    "EXISTS", "PRIMARY", "KEY", "UNIQUE", "DEFAULT", "COLLATE", "ASC", "DESC",
    "COALESCE", "SUM", "COUNT", "MAX", "MIN", "AVG", "IFNULL", "NULLIF",
    "CAST", "LOWER", "UPPER", "TRIM", "LENGTH", "SUBSTR", "REPLACE", "ABS",
    "ROUND", "EXISTS", "IF", "AUTOINCREMENT", "WITHOUT", "ROWID",
}


def strip_sql_comments(q):
    q = re.sub(r"--[^\n]*", " ", q)
    q = re.sub(r"/\*.*?\*/", " ", q, flags=re.S)
    return q


def load_entity_columns():
    """table name -> set(column names)"""
    tables = defaultdict(set)
    if not os.path.isdir(ENTITY_DIR):
        return tables
    for name in sorted(os.listdir(ENTITY_DIR)):
        if not name.endswith(".kt"):
            continue
        src = open(os.path.join(ENTITY_DIR, name), encoding="utf-8", errors="replace").read()
        tm = TABLE_RE.search(src)
        if not tm:
            continue
        table = tm.group(1)
        # constructor params
        ctor = re.search(r"\(\s*(.*?)\n\)", src[src.index("class "):], re.S)
        if not ctor:
            continue
        for line in ctor.group(1).splitlines():
            line = line.strip().rstrip(",")
            m = re.match(r"(?:@\w+(?:\([^)]*\))?\s*)*(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)\s*:", line)
            if m:
                tables[table].add(m.group(1))
    return tables


def referenced_tables(sql):
    """Tables named after FROM / JOIN / INTO / UPDATE."""
    out = []
    for m in re.finditer(r"\b(?:FROM|JOIN|INTO|UPDATE)\s+([A-Za-z_][A-Za-z0-9_]*)", sql, re.I):
        t = m.group(1)
        if t.lower() not in ("select", "if", "exists"):
            out.append(t)
    return out


def referenced_columns(sql, tables):
    """Column-looking identifiers that are not SQL keywords, literals or aliases."""
    body = re.sub(r"'(?:''|[^'])*'", " ", sql)          # string literals
    body = re.sub(r'"(?:[^"]|"")*"', " ", body)          # quoted identifiers
    body = re.sub(r"--[^\n]*", " ", body)
    body = re.sub(r"\b\d+(\.\d+)?\b", " ", body)         # numbers
    found = set()
    for m in re.finditer(r"\b([A-Za-z_][A-Za-z0-9_]*)\b", body):
        w = m.group(1)
        if w.upper() in SQL_KEYWORDS:
            continue
        # Skip bind parameters (:name) and their neighbours.
        start = m.start()
        if start > 0 and body[start - 1] == ":":
            continue
        # Skip aliases introduced by AS.
        prefix = body[max(0, start - 12):start].upper()
        if re.search(r"\bAS\s*$", prefix):
            continue
        found.add(w)
    return found


def main():
    tables = load_entity_columns()
    problems = []

    for label, base in SOURCESETS:
        if not os.path.isdir(base):
            continue
        for dirpath, _dirs, names in os.walk(base):
            for n in sorted(names):
                if not n.endswith(".kt"):
                    continue
                path = os.path.join(dirpath, n)
                raw = open(path, encoding="utf-8", errors="replace").read()
                for qm in QUERY_RE.finditer(raw):
                    sql = strip_sql_comments(qm.group(1))
                    line_no = raw[:qm.start()].count("\n") + 1
                    used_tables = referenced_tables(sql)
                    known = set()
                    for t in used_tables:
                        if t in tables:
                            known |= tables[t]
                        else:
                            problems.append((path, line_no, f"unknown table '{t}'"))
                    if not known:
                        continue
                    for col in referenced_columns(sql, known):
                        # column may belong to any of the used tables
                        if any(col in tables[t] for t in used_tables if t in tables):
                            continue
                        # the table name itself is not a column
                        if col in used_tables:
                            continue
                        # allow aliases and SQLite builtins
                        if col.startswith("__"):
                            continue
                        problems.append((path, line_no, f"column '{col}' not found on {sorted(used_tables)}"))

    if problems:
        print(f"=== {len(problems)} possible query/schema mismatch(es) ===")
        seen = set()
        for path, line, msg in problems:
            key = (path, line, msg)
            if key in seen:
                continue
            seen.add(key)
            print(f"  {os.path.relpath(path, ROOT)}:{line}\n      {msg}")
        print("\nRESULT: PROBLEMS FOUND")
        return 1
    print("=== clean ===\nRESULT: clean")
    return 0


if __name__ == "__main__":
    sys.exit(main())
