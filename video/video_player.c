#include "video_meta.h"

__attribute__((naked)) void _start(void) {
    __asm__ volatile(
        "li sp, 0x8000000\n"   // stack starts at top of the 64MB RAM, grows down
        "call main\n"
        "loop: j loop\n"
    );
}

extern unsigned char video_data[];  // provided by video_data.s via .incbin


int main(void) {
    volatile unsigned int *fb = (volatile unsigned int *)0x8000000;
    const unsigned int pixels_per_frame = VIDEO_WIDTH * VIDEO_HEIGHT;

    while (1) {
        for (unsigned int f = 0; f < VIDEO_FRAME_COUNT; f++) {
            const unsigned char *src = video_data + (unsigned int)f * pixels_per_frame;
            for (unsigned int p = 0; p < pixels_per_frame; p++) {
                fb[p] = src[p];  
            }
        }
    }
}