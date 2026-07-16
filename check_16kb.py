#!/usr/bin/env python3
"""Verify every 64-bit .so in an AAB/APK has 16 KB-aligned LOAD segments.

Play rejects bundles where a 64-bit native library still uses 4 KB LOAD
alignment. 32-bit ABIs are exempt, so they are reported but never fail
the run.

Reads the ELF program headers directly, so it needs no readelf, no NDK
and no unzip -- just python3.

Usage: ./check_16kb.py [path/to/app-release.aab]
"""

import struct
import sys
import zipfile
from pathlib import Path

DEFAULT_ARCHIVE = "app/build/outputs/bundle/release/app-release.aab"

# Android only enforces the 16 KB page size on 64-bit ABIs.
ABIS_64 = {"arm64-v8a", "x86_64", "riscv64"}

PT_LOAD = 1
MIN_ALIGN = 16 * 1024


def load_aligns(blob):
    """Yield p_align for every PT_LOAD segment in an ELF image."""
    if blob[:4] != b"\x7fELF":
        return

    is_64 = blob[4] == 2
    endian = "<" if blob[5] == 1 else ">"

    if is_64:
        e_phoff = struct.unpack_from(endian + "Q", blob, 0x20)[0]
        e_phentsize, e_phnum = struct.unpack_from(endian + "HH", blob, 0x36)
        align_off, fmt = 0x30, endian + "Q"
    else:
        e_phoff = struct.unpack_from(endian + "I", blob, 0x1C)[0]
        e_phentsize, e_phnum = struct.unpack_from(endian + "HH", blob, 0x2A)
        align_off, fmt = 0x1C, endian + "I"

    for i in range(e_phnum):
        ph = e_phoff + i * e_phentsize
        if struct.unpack_from(endian + "I", blob, ph)[0] != PT_LOAD:
            continue
        yield struct.unpack_from(fmt, blob, ph + align_off)[0]


def main():
    archive = Path(sys.argv[1] if len(sys.argv) > 1 else DEFAULT_ARCHIVE)

    if not archive.is_file():
        print(f"not found: {archive}", file=sys.stderr)
        print(f"usage: {sys.argv[0]} [path/to/app-release.aab]", file=sys.stderr)
        return 2

    print(f"Checking {archive}\n")

    failed = False
    rows = []

    with zipfile.ZipFile(archive) as z:
        names = sorted(n for n in z.namelist() if n.endswith(".so"))
        for name in names:
            aligns = list(load_aligns(z.read(name)))
            if not aligns:
                rows.append(("?   ", "-", name))
                continue

            worst = min(aligns)
            abi = Path(name).parent.name

            if abi in ABIS_64:
                if worst >= MIN_ALIGN:
                    status = "OK  "
                else:
                    status = "FAIL"
                    failed = True
            else:
                status = "skip"  # 32-bit: requirement does not apply

            rows.append((status, hex(worst), name))

    if not rows:
        print("no .so found in archive -- nothing to check")
        return 0

    width = max(len(r[1]) for r in rows)
    for status, align, name in rows:
        print(f"{status}  {align:<{width}}  {name}")

    print()
    if failed:
        print("FAIL -- some 64-bit libraries still use 4 KB alignment (see above)")
        return 1

    print("PASS -- all 64-bit libraries are 16 KB aligned")
    return 0


if __name__ == "__main__":
    sys.exit(main())
