__attribute__((naked)) void _start(void) {
    __asm__ volatile(
        "li sp, 0x4000000\n"  
        "call main\n"
        "loop: j loop\n"
    );
}


#define IMG_W 320
#define IMG_H 240



volatile unsigned char buffer[100];

static void fill_buffer(){
   for (int i = 0; i < 320; i++) {
        buffer[i] = ( i >> 1);
    }
}



static void draw_image(volatile unsigned int* frame, int ox, int oy) {
    for (int y = 0; y < IMG_H; y++) {
        for (int x = 0; x < 320; x++) {
            frame[(oy + y) * 320 + (ox + x)] = (char)buffer[x];
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
    fill_buffer();
    
    for (int i = 0; i < 320; i++) {
        frame[i] = 0xFF;
        frame[239 * 320 + i] = 0xFF;
        frame[320 * i + 1] = 0xFF;
        frame[320 * i + 319] = buffer[1];
    }

    while (1) {
        draw_image(frame, 0, 0);
        
    }
}