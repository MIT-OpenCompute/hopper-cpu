__attribute__((naked)) void _start(void) {
    __asm__ volatile(
        "li sp, 0x4000\n"  // top of 32KB RAM
        "call main\n"
        "loop: j loop\n"
    );
}

int main() {
    volatile unsigned int* frame = (volatile unsigned int*)0x4000;

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

    while (1) {
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

        // if (*btns & 0b0001 && paddY1 < 239 - 21) {
        //     paddY1 += 1;
        // }

        // if (*btns & 0b0010 && paddY1 > 21) {
        //     paddY1 -= 1;
        // }

        // if (*btns & 0b0100 && paddY2 < 239 - 21) {
        //     paddY2 += 1;
        // }

        // if (*btns & 0b1000 && paddY2 > 21) {
        //     paddY2 -= 1;
        // }

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