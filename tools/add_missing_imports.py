#!/usr/bin/env python3
"""Add missing Compose/KotlinX imports to Kotlin files.

Uses a curated map from a simple symbol name to its fully-qualified import.
Only adds an import when the symbol is actually referenced in the file and the
file does not already import it (directly or via a wildcard of the same package).

Run:  python3 tools/add_missing_imports.py [--check]
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(ROOT, "app", "src")

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
    "wrapContentWidth": "androidx.compose.foundation.layout.wrapContentWidth",
    "wrapContentHeight": "androidx.compose.foundation.layout.wrapContentHeight",
    "padding": "androidx.compose.foundation.layout.padding",
    "absolutePadding": "androidx.compose.foundation.layout.absolutePadding",
    "aspectRatio": "androidx.compose.foundation.layout.aspectRatio",
    "defaultMinSize": "androidx.compose.foundation.layout.defaultMinSize",
    "offset": "androidx.compose.foundation.layout.offset",
    "requiredHeight": "androidx.compose.foundation.layout.requiredHeight",
    "requiredWidth": "androidx.compose.foundation.layout.requiredWidth",
    "matchParentSize": "androidx.compose.foundation.layout.matchParentSize",
    "Row": "androidx.compose.foundation.layout.Row",
    "Column": "androidx.compose.foundation.layout.Column",
    "Box": "androidx.compose.foundation.layout.Box",
    "Spacer": "androidx.compose.foundation.layout.Spacer",
    "Arrangement": "androidx.compose.foundation.layout.Arrangement",
    "Alignment": "androidx.compose.ui.Alignment",

    # Compose runtime
    "collectAsState": "androidx.compose.runtime.collectAsState",
    "collectAsStateWithLifecycle": "androidx.lifecycle.compose.collectAsStateWithLifecycle",
    "observeAsState": "androidx.lifecycle.compose.observeAsState",
    "LaunchedEffect": "androidx.compose.runtime.LaunchedEffect",
    "rememberCoroutineScope": "androidx.compose.runtime.rememberCoroutineScope",
    "getValue": "androidx.compose.runtime.getValue",
    "setValue": "androidx.compose.runtime.setValue",
    "mutableStateOf": "androidx.compose.runtime.mutableStateOf",
    "remember": "androidx.compose.runtime.remember",
    "derivedStateOf": "androidx.compose.runtime.derivedStateOf",
    "produceState": "androidx.compose.runtime.produceState",
    "snapshotFlow": "androidx.compose.runtime.snapshotFlow",

    # Compose UI
    "TextAlign": "androidx.compose.ui.text.style.TextAlign",
    "FontWeight": "androidx.compose.ui.text.font.FontWeight",
    "FontFamily": "androidx.compose.ui.text.font.FontFamily",
    "TextStyle": "androidx.compose.ui.text.TextStyle",
    "KeyboardOptions": "androidx.compose.foundation.text.KeyboardOptions",
    "KeyboardType": "androidx.compose.ui.text.input.KeyboardType",
    "VisualTransformation": "androidx.compose.ui.text.input.VisualTransformation",
    "ImeAction": "androidx.compose.ui.text.input.ImeAction",
    "LocalDensity": "androidx.compose.ui.platform.LocalDensity",
    "LocalContext": "androidx.compose.ui.platform.LocalContext",
    "LocalConfiguration": "androidx.compose.ui.platform.LocalConfiguration",
    "painterResource": "androidx.compose.ui.res.painterResource",
    "stringResource": "androidx.compose.ui.res.stringResource",
    "Color": "androidx.compose.ui.graphics.Color",
    "Image": "androidx.compose.foundation.Image",
    "Divider": "androidx.compose.material3.Divider",
    "HorizontalDivider": "androidx.compose.material3.HorizontalDivider",
    "VerticalDivider": "androidx.compose.material3.VerticalDivider",

    # Compose Material 3
    "Menu": "androidx.compose.material3.Menu",
    "DropdownMenu": "androidx.compose.material3.DropdownMenu",
    "DropdownMenuItem": "androidx.compose.material3.DropdownMenuItem",
    "DataRow": "androidx.compose.material3.DataRow",
    "AlertDialog": "androidx.compose.material3.AlertDialog",
    "Card": "androidx.compose.material3.Card",
    "CardDefaults": "androidx.compose.material3.CardDefaults",
    "Checkbox": "androidx.compose.material3.Checkbox",
    "CircularProgressIndicator": "androidx.compose.material3.CircularProgressIndicator",
    "LinearProgressIndicator": "androidx.compose.material3.LinearProgressIndicator",
    "Icon": "androidx.compose.material3.Icon",
    "IconButton": "androidx.compose.material3.IconButton",
    "LinearProgressIndicator": "androidx.compose.material3.LinearProgressIndicator",
    "OutlinedButton": "androidx.compose.material3.OutlinedButton",
    "OutlinedTextField": "androidx.compose.material3.OutlinedTextField",
    "Scaffold": "androidx.compose.material3.Scaffold",
    "Snackbar": "androidx.compose.material3.Snackbar",
    "Surface": "androidx.compose.material3.Surface",
    "Switch": "androidx.compose.material3.Switch",
    "Tab": "androidx.compose.material3.Tab",
    "TabRow": "androidx.compose.material3.TabRow",
    "Text": "androidx.compose.material3.Text",
    "TextButton": "androidx.compose.material3.TextButton",
    "TopAppBar": "androidx.compose.material3.TopAppBar",
    "ExposedDropdownMenuBox": "androidx.compose.material3.ExposedDropdownMenuBox",
    "ExposedDropdownMenuDefaults": "androidx.compose.material3.ExposedDropdownMenuDefaults",
    "ExposedDropdownMenu": "androidx.compose.material3.ExposedDropdownMenu",

    # Icons
    "Icons": "androidx.compose.material.icons.Icons",

    # KotlinX coroutines / flow
    "asStateFlow": "kotlinx.coroutines.flow.asStateFlow",
    "first": "kotlinx.coroutines.flow.first",
    "map": "kotlinx.coroutines.flow.map",
    "filter": "kotlinx.coroutines.flow.filter",
    "collect": "kotlinx.coroutines.flow.collect",
    "launch": "kotlinx.coroutines.launch",
    "delay": "kotlinx.coroutines.delay",
    "withContext": "kotlinx.coroutines.withContext",
    "runBlocking": "kotlinx.coroutines.runBlocking",
    "Dispatchers": "kotlinx.coroutines.Dispatchers",

    # Room
    "withTransaction": "androidx.room.withTransaction",

    # Work
    "setTag": "androidx.work.setTag",

    # Ktor
    "bodyAsText": "io.ktor.client.statement.bodyAsText",
}

# Icons that live in the material-icons-extended artifact.
# Only names that cannot collide with a project symbol or a Room annotation.
ICON_IMPORTS = {
    "Check": "androidx.compose.material.icons.filled.Check",
    "Close": "androidx.compose.material.icons.filled.Close",
    "Add": "androidx.compose.material.icons.filled.Add",
    "Warning": "androidx.compose.material.icons.filled.Warning",
    "Info": "androidx.compose.material.icons.filled.Info",
    "Error": "androidx.compose.material.icons.filled.Error",
    "Security": "androidx.compose.material.icons.filled.Security",
    "Download": "androidx.compose.material.icons.filled.Download",
    "Upload": "androidx.compose.material.icons.filled.Upload",
    "ContentCopy": "androidx.compose.material.icons.filled.ContentCopy",
    "Refresh": "androidx.compose.material.icons.filled.Refresh",
    "Settings": "androidx.compose.material.icons.filled.Settings",
    "Search": "androidx.compose.material.icons.filled.Search",
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
}

# Symbols whose names are too generic to be auto-imported: they collide with
# local variables, Room annotations, project entities or member functions.
UNSAFE = {
    "width", "height", "size", "padding", "map", "filter", "first", "Menu",
    "DataRow", "Divider", "Delete", "Receipt", "Offset", "Text", "Icon",
    "Box", "Row", "Column", "Spacer", "Alignment", "Card", "Surface", "Tab",
    "KeyboardType", "ImeAction", "Color", "FontFamily", "FontWeight", "TextStyle",
    "LocalDensity", "LocalConfiguration", "Image", "Switch", "TabRow",
}

ALL = dict(IMPORTS)
ALL.update(ICON_IMPORTS)
for _u in UNSAFE:
    ALL.pop(_u, None)


def strip_code(src):
    src = re.sub(r"/\*.*?\*/", " ", src, flags=re.S)
    src = re.sub(r"//[^\n]*", " ", src)
    src = re.sub(r'"""(?:.|\n)*?"""', '""', src)
    src = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', src)
    return src


def local_declarations(raw):
    """Names declared in this file (functions, vals, classes, parameters)."""
    names = set()
    for pat in (
        r"\bfun\s+[A-Za-z0-9_]*\.?([A-Za-z_][A-Za-z0-9_]*)\s*\(",
        r"\b(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)",
        r"\b(?:class|object|interface|enum class)\s+([A-Za-z_][A-Za-z0-9_]*)",
        r"\bdata class\s+([A-Za-z_][A-Za-z0-9_]*)",
    ):
        names |= set(re.findall(pat, raw))
    # Function parameters
    for params in re.findall(r"\(([^)]*)\)", raw):
        for p in params.split(","):
            m = re.match(r"\s*(?:@\w+\s+)*(?:val|var\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*:", p)
            if m:
                names.add(m.group(1))
    return names


def process(path, check_only):
    raw = open(path, encoding="utf-8").read()
    code = strip_code(raw)

    imported = set()
    wildcards = set()
    for m in re.finditer(r"^\s*import\s+([\w.]+(?:\.\*)?)", raw, re.M):
        fq = m.group(1)
        if fq.endswith(".*"):
            wildcards.add(fq[:-2])
        else:
            imported.add(fq.split(".")[-1])

    local = local_declarations(raw)

    needed = []
    for sym, fq in ALL.items():
        if sym in imported or sym in local:
            continue
        pkg = fq.rsplit(".", 1)[0]
        if pkg in wildcards:
            continue
        # The symbol must be used as a standalone identifier.
        if not re.search(r"(?<![\w.])" + re.escape(sym) + r"\b", code):
            continue
        needed.append((sym, fq))

    if not needed:
        return None

    if check_only:
        return needed

    # Insert the imports after the last existing import, keeping them sorted.
    lines = raw.splitlines(keepends=True)
    import_idx = [i for i, l in enumerate(lines) if l.startswith("import ")]
    if not import_idx:
        # No imports: insert after the package declaration.
        pkg_idx = next(i for i, l in enumerate(lines) if l.startswith("package "))
        insert_at = pkg_idx + 2
    else:
        insert_at = import_idx[-1] + 1

    new_imports = sorted(f"import {fq}\n" for _sym, fq in needed)
    lines[insert_at:insert_at] = new_imports
    open(path, "w", encoding="utf-8").write("".join(lines))
    return needed


def main():
    check_only = "--check" in sys.argv
    total_files = 0
    total_imports = 0
    for label in ("main", "test", "androidTest"):
        base = os.path.join(APP, label)
        if not os.path.isdir(base):
            continue
        for dirpath, _dirs, names in os.walk(base):
            for n in sorted(names):
                if not n.endswith(".kt"):
                    continue
                path = os.path.join(dirpath, n)
                res = process(path, check_only)
                if res:
                    total_files += 1
                    total_imports += len(res)
                    rel = os.path.relpath(path, ROOT)
                    for sym, fq in res:
                        verb = "would add" if check_only else "added"
                        print(f"{verb}: {rel} -> {fq}")
    print(f"\n{total_files} file(s), {total_imports} import(s) "
          f"{'needed' if check_only else 'added'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
