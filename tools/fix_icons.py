#!/usr/bin/env python3
"""Migrate the Material 1 icon API to the Material 3 one.

Compose 1.6.x removed `Icons.Default` entirely — only `Icons.Filled`,
`Icons.Outlined`, `Icons.Rounded`, `Icons.Sharp`, `Icons.TwoTone` and
`Icons.AutoMirrored.*` exist now. The icons are also `ImageVector` *properties*,
not composable functions, so `Icons.Filled.CloudDone(...)` does not compile —
they have to be rendered with `Icon(Icons.Filled.CloudDone, ...)`.

This script:
  1. rewrites `Icons.Default.X` -> `Icons.Filled.X`
  2. adds the matching `androidx.compose.material.icons.filled.X` imports
  3. rewrites `Icons.Filled.X(...)` -> `Icon(Icons.Filled.X, ...)`
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(ROOT, "app", "src", "main", "java")

ICON_RE = re.compile(r"Icons\.(?:Default|Filled|Outlined|Rounded|Sharp|TwoTone)\.([A-Z][A-Za-z0-9_]*)")
# Icons.Filled.X(  ->  Icon(Icons.Filled.X, ...)
INVOKE_RE = re.compile(r"Icons\.(Filled|Outlined|Rounded|Sharp|TwoTone)\.([A-Z][A-Za-z0-9_]*)\s*\(")

changed = []
for dirpath, _dirs, names in os.walk(APP):
    for n in sorted(names):
        if not n.endswith(".kt"):
            continue
        path = os.path.join(dirpath, n)
        src = open(path, encoding="utf-8").read()
        if "Icons." not in src:
            continue
        original = src

        # 1. Icons.Default -> Icons.Filled
        src = re.sub(r"Icons\.Default\.", "Icons.Filled.", src)

        # 2. Icon properties invoked as functions -> Icon(...)
        src = INVOKE_RE.sub(
            lambda m: f"Icon(Icons.{m.group(1)}.{m.group(2)}, contentDescription = null)",
            src,
        )

        if src == original:
            continue

        # 3. Collect the icons used and add their imports.
        used = set()
        for m in ICON_RE.finditer(src):
            used.add(m.group(1))

        needed = []
        for icon in sorted(used):
            fq = f"androidx.compose.material.icons.filled.{icon}"
            if f"import {fq}" in src:
                continue
            needed.append(f"import {fq}\n")

        if needed:
            lines = src.splitlines(keepends=True)
            import_idx = [i for i, l in enumerate(lines) if l.startswith("import ")]
            if import_idx:
                insert_at = import_idx[-1] + 1
            else:
                pkg_idx = next(i for i, l in enumerate(lines) if l.startswith("package "))
                insert_at = pkg_idx + 2
            lines[insert_at:insert_at] = needed
            src = "".join(lines)

        open(path, "w", encoding="utf-8").write(src)
        changed.append(os.path.relpath(path, ROOT))

for c in changed:
    print(f"fixed: {c}")
print(f"\n{len(changed)} file(s) changed")
