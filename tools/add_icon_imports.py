#!/usr/bin/env python3
"""
Adds the exact `androidx.compose.material.icons.filled.X` import for every
`Icons.Default.X` reference in the given Kotlin files.

DEBUGGING.md rule 3: only use icons that exist in the pinned
material-icons-extended version. Each icon needs its own import; the
`Icons` object itself lives in `androidx.compose.material.icons`.
"""
import re
import sys
from pathlib import Path

ICON_PATTERN = re.compile(r"Icons\.Default\.([A-Za-z][A-Za-z0-9]*)")

for arg in sys.argv[1:]:
    path = Path(arg)
    text = path.read_text(encoding="utf-8")

    # 1. Make sure `Icons` itself is imported from the right package.
    if "import androidx.compose.material.icons.Icons" not in text:
        text = text.replace(
            "import androidx.compose.material3.Icons",
            "import androidx.compose.material.icons.Icons",
        )

    used = sorted(set(ICON_PATTERN.findall(text)))

    # 2. Add one import per icon that is used but not yet imported.
    needed = []
    for icon in used:
        import_line = f"import androidx.compose.material.icons.filled.{icon}"
        if import_line not in text:
            needed.append(import_line)

    if needed:
        # Insert after the `Icons` import, keeping the import block tidy.
        anchor = "import androidx.compose.material.icons.Icons"
        block = anchor + "\n" + "\n".join(sorted(needed))
        text = text.replace(anchor, block, 1)

    path.write_text(text, encoding="utf-8")
    print(f"{path.name}: {len(used)} icons, {len(needed)} new imports")
