#include "video_meta.h"

__attribute__((naked)) void _start(void) {
    __asm__ volatile(
        "li sp, 0x8000000\n"
        "call main\n"
        "loop: j loop\n"
    );
}

extern unsigned char video_data[];

void debug_num(unsigned int value) {
    *((volatile unsigned int*)0x70000008) = value;
}
volatile unsigned char* uart_tx = (volatile unsigned char*)0x08000034;

void debug_log(char* character) {
    while (*character != '\0') {
        *uart_tx = *(character);
        *((volatile unsigned int*)0x70000000) = *(character);
        character++;
    }
}

/* ------------------------------------------------------------------ */
/* CRC info block. The host script finds MAGIC in the .bin and patches */
/* length + expected CRC in place. Must exist before patching.         */
/* ------------------------------------------------------------------ */
#define CRC_MAGIC 0x1BADC0DEu

__attribute__((used, aligned(16)))
volatile const unsigned int crc_info[4] = { CRC_MAGIC, 0, 0, 0 };
/* [0]=magic  [1]=image length in bytes  [2]=expected CRC32  [3]=pad */

/* Nibble-table CRC32 (zlib/IEEE, reflected, poly 0xEDB88320).
 * Table is 64 bytes; ~2 lookups per byte instead of 8 shift rounds,
 * so a multi-MB image checks in a fraction of the time. */
static const unsigned int crc_tab4[16] = {
    0x00000000, 0x1DB71064, 0x3B6E20C8, 0x26D930AC,
    0x76DC4190, 0x6B6B51F4, 0x4DB26158, 0x5005713C,
    0xEDB88320, 0xF00F9344, 0xD6D6A3E8, 0xCB61B38C,
    0x9B64C2B0, 0x86D3D2D4, 0xA00AE278, 0xBDBDF21C
};

static unsigned int check_image_crc(unsigned int *computed_out) {
    unsigned int length   = crc_info[1];
    unsigned int expected = crc_info[2];
    unsigned int skip_lo  = (unsigned int)&crc_info[2]; /* CRC word itself */
    unsigned int skip_hi  = skip_lo + 4;                /* is read as zero */

    const volatile unsigned char *img = (const volatile unsigned char *)0;
    unsigned int crc = 0xFFFFFFFFu;
    debug_log("check crc");
    for (unsigned int i = 0; i < length; i++) {
        unsigned char b = (i >= skip_lo && i < skip_hi) ? 0 : img[i];
        crc = crc_tab4[(crc ^ b) & 0xF] ^ (crc >> 4);
        crc = crc_tab4[(crc ^ (b >> 4)) & 0xF] ^ (crc >> 4);
    }
    crc = ~crc;

    if (computed_out) *computed_out = crc;
    return (crc == expected) ? 0u : 1u;
}

#define COLOR_GREEN 0x1C  /* RGB332 */
#define COLOR_RED   0xE0

static void paint_frame(volatile unsigned int *fb, unsigned int color, unsigned int n) {
    for (unsigned int i = 0; i < n; i++) fb[i] = color;
}

int main(void) {
    volatile unsigned int *fb    = (volatile unsigned int *)0x10000000;
    volatile unsigned int *timer = (volatile unsigned int *)0x8000004;

    const unsigned int pixels_per_frame = VIDEO_WIDTH * VIDEO_HEIGHT;
    debug_log("ppf");
    /* ---- load-integrity gate ---- */
    unsigned int computed;
    if (check_image_crc(&computed)) {
        paint_frame(fb, COLOR_RED, pixels_per_frame);
        debug_log("corrupted");
        while (1) { }                       /* corrupted load: stop here */
    }
    paint_frame(fb, COLOR_GREEN, pixels_per_frame);

    /* hold green ~2s so you can see it (66000 ticks ~= one 66ms frame
       in your player, so ~30 frames worth; adjust to taste) */
    unsigned int t0 = *timer;
    while (*timer - t0 < 30u * 66000u) { }
    debug_log("good");
    /* ---- original video loop ---- */
    while (1) {
        for (unsigned int f = 0; f < VIDEO_FRAME_COUNT; f++) {
            int ctime = *timer;
            const unsigned char *src = video_data + (unsigned int)f * pixels_per_frame;
            while (*timer - ctime < 60000) {
                for (unsigned int p = 0; p < pixels_per_frame; p++) {
                    fb[p] = src[p];
                }
            }
        }
    }
}