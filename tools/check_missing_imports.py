#!/usr/bin/env python3
"""Detect well-known library symbols used without an import.

The Room KSP processor reports `MissingType: Element 'X' references a type that
is not present` when a DAO uses a type it cannot resolve — typically a missing
`kotlinx.coroutines.flow.Flow` import. This checks the common suspects across
the whole Android source set.

Run:  python3 tools/check_missing_imports.py
Exit code 0 = clean, 1 = problems found.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(ROOT, "app", "src")
SOURCESETS = [("main", os.path.join(APP, "main")),
              ("test", os.path.join(APP, "test")),
              ("androidTest", os.path.join(APP, "androidTest"))]

# symbol -> the import that provides it
SYMBOL_IMPORTS = {
    "Flow": "kotlinx.coroutines.flow.Flow",
    "StateFlow": "kotlinx.coroutines.flow.StateFlow",
    "SharedFlow": "kotlinx.coroutines.flow.SharedFlow",
    "MutableStateFlow": "kotlinx.coroutines.flow.MutableStateFlow",
    "MutableSharedFlow": "kotlinx.coroutines.flow.MutableSharedFlow",
    "CoroutineScope": "kotlinx.coroutines.CoroutineScope",
    "CoroutineDispatcher": "kotlinx.coroutines.CoroutineDispatcher",
    "Dispatchers": "kotlinx.coroutines.Dispatchers",
    "Job": "kotlinx.coroutines.Job",
    "Deferred": "kotlinx.coroutines.Deferred",
    "launch": "kotlinx.coroutines.launch",
    "withContext": "kotlinx.coroutines.withContext",
    "delay": "kotlinx.coroutines.delay",
    "runBlocking": "kotlinx.coroutines.runBlocking",
    "suspendCoroutine": "kotlinx.coroutines.suspendCoroutine",
    "CompletableDeferred": "kotlinx.coroutines.CompletableDeferred",
    "Json": "kotlinx.serialization.json.Json",
    "Serializer": "kotlinx.serialization.Serializer",
    "Context": "kotlinx.coroutines.CoroutineContext",
}

# Symbols that must be imported, but only count as "used" in a type/expression
# position that is not part of a declaration we own.
USE_RE = re.compile(r"(?<![\w.])([A-Z][A-Za-z0-9_]*)")


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
    problems = []
    for label, base in SOURCESETS:
        if not os.path.isdir(base):
            continue
        for path in kotlin_files(base):
            raw = open(path, encoding="utf-8", errors="replace").read()
            imported = set()
            wildcards = []
            for line in raw.splitlines():
                im = re.match(r"^\s*import\s+([\w.]+)(?:\s+as\s+(\w+))?\s*$", line)
                if im:
                    if im.group(1).endswith(".*"):
                        wildcards.append(im.group(1)[:-2])
                    else:
                        imported.add(im.group(2) or im.group(1).split(".")[-1])

            code = re.sub(r"/\*.*?\*/", " ", raw, flags=re.S)
            code = re.sub(r"//[^\n]*", " ", code)
            code = re.sub(r'"""(?:.|\n)*?"""', '""', code)
            code = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', code)

            for sym, imp in SYMBOL_IMPORTS.items():
                if sym in imported:
                    continue
                # Wildcard imports of the owning package satisfy the requirement.
                pkg = imp.rsplit(".", 1)[0]
                if pkg in wildcards:
                    continue
                # The symbol must actually be referenced (not a member access like
                # `foo.Flow`, and not part of an identifier).
                if not re.search(r"(?<![\w.])" + sym + r"\b", code):
                    continue
                # ...and it must be referenced in a type position:
                #   ": Flow<...>", "<Flow", ", Flow", "(Flow", "-> Flow"
                typed = (r"(?<![\w.])(?::|<|,|\(|\)\s*->|->|=\s*|return\s+)"
                         r"\s*" + sym + r"\b")
                if not re.search(typed, code):
                    continue
                problems.append((path, sym, imp))

    if problems:
        print(f"=== {len(problems)} library symbol(s) used without an import ===")
        seen = set()
        for path, sym, imp in problems:
            key = (path, sym)
            if key in seen:
                continue
            seen.add(key)
            print(f"  {os.path.relpath(path, ROOT)}\n      {sym}  ->  import {imp}")
        print("\nRESULT: PROBLEMS FOUND")
        return 1
    print("=== clean ===\nRESULT: clean")
    return 0


if __name__ == "__main__":
    sys.exit(main())
