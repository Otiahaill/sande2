#!/usr/bin/env python3
"""Find `.await()` calls on functions that are already `suspend`.

`kotlinx.coroutines.Deferred.await()` is the only legitimate use of `.await()`.
Calling `.await()` on an ordinary suspend function — for example
`tokenManager.getTokens().await()` — is a compile error: "Unresolved reference:
await".

This script flags `something().await()` where `something` is a known suspend
function in the project, or where the receiver is clearly not a Deferred.

Run:  python3 tools/check_await.py
Exit code 0 = clean, 1 = problems found.
"""
import os
import re
import sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(ROOT, "app", "src")

# Functions that are `suspend` in this project — calling .await() on them is wrong.
SUSPEND_FUNCS = {
    "getTokens", "getCurrentTokens", "getDeviceCode", "getBusinessId",
    "getRecoveryCodes", "markRecoveryCodesConfirmed", "areRecoveryCodesConfirmed",
    "saveTokens", "saveRecoveryCodes", "clearTokens", "updateAccessToken",
    "updateTokensAfterRefresh", "extendOfflineLease", "getOfflineLeaseExpiry",
    "getNegativeStockPolicy", "setNegativeStockPolicy", "getReceiptFooter",
    "setReceiptFooter", "getPrinterAddress", "getPrinterProfile", "setPrinter",
    "hasLegacyData", "getLegacyDataCounts", "adoptLegacyData",
    "checkLegacyData", "extendLease", "getValidAccessToken",
    "getUnsyncedByBusiness", "getByBusinessAndDevice", "countByBusiness",
    "getAllByBusiness", "insert", "insertAll", "update", "delete",
    "registerBusiness", "registerDevice", "acceptInvitation", "googleAuth",
    "refreshToken", "logout", "listMemberships", "listDevices", "approveDevice",
    "createBackup", "healthCheck", "getMyBusiness", "updateProfile",
    "inviteStaff", "pushSync", "pullSync", "getSyncStatus",
}

AWAIT_RE = re.compile(r"\b([a-zA-Z_][A-Za-z0-9_]*)\s*\(\s*\)\s*\.\s*await\s*\(")


def main():
    problems = []
    for label in ("main", "test", "androidTest"):
        base = os.path.join(APP, label)
        if not os.path.isdir(base):
            continue
        for dirpath, _dirs, names in os.walk(base):
            for n in sorted(names):
                if not n.endswith(".kt"):
                    continue
                path = os.path.join(dirpath, n)
                raw = open(path, encoding="utf-8", errors="replace").read()
                for m in AWAIT_RE.finditer(raw):
                    fn = m.group(1)
                    if fn in SUSPEND_FUNCS:
                        line = raw[:m.start()].count("\n") + 1
                        problems.append((path, line, fn))

    if problems:
        print(f"=== {len(problems)} `.await()` call(s) on an already-suspend function ===")
        for path, line, fn in problems:
            print(f"  {os.path.relpath(path, ROOT)}:{line}\n      {fn}().await()")
        print("\nRESULT: PROBLEMS FOUND")
        return 1
    print("=== clean ===\nRESULT: clean")
    return 0


if __name__ == "__main__":
    sys.exit(main())
