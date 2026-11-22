#!/usr/bin/env python3
"""Rewrite Ktor request calls to the 2.3.8 URL-first form.

In Ktor 2.3.8 there is no `HttpRequestBuilder.url(String)` extension — the
supported forms are:

    client.post<T>("https://host/path") { ... }
    url { takeFrom("https://host/path") }

This script rewrites the first form, which is the simplest.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(ROOT, "app", "src", "main", "java")

# client.get<X> {\n    url("...")\n ... }  ->  client.get<X>("...") { ... }
CALL_RE = re.compile(
    r"(client\.(?:get|post)<[^>]*>\s*)\{\s*\n(\s*)url\(\s*(\"[^\"]*\"(?:\s*\+\s*\"[^\"]*\")*)\s*\)\s*\n",
)

total = 0
for dirpath, _dirs, names in os.walk(APP):
    for n in sorted(names):
        if not n.endswith(".kt"):
            continue
        path = os.path.join(dirpath, n)
        src = open(path, encoding="utf-8").read()
        if "client.get" not in src and "client.post" not in src:
            continue
        new = CALL_RE.sub(lambda m: f"{m.group(1)}({m.group(3)}) {{\n", src)
        if new != src:
            open(path, "w", encoding="utf-8").write(new)
            print(f"rewrote: {os.path.relpath(path, ROOT)}")
            total += 1

print(f"\n{total} file(s) rewritten")
