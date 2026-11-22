#!/usr/bin/env python3
"""Replace the Material-1 `ProgressIndicator` with the Material 3 equivalents.

Material 3 has no `ProgressIndicator` composable — it was split into
`CircularProgressIndicator` and `LinearProgressIndicator`. The bare
`ProgressIndicator()` calls in this codebase are all circular spinners, and the
ones with a `size(...)` modifier are inline button spinners, so every usage maps
to `CircularProgressIndicator`.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(ROOT, "app", "src", "main", "java")

changed = []

for dirpath, _dirs, names in os.walk(APP):
    for n in sorted(names):
        if not n.endswith(".kt"):
            continue
        path = os.path.join(dirpath, n)
        src = open(path, encoding="utf-8").read()
        if "ProgressIndicator" not in src:
            continue
        original = src

        # Drop the (non-existent) import.
        src = re.sub(r"^\s*import androidx\.compose\.material3\.ProgressIndicator\s*\n", "", src, flags=re.M)

        # Ensure CircularProgressIndicator is imported when it is now used.
        if "CircularProgressIndicator" in src and "import androidx.compose.material3.CircularProgressIndicator" not in src:
            # Insert it next to the other material3 imports.
            m = re.search(r"^import androidx\.compose\.material3\.[A-Za-z]+\s*$", src, re.M)
            if m:
                src = src[:m.start()] + "import androidx.compose.material3.CircularProgressIndicator\n" + src[m.start():]
            else:
                m = re.search(r"^import androidx\.compose\.runtime\.[A-Za-z]+\s*$", src, re.M)
                if m:
                    src = src[:m.start()] + "import androidx.compose.material3.CircularProgressIndicator\n" + src[m.start():]

        # Replace the usage, but do not touch Circular/LinearProgressIndicator.
        src = re.sub(r"(?<!Circular)(?<!Linear)ProgressIndicator\b", "CircularProgressIndicator", src)

        if src != original:
            open(path, "w", encoding="utf-8").write(src)
            changed.append(os.path.relpath(path, ROOT))

for c in changed:
    print(f"fixed: {c}")
print(f"\n{len(changed)} file(s) changed")
