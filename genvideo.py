#!/usr/bin/env python3
"""
Generates a short synthetic test video as raw RGB332 frame data,
matching the framebuffer format used by the RISC-V firmware
(one byte per pixel, RRRGGGBB layout, confirmed against the Pong
code's paddle colors: 0x03 = blue, 0xE0 = red).

Produces (in the current directory):
  video.bin      - raw frame data, WIDTH*HEIGHT bytes per frame, concatenated
  video_meta.h   - C header with WIDTH/HEIGHT/FRAME_COUNT/delay macros

Once you're happy the pipeline works end to end, swap this script out
for real_video_convert.py to use an actual video file.
"""

WIDTH = 320
HEIGHT = 240
FRAME_COUNT = 40
BALL_RADIUS = 10


def rgb332(r, g, b):
    # RRRGGGBB: top 3 bits of R, top 3 bits of G, top 2 bits of B
    return (r & 0xE0) | ((g & 0xE0) >> 3) | ((b & 0xC0) >> 6)


BG = rgb332(0, 0, 40)       # dark blue background
BALL = rgb332(205, 220, 0)  # yellow ball
BORDER = rgb332(205, 205, 205)


def make_frame(t):
    frame = bytearray(bytes([BG]) * (WIDTH * HEIGHT))

    # border, same style as the Pong code
    for x in range(WIDTH):
        frame[x] = BORDER
        frame[(HEIGHT - 1) * WIDTH + x] = BORDER
    for y in range(HEIGHT):
        frame[y * WIDTH] = BORDER
        frame[y * WIDTH + WIDTH - 1] = BORDER

    # bouncing ball position (simple triangle-wave bounce)
    period_x = FRAME_COUNT
    period_y = max(FRAME_COUNT // 2, 1)
    margin = BALL_RADIUS + 2
    span_x = WIDTH - 2 * margin
    span_y = HEIGHT - 2 * margin
    cx = margin + int(span_x * abs(((t % (2 * period_x)) - period_x) / period_x))
    cy = margin + int(span_y * abs(((t % (2 * period_y)) - period_y) / period_y))

    for dy in range(-BALL_RADIUS, BALL_RADIUS + 1):
        for dx in range(-BALL_RADIUS, BALL_RADIUS + 1):
            if dx * dx + dy * dy <= BALL_RADIUS * BALL_RADIUS:
                x, y = cx + dx, cy + dy
                if 0 <= x < WIDTH and 0 <= y < HEIGHT:
                    frame[y * WIDTH + x] = BALL
    return frame


def main():
    with open("video.bin", "wb") as f:
        for t in range(FRAME_COUNT):
            f.write(make_frame(t))

    with open("video_meta.h", "w") as f:
        f.write("#ifndef VIDEO_META_H\n#define VIDEO_META_H\n\n")
        f.write(f"#define VIDEO_WIDTH  {WIDTH}\n")
        f.write(f"#define VIDEO_HEIGHT {HEIGHT}\n")
        f.write(f"#define VIDEO_FRAME_COUNT {FRAME_COUNT}\n")
        f.write("#define VIDEO_FRAME_DELAY_NOPS 400000  // tune to taste for real fps\n")
        f.write("\n#endif\n")

    total = WIDTH * HEIGHT * FRAME_COUNT
    print(f"Wrote video.bin ({total} bytes, {FRAME_COUNT} frames of {WIDTH}x{HEIGHT})")


if __name__ == "__main__":
    main()