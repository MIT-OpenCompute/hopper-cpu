#include "debug.h"

/* Cache-hostile memory test.
   - Region: 4MB starting at 0x05000000 (avoids program/.bss/heap near 0x0
     and the framebuffer at 0x10000000 - adjust TEST_BASE if that overlaps
     anything on your memory map).
   - Access order: an additive Weyl sequence (idx = (idx + STRIDE) & MASK)
     with STRIDE odd and MASK = NUM_WORDS-1 (power-of-two region). Since
     STRIDE is odd and NUM_WORDS is a power of two, this visits every word
     exactly once, in a fully scrambled order - no two consecutive accesses
     land in the same 4-word (16-byte) cache line, so every single access
     is a guaranteed miss/eviction regardless of cache size.
   - Uses only add/and/xor - no multiply or divide, so this test doesn't
     depend on the soft-div/mul paths you're currently debugging elsewhere. */

#define TEST_BASE       ((volatile unsigned int *)0x05000000)
#define NUM_WORDS       (1u << 20)      /* 1M words = 4MB region */
#define MASK            (NUM_WORDS - 1)
#define STRIDE          0x9E3779B1u     /* odd - guarantees full period */
#define PATTERN_XOR     0xA5A5A5A5u

void memtest(void) {
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

    if (errors == 0) {
        debug_log("MEMTEST PASSED\n");
        debug_num(0xC0);
    } else {
        debug_log("MEMTEST FAILED\n");
        debug_num(0xC1);
    }

    debug_log("memtest: complete\n");
    debug_num(0x9F);
}