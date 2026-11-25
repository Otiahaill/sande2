"""Dump the interesting parts of a built AndroidManifest.xml.

Prints the attributes of the manifest, the application and every activity,
plus the action/category of each intent filter. This is the decisive check
for "installs but does not appear in the launcher".
"""
import struct
import sys
import zipfile

TYPE_REFERENCE = 0x01
TYPE_STRING = 0x03
TYPE_INT_DEC = 0x10
TYPE_INT_HEX = 0x11
TYPE_INT_BOOLEAN = 0x12


def load(path):
    z = zipfile.ZipFile(path)
    data = z.read("AndroidManifest.xml")

    off = 8
    (string_count, style_count, flags, strings_start, styles_start) = struct.unpack_from(
        "<IIIII", data, off + 8
    )
    utf8 = bool(flags & (1 << 8))
    strings = []
    base = off + strings_start
    for i in range(string_count):
        (o,) = struct.unpack_from("<I", data, off + 28 + i * 4)
        p = base + o
        try:
            if utf8:
                p += 2
                bl = data[p]
                strings.append(data[p + 1:p + 1 + bl].decode("utf-8", "replace"))
            else:
                (n,) = struct.unpack_from("<H", data, p)
                strings.append(data[p + 2:p + 2 + n * 2].decode("utf-16-le", "replace"))
        except Exception:
            strings.append("")

    def S(i):
        return strings[i] if 0 <= i < len(strings) else "?%d" % i

    def value(dt, raw):
        """Renders a Res_value given its dataType byte and data word."""
        if dt == TYPE_STRING:
            return S(raw)
        if dt == TYPE_INT_BOOLEAN:
            return "true" if raw else "false"
        if dt == TYPE_INT_HEX:
            return "0x%x" % raw
        if dt == TYPE_REFERENCE:
            return "@0x%08x" % raw
        if dt == TYPE_INT_DEC:
            return str(raw)
        return "0x%x" % raw

    # Walk the chunks.
    elements = []
    o = 8
    while o < len(data):
        ctype, hsize, csize = struct.unpack_from("<HHI", data, o)
        if ctype == 0x0102:
            (ns, name) = struct.unpack_from("<II", data, o + 16)
            (attr_start, attr_size, attr_count) = struct.unpack_from("<HHH", data, o + 24)
            # The attribute array starts right after the fixed part of the
            # START_ELEMENT chunk (verified empirically: ns, name and the
            # Res_value of an attribute are 20 bytes each).
            a = o + 36
            attrs = []
            for i in range(attr_count):
                (ano,) = struct.unpack_from("<I", data, a + i * attr_size + 4)
                dt = data[a + i * attr_size + 15]
                (raw,) = struct.unpack_from("<I", data, a + i * attr_size + 16)
                attrs.append((S(ano), value(dt, raw)))
            elements.append((S(name), attrs))
        elif ctype == 0x0101:  # END_ELEMENT
            pass
        o += csize
        if csize == 0:
            break
    return elements


def main():
    elements = load(sys.argv[1])
    for tag, attrs in elements:
        if tag in ("manifest", "application", "activity", "activity-alias",
                   "intent-filter", "action", "category"):
            print("<%s>" % tag)
            for k, v in attrs:
                print("    %s = %s" % (k, v))


if __name__ == "__main__":
    main()
