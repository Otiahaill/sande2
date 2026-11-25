"""Fix the D1 `.all()` misuse across the backend.

`D1PreparedStatement.all()` resolves to a `D1Result`, whose rows live in
`.results`. A lot of the backend code treats the result as if it were the row
array itself (`rows.length`, `rows[0]`, `rows.map(...)`), which is a runtime
bug, not just a type error.

This rewrites `await <expr>.all()` into `await rows(<expr>.all())` using the
`rows()` helper, which unwraps `.results` and keeps the row array.
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BACKEND = os.path.join(ROOT, "backend", "src")

HELPER = """/**
 * Unwraps the rows from a D1 result.
 *
 * `D1PreparedStatement.all()` resolves to a `D1Result`; the rows themselves
 * are in `.results`. Code that wants the rows uses this helper so the
 * distinction is explicit.
 */
export async function rows<T = Record<string, unknown>>(
  result: Promise<D1Result<T>>
): Promise<T[]> {
  return (await result).results;
}
"""


def add_helper(path):
    """Adds the `rows` helper to a file that needs it."""
    src = io.open(path, encoding="utf-8").read()
    if "export async function rows<" in src:
        return
    # Insert after the last import line.
    lines = src.split("\n")
    last_import = max(
        (i for i, l in enumerate(lines) if l.startswith("import ")), default=0
    )
    lines.insert(last_import + 1, "\n" + HELPER)
    io.open(path, "w", encoding="utf-8").write("\n".join(lines))


def rewrite(text):
    """Wraps every `await <expr>.all()` in the `rows()` helper."""
    out = []
    # Split on ';' so each statement is handled on its own.
    for chunk in text.split(";"):
        idx = chunk.find(".all()")
        if idx == -1:
            out.append(chunk)
            continue
        await_idx = chunk.rfind("await", 0, idx)
        if await_idx == -1:
            out.append(chunk)
            continue
        expr = chunk[await_idx + len("await"):idx]
        replacement = "await rows(%s.all())" % expr
        chunk = chunk[:await_idx] + replacement + chunk[idx + len(".all()"):]
        out.append(chunk)
    return ";".join(out)


def main():
    changed = 0
    total = 0
    for dirpath, _dirs, names in os.walk(BACKEND):
        if "node_modules" in dirpath:
            continue
        for name in sorted(names):
            if not name.endswith(".ts") or name.endswith(".test.ts"):
                continue
            path = os.path.join(dirpath, name)
            src = io.open(path, encoding="utf-8").read()
            if ".all()" not in src:
                continue
            new = rewrite(src)
            if new != src:
                count = src.count(".all()")
                total += count
                io.open(path, "w", encoding="utf-8").write(new)
                add_helper(path)
                changed += 1
                print("rewrote %d .all() call(s) in %s" % (count, name))
    print("\n%d file(s), %d call(s)" % (changed, total))
    return 0


if __name__ == "__main__":
    sys.exit(main())
