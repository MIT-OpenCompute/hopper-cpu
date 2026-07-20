/*
 * Standalone bare-metal test for mul / mulh / mulhu / mulhsu.
 *
 * Goal: verify the FPGA's multiplier produces correct HIGH-WORD results,
 * independent of Doom, libgcc, or any 64-bit C multiplication (which the
 * compiler could itself lower to mulh/mulhu, making it useless as a
 * reference). The reference value is computed using only 16x16->32
 * multiplies (which always fit in 32 bits, so the compiler will never need
 * to emit mulh/mulhu for them), combined with shifts/adds.
 *
 * Wire up like the working pong test: sp at 0x7000000, UART tx at
 * 0x08000034 (single byte, poll-free write assumed like the pong code).
 */

#include <stdint.h>

#define UART_TX ((volatile unsigned char*)0x08000034)

__attribute__((naked)) void _start(void) {
    __asm__ volatile(
        "li sp, 0x7000000\n"
        "call main\n"
        "loop: j loop\n"
    );
}

static void uart_putc(char c) {
    *UART_TX = (unsigned char)c;
}

static void uart_puts(const char *s) {
    while (*s) uart_putc(*s++);
}

static void uart_puthex32(uint32_t v) {
    static const char hex[] = "0123456789ABCDEF";
    for (int i = 7; i >= 0; i--) {
        uart_putc(hex[(v >> (i * 4)) & 0xF]);
    }
}

/* ---- Reference 64-bit multiply using only 16x16->32 partial products ---- */
/* No 64-bit C multiplication anywhere here, and every intermediate multiply
 * has 16-bit operands (product always fits in 32 bits), so the compiler
 * cannot lower any of this to mulh/mulhu. Pure mul + shifts + adds. */

static void ref_umul64(uint32_t a, uint32_t b, uint32_t *hi, uint32_t *lo) {
    uint32_t a_lo = a & 0xFFFF, a_hi = a >> 16;
    uint32_t b_lo = b & 0xFFFF, b_hi = b >> 16;

    uint32_t p0 = a_lo * b_lo; /* up to 32 bits, fine */
    uint32_t p1 = a_lo * b_hi; /* up to 32 bits, fine */
    uint32_t p2 = a_hi * b_lo; /* up to 32 bits, fine */
    uint32_t p3 = a_hi * b_hi; /* up to 32 bits, fine */

    /* combine: result = p3<<32 + (p1+p2)<<16 + p0, done with carries */
    uint32_t mid = p1 + p2;
    uint32_t carry_mid = (mid < p1) ? 1u : 0u; /* overflow of 32-bit add */

    uint32_t lo_ = p0 + (mid << 16);
    uint32_t carry_lo = (lo_ < p0) ? 1u : 0u;

    uint32_t hi_ = p3 + (mid >> 16) + (carry_mid << 16) + carry_lo;

    *hi = hi_;
    *lo = lo_;
}

/* signed x signed high word, via unsigned ref + sign correction */
static uint32_t ref_mulh(int32_t a, int32_t b) {
    uint32_t ua = (uint32_t)(a < 0 ? -a : a);
    uint32_t ub = (uint32_t)(b < 0 ? -b : b);
    uint32_t hi, lo;
    ref_umul64(ua, ub, &hi, &lo);
    int neg = ((a < 0) ^ (b < 0)) ? 1 : 0;
    if (neg) {
        /* negate 64-bit {hi,lo} */
        lo = ~lo;
        hi = ~hi;
        lo += 1;
        if (lo == 0) hi += 1;
    }
    return hi;
}

/* signed a, unsigned b, high word */
static uint32_t ref_mulhsu(int32_t a, uint32_t b) {
    uint32_t ua = (uint32_t)(a < 0 ? -a : a);
    uint32_t hi, lo;
    ref_umul64(ua, b, &hi, &lo);
    if (a < 0) {
        lo = ~lo;
        hi = ~hi;
        lo += 1;
        if (lo == 0) hi += 1;
    }
    return hi;
}

static uint32_t ref_mulhu(uint32_t a, uint32_t b) {
    uint32_t hi, lo;
    ref_umul64(a, b, &hi, &lo);
    return hi;
}

/* ---- Force actual hardware instructions via inline asm ---- */

static inline uint32_t hw_mul(uint32_t a, uint32_t b) {
    uint32_t r;
    __asm__ volatile("mul %0, %1, %2" : "=r"(r) : "r"(a), "r"(b));
    return r;
}
static inline uint32_t hw_mulh(int32_t a, int32_t b) {
    uint32_t r;
    __asm__ volatile("mulh %0, %1, %2" : "=r"(r) : "r"(a), "r"(b));
    return r;
}
static inline uint32_t hw_mulhu(uint32_t a, uint32_t b) {
    uint32_t r;
    __asm__ volatile("mulhu %0, %1, %2" : "=r"(r) : "r"(a), "r"(b));
    return r;
}
static inline uint32_t hw_mulhsu(int32_t a, uint32_t b) {
    uint32_t r;
    __asm__ volatile("mulhsu %0, %1, %2" : "=r"(r) : "r"(a), "r"(b));
    return r;
}

typedef struct { uint32_t a, b; } pair_t;

/* operand pairs chosen to stress sign handling, boundary values, and
 * patterns typical of a reciprocal-based software divide (which is what
 * libgcc emits for rv32i_zmmul) */
static const pair_t pairs[] = {
    {0x00000000u, 0x00000000u},
    {0x00000001u, 0x00000001u},
    {0xFFFFFFFFu, 0xFFFFFFFFu},   /* -1 * -1 (signed) */
    {0x80000000u, 0x80000000u},  /* INT32_MIN * INT32_MIN */
    {0x7FFFFFFFu, 0x7FFFFFFFu},  /* INT32_MAX * INT32_MAX */
    {0x80000000u, 0x00000001u},
    {0x7FFFFFFFu, 0x00000002u},
    {0x12345678u, 0x9ABCDEF0u},
    {0xDEADBEEFu, 0xCAFEBABEu},
    {0x0000FFFFu, 0x0000FFFFu},
    {0xFFFF0000u, 0x0000FFFFu},
    {0x55555555u, 0xAAAAAAAAu},
    {0x00000064u, 0x000003E8u}, /* small values like Doom fixed-point mults */
    {0xFFFFFF9Cu, 0x00000064u}, /* -100 * 100 */
    {0x40000000u, 0x00000004u},
    {0x89ABCDEFu, 0x01234567u},
};

int main(void) {
    int n = (int)(sizeof(pairs) / sizeof(pairs[0]));
    int fails = 0;

    uart_puts("MUL/MULH/MULHU/MULHSU HW TEST\n");

    for (int i = 0; i < n; i++) {
        uint32_t a = pairs[i].a;
        uint32_t b = pairs[i].b;

        uint32_t hi_ref, lo_ref;
        ref_umul64(a, b, &hi_ref, &lo_ref);

        uint32_t lo_hw   = hw_mul(a, b);
        uint32_t mulh_hw  = hw_mulh((int32_t)a, (int32_t)b);
        uint32_t mulh_ref = ref_mulh((int32_t)a, (int32_t)b);
        uint32_t mulhu_hw  = hw_mulhu(a, b);
        uint32_t mulhu_ref = hi_ref;
        uint32_t mulhsu_hw  = hw_mulhsu((int32_t)a, b);
        uint32_t mulhsu_ref = ref_mulhsu((int32_t)a, b);

        int mul_ok    = (lo_hw == lo_ref);
        int mulh_ok   = (mulh_hw == mulh_ref);
        int mulhu_ok  = (mulhu_hw == mulhu_ref);
        int mulhsu_ok = (mulhsu_hw == mulhsu_ref);

        uart_puts("a=");    uart_puthex32(a);
        uart_puts(" b=");   uart_puthex32(b);
        uart_puts(" mul:");    uart_putc(mul_ok    ? 'P' : 'F');
        uart_puts(" mulh:");   uart_putc(mulh_ok   ? 'P' : 'F');
        uart_puts(" mulhu:");  uart_putc(mulhu_ok  ? 'P' : 'F');
        uart_puts(" mulhsu:"); uart_putc(mulhsu_ok ? 'P' : 'F');

        if (!mul_ok) {
            uart_puts(" [mul hw="); uart_puthex32(lo_hw);
            uart_puts(" ref=");     uart_puthex32(lo_ref); uart_puts("]");
        }
        if (!mulh_ok) {
            uart_puts(" [mulh hw="); uart_puthex32(mulh_hw);
            uart_puts(" ref=");      uart_puthex32(mulh_ref); uart_puts("]");
        }
        if (!mulhu_ok) {
            uart_puts(" [mulhu hw="); uart_puthex32(mulhu_hw);
            uart_puts(" ref=");       uart_puthex32(mulhu_ref); uart_puts("]");
        }
        if (!mulhsu_ok) {
            uart_puts(" [mulhsu hw="); uart_puthex32(mulhsu_hw);
            uart_puts(" ref=");        uart_puthex32(mulhsu_ref); uart_puts("]");
        }
        uart_putc('\n');

        if (!mul_ok || !mulh_ok || !mulhu_ok || !mulhsu_ok) fails++;
    }

    uart_puts("DONE fails=");
    uart_puthex32((uint32_t)fails);
    uart_putc('\n');

    while (1) { __asm__ volatile("nop"); }
    return 0;
}