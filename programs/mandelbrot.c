#include <stdint.h>

__attribute__((naked)) void _start(void) {
    __asm__ volatile(
        "li sp, 0x8000000\n"
        "call main\n"
        "loop: j loop\n"
    );
}

/* ---- provided debug hooks ---- */
void debug_log(char* character) {
    while (*character != '\0') {
        *((volatile unsigned int*)0x70000000) = *(character);
        *((volatile unsigned char*)0x8000034) = *(character);
        character++;
    }
}

void debug_num(unsigned int value) {
    *((volatile unsigned int*)0x70000008) = value;
}

/* debug_num only works in sim (pokes a sim-only trap address) -- on real
 * hardware nothing reads 0x70000008, so all numeric output has to go
 * through debug_log as ASCII hex over the real UART instead. */
static void debug_putc(char c) {
    char s[2] = { c, 0 };
    debug_log(s);
}

static void debug_hex32(unsigned int value) {
    static const char hex[] = "0123456789ABCDEF";
    for (int i = 7; i >= 0; i--) {
        debug_putc(hex[(value >> (i * 4)) & 0xF]);
    }
}

static void trace(char *label, unsigned int value) {
    debug_log(label);
    debug_hex32(value);
    debug_log("\n");
}

#define SCALE 1024
#define MAX_ITER 32

void draw_mandelbrot(volatile unsigned int* frame, int cx, int cy, int zoom) {
    int x_start = cx - zoom;
    int y_start = cy - (zoom * 240 / 320);
    int x_step = (zoom * 2) / 320;
    int y_step = (zoom * 2 * 240 / 320) / 240;
    if (x_step < 1) x_step = 1;
    if (y_step < 1) y_step = 1;

    trace("draw: cx=", (unsigned int)cx);
    trace("draw: cy=", (unsigned int)cy);
    trace("draw: zoom=", (unsigned int)zoom);
    trace("draw: x_start=", (unsigned int)x_start);
    trace("draw: y_start=", (unsigned int)y_start);
    trace("draw: x_step=", (unsigned int)x_step);
    trace("draw: y_step=", (unsigned int)y_step);

    for (int py = 0; py < 240; py++) {
        int ci = y_start + py * y_step;
        for (int px = 0; px < 320; px++) {
            int cr = x_start + px * x_step;
            int zr = 0;
            int zi = 0;
            int iter = 0;

            int trace_this = (px == 0 && py == 0);

            if (trace_this) {
                debug_log("=== pixel(0,0) trace start ===\n");
                trace("cr=", (unsigned int)cr);
                trace("ci=", (unsigned int)ci);
            }

            while (iter < MAX_ITER) {
                int zr_times_zr = zr * zr;          /* raw mul result, pre-shift */
                int zi_times_zi = zi * zi;
                int zr_times_zi = zr * zi;           /* raw mul result, pre-shift/scale */

                int zr2 = zr_times_zr >> 10;
                int zi2 = zi_times_zi >> 10;

                if (trace_this) {
                    debug_log("-- iter=");
                    debug_hex32((unsigned int)iter);
                    debug_log("\n");
                    trace("  zr=", (unsigned int)zr);
                    trace("  zi=", (unsigned int)zi);
                    trace("  zr*zr(raw)=", (unsigned int)zr_times_zr);
                    trace("  zi*zi(raw)=", (unsigned int)zi_times_zi);
                    trace("  zr*zi(raw)=", (unsigned int)zr_times_zi);
                    trace("  zr2(>>10)=", (unsigned int)zr2);
                    trace("  zi2(>>10)=", (unsigned int)zi2);
                }

                if (zr2 + zi2 > 4 * SCALE) {
                    if (trace_this) debug_log("  -> escaped\n");
                    break;
                }

                int new_zr = zr2 - zi2 + cr;
                zi = ((2 * zr_times_zi) >> 10) + ci;
                zr = new_zr;
                iter++;
            }

            if (trace_this) {
                trace("=== pixel(0,0) final iter=", (unsigned int)iter);
            }

            unsigned int color;
            if (iter == MAX_ITER) {
                color = 0x00;
            } else {
                color = (unsigned int)(iter * 7);
            }
            frame[320 * py + px] = color;
        }
    }
}

/* ---- multiply self-test, run once at boot before any rendering ----
 * Known-good pairs with results a human/script can verify by eye.
 * If mul is aliasing add (as seen on real hw for zmmul), 5*5 will print
 * as 10, not 25, immediately flagging the problem before anything else
 * runs. */
static void mul_case(int idx, int a, int b, int expect) {
    int got = a * b;
    debug_log("case ");
    debug_hex32((unsigned int)idx);
    debug_log(": a=");
    debug_hex32((unsigned int)a);
    debug_log(" b=");
    debug_hex32((unsigned int)b);
    debug_log(" got=");
    debug_hex32((unsigned int)got);
    debug_log(" expect=");
    debug_hex32((unsigned int)expect);
    debug_log(got == expect ? " PASS\n" : " FAIL\n");
}

static void mul_selftest(void) {
    debug_log("=== mul self-test start ===\n");
    mul_case(0, 5, 5, 25);
    mul_case(1, 7, 6, 42);
    mul_case(2, -3, 4, -12);
    mul_case(3, 1024, 1024, 1048576);
    mul_case(4, 100, 1000, 100000);
    mul_case(5, -1, -1, 1);
    debug_log("=== mul self-test end ===\n");
}

int main() {
    volatile unsigned int* frame = (volatile unsigned int*)0x10000000;
    volatile unsigned int* timer = (volatile unsigned int*)0x8000004;

    debug_log("boot\n");

    mul_selftest();

    int cx = -768;
    int cy = 0;
    int step = 0;

    while (1) {
        int ctime = *timer;

        debug_log("frame: step=");
        debug_hex32((unsigned int)step);
        debug_log("\n");

        int a = -120, b = -154224;
        int r1 = a * a;
        int r2 = b * b;
        int r3 = a * b;
        trace("r1(a*a)=", (unsigned int)r1);
        trace("r2(b*b)=", (unsigned int)r2);
        trace("r3(a*b)=", (unsigned int)r3);

        // int zoom = 1536;
        // for (int i = 0; i < step; i++) {
        //     zoom = (zoom * 3) / 4;
        // }
        // draw_mandelbrot(frame, cx, cy, zoom);

        step++;
        // if (step > 8) step = 0;

        while (*timer - ctime < 500000) {
            __asm__ volatile("nop");
        }
    }
}