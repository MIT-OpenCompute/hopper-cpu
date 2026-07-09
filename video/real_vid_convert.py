#!/usr/bin/env python3
"""
Converts a real video file into raw RGB332 frame data for the RISC-V
video player firmware, with Bayer ordered dithering for better
perceived color depth. Requires ffmpeg on PATH and Pillow.

Usage:
    python3 real_video_convert.py input.mp4 [--fps 15] [--width 320] [--height 240] [--no-dither]

Produces (in the current directory):
  video.bin
  video_meta.h
"""
import argparse
import subprocess
import shutil
import tempfile
import os
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    raise SystemExit("Please install Pillow first: pip install pillow")

# 8x8 Bayer matrix, values 0..63. Ordered dithering uses a fixed
# repeating pattern (unlike Floyd-Steinberg) so it doesn't flicker
# frame-to-frame in video - the same pattern is stable across time.
BAYER8 = [
    [0, 48, 12, 60, 3, 51, 15, 63],
    [32, 16, 44, 28, 35, 19, 47, 31],
    [8, 56, 4, 52, 11, 59, 7, 55],
    [40, 24, 36, 20, 43, 27, 39, 23],
    [2, 50, 14, 62, 1, 49, 13, 61],
    [34, 18, 46, 30, 33, 17, 45, 29],
    [10, 58, 6, 54, 9, 57, 5, 53],
    [42, 26, 38, 22, 41, 25, 37, 21],
]

R_BITS, G_BITS, B_BITS = 3, 3, 2


def quantize(v, bits, bayer_val):
    # bayer_val in [0,63] -> offset spanning one quantization step,
    # centered at 0, nudges v up or down before truncating to `bits`.
    step = 256 // (1 << bits)
    offset = (bayer_val / 64.0 - 0.5) * step
    v = v + offset
    if v < 0:
        v = 0
    elif v > 255:
        v = 255
    return int(v) >> (8 - bits)


def rgb332_bytes(img: "Image.Image", dither: bool) -> bytes:
    img = img.convert("RGB")
    w, h = img.width, img.height
    out = bytearray(w * h)
    px = img.load()
    i = 0
    for y in range(h):
        row = BAYER8[y & 7]
        for x in range(w):
            r, g, b = px[x, y]
            if dither:
                bv = row[x & 7]
                rq = quantize(r, R_BITS, bv)
                gq = quantize(g, G_BITS, bv)
                bq = quantize(b, B_BITS, bv)
            else:
                rq = r >> (8 - R_BITS)
                gq = g >> (8 - G_BITS)
                bq = b >> (8 - B_BITS)
            out[i] = (rq << 5) | (gq << 2) | bq
            i += 1
    return bytes(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("input")
    ap.add_argument("--fps", type=int, default=15)
    ap.add_argument("--width", type=int, default=320)
    ap.add_argument("--height", type=int, default=240)
    ap.add_argument("--max-frames", type=int, default=0,
                     help="0 = no limit (be careful with RAM budget!)")
    ap.add_argument("--no-dither", action="store_true",
                     help="disable dithering, just truncate to RGB332")
    args = ap.parse_args()

    if shutil.which("ffmpeg") is None:
        raise SystemExit("ffmpeg not found on PATH")

    with tempfile.TemporaryDirectory() as tmp:
        pattern = os.path.join(tmp, "f_%06d.png")
        cmd = [
            "ffmpeg", "-i", args.input,
            "-vf", f"fps={args.fps},scale={args.width}:{args.height}:force_original_aspect_ratio=decrease,"
                   f"pad={args.width}:{args.height}:(ow-iw)/2:(oh-ih)/2",
            pattern,
        ]
        subprocess.run(cmd, check=True)

        frame_files = sorted(Path(tmp).glob("f_*.png"))
        if args.max_frames:
            frame_files = frame_files[: args.max_frames]

        if not frame_files:
            raise SystemExit("No frames extracted - check the input file/ffmpeg output above")

        with open("video.bin", "wb") as out:
            for ff in frame_files:
                img = Image.open(ff)
                out.write(rgb332_bytes(img, dither=not args.no_dither))

        n = len(frame_files)
        total_bytes = args.width * args.height * n
        print(f"Wrote video.bin: {n} frames, {total_bytes} bytes "
              f"({total_bytes / (1024*1024):.1f} MB), dither={'off' if args.no_dither else 'on'}")

        nops = max(1, int(2_000_000 / args.fps))  # rough starting point, tune on hardware
        with open("video_meta.h", "w") as f:
            f.write("#ifndef VIDEO_META_H\n#define VIDEO_META_H\n\n")
            f.write(f"#define VIDEO_WIDTH  {args.width}\n")
            f.write(f"#define VIDEO_HEIGHT {args.height}\n")
            f.write(f"#define VIDEO_FRAME_COUNT {n}\n")
            f.write(f"#define VIDEO_FRAME_DELAY_NOPS {nops}  // tune to hit real {args.fps}fps\n")
            f.write("\n#endif\n")


if __name__ == "__main__":
    main()