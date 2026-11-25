"""Prints the signing certificate fingerprints of an APK.

Google Sign-In on Android checks that the SHA-1 of the certificate that
signed the APK is registered for the OAuth Android client. If the fingerprint
does not match, Google refuses the request with "No credentials available".

The certificate lives in the APK Signature Scheme v2/v3 block, so it is read
directly from the APK.
"""
import base64
import hashlib
import struct
import sys
import zipfile


def find_signing_block(raw):
    """Locates the APK Signing Block (it sits before the central directory)."""
    # The central directory starts at the End Of Central Directory record.
    eocd = raw.rfind(b"PK\x05\x06")
    if eocd < 0:
        return None, None
    (cd_offset,) = struct.unpack_from("<I", raw, eocd + 16)
    if cd_offset < 24:
        return None, None
    magic = raw[cd_offset - 16:cd_offset]
    if magic != b"APK Sig Block 42":
        return None, None
    (block_size,) = struct.unpack_from("<Q", raw, cd_offset - 24)
    start = cd_offset - block_size - 8
    return start, cd_offset - 8


def certificates(raw):
    """Yields the DER certificates in the signing block."""
    start, end = find_signing_block(raw)
    if start is None:
        return
    pos = start + 8
    while pos < end - 24:
        (pair_len,) = struct.unpack_from("<Q", raw, pos)
        if pair_len <= 0:
            break
        (id_value,) = struct.unpack_from("<I", raw, pos + 8)
        value = raw[pos + 12:pos + 8 + pair_len - 4]
        if id_value == 0x7109871A:  # APK_SIGNATURE_SCHEME_V2_BLOCK_ID
            yield from parse_v2_block(value)
        elif id_value == 0xF05368C0:  # APK_SIGNATURE_SCHEME_V3_BLOCK_ID
            yield from parse_v3_block(value)
        pos += 8 + pair_len


def parse_v2_block(value):
    # signers: length-prefixed sequence of length-prefixed signers
    pos = 0
    (signers_len,) = struct.unpack_from("<I", value, pos)
    pos += 4
    end = pos + signers_len
    while pos < end:
        (signer_len,) = struct.unpack_from("<I", value, pos)
        pos += 4
        signer = value[pos:pos + signer_len]
        pos += signer_len

        # signer = signed data | signatures | public key
        p = 0
        (sd_len,) = struct.unpack_from("<I", signer, p)
        p += 4
        sd = signer[p:p + sd_len]
        p += sd_len
        (sigs_len,) = struct.unpack_from("<I", signer, p)
        p += 4 + sigs_len
        (pk_len,) = struct.unpack_from("<I", signer, p)
        p += 4 + pk_len

        # signed data = digests | certificates | additional attributes
        q = 0
        (digests_len,) = struct.unpack_from("<I", sd, q)
        q += 4 + digests_len
        (certs_len,) = struct.unpack_from("<I", sd, q)
        q += 4
        certs = sd[q:q + certs_len]
        r = 0
        while r + 4 <= len(certs):
            (cert_len,) = struct.unpack_from("<I", certs, r)
            r += 4
            if cert_len == 0:
                continue
            yield certs[r:r + cert_len]
            r += cert_len


def parse_v3_block(value):
    yield from parse_v2_block(value)


def fingerprints(der):
    md = hashlib.sha1(der).digest()
    sha256 = hashlib.sha256(der).digest()
    def fmt(d):
        return ":".join("%02X" % b for b in d)
    return fmt(md), fmt(sha256)


def main():
    path = sys.argv[1]
    raw = open(path, "rb").read()

    print("APK: %s" % path)
    z = zipfile.ZipFile(path)
    v1 = [n for n in z.namelist() if n.startswith("META-INF/") and n.endswith((".RSA", ".DSA", ".EC"))]
    if v1:
        print("v1 signature entries: %s" % ", ".join(v1))

    seen = set()
    for der in certificates(raw):
        sha1, sha256 = fingerprints(der)
        if sha1 in seen:
            continue
        seen.add(sha1)
        print("")
        print("SHA-1:   %s" % sha1)
        print("SHA-256: %s" % sha256)

    if not seen:
        print("")
        print("No signing certificate found in the APK signing block.")


if __name__ == "__main__":
    main()
