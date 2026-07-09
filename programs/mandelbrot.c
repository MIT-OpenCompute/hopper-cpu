__attribute__((naked)) void _start(void) {
    __asm__ volatile(
        "li sp, 0x4000000\n"
        "call main\n"
        "loop: j loop\n"
    );
}

#define SCALE 1024
#define MAX_ITER 32

void draw_mandelbrot(volatile unsigned char* frame, int cx, int cy, int zoom) {
    int x_start = cx - zoom;
    int y_start = cy - (zoom * 240 / 320);
    int x_step = (zoom * 2) / 320;
    int y_step = (zoom * 2 * 240 / 320) / 240;

    if (x_step < 1) x_step = 1;
    if (y_step < 1) y_step = 1;

    for (int py = 0; py < 240; py++) {
        int ci = y_start + py * y_step;
        for (int px = 0; px < 320; px++) {
            int cr = x_start + px * x_step;

            int zr = 0;
            int zi = 0;
            int iter = 0;

            while (iter < MAX_ITER) {
                int zr2 = (zr * zr) >> 10;
                int zi2 = (zi * zi) >> 10;

                if (zr2 + zi2 > 4 * SCALE) break;

                int new_zr = zr2 - zi2 + cr;
                zi = ((2 * zr * zi) >> 10) + ci;
                zr = new_zr;
                iter++;
            }

            unsigned char color;
            if (iter == MAX_ITER) {
                color = 0x00;
            } else {
                color = (unsigned char)(iter * 7);
            }

            frame[0x4000000 + (320 * py + px)] = color;
        }
    }
}

int main() {
    volatile unsigned char* frame = (volatile unsigned char*)0x0;

    int cx = -768;
    int cy = 0;
    int step = 0;

    while (1) {
        int zoom = 1536;
        for (int i = 0; i < step; i++) {
            zoom = (zoom * 3) / 4;
        }
        draw_mandelbrot(frame, cx, cy, zoom);
        step++;
        if (step > 8) step = 0;
    }
}