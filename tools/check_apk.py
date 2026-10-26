#!/usr/bin/env python3
"""Validate the actual delivery archive, not only Gradle's intended ABIs."""
import struct
import sys
import zipfile

expected = {"arm64-v8a", "x86_64"}
required = {"libapp.so", "libflutter.so", "libbroadband_native.so", "libiperf_exec.so", "libdartjni.so"}
with zipfile.ZipFile(sys.argv[1]) as archive:
    libs = [name for name in archive.namelist() if name.startswith("lib/") and name.endswith(".so")]
    actual = {name.split("/")[1] for name in libs}
    if actual != expected:
        raise SystemExit(f"Unexpected or missing ABI: {actual}")
    if any("web_fixture" in name for name in archive.namelist()):
        raise SystemExit("Temporary HTTPS certificate must not be shipped")
    for name in archive.namelist():
        if name.endswith('.dex') and b'SystemDnsCacheFixture' in archive.read(name):
            raise SystemExit('Debug DNS fixture must not be shipped')
    for abi in expected:
        names = {name.split("/")[-1] for name in libs if name.split("/")[1] == abi}
        if names != required:
            raise SystemExit(f"Incomplete engine set for {abi}: {names}")
    for name in libs:
        data = archive.read(name)
        if data[:6] != b"\x7fELF\x02\x01":
            raise SystemExit(f"Expected ELF64 little-endian: {name}")
        start = struct.unpack_from("<Q", data, 32)[0]
        size, count = struct.unpack_from("<HH", data, 54)
        alignments = [struct.unpack_from("<Q", data, start + i * size + 48)[0]
                      for i in range(count) if struct.unpack_from("<I", data, start + i * size)[0] == 1]
        if not alignments or any(alignment < 16384 for alignment in alignments):
            raise SystemExit(f"ELF LOAD alignment below 16 KiB: {name}: {alignments}")
        print(f"{name}: ELF 16 KiB alignment OK")
print("Both architectures contain complete engines; temporary certificate absent.")
