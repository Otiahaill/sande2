"""Add a guard for the route parameters that Hono types as optional.

`c.req.param('x')` returns `string | undefined`, but a matched route always
has the parameter. The code passed the value straight into a function that
expects a string, which is a type error and would also be a runtime crash if
the route were ever called without the parameter.

This inserts a 400 response for the missing parameter, right after the
`const <name> = c.req.param('<name>');` line.
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BACKEND = os.path.join(ROOT, "backend", "src")

PATTERN = re.compile(
    r"^(\s*)const (\w+) = c\.req\.param\('(\w+)'\);$", re.M
)


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
            out = []
            count = 0
            for line in src.split("\n"):
                out.append(line)
                m = PATTERN.match(line)
                if not m:
                    continue
                indent, var = m.group(1), m.group(2)
                # Don't add the guard twice.
                if "if (!%s)" % var in src:
                    continue
                out.append("%sif (!%s) {" % (indent, var))
                out.append(
                    "%s  return c.json({ success: false, error: { code: '400', "
                    "message: '%s is required' } }, 400);" % (indent, var)
                )
                out.append("%s}" % indent)
                count += 1
            if count:
                io.open(path, "w", encoding="utf-8").write("\n".join(out))
                changed += 1
                total += count
                print("guarded %d route parameter(s) in %s" % (count, name))
    print("\n%d file(s), %d parameter(s)" % (changed, total))
    return 0


if __name__ == "__main__":
    sys.exit(main())
