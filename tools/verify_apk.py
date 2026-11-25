"""Verify an APK without a device: structure, manifest and dex contents.

The Android SDK build tools are not installed on this device, so the checks
here work on the raw APK: the binary manifest's string pool is parsed for the
package name, version and permissions, the APK signing block is checked for a
v2/v3 signature, and the dex files are scanned for the classes the app is
expected to contain.
"""
import io
import os
import re
import struct
import sys
import zipfile


def parse_string_pool(data):
    """The strings of a binary XML (AXML) string pool."""
    if len(data) < 8:
        return []
    # The string pool chunk starts at offset 8 in a binary XML document.
    if data[0:4] != b"\x03\x00\x08\x00":
        return []
    off = 8
    if len(data) < off + 28:
        return []
    (chunk_type,) = struct.unpack_from("<H", data, off)
    if chunk_type != 0x0001:
        return []
    # type(2) headerSize(2) | chunkSize(4) stringCount(4) styleCount(4)
    # flags(4) stringsStart(4) stylesStart(4)
    (chunk_size, string_count, style_count, flags, strings_start, styles_start) = (
        struct.unpack_from("<IIIIII", data, off + 4)
    )
    utf8 = bool(flags & (1 << 8))
    offsets = []
    for i in range(string_count):
        (o,) = struct.unpack_from("<I", data, off + 28 + i * 4)
        offsets.append(o)
    base = off + strings_start
    out = []
    for o in offsets:
        p = base + o
        if p + 2 > len(data):
            continue
        try:
            if utf8:
                # Two length fields (characters, then bytes), then the bytes.
                char_len = data[p + 1]
                byte_len = data[p + 2]
                out.append(data[p + 3:p + 3 + byte_len].decode("utf-8", "replace"))
            else:
                (n,) = struct.unpack_from("<H", data, p)
                out.append(data[p + 2:p + 2 + n * 2].decode("utf-16-le", "replace"))
        except Exception:
            continue
    return out


def has_apk_signing_block(raw):
    """APK Signature Scheme v2/v3 stores the signature outside META-INF."""
    # The APK Signing Block sits just before the central directory and starts
    # with a 16-byte magic.
    magic = b"APK Sig Block 42"
    return magic in raw


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 1
    path = sys.argv[1]
    size = os.path.getsize(path)
    print("APK: %s" % path)
    print("size: %d bytes (%.1f MB)" % (size, size / 1048576.0))

    z = zipfile.ZipFile(path)
    names = z.namelist()
    ok = True

    def check(label, condition, detail=""):
        nonlocal ok
        print("  [%s] %s%s" % ("OK" if condition else "!!", label, (" - " + detail) if detail else ""))
        if not condition:
            ok = False

    print("\nStructure")
    dex = [n for n in names if re.fullmatch(r"classes\d*\.dex", n)]
    check("classes.dex present", bool(dex), "%d dex file(s)" % len(dex))
    check("AndroidManifest.xml present", "AndroidManifest.xml" in names)
    check("resources.arsc present", "resources.arsc" in names)
    check("resources packaged", any(n.startswith("res/") for n in names))
    native = [n for n in names if n.startswith("lib/")]
    print("       native libraries: %d (this app is pure Kotlin/Compose)"
          % len(native))

    v1 = [n for n in names if n.startswith("META-INF/") and n.endswith((".RSA", ".DSA", ".EC"))]
    raw = open(path, "rb").read()
    v2 = has_apk_signing_block(raw)
    check("APK signed", bool(v1) or v2,
          "v1: %s | v2/v3 signing block: %s" % (", ".join(v1) or "none", v2))

    print("\nManifest")
    manifest = z.read("AndroidManifest.xml")
    strings = parse_string_pool(manifest)
    joined = "\n".join(strings)
    check("applicationId com.example.sande", "com.example.sande" in joined)
    check("versionName 1.0.0", "1.0.0" in joined)

    perms = sorted({s for s in strings if s.startswith("android.permission.")})
    for p in perms:
        print("       permission %s" % p)
    check("INTERNET permission", "android.permission.INTERNET" in perms)
    check("BLUETOOTH_CONNECT permission", "android.permission.BLUETOOTH_CONNECT" in perms)

    app_entries = sorted({
        s.lstrip("#%:\" ") for s in strings
        if "com.example.sande" in s and not s.startswith("android.")
    })
    print("       app entries: %d" % len(app_entries))
    for a in app_entries[:12]:
        print("         %s" % a)
    check("launcher activity present", any("SplashActivity" in a for a in app_entries))
    check("FileProvider present", any("fileprovider" in a.lower() for a in app_entries))

    print("\nDex")
    all_classes = set()
    for d in dex:
        raw_dex = z.read(d)
        for m in re.finditer(rb"Lcom/example/sande/[A-Za-z0-9_/$]+;", raw_dex):
            all_classes.add(m.group(0)[1:-1].decode())
    print("       com.example.sande classes: %d" % len(all_classes))
    expected = [
        "com/example/sande/di/SandeApplication",
        "com/example/sande/ui/SplashActivity",
        "com/example/sande/data/db/AppDatabase",
        "com/example/sande/data/remote/ApiClient",
        "com/example/sande/data/sync/OutboxPayloadSerializer",
        "com/example/sande/ui/screens/ReportsScreenKt",
        "com/example/sande/ui/screens/SalesScreenKt",
        "com/example/sande/data/repository/CheckoutRepository",
    ]
    for e in expected:
        check("class %s" % e.rsplit("/", 1)[-1], e in all_classes)

    print("\n%s" % ("APK VERIFICATION PASSED" if ok else "APK VERIFICATION FOUND PROBLEMS"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
