#!/usr/bin/env python3
"""Collect the unresolved symbols per file from a GitHub Actions log.

Usage: python3 tools/collect_ci_errors.py <run-id>
Reads the log via `gh run view --log-failed` and prints, per file, the
distinct unresolved references with their line numbers.
"""
import re
import subprocess
import sys

REPO = "Otiahaill/sande2"
ROOT = "/storage/emulated/0/Android/data/com.androidharness.app/files/workspace/sande-2-online"

ERR = re.compile(
    r"e: file:///\S*?/app/src/main/java/com/example/sande/(\S+\.kt):(\d+):(\d+) (.+)"
)


def main():
    run_id = sys.argv[1]
    out = subprocess.run(
        ["gh", "run", "view", run_id, "--repo", REPO, "--log-failed"],
        capture_output=True, text=True).stdout

    by_file = {}
    for m in ERR.finditer(out):
        path, line, _col, msg = m.groups()
        by_file.setdefault(path, []).append((int(line), msg))

    total = 0
    for path in sorted(by_file, key=lambda p: -len(by_file[p])):
        entries = sorted(set(by_file[path]))
        total += len(entries)
        print(f"\n### {path}  ({len(entries)})")
        for line, msg in entries:
            print(f"  {line}: {msg}")
    print(f"\nTOTAL distinct errors: {total}")


if __name__ == "__main__":
    main()
