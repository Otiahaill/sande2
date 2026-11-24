"""Fix the D1 `.all()` misuse in backend/src/db/tenant.ts.

`D1PreparedStatement.all()` resolves to a `D1Result`, whose rows live in
`.results`. The repository layer returns those results straight to the routes,
which then treat them as the row array (`rows.length`, `rows[0]`,
`rows.map(...)`) — a runtime bug, not just a type error.

This rewrites `return <expr>.all();` into `return rows(<expr>.all());` using
the `rows()` helper, which unwraps `.results`.

The rewrite is statement scoped: the expression is taken from the `return` to
the matching `.all()`, never across a `;`.
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TARGET = os.path.join(ROOT, "backend", "src", "db", "tenant.ts")

HELPER = '''
/**
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
'''


def main():
    src = io.open(TARGET, encoding="utf-8").read()

    out = []
    count = 0
    # Split on ';' so a statement is never matched across another statement.
    for chunk in src.split(";"):
        idx = chunk.find(".all()")
        if idx != -1 and "return" in chunk:
            ret_idx = chunk.rfind("return", 0, idx)
            expr = chunk[ret_idx + len("return"):idx]
            chunk = (
                chunk[:ret_idx]
                + "return rows(%s.all())" % expr
                + chunk[idx + len(".all()"):]
            )
            count += 1
        out.append(chunk)
    src = ";".join(out)

    if "export async function rows<" not in src:
        lines = src.split("\n")
        last = max(
            (i for i, l in enumerate(lines) if l.startswith("import ")), default=0
        )
        lines.insert(last + 1, HELPER)
        src = "\n".join(lines)

    io.open(TARGET, "w", encoding="utf-8").write(src)
    print("rewrote %d return .all() call(s) in tenant.ts" % count)
    return 0


if __name__ == "__main__":
    sys.exit(main())
