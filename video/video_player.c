#include "video_meta.h"

__attribute__((naked)) void _start(void) {
    __asm__ volatile(
        "li sp, 0x8000000\n"   
        "call main\n"
        "loop: j loop\n"
    );
}

extern unsigned char video_data[]; 


int main(void) {
    volatile unsigned int *fb = (volatile unsigned int *)0x10000000;
   volatile unsigned int* timer = (volatile unsigned int*)0x8000004;

    const unsigned int pixels_per_frame = VIDEO_WIDTH * VIDEO_HEIGHT;

    while (1) {
        for (unsigned int f = 0; f < VIDEO_FRAME_COUNT; f++) {
            int ctime = *timer;
            const unsigned char *src = video_data + (unsigned int)f * pixels_per_frame;
            while(*timer - ctime < 66000){
                for (unsigned int p = 0; p < pixels_per_frame; p++) {
                    fb[p] = src[p];  
                }
            

            }
        }
    }
}