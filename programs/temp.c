__attribute__((naked)) void _start(void) {
    __asm__ volatile(
        "li sp, 0x40000\n"  
        "call main\n"
        "loop: j loop\n"
    );
}



volatile unsigned char buffer[1];

static void fill_buffer(){
//    for (int i = 0; i < 1; i++) {
//         buffer[i] = ( i>>1) ;
//     }
    buffer[0] = 20;
}

int main() {
    volatile unsigned int* frame = (volatile unsigned int*)0x4000000;
    fill_buffer();
    frame[0] = (char)buffer[0] + 100;
}