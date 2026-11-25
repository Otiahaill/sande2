"""Checks that the launcher activity is declared correctly.

An activity only appears in the app drawer - and only gets an "Open" button
in Settings - when its intent filter contains the exact strings
`android.intent.action.MAIN` and `android.intent.category.LAUNCHER`.

A near miss such as `android.intent.LAUNCHER` (without `category.`) compiles
and installs, but Android silently ignores it: the package is installed and
unopenable. This check catches that.
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MANIFEST = os.path.join(ROOT, "app", "src", "main", "AndroidManifest.xml")

MAIN_ACTION = "android.intent.action.MAIN"
LAUNCHER_CATEGORY = "android.intent.category.LAUNCHER"


def main():
    src = io.open(MANIFEST, encoding="utf-8").read()

    problems = []

    # Every category/action value that looks like an intent one.
    for value in re.findall(r'android:name="(android\.intent[^"]*)"', src):
        if value.startswith("android.intent."):
            known = {
                MAIN_ACTION,
                LAUNCHER_CATEGORY,
                "android.intent.category.DEFAULT",
                "android.intent.category.BROWSABLE",
            }
            if value not in known:
                problems.append(
                    "unknown intent name %r - a typo here makes the app "
                    "installable but not launchable" % value
                )

    # The launcher activity itself.
    if 'android.intent.category.LAUNCHER' not in src:
        problems.append(
            "no %s category is declared, so the app has no launcher activity"
            % LAUNCHER_CATEGORY
        )
    if MAIN_ACTION not in src:
        problems.append("no %s action is declared" % MAIN_ACTION)

    # The activity that carries the filter must be exported, or the launcher
    # cannot start it on Android 12 and newer.
    filter_block = re.search(
        r"<activity\b(.*?)</activity>", src, re.S
    )
    if filter_block and MAIN_ACTION in filter_block.group(1):
        block = filter_block.group(1)
        if LAUNCHER_CATEGORY not in block:
            problems.append(
                "the MAIN activity does not carry the %s category"
                % LAUNCHER_CATEGORY
            )
        if 'android:exported="true"' not in block:
            problems.append(
                "the launcher activity is not exported, so the launcher "
                "cannot start it"
            )

    for p in problems:
        print("[%s] %s" % (os.path.basename(__file__), p))
    if problems:
        print("\nRESULT: %d problem(s)" % len(problems))
        return 1
    print("RESULT: clean")
    return 0


if __name__ == "__main__":
    sys.exit(main())
