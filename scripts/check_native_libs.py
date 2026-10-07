"""Check 64-bit ELF LOAD/RELRO geometry and uncompressed APK offsets for 16 KB pages."""
import struct
import sys
import zipfile
from pathlib import Path

PAGE = 16384


def require(condition, message):
    if not condition:
        raise ValueError(message)


def check_elf(blob):
    require(blob[:6] == b"\x7fELF\x02\x01", "expected little-endian ELF64")
    offset = struct.unpack_from("<Q", blob, 32)[0]
    stride, count = struct.unpack_from("<HH", blob, 54)
    require(stride >= 56, "invalid ELF program header stride")
    headers = [struct.unpack_from("<IIQQQQQQ", blob, offset + i * stride) for i in range(count)]
    loads = [h for h in headers if h[0] == 1]
    require(bool(loads), "no LOAD segments")
    for h in loads:
        require(h[7] >= PAGE and h[7] & (h[7] - 1) == 0, "LOAD alignment below 16 KB")
        require((h[3] - h[2]) % PAGE == 0, "LOAD virtual/file offsets are incongruent")
    for h in headers:
        if h[0] != 0x6474E552 or h[6] == 0:  # PT_GNU_RELRO
            continue
        start, end = h[3], h[3] + h[6]
        page_start, page_end = start // PAGE * PAGE, (end + PAGE - 1) // PAGE * PAGE
        # Android rounds RELRO outwards. A whole LOAD/suffix needn't end on a page:
        # reject only when rounding also protects writable bytes outside RELRO.
        for load in loads:
            if not load[1] & 2:  # PF_W
                continue
            lo, hi = load[3], load[3] + load[6]
            require(max(lo, page_start) >= min(hi, start), "RELRO rounding protects preceding writable bytes")
            require(max(lo, end) >= min(hi, page_end), "RELRO rounding protects following writable bytes")


def check_archive(path):
    count = 0
    with zipfile.ZipFile(path) as archive:
        for info in archive.infolist():
            parts = info.filename.split("/")
            if not info.filename.endswith(".so") or not any(abi in parts for abi in ("arm64-v8a", "x86_64")):
                continue
            try:
                check_elf(archive.read(info))
                if path.suffix == ".apk" and info.compress_type == zipfile.ZIP_STORED:
                    archive.fp.seek(info.header_offset)
                    header = archive.fp.read(30)
                    name_size, extra_size = struct.unpack_from("<HH", header, 26)
                    require((info.header_offset + 30 + name_size + extra_size) % PAGE == 0, "APK offset below 16 KB alignment")
            except (ValueError, struct.error) as error:
                raise ValueError(f"{path.name}: {info.filename}: {error}") from error
            count += 1
    require(count > 0, f"{path.name}: no 64-bit native libraries checked")
    print(f"{path.name}: {count} native libraries pass LOAD/RELRO" + (" and ZIP alignment" if path.suffix == ".apk" else ""))


def self_test():
    def elf(writable_size=0x1000, alignment=PAGE):
        headers = [
            (1, 5, 0, 0, 0, 0x1000, 0x1000, alignment),
            (1, 6, 0x1000, 0x5000, 0, writable_size, writable_size, alignment),
            (0x6474E552, 4, 0x1000, 0x5000, 0, 0x1000, 0x1000, 1),
        ]
        blob = bytearray(64 + 56 * len(headers))
        blob[:6] = b"\x7fELF\x02\x01"
        struct.pack_into("<Q", blob, 32, 64)
        struct.pack_into("<HH", blob, 54, 56, len(headers))
        for i, header in enumerate(headers):
            struct.pack_into("<IIQQQQQQ", blob, 64 + i * 56, *header)
        return blob
    check_elf(elf())  # Safe whole-segment RELRO, endpoint isn't 16 KB aligned.
    for bad in (elf(writable_size=0x2000), elf(alignment=4096), b"not ELF"):
        try:
            check_elf(bad)
        except (ValueError, struct.error):
            pass
        else:
            raise AssertionError("invalid geometry passed")
    print("native checker self-test passed")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
    else:
        require(len(sys.argv) > 1, "usage: check_native_libs.py APK [AAB]")
        for arg in sys.argv[1:]:
            check_archive(Path(arg))
