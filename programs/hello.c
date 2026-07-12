__attribute__((naked)) void _start(void) {
    __asm__ volatile(
        "li sp, 0x7000000\n"  
        "call main\n"
        "loop: j loop\n"
    );
}



#define IMG_W 320
#define IMG_H 240






int main() {
    volatile unsigned int* frame = (volatile unsigned int*)0x10000000;
    volatile unsigned int* timer = (volatile unsigned int*)0x8000004;
    volatile unsigned char* uart_tx = (volatile unsigned char*)0x08000034;
    
    volatile unsigned int* keytracker = (volatile unsigned int*)0x08000008;

    const int W_BIT     = 0x1A;     
    const int S_BIT     = 0x16;      
    const int UP_BIT    = 0x52 - 64;
    const int DOWN_BIT  = 0x51 - 64;

    const int PADDLE_SPEED = 2;
    const int PADDLE_MIN   = 22;  
    const int PADDLE_MAX   = 217; 

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
        int ctime = *timer;

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

        // ---- paddle control: W/S move the left paddle, Up/Down move the right ----
        unsigned int word0 = keytracker[0];
        unsigned int word2 = keytracker[2];

        if ((word0 >> W_BIT) & 1) {
            paddY1 -= PADDLE_SPEED;
        }
        if ((word0 >> S_BIT) & 1) {
            paddY1 += PADDLE_SPEED;
        }
        if ((word2 >> UP_BIT) & 1) {
            paddY2 -= PADDLE_SPEED;
        }
        if ((word2 >> DOWN_BIT) & 1) {
            paddY2 += PADDLE_SPEED;
        }

        if (paddY1 < PADDLE_MIN) paddY1 = PADDLE_MIN;
        if (paddY1 > PADDLE_MAX) paddY1 = PADDLE_MAX;
        if (paddY2 < PADDLE_MIN) paddY2 = PADDLE_MIN;
        if (paddY2 > PADDLE_MAX) paddY2 = PADDLE_MAX;

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
<<<<<<< HEAD
=======
        }

        for (int i = 0; i < 8000; i++) {
            __asm__ volatile("nop");
>>>>>>> 9f3f1ccb74f58cfae7e2055ad46a2933e7b06a25
        }
        *uart_tx = 'a';
        while (*timer - ctime < 8000) {
            __asm__ volatile("nop");
        }
    }
}