__attribute__((naked)) void _start(void) {
    __asm__ volatile(
        "li sp, 0x4000000\n"  
        "call main\n"
        "loop: j loop\n"
    );
}


#define IMG_W 320
#define IMG_H 240





static void draw_image(volatile unsigned int* frame, int ox, int oy) {
    for (int y = 0; y < IMG_H; y++) {
        for (int x = 0; x < IMG_W; x++) {
            frame[(oy + y) * 320 + (ox + x)] = 122;
        }
    }
}




int main() {
    volatile unsigned int* frame = (volatile unsigned int*)0x4000000;

    int paddY1 = 120;
    int paddY2 = 120;

    int ballX = 160;
    int ballY = 120;
    int dX = 1;
    int dY = 1;
    
    for (int i = 0; i < 320; i++) {
        frame[i] = 0xFF;
        frame[239 * 320 + i] = 0xFF;
        frame[320 * i + 1] = 0xFF;
        frame[320 * i + 319] = 0xFF;
    }
    // char buffer[10];
    // buffer[0] = 0xE0;
    // buffer[1] = 0xFF;
    // buffer[2] = 0x03;
    // buffer[4] = 120;
    // fill_buffer();

    while (1) {
        draw_image(frame, 0, 0);
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                frame[320 * (ballY + y) + ballX + x] = 0x00;
            }
        }

        for (int x = -2; x <= 2; x++) {
            for (int y = -20; y <= 20; y++) {
                frame[320 * (paddY1 + y) + 20 + x] = 0x00;
            }
        }

        for (int x = -2; x <= 2; x++) {
            for (int y = -20; y <= 20; y++) {
                frame[320 * (paddY2 + y) + 299 + x] = 0x00;
            }
        }

        ballX += dX;
        ballY += dY;

        if (ballX == 319 - 6) {
            dX = -1;
        }

        if (ballX == 6) {
            dX = 1;
        }

        if (ballY == 239 - 6) {
            dY = -1;
        }

        if (ballY == 6) {
            dY = 1;
        }

        if (ballX <= 25 && ballX >= 15 && ballY <= paddY1 + 25 && ballY >= paddY1 - 25) {
            dX = 1;
        }

        if (ballX <= 304 && ballX >= 294 && ballY <= paddY2 + 25 && ballY >= paddY2 - 25) {
            dX = -1;
        }

        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                frame[320 * (ballY + y) + ballX + x] = 0xFF;
            }
        }

        for (int x = -2; x <= 2; x++) {
            for (int y = -20; y <= 20; y++) {
                frame[320 * (paddY1 + y) + 20 + x] = 0x03;
            }
        }

        for (int x = -2; x <= 2; x++) {
            for (int y = -20; y <= 20; y++) {
                frame[320 * (paddY2 + y) + 299 + x] = 0xE0;
            }
        }

        for (int i = 0; i < 8000; i++) {
            __asm__ volatile("nop");
        }
    }
}