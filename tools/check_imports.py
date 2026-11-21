#!/usr/bin/env python3
"""Static check for unresolved Kotlin references in the SANDE Android app.

Pass 1 — IMPORT RESOLUTION
    Every `import com.example.sande.*` must resolve to a symbol actually
    declared in the project. Catches imports of deleted/renamed classes.

Pass 2 — MISSING IMPORT OF A PROJECT SYMBOL
    Every symbol declared in the project (classes, objects, interfaces, enums,
    top-level functions and properties) that is *used* in a file must be
    imported by that file or declared in it. This catches the classic
    "used AppDatabase but forgot the import" error, which the Kotlin compiler
    would otherwise catch for us.

Deliberately does NOT try to validate AndroidX / Kotlin stdlib symbols —
those are covered by the compiler and produce too much noise here.

Run:  python3 tools/check_imports.py
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

PROJECT_PREFIX = "com.example.sande"

DECL_RE = re.compile(
    r"^\s*(?:@\w+(?:\([^)]*\))?\s*)*"
    r"(?:public\s+|internal\s+|private\s+|open\s+|abstract\s+|sealed\s+|data\s+|value\s+|enum\s+|annotation\s+|inline\s+|suspend\s+|operator\s+|infix\s+|expect\s+|actual\s+)*"
    r"(?:class|interface|object|fun|val|var|typealias)\s+"
    r"(?:<[^>]*>\s*)?"
    r"([A-Za-z_][A-Za-z0-9_]*)",
    re.M,
)

USE_RE = re.compile(r"(?<![\w.])([A-Z][A-Za-z0-9_]{1,})")


def kotlin_files(base):
    out = []
    if not os.path.isdir(base):
        return out
    for dirpath, _dirs, names in os.walk(base):
        for n in sorted(names):
            if n.endswith(".kt"):
                out.append(os.path.join(dirpath, n))
    return out


def strip_code(src):
    src = re.sub(r"/\*.*?\*/", " ", src, flags=re.S)
    src = re.sub(r"//[^\n]*", " ", src)
    src = re.sub(r'"""(?:.|\n)*?"""', '""', src)
    src = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', src)
    src = re.sub(r"'(?:\\.|[^'\\\n])'", "'x'", src)
    return src


def main():
    all_files = []
    for _label, base in SOURCESETS:
        all_files += kotlin_files(base)

    # package -> declared symbols ; symbol -> set(packages)
    declared = defaultdict(set)
    sym_packages = defaultdict(set)
    file_local = {}

    for path in all_files:
        raw = open(path, encoding="utf-8", errors="replace").read()
        pm = re.search(r"^package\s+([\w.]+)", raw, re.M)
        pkg = pm.group(1) if pm else ""
        syms = set(DECL_RE.findall(raw))
        file_local[path] = syms
        for s in syms:
            declared[pkg].add(s)
            sym_packages[s].add(pkg)

    failed = False

    for label, base in SOURCESETS:
        if not os.path.isdir(base):
            continue
        imp_problems, use_problems = [], []

        for path in kotlin_files(base):
            raw = open(path, encoding="utf-8", errors="replace").read()
            code = strip_code(raw)

            imported = set()
            wildcards = []
            for line in raw.splitlines():
                im = re.match(r"^\s*import\s+([\w.]+(?:\.\*)?)(?:\s+as\s+(\w+))?\s*$", line)
                if im:
                    if im.group(1).endswith(".*"):
                        wildcards.append(im.group(1)[:-2])
                    else:
                        imported.add(im.group(2) or im.group(1).split(".")[-1])

            pm = re.search(r"^package\s+([\w.]+)", raw, re.M)
            own_pkg = pm.group(1) if pm else ""

            # --- Pass 1: import resolution ---
            for line in raw.splitlines():
                im = re.match(r"^\s*import\s+(" + re.escape(PROJECT_PREFIX) + r"\.[\w.]+)", line)
                if not im:
                    continue
                fq = im.group(1)
                parts = fq.split(".")
                ok = False
                for i in range(len(parts) - 1, 2, -1):
                    pkg = ".".join(parts[:i])
                    if parts[i] in declared.get(pkg, ()) or fq in declared.get(pkg, ()):
                        ok = True
                        break
                if not ok:
                    imp_problems.append((path, fq))

            # --- Pass 2: project symbol used without import ---
            local = set(file_local[path])
            local |= set(re.findall(r"\b(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)", raw))
            local |= set(re.findall(r"\bclass\s+([A-Za-z_][A-Za-z0-9_]*)", raw))
            local |= set(re.findall(r"\bobject\s+([A-Za-z_][A-Za-z0-9_]*)", raw))
            local |= set(re.findall(r"\binterface\s+([A-Za-z_][A-Za-z0-9_]*)", raw))
            local |= set(re.findall(r"\benum class\s+([A-Za-z_][A-Za-z0-9_]*)", raw))
            local |= set(re.findall(r"\bfun\s+[A-Za-z0-9_]*\.?([A-Za-z_][A-Za-z0-9_]*)\s*\(", raw))

            # Symbols reachable without an import: own package, wildcard imports.
            reachable = set(declared.get(own_pkg, ()))
            for wc in wildcards:
                reachable |= set(declared.get(wc, ()))

            for name in sorted(set(USE_RE.findall(code))):
                if name in imported or name in local or name in reachable:
                    continue
                # Only flag it if the project declares that symbol somewhere.
                if name not in sym_packages:
                    continue
                use_problems.append((path, name))

        if imp_problems:
            failed = True
            print(f"\n=== {label}: unresolved IMPORTS ===")
            for path, sym in imp_problems:
                print(f"  {os.path.relpath(path, ROOT)}\n      import {sym}")
        if use_problems:
            failed = True
            print(f"\n=== {label}: project symbol used WITHOUT import ===")
            for path, sym in use_problems:
                print(f"  {os.path.relpath(path, ROOT)}\n      {sym}")
        if not imp_problems and not use_problems:
            print(f"=== {label}: clean ===")

    print("\nRESULT:", "PROBLEMS FOUND" if failed else "clean")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
