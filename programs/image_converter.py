#!/usr/bin/env python3
import sys
from PIL import Image

IMG_W, IMG_H = 64, 64  # must match IMG_W/IMG_H in the C code

img = Image.open(sys.argv[1]).convert("RGB").resize((IMG_W, IMG_H))
px = img.load()
out = bytearray(IMG_W * IMG_H)
i = 0
for y in range(IMG_H):
    for x in range(IMG_W):
        r, g, b = px[x, y]
        out[i] = (r & 0xE0) | ((g & 0xE0) >> 3) | ((b & 0xC0) >> 6)
        i += 1

open("image.bin", "wb").write(out)
print(f"wrote image.bin: {len(out)} bytes")