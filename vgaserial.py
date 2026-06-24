#!/usr/bin/env python3
import serial
import numpy as np
import pygame

W, H   = 320, 240
BAUD   = 8_000_000
PORT   = "/dev/ttyUSB1"

pygame.init()
screen = pygame.display.set_mode((W * 2, H * 2))
pygame.display.set_caption("VGA over UART")
clock  = pygame.time.Clock()

ser = serial.Serial(PORT, BAUD, timeout=1)
ser.reset_input_buffer()

buf = bytearray(W * H)

def decode_pixel(b):
    # 8-bit colour: RRRGGGBB
    r = ((b >> 5) & 0x7) * 255 // 7
    g = ((b >> 2) & 0x7) * 255 // 7
    b_ = ( b       & 0x3) * 255 // 3
    return r, g, b_

print(f"Listening on {PORT} at {BAUD} baud ...")

running = True
while running:
    for event in pygame.event.get():
        if event.type == pygame.QUIT:
            running = False
        if event.type == pygame.KEYDOWN and event.key == pygame.K_ESCAPE:
            running = False

    # read exactly one frame
    raw = ser.read(W * H)
    print(raw)
    if len(raw) < W * H:
        continue   # timeout, skip

    # build RGB array
    arr = np.frombuffer(raw, dtype=np.uint8)
    r = ((arr >> 5) & 0x7) * 255 // 7
    g = ((arr >> 2) & 0x7) * 255 // 7
    b = ( arr       & 0x3) * 255 // 3

    rgb = np.stack([r, g, b], axis=1).reshape(H, W, 3).astype(np.uint8)

    surf = pygame.surfarray.make_surface(rgb.transpose(1, 0, 2))
    surf = pygame.transform.scale(surf, (W * 2, H * 2))
    screen.blit(surf, (0, 0))
    pygame.display.flip()
    clock.tick(60)

ser.close()
pygame.quit()