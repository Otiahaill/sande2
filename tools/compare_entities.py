#!/usr/bin/env python3
"""Compare Room entity column sets between two git revisions.

Used to verify that MIGRATION_4_5 (which is relative to the v3 schema) produces
a table shape that matches the current v6 entities, since Room validates the
full column set of every known table after a migration.

Usage: python3 tools/compare_entities.py <old-rev> <new-rev>
"""
import re
import subprocess
import sys

ROOT = "/storage/emulated/0/Android/data/com.androidharness.app/files/workspace/sande-2-online"
ENT_DIR = "app/src/main/java/com/example/sande/data/entities"

DECL_RE = re.compile(
    r"^\s*(?:@\w+(?:\([^)]*\))?\s*)*"
    r"(?:public\s+|internal\s+|private\s+|open\s+|abstract\s+|sealed\s+|data\s+|value\s+|enum\s+|annotation\s+)*"
    r"(?:class|interface|object|fun|val|var|typealias)\s+"
    r"(?:<[^>]*>\s*)?"
    r"([A-Za-z_][A-Za-z0-9_]*)",
    re.M,
)


def entities_at(rev):
    out = subprocess.run(
        ["git", "ls-tree", "-r", "--name-only", rev, ENT_DIR],
        cwd=ROOT, capture_output=True, text=True, check=True).stdout
    result = {}
    for path in out.splitlines():
        if not path.endswith(".kt"):
            continue
        src = subprocess.run(["git", "show", f"{rev}:{path}"], cwd=ROOT,
                             capture_output=True, text=True, check=True).stdout
        cls = re.search(r"(?:data\s+)?class\s+([A-Za-z_][A-Za-z0-9_]*)", src)
        if not cls:
            continue
        name = cls.group(1)
        table = re.search(r'tableName\s*=\s*"([^"]+)"', src)
        # Columns: constructor params of the primary constructor.
        body = src[src.index("class "):]
        ctor = re.search(r"\(\s*(.*?)\n\)", body, re.S)
        cols = []
        if ctor:
            for line in ctor.group(1).splitlines():
                line = line.strip().rstrip(",")
                m = re.match(r"(?:@\w+(?:\([^)]*\))?\s*)*(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)\s*:\s*([^=]+)", line)
                if m:
                    cols.append((m.group(1), m.group(2).strip()))
        result[name] = {
            "table": table.group(1) if table else name,
            "cols": cols,
        }
    return result


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    old_rev, new_rev = sys.argv[1], sys.argv[2]
    old = entities_at(old_rev)
    new = entities_at(new_rev)

    print(f"=== entities at {old_rev}: {len(old)} ===")
    for n, v in sorted(old.items()):
        print(f"  {n} -> {v['table']}: {[c[0] for c in v['cols']]}")

    print(f"\n=== entities at {new_rev}: {len(new)} ===")
    for n, v in sorted(new.items()):
        print(f"  {n} -> {v['table']}: {[c[0] for c in v['cols']]}")

    print("\n=== entities present in OLD but GONE in NEW (table stays in the DB, orphaned) ===")
    for n in sorted(set(old) - set(new)):
        print(f"  {n} ({old[n]['table']})")

    print("\n=== column changes for entities in BOTH ===")
    for n in sorted(set(old) & set(new)):
        o = [c[0] for c in old[n]["cols"]]
        w = [c[0] for c in new[n]["cols"]]
        if o != w:
            print(f"  {n} ({old[n]['table']}):")
            print(f"      old: {o}")
            print(f"      new: {w}")
            for cn, ct in new[n]["cols"]:
                for on, ot in old[n]["cols"]:
                    if cn == on and ct != ot:
                        print(f"      type change {cn}: {ot}  ->  {ct}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
