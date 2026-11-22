#!/usr/bin/env python3
"""Fix the remaining Material-1 style usages in the UI layer.

1. `TextAlign` lives in `androidx.compose.ui.text.style`, not
   `androidx.compose.ui.text`.
2. `observeAsState` for LiveData lives in `androidx.compose.runtime.livedata`
   and needs the `runtime-livedata` artifact. The view models already expose
   StateFlow, so the screens are migrated to `collectAsState()` instead, which
   removes the dependency entirely.
3. Bare icon composables (`Download(...)`, `Security(...)`) are ImageVector
   properties in Compose 1.6 and must be rendered with `Icon(...)`.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(ROOT, "app", "src", "main", "java")

# Icons that were used as if they were composables.
BARE_ICONS = [
    "Download", "Upload", "Security", "Check", "Warning", "Info", "Error",
    "ContentCopy", "Refresh", "Add", "Close", "Search", "Settings", "Sync",
    "Cloud", "Backup", "Restore", "Person", "People", "History", "Inventory",
    "ShoppingCart", "Dashboard", "Logout", "AccountBalance", "MoreVert",
]

BARE_RE = re.compile(
    r"(?<![\w.])(" + "|".join(BARE_ICONS) + r")\s*\(\s*\n(\s*)contentDescription\s*=",
)

changed = []
for dirpath, _dirs, names in os.walk(APP):
    for n in sorted(names):
        if not n.endswith(".kt"):
            continue
        path = os.path.join(dirpath, n)
        src = open(path, encoding="utf-8").read()
        original = src

        # 1. TextAlign package
        src = src.replace(
            "import androidx.compose.ui.text.TextAlign",
            "import androidx.compose.ui.text.style.TextAlign",
        )

        # 2. observeAsState -> collectAsState
        src = re.sub(r"\.observeAsState\(([^)]*)\)", r".collectAsState()", src)
        src = src.replace(
            "import androidx.lifecycle.compose.observeAsState",
            "import androidx.compose.runtime.collectAsState",
        )

        # 3. Bare icon composables -> Icon(...)
        src = BARE_RE.sub(
            lambda m: f"Icon(\n{m.group(2)}imageVector = Icons.Filled.{m.group(1)},\n{m.group(2)}contentDescription =",
            src,
        )

        if src == original:
            continue

        # Ensure the needed imports are present.
        needed = []
        if "Icon(" in src and "import androidx.compose.material3.Icon" not in src:
            needed.append("import androidx.compose.material3.Icon\n")
        if "Icons.Filled." in src and "import androidx.compose.material.icons.Icons" not in src:
            needed.append("import androidx.compose.material.icons.Icons\n")

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
