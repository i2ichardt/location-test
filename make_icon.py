#!/usr/bin/env python3
"""Write a small launcher PNG. No third-party image libraries."""
import struct
import zlib
from pathlib import Path

W = H = 192


def px(x, y):
    # slate tile, copper pin, bone dot
    cx, cy = 96, 78
    dx, dy = x - cx, y - cy
    if dx * dx + dy * dy <= 28 * 28:
        return (243, 241, 235, 255)
    # pin body: circle + triangle
    if dx * dx + (y - 86) * (y - 86) <= 34 * 34 and y < 118:
        return (196, 98, 45, 255)
    if 70 <= x <= 122 and 100 <= y <= 150:
        # triangle narrowing to the bottom
        half = int((150 - y) * 0.72)
        if abs(x - 96) <= max(half, 0):
            return (196, 98, 45, 255)
    return (36, 62, 74, 255)


def chunk(tag, data):
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)


raw = b"".join(b"\x00" + b"".join(bytes(px(x, y)) for x in range(W)) for y in range(H))
png = b"\x89PNG\r\n\x1a\n"
png += chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0))
png += chunk(b"IDAT", zlib.compress(raw, 9))
png += chunk(b"IEND", b"")
out = Path(__file__).resolve().parent / "res" / "drawable" / "ic_launcher.png"
out.write_bytes(png)
print(out, len(png))
