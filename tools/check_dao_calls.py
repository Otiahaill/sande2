#!/usr/bin/env python3
"""Check that every `somethingDao().method(...)` call resolves to a real DAO method.

Also checks `database.xxxDao()` resolves to a DAO accessor on AppDatabase.

This catches the class of bug that broke LegacyAdoptionManager: calls to DAO
methods that were never declared (or that were removed with the old schema).

Run:  python3 tools/check_dao_calls.py
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

FUN_RE = re.compile(r"\b(?:suspend\s+)?fun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(")


def kotlin_files(base):
    out = []
    if not os.path.isdir(base):
        return out
    for dirpath, _dirs, names in os.walk(base):
        for n in sorted(names):
            if n.endswith(".kt"):
                out.append(os.path.join(dirpath, n))
    return out


def main():
    files = []
    for _l, b in SOURCESETS:
        files += kotlin_files(b)

    # DAO interface name -> set of method names
    dao_methods = {}
    db_accessors = set()
    for path in files:
        raw = open(path, encoding="utf-8", errors="replace").read()
        pm = re.search(r"^package\s+([\w.]+)", raw, re.M)
        pkg = pm.group(1) if pm else ""
        if "/data/daos/" in path.replace("\\", "/"):
            iface = re.search(r"^interface\s+([A-Za-z_][A-Za-z0-9_]*)", raw, re.M)
            if iface:
                dao_methods[iface.group(1)] = set(FUN_RE.findall(raw))
        if path.endswith(os.path.join("db", "AppDatabase.kt")):
            db_accessors = set(re.findall(r"abstract fun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(\)", raw))

    problems = []

    # database.xxxDao().method(...)
    call_re = re.compile(r"\b(?:database|db)\s*\.\s*([a-zA-Z_][A-Za-z0-9_]*)\s*\(\s*\)\s*\.\s*([a-zA-Z_][A-Za-z0-9_]*)\s*\(")

    def dao_methods_for(accessor):
        """Map an accessor name (productDao) to its DAO interface (ProductDao)."""
        if accessor in dao_methods:
            return dao_methods[accessor]
        cap = accessor[:1].upper() + accessor[1:]
        if cap in dao_methods:
            return dao_methods[cap]
        # e.g. outboxDao -> OutboxDao
        return set()

    for label, base in SOURCESETS:
        if not os.path.isdir(base):
            continue
        for path in kotlin_files(base):
            raw = open(path, encoding="utf-8", errors="replace").read()
            for m in call_re.finditer(raw):
                accessor, method = m.group(1), m.group(2)
                if not accessor.endswith("Dao"):
                    continue
                if accessor not in db_accessors:
                    problems.append((path, f"database.{accessor}() is not a DAO accessor on AppDatabase"))
                else:
                    methods = dao_methods_for(accessor)
                    if method not in methods:
                        problems.append((path, f"{accessor}.{method}() does not exist"))

    if problems:
        print(f"=== {len(problems)} unresolved DAO call(s) ===")
        seen = set()
        for path, msg in problems:
            key = (path, msg)
            if key in seen:
                continue
            seen.add(key)
            print(f"  {os.path.relpath(path, ROOT)}\n      {msg}")
        print("\nRESULT: PROBLEMS FOUND")
        return 1
    print("=== clean ===\nRESULT: clean")
    return 0


if __name__ == "__main__":
    sys.exit(main())
