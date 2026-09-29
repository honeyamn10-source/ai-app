#!/usr/bin/env python3
"""Fails when a 64-bit native library in an Android App Bundle or APK is not 16 KB page aligned.

Google Play requires apps targeting Android 15+ to support 16 KB memory pages: every PT_LOAD
segment of each arm64-v8a / x86_64 .so must have p_align >= 16384. 32-bit ABIs are exempt.

Usage: check-16kb-alignment.py app-release.aab [more.aab|.apk ...]
"""
import struct
import sys
import zipfile

PAGE = 16 * 1024
ABIS_64 = ("arm64-v8a", "x86_64")


def load_alignments(data: bytes):
    if data[:4] != b"\x7fELF":
        raise ValueError("not an ELF file")
    if data[4] != 2:
        raise ValueError("not a 64-bit ELF")
    endian = "<" if data[5] == 1 else ">"
    phoff, = struct.unpack_from(endian + "Q", data, 0x20)
    phentsize, phnum = struct.unpack_from(endian + "HH", data, 0x36)
    aligns = []
    for i in range(phnum):
        base = phoff + i * phentsize
        p_type, = struct.unpack_from(endian + "I", data, base)
        if p_type == 1:  # PT_LOAD
            p_align, = struct.unpack_from(endian + "Q", data, base + 0x30)
            aligns.append(p_align)
    return aligns


def main(paths):
    failures, checked = [], 0
    for path in paths:
        with zipfile.ZipFile(path) as archive:
            for name in archive.namelist():
                parts = name.split("/")
                if not name.endswith(".so") or not any(abi in parts for abi in ABIS_64):
                    continue
                checked += 1
                try:
                    aligns = load_alignments(archive.read(name))
                except ValueError as error:
                    failures.append(f"{name}: {error}")
                    continue
                smallest = min(aligns) if aligns else 0
                status = "ok" if smallest >= PAGE else "NOT 16 KB ALIGNED"
                print(f"{status:>18}  min p_align={smallest:>6}  {name}")
                if smallest < PAGE:
                    failures.append(name)
    print(f"checked {checked} 64-bit native libraries")
    if failures:
        print("\n16 KB page-size check failed (Google Play will reject or warn):", file=sys.stderr)
        for item in failures:
            print(f"  - {item}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__, file=sys.stderr)
        sys.exit(2)
    sys.exit(main(sys.argv[1:]))
