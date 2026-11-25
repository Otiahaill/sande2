"""
Compare the tables created by MIGRATION_4_5 against the @Entity declarations.

Room validates the on-disk schema against the compiled entity schema at
runtime (SchemaBundle / TableInfo.read). Any mismatch in column names, types,
nullability, defaults, primary keys, foreign keys or indices makes the
database throw IllegalStateException on first open.

This script parses both sides and reports the differences. It is a static
approximation of Room's own comparison, but it catches the mistakes that
actually happen: a missing column, a NOT NULL that the entity does not
declare, a foreign key that was forgotten, or a missing index.
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(ROOT, "app", "src", "main", "java", "com", "example", "sande")
DB = os.path.join(APP, "data", "db", "AppDatabase.kt")
ENT = os.path.join(APP, "data", "entities")

# ---------- entity parsing ----------


def parse_entities():
    """Return {tableName: {columns, pk, fks, indices, notNull}} per entity file."""
    out = {}
    for fname in sorted(os.listdir(ENT)):
        if not fname.endswith(".kt"):
            continue
        src = io.open(os.path.join(ENT, fname), encoding="utf-8").read()
        m = re.search(r"@Entity\s*\((.*?)\)\s*data class (\w+)", src, re.S)
        if not m:
            continue
        ann, cls = m.group(1), m.group(2)
        tm = re.search(r'tableName\s*=\s*"([^"]+)"', ann)
        table = tm.group(1) if tm else cls.lower()

        cols, notnull = [], []
        body = src[m.end():]
        for pm in re.finditer(
            r"@PrimaryKey\s+(?:val\s+)?(\w+):\s*([\w<>?]+)|"
            r"(?<!@PrimaryKey\s)(?:val\s+)?(\w+):\s*([\w<>?]+)",
            body,
        ):
            if pm.group(1):
                name, typ = pm.group(1), pm.group(2)
            else:
                name, typ = pm.group(3), pm.group(4)
            if name in ("id",):
                pass
            if name in [c[0] for c in cols]:
                continue
            cols.append((name, typ))
            # A Kotlin non-null type means NOT NULL in the generated schema.
            if not typ.endswith("?"):
                notnull.append(name)

        fks = []
        for fm in re.finditer(
            r"ForeignKey\s*\(\s*entity\s*=\s*\w+::class,\s*parentColumns\s*=\s*\[([^\]]*)\],"
            r"\s*childColumns\s*=\s*\[([^\]]*)\],\s*onDelete\s*=\s*ForeignKey\.(\w+)",
            ann,
        ):
            fks.append(
                (
                    [x.strip().strip('"') for x in fm.group(1).split(",")],
                    [x.strip().strip('"') for x in fm.group(2).split(",")],
                    fm.group(3),
                )
            )

        indices = []
        for im in re.finditer(r"Index\s*\((.*?)\)", ann, re.S):
            block = im.group(1)
            vals = re.search(r"value\s*=\s*\[([^\]]*)\]", block)
            names = re.findall(r"Index\(\s*\"([^\"]+)\"", block)
            if vals:
                names = [x.strip().strip('"') for x in vals.group(1).split(",") if x.strip()]
            indices.append(names)

        out[table] = {
            "class": cls,
            "cols": cols,
            "notnull": notnull,
            "fks": fks,
            "indices": indices,
        }
    return out


# ---------- migration parsing ----------


def parse_migration():
    src = io.open(DB, encoding="utf-8").read()
    tables = {}
    for m in re.finditer(
        r"CREATE TABLE IF NOT EXISTS (\w+)\s*\((.*?)\n\s*\)\s*\)", src, re.S
    ):
        name, body = m.group(1), m.group(2)
        cols = []
        for line in body.split("\n"):
            line = line.strip().rstrip(",")
            if not line:
                continue
            cm = re.match(r"(\w+)\s+([A-Z]+)(\s+NOT NULL)?(\s+DEFAULT\s+(\S+))?", line)
            if cm:
                # `id TEXT PRIMARY KEY NOT NULL` and `id TEXT PRIMARY KEY` are
                # both NOT NULL columns in SQLite.
                is_not_null = bool(cm.group(3)) or "PRIMARY KEY" in line
                cols.append(
                    (
                        cm.group(1),
                        cm.group(2),
                        is_not_null,
                        cm.group(5),
                    )
                )
            elif "FOREIGN KEY" in line:
                cols.append(("__FK__", line, None, None))
            elif line.startswith("UNIQUE"):
                cols.append(("__UNIQUE__", line, None, None))
        # indices created for this table
        idx = []
        for im in re.finditer(
            r"CREATE INDEX IF NOT EXISTS index_%s_(\w+) ON %s" % (name, name), src
        ):
            idx.append([im.group(1)])
        tables[name] = {"cols": cols, "indices": idx}
    return tables


def main():
    entities = parse_entities()
    mig = parse_migration()

    problems = 0
    for table, ent in sorted(entities.items()):
        if table not in mig:
            continue
        m = mig[table]
        mig_cols = {c[0]: c for c in m["cols"] if not c[0].startswith("__")}
        ent_cols = {c[0]: c[1] for c in ent["cols"]}

        # 1. columns present in the entity but missing from the CREATE TABLE
        missing = [c for c in ent_cols if c not in mig_cols]
        if missing:
            problems += 1
            print("[MISSING COLUMN] %s: %s" % (table, ", ".join(missing)))

        # 2. NOT NULL mismatch
        for name, ctype in ent_cols.items():
            if name not in mig_cols:
                continue
            want_notnull = name in ent["notnull"]
            got_notnull = mig_cols[name][2]
            if want_notnull != got_notnull:
                problems += 1
                print(
                    "[NOT NULL MISMATCH] %s.%s entity=%s migration=%s"
                    % (table, name, want_notnull, got_notnull)
                )

        # 3. foreign keys
        mig_fk_text = " ".join(
            c[1] for c in m["cols"] if c[0] == "__FK__"
        ).upper()
        for parents, children, ondelete in ent["fks"]:
            for child in children:
                if child.upper() not in mig_fk_text:
                    problems += 1
                    print(
                        "[MISSING FK] %s.%s -> %s (%s)"
                        % (table, child, parents, ondelete)
                    )

        # 4. indices
        mig_idx_cols = set()
        for group in m["indices"]:
            mig_idx_cols.update(group)
        for group in ent["indices"]:
            for col in group:
                if col not in mig_idx_cols:
                    problems += 1
                    print("[MISSING INDEX] %s(%s)" % (table, col))

    print("")
    if problems:
        print("RESULT: %d schema problem(s) found" % problems)
        return 1
    print("RESULT: clean")
    return 0


if __name__ == "__main__":
    sys.exit(main())
