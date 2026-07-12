void debug_log(char *character) {
    while (*character != '\0') {
        *((volatile unsigned int *)0x70000000) = *(character);
        character++;
    }
}

void debug_num(unsigned int value) {
    *((volatile unsigned int *)0x70000008) = value;
}