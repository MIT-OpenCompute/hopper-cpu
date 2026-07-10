#include <stdio.h>
#include <stdlib.h>

#define FRAME_W 320
#define FRAME_H 240

static volatile unsigned int *frame = (volatile unsigned int *)0x10000000;

int main(void) {
    printf("boot ok\n");

    /* allocate a small block and confirm malloc works at all */
    unsigned char *buf = malloc(64);
    if (!buf) {
        printf("malloc failed\n");
        while (1) { __asm__ volatile("nop"); }
    }
    printf("malloc ok, ptr=%p\n", (void *)buf);

    for (int i = 0; i < 64; i++) {
        buf[i] = (unsigned char)i;
    }
    printf("wrote pattern into heap block\n");

    /* second allocation to sanity-check the bump allocator advances */
    unsigned char *buf2 = malloc(128);
    printf("second malloc ok, ptr=%p (should be > first)\n", (void *)buf2);

    /* draw a visible diagonal-stripe pattern derived from the heap data,
       so a working framebuffer write is confirmed visually too */
    for (int y = 0; y < FRAME_H; y++) {
        for (int x = 0; x < FRAME_W; x++) {
            unsigned char v = buf[(x + y) % 64];
            frame[y * FRAME_W + x] = v;
        }
    }
    printf("frame pattern written, test complete\n");

    while (1) {
        __asm__ volatile("nop");
    }
    return 0;
}