#!/usr/bin/env python3
"""Add the imports that the GitHub Actions build reported as missing.

The compiler is the only authority on which symbol is missing in which file,
so this script is driven by the error list collected from a CI run
(`tools/collect_ci_errors.py` output) rather than by guessing from the source.

Usage:
    python3 tools/fix_ci_imports.py <errors-file>

Only symbols that the compiler reported as unresolved in a specific file are
touched, and only when the file does not already import them.
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# symbol -> fully qualified import
IMPORTS = {
    # Compose foundation layout
    "fillMaxWidth": "androidx.compose.foundation.layout.fillMaxWidth",
    "fillMaxHeight": "androidx.compose.foundation.layout.fillMaxHeight",
    "fillMaxSize": "androidx.compose.foundation.layout.fillMaxSize",
    "width": "androidx.compose.foundation.layout.width",
    "height": "androidx.compose.foundation.layout.height",
    "size": "androidx.compose.foundation.layout.size",
    "wrapContentSize": "androidx.compose.foundation.layout.wrapContentSize",
    "weight": "androidx.compose.foundation.layout.weight",
    "padding": "androidx.compose.foundation.layout.padding",
    "Spacer": "androidx.compose.foundation.layout.Spacer",
    "Row": "androidx.compose.foundation.layout.Row",
    "Column": "androidx.compose.foundation.layout.Column",
    "Box": "androidx.compose.foundation.layout.Box",
    "Arrangement": "androidx.compose.foundation.layout.Arrangement",
    "Alignment": "androidx.compose.ui.Alignment",
    "BorderStroke": "androidx.compose.foundation.BorderStroke",

    # Compose text / keyboard
    "KeyboardOptions": "androidx.compose.foundation.text.KeyboardOptions",
    "KeyboardType": "androidx.compose.ui.text.input.KeyboardType",
    "VisualTransformation": "androidx.compose.ui.text.input.VisualTransformation",
    "ImeAction": "androidx.compose.ui.text.input.ImeAction",
    "LocalDensity": "androidx.compose.ui.platform.LocalDensity",

    # Compose units
    "sp": "androidx.compose.ui.unit.sp",
    "dp": "androidx.compose.ui.unit.dp",

    # Compose material 3
    "Icon": "androidx.compose.material3.Icon",
    "IconButton": "androidx.compose.material3.IconButton",
    "DataRow": "androidx.compose.material3.DataRow",
    "ExposedDropdownMenu": "androidx.compose.material3.ExposedDropdownMenu",
    "ExposedDropdownMenuBox": "androidx.compose.material3.ExposedDropdownMenuBox",
    "OutlinedTextField": "androidx.compose.material3.OutlinedTextField",
    "TextButton": "androidx.compose.material3.TextButton",
    "FilledTonalButton": "androidx.compose.material3.FilledTonalButton",
    "LinearProgressIndicator": "androidx.compose.material3.LinearProgressIndicator",
    "CircularProgressIndicator": "androidx.compose.material3.CircularProgressIndicator",
    "HorizontalDivider": "androidx.compose.material3.HorizontalDivider",
    "VerticalDivider": "androidx.compose.material3.VerticalDivider",
    "Surface": "androidx.compose.material3.Surface",
    "SnackbarHost": "androidx.compose.material3.SnackbarHost",
    "SnackbarHostState": "androidx.compose.material3.SnackbarHostState",
    "TopAppBarDefaults": "androidx.compose.material3.TopAppBarDefaults",

    # Material icons (extended artifact)
    "CheckCircle": "androidx.compose.material.icons.filled.CheckCircle",
    "Mail": "androidx.compose.material.icons.filled.Mail",
    "Phishing": "androidx.compose.material.icons.filled.Phishing",
    "Print": "androidx.compose.material.icons.filled.Print",
    "Share": "androidx.compose.material.icons.filled.Share",
    "Delete": "androidx.compose.material.icons.filled.Delete",
    "Warning": "androidx.compose.material.icons.filled.Warning",
    "Info": "androidx.compose.material.icons.filled.Info",
    "Error": "androidx.compose.material.icons.filled.Error",
    "Add": "androidx.compose.material.icons.filled.Add",
    "Close": "androidx.compose.material.icons.filled.Close",
    "Menu": "androidx.compose.material.icons.filled.Menu",
    "Search": "androidx.compose.material.icons.filled.Search",
    "Refresh": "androidx.compose.material.icons.filled.Refresh",
    "Settings": "androidx.compose.material.icons.filled.Settings",
    "MoreVert": "androidx.compose.material.icons.filled.MoreVert",
    "Sync": "androidx.compose.material.icons.filled.Sync",
    "Cloud": "androidx.compose.material.icons.filled.Cloud",
    "Backup": "androidx.compose.material.icons.filled.Backup",
    "Restore": "androidx.compose.material.icons.filled.Restore",
    "Person": "androidx.compose.material.icons.filled.Person",
    "People": "androidx.compose.material.icons.filled.People",
    "History": "androidx.compose.material.icons.filled.History",
    "Inventory": "androidx.compose.material.icons.filled.Inventory",
    "ShoppingCart": "androidx.compose.material.icons.filled.ShoppingCart",
    "Dashboard": "androidx.compose.material.icons.filled.Dashboard",
    "Logout": "androidx.compose.material.icons.filled.Logout",
    "AccountBalance": "androidx.compose.material.icons.filled.AccountBalance",
    "Security": "androidx.compose.material.icons.filled.Security",
    "Download": "androidx.compose.material.icons.filled.Download",
    "Upload": "androidx.compose.material.icons.filled.Upload",
    "ContentCopy": "androidx.compose.material.icons.filled.ContentCopy",

    # KotlinX coroutines / flow
    "asStateFlow": "kotlinx.coroutines.flow.asStateFlow",
    "distinctUntilChanged": "kotlinx.coroutines.flow.distinctUntilChanged",
    "first": "kotlinx.coroutines.flow.first",
    "map": "kotlinx.coroutines.flow.map",
    "launch": "kotlinx.coroutines.launch",
    "runBlocking": "kotlinx.coroutines.runBlocking",
    "withContext": "kotlinx.coroutines.withContext",
    "delay": "kotlinx.coroutines.delay",

    # Room
    "withTransaction": "androidx.room.withTransaction",

    # Ktor
    "body": "io.ktor.client.request.body",
}


def parse_errors(path):
    """{relative kotlin path: set(symbol)} for unresolved references."""
    out = {}
    with io.open(path, encoding="utf-8") as fh:
        for line in fh:
            m = re.match(
                r"^(\S+\.kt):\d+:\d+ Unresolved reference: ([A-Za-z_][A-Za-z0-9_]*)",
                line.strip(),
            )
            if not m:
                continue
            rel, sym = m.group(1), m.group(2)
            out.setdefault(rel, set()).add(sym)
    return out


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 1
    errors = parse_errors(sys.argv[1])

    total_files = 0
    total_imports = 0
    unknown = {}

    for rel, symbols in sorted(errors.items()):
        # The compiler prints paths relative to the source root, e.g.
        # "ui/screens/ReportsScreen.kt" for the app module.
        candidates = [
            os.path.join(ROOT, rel),
            os.path.join(
                ROOT, "app", "src", "main", "java", "com", "example", "sande", rel
            ),
        ]
        path = next((c for c in candidates if os.path.exists(c)), None)
        if path is None:
            print("SKIP (missing file): %s" % rel)
            continue

        raw = io.open(path, encoding="utf-8").read()
        imported = set()
        wildcards = set()
        for m in re.finditer(r"^\s*import\s+([\w.]+(?:\.\*)?)", raw, re.M):
            fq = m.group(1)
            if fq.endswith(".*"):
                wildcards.add(fq[:-2])
            else:
                imported.add(fq.split(".")[-1])

        needed = []
        for sym in sorted(symbols):
            fq = IMPORTS.get(sym)
            if fq is None:
                unknown.setdefault(sym, []).append(rel)
                continue
            if sym in imported:
                continue
            if fq.rsplit(".", 1)[0] in wildcards:
                continue
            needed.append(fq)

        if not needed:
            continue

        lines = raw.splitlines(keepends=True)
        import_idx = [i for i, l in enumerate(lines) if l.startswith("import ")]
        if import_idx:
            insert_at = import_idx[-1] + 1
        else:
            pkg_idx = next(i for i, l in enumerate(lines) if l.startswith("package "))
            insert_at = pkg_idx + 2

        lines[insert_at:insert_at] = sorted("import %s\n" % fq for fq in needed)
        io.open(path, "w", encoding="utf-8").write("".join(lines))

        total_files += 1
        total_imports += len(needed)
        print("FIXED %s: %s" % (rel, ", ".join(sorted(needed))))

    if unknown:
        print("\nSymbols with no known import (need a manual look):")
        for sym, files in sorted(unknown.items()):
            print("  %-28s %d file(s): %s" % (sym, len(files), files[0]))

    print("\n%d file(s), %d import(s) added" % (total_files, total_imports))
    return 0


if __name__ == "__main__":
    sys.exit(main())
