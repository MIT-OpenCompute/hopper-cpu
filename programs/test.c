#include <stddef.h>

__attribute__((naked)) void _start(void) {
    __asm__ volatile(
        "li sp, 0x7000000\n"
        "call main\n"
        "loop: j loop\n"
    );
}

void debug_num(unsigned int value) {
    *((volatile unsigned int*)0x70000008) = value;
}
volatile unsigned char* uart_tx = (volatile unsigned char*)0x08000034;

void debug_log(char* character) {
    while (*character != '\0') {
        *uart_tx = *(character);
        *((volatile unsigned int*)0x70000000) = *(character);
        character++;
    }
}

#define TEST_BASE       ((volatile unsigned int *)0x05000000)
#define NUM_WORDS       (1u << 21)      /* 2M words = 8MB region */
#define MASK            (NUM_WORDS - 1)
#define STRIDE          0x9E3779B1u     /* odd - guarantees full period */
#define PATTERN_XOR     0xA5A5A5A5u

/* how many address bits to exercise in the address-bus test */
#define ADDR_BUS_BITS   21              /* covers the 8MB test region */

#define IMG_W 320
#define IMG_H 240
#define FRAME_BASE      ((volatile unsigned int *)0x10000000)

#define COLOR_GREEN     0x1C   /* RGB332: 000 111 00 */
#define COLOR_RED       0xE0   /* RGB332: 111 000 00 */

/* ---- status codes reported via debug_num ----
 * 0x10-0x1F : data bus test
 * 0x20-0x2F : address bus test
 * 0x30-0x3F : moving inversions test
 * 0x80-0x9F : legacy random-stride pass (kept from original)
 * 0xC0      : overall PASS
 * 0xC1      : overall FAIL
 */

static void report_mismatch(unsigned int addr_or_idx, unsigned int expected, unsigned int actual) {
    debug_log("  bad addr/idx follows\n");
    debug_num(addr_or_idx);
    debug_log("  expected follows\n");
    debug_num(expected);
    debug_log("  actual follows\n");
    debug_num(actual);
}

/* ------------------------------------------------------------------ */
/* Stage 1: Data bus test (walking ones / walking zeros)               */
/* Verifies every data line can independently hold 0 and 1 at a single */
/* fixed address. Catches stuck-at and adjacent-line shorts on D[31:0].*/
/* ------------------------------------------------------------------ */
static unsigned int test_data_bus(void) {
    debug_log("memtest: data bus walking-ones/zeros\n");
    debug_num(0x10);

    volatile unsigned int *addr = TEST_BASE;
    unsigned int errors = 0;

    for (int bit = 0; bit < 32; bit++) {
        unsigned int pattern = 1u << bit;

        /* walking ones */
        *addr = pattern;
        unsigned int actual = *addr;
        if (actual != pattern) {
            if (errors == 0) {
                debug_log("memtest: data bus FAIL (walking one)\n");
                report_mismatch(bit, pattern, actual);
            }
            errors++;
        }

        /* walking zeros (inverse pattern) */
        unsigned int inv_pattern = ~pattern;
        *addr = inv_pattern;
        actual = *addr;
        if (actual != inv_pattern) {
            if (errors == 0) {
                debug_log("memtest: data bus FAIL (walking zero)\n");
                report_mismatch(bit, inv_pattern, actual);
            }
            errors++;
        }
    }

    debug_num(0x1E);
    if (errors == 0) {
        debug_log("memtest: data bus OK\n");
    } else {
        debug_log("memtest: data bus errors follow\n");
        debug_num(errors);
    }
    return errors;
}

/* ------------------------------------------------------------------ */
/* Stage 2: Address bus test                                           */
/* Writes a unique value at each power-of-two offset, then checks that */
/* no other power-of-two offset (or the base) got corrupted. Catches   */
/* stuck-at, shorted, or aliased address lines.                        */
/* ------------------------------------------------------------------ */
static unsigned int test_address_bus(void) {
    debug_log("memtest: address bus test\n");
    debug_num(0x20);

    unsigned int errors = 0;
    unsigned int base_pattern = 0xFFFFFFFFu;

    /* write a sentinel at the base address, then a unique pattern at */
    /* every power-of-two word offset */
    TEST_BASE[0] = base_pattern;
    for (int bit = 0; bit < ADDR_BUS_BITS; bit++) {
        TEST_BASE[1u << bit] = (0xA5A50000u | bit);
    }

    /* verify base address wasn't clobbered */
    unsigned int actual = TEST_BASE[0];
    if (actual != base_pattern) {
        if (errors == 0) {
            debug_log("memtest: address bus FAIL (base addr clobbered)\n");
            report_mismatch(0, base_pattern, actual);
        }
        errors++;
    }

    /* verify each power-of-two offset still holds its unique pattern */
    for (int bit = 0; bit < ADDR_BUS_BITS; bit++) {
        unsigned int expected = (0xA5A50000u | bit);
        actual = TEST_BASE[1u << bit];
        if (actual != expected) {
            if (errors == 0) {
                debug_log("memtest: address bus FAIL (offset mismatch)\n");
                report_mismatch(1u << bit, expected, actual);
            }
            errors++;
        }
    }

    debug_num(0x2E);
    if (errors == 0) {
        debug_log("memtest: address bus OK\n");
    } else {
        debug_log("memtest: address bus errors follow\n");
        debug_num(errors);
    }
    return errors;
}

/* ------------------------------------------------------------------ */
/* Stage 3: Moving inversions test                                     */
/* For each fixed pattern: fill the whole region, then verify it, then */
/* move to the next (usually inverted) pattern. Good at catching       */
/* pattern-sensitive faults and coupling between cells.                */
/* ------------------------------------------------------------------ */
static unsigned int run_fixed_pattern_pass(unsigned int pattern) {
    for (unsigned int i = 0; i < NUM_WORDS; i++) {
        TEST_BASE[i] = pattern;
    }

    unsigned int errors = 0;
    for (unsigned int i = 0; i < NUM_WORDS; i++) {
        unsigned int actual = TEST_BASE[i];
        if (actual != pattern) {
            if (errors == 0) {
                report_mismatch(i, pattern, actual);
            }
            errors++;
        }
    }
    return errors;
}

static unsigned int test_moving_inversions(void) {
    debug_log("memtest: moving inversions pass\n");
    debug_num(0x30);

    static const unsigned int patterns[] = {
        0x00000000u,
        0xFFFFFFFFu,
        0xAAAAAAAAu,
        0x55555555u,
    };
    const int num_patterns = (int)(sizeof(patterns) / sizeof(patterns[0]));

    unsigned int total_errors = 0;
    for (int p = 0; p < num_patterns; p++) {
        debug_num(0x31 + p);
        unsigned int errors = run_fixed_pattern_pass(patterns[p]);
        if (errors != 0) {
            debug_log("memtest: moving inversions FAIL on pattern\n");
            debug_num(patterns[p]);
            debug_log("memtest: pattern error count follows\n");
            debug_num(errors);
        }
        total_errors += errors;
    }

    debug_num(0x3E);
    if (total_errors == 0) {
        debug_log("memtest: moving inversions OK\n");
    } else {
        debug_log("memtest: moving inversions total errors follow\n");
        debug_num(total_errors);
    }
    return total_errors;
}

/* ------------------------------------------------------------------ */
/* Stage 4: Pseudo-random stride/XOR pass (original test, kept as a    */
/* final randomized sweep - good at catching things fixed patterns     */
/* miss because addresses are visited in a scrambled, full-period      */
/* order rather than sequentially).                                    */
/* ------------------------------------------------------------------ */
static unsigned int test_random_stride(void) {
    debug_log("memtest: starting write pass\n");
    debug_num(0x80);

    unsigned int idx = 0;
    for (unsigned int i = 0; i < NUM_WORDS; i++) {
        idx = (idx + STRIDE) & MASK;
        TEST_BASE[idx] = i ^ PATTERN_XOR;
        if ((i & 0xFFFFF) == 0 && i != 0) {
            debug_num(0x80 + (i >> 20));   /* progress every ~1M words */
        }
    }
    debug_log("memtest: write pass done\n");
    debug_num(0x8E);

    debug_log("memtest: starting verify pass\n");
    debug_num(0x90);

    unsigned int errors = 0;
    unsigned int first_bad_idx = 0xFFFFFFFF;
    unsigned int first_bad_expected = 0;
    unsigned int first_bad_actual = 0;

    idx = 0;
    for (unsigned int i = 0; i < NUM_WORDS; i++) {
        idx = (idx + STRIDE) & MASK;
        unsigned int expected = i ^ PATTERN_XOR;
        unsigned int actual = TEST_BASE[idx];
        if (actual != expected) {
            if (errors == 0) {
                first_bad_idx = idx;
                first_bad_expected = expected;
                first_bad_actual = actual;
            }
            errors++;
        }
        if ((i & 0xFFFFF) == 0 && i != 0) {
            debug_num(0x90 + (i >> 20));
        }
    }

    debug_log("memtest: verify pass done\n");
    debug_num(0x9E);

    debug_log("memtest: error count follows\n");
    debug_num(errors);

    if (errors != 0) {
        debug_log("memtest: FIRST bad idx follows\n");
        debug_num(first_bad_idx);
        debug_log("memtest: FIRST expected value follows\n");
        debug_num(first_bad_expected);
        debug_log("memtest: FIRST actual value follows\n");
        debug_num(first_bad_actual);
    }

    debug_log("memtest: random stride pass complete\n");
    debug_num(0x9F);

    return errors;
}

/* ------------------------------------------------------------------ */
/* Top level: run every stage, stop early on first failure so you get  */
/* a clean signal about which fault class you're dealing with.         */
/* ------------------------------------------------------------------ */
unsigned int memtest(void) {
    unsigned int errors = 0;

    errors = test_data_bus();
    if (errors != 0) goto done;

    errors = test_address_bus();
    if (errors != 0) goto done;

    errors = test_moving_inversions();
    if (errors != 0) goto done;

    errors = test_random_stride();

done:
    if (errors == 0) {
        debug_log("MEMTEST PASSED\n");
        debug_num(0xC0);
    } else {
        debug_log("MEMTEST FAILED\n");
        debug_num(0xC1);
    }

    debug_log("memtest: complete\n");
    debug_num(0x9F);

    return errors;
}

static void paint_frame(unsigned int color) {
    volatile unsigned int* frame = FRAME_BASE;
    for (int i = 0; i < IMG_W * IMG_H; i++) {
        frame[i] = color;
    }
}

int main() {
    unsigned int errors = memtest();

    if (errors == 0) {
        paint_frame(COLOR_GREEN);
    } else {
        paint_frame(COLOR_RED);
    }

    return 0;
}