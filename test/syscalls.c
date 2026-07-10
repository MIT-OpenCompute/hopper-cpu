#include <sys/stat.h>
#include <errno.h>
#include <stdint.h>

extern char __heap_start;
extern char __heap_end;

static char *heap_ptr = &__heap_start;

/* UART transmit register - confirmed address. */
#define UART_TX ((volatile unsigned int *)0x08000034)

static void uart_putc(char c) {
    *UART_TX = (unsigned int)(unsigned char)c;
}

void *_sbrk(int incr) {
    char *prev = heap_ptr;
    if (heap_ptr + incr > &__heap_end) {
        errno = ENOMEM;
        return (void *)-1;
    }
    heap_ptr += incr;
    return (void *)prev;
}

int _write(int fd, char *buf, int len) {
    (void)fd;
    for (int i = 0; i < len; i++) {
        if (buf[i] == '\n') uart_putc('\r');
        uart_putc(buf[i]);
    }
    return len;
}

int _read(int fd, char *buf, int len) {
    (void)fd; (void)buf;
    return 0;
}

int _close(int fd) {
    (void)fd;
    return -1;
}

int _lseek(int fd, int offset, int whence) {
    (void)fd; (void)offset; (void)whence;
    return 0;
}

int _fstat(int fd, struct stat *st) {
    (void)fd;
    st->st_mode = S_IFCHR;
    return 0;
}

int _isatty(int fd) {
    (void)fd;
    return 1;
}

void _exit(int code) {
    (void)code;
    while (1) { __asm__ volatile("nop"); }
}

int _kill(int pid, int sig) {
    (void)pid; (void)sig;
    errno = EINVAL;
    return -1;
}

int _getpid(void) {
    return 1;
}