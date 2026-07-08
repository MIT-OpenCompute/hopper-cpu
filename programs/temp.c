__attribute__((naked)) void _start(void) {
    __asm__ volatile(
        "li sp, 0x4000000\n"  
        "call main\n"
        "loop: j loop\n"
    );
}


#define IMG_W 320
#define IMG_H 240



volatile unsigned char buffer[320];

static void fill_buffer(){
   for (int i = 0; i < 320; i++) {
        buffer[i] = ( i>>1) ;
    }
}
static void draw_image(volatile unsigned int* frame) {
    for (int y = 0; y < IMG_H; y++) {
        for (int x = 0; x < IMG_W; x++) {
            frame[(y) * 320 + (x)] = (char)buffer[x];
        }
    }
}

int main() {
    volatile unsigned int* frame = (volatile unsigned int*)0x4000000;
    fill_buffer();
    while (1) {
        draw_image(frame);
        
    }
}