#include <stdio.h>

// Simulated global scratchpad memory to verify byte, half-word, and word store/load patterns
unsigned int memory_scratchpad[16] __attribute__((aligned(4)));

// A rolling checksum that accumulates all execution states.
unsigned int rolling_checksum = 0x12345678;

void accumulate_checksum(unsigned int value) {
    // Standard fast mixing step (Galois-style accumulator)
    rolling_checksum = (rolling_checksum >> 1) | (rolling_checksum << 31);
    rolling_checksum ^= value;
}

int main() {
    printf("[HOST VERIFICATION] Starting comprehensive code strain test...\n");

    // Extreme mathematical bounds for testing signed/unsigned comparisons and overflows
    int signed_min = 0x80000000;
    int signed_max = 0x7FFFFFFF;
    unsigned int unsigned_max = 0xFFFFFFFF;
    int negative_one = -1;

    // =========================================================================
    // SECTION 1: SHIFT & ARITHMETIC CORNER CASES (SLL, SRL, SRA, ADD, SUB)
    // =========================================================================
    printf("Executing Phase 1: ALU & Shift Edge Cases...\n");
    
 for (int shift_amt = 0; shift_amt < 32; shift_amt++) {
        // Test SRA (Shift Right Arithmetic)
        int sra_res = signed_min >> shift_amt;
        // Test SRL (Shift Right Logical)
        unsigned int srl_res = unsigned_max >> shift_amt;
        // Test SLL (Shift Left Logical)
        unsigned int sll_res = 1u << shift_amt;

        accumulate_checksum(sra_res);
        accumulate_checksum(srl_res);
        accumulate_checksum(sll_res);

        int temp1 = sra_res + srl_res;
        int temp2 = temp1 - negative_one;
        int temp3 = temp2 << (shift_amt & 0x1F);
        
        accumulate_checksum(temp3);

        // --- AGGRESSIVE STEP LOGGER ---
        printf("shamt: %2d | sra: 0x%08X | srl: 0x%08X | sll: 0x%08X | t3: 0x%08X | ck: 0x%08X\n", 
               shift_amt, (unsigned int)sra_res, srl_res, sll_res, (unsigned int)temp3, rolling_checksum);
    }

    // // =========================================================================
    // // SECTION 2: BRANCH CONDITION MATRIX
    // // =========================================================================
    // printf("Executing Phase 2: Dynamic Branch Comparator Matrix...\n");
    
    // // 1. Signed Checks
    // if (signed_min < signed_max)  accumulate_checksum(0xA1);
    // else                          accumulate_checksum(0x00);

    // if (negative_one < 0)         accumulate_checksum(0xA2);
    // else                          accumulate_checksum(0x00);

    // if (signed_max > negative_one) accumulate_checksum(0xA3);
    // else                          accumulate_checksum(0x00);

    // // 2. Unsigned Checks
    // if ((unsigned int)signed_min > (unsigned int)signed_max) accumulate_checksum(0xB1);
    // else                                                      accumulate_checksum(0x00);

    // if ((unsigned int)negative_one > 0u)                      accumulate_checksum(0xB2);
    // else                                                      accumulate_checksum(0x00);

    // // 3. Complex Branching Chain
    // for (int b = -5; b <= 5; b++) {
    //     if (b == 0) {
    //         accumulate_checksum(0xCC);
    //     } else if (b > 0) {
    //         if (b % 2 == 0) accumulate_checksum(b * 10);
    //         else            accumulate_checksum(b * 20);
    //     } else {
    //         if (b % 2 == 0) accumulate_checksum(b * 30);
    //         else            accumulate_checksum(b * 40);
    //     }
    // }

    // // =========================================================================
    // // SECTION 3: MEMORY SUB-SYSTEM BOUNDARY VERIFICATION (Sign Extension)
    // // =========================================================================
    // printf("Executing Phase 3: Byte and Half-word Sign Extension Memory Sweep...\n");

    // // Initialize scratchpad with known data pattern
    // memory_scratchpad[0] = 0xDEADBEEF;
    // memory_scratchpad[1] = 0x00000000;
    // memory_scratchpad[2] = 0xFFFFFFFF;

    // // Pointer-aliasing to byte level
    // unsigned char* byte_ptr = (unsigned char*)memory_scratchpad;
    // unsigned short* half_ptr = (unsigned short*)memory_scratchpad;

    // // Test Byte Stores and Sign-extension Loads
    // byte_ptr[4] = 0x80; 
    // byte_ptr[5] = 0x7F; 

    // // Read back explicitly checking sign extension match
    // int lb_sign = (signed char)byte_ptr[4]; 
    // accumulate_checksum(lb_sign);

    // unsigned int lbu_zero = byte_ptr[4];
    // accumulate_checksum(lbu_zero);

    // // Test Half-Word Stores and Loads
    // half_ptr[4] = 0xFA12; 
    // int lh_sign = (signed short)half_ptr[4];
    // accumulate_checksum(lh_sign);

    // unsigned int lhu_zero = half_ptr[4];
    // accumulate_checksum(lhu_zero);

    // // Verify raw memory dump
    // accumulate_checksum(memory_scratchpad[1]);
    // accumulate_checksum(memory_scratchpad[2]);

    // // =========================================================================
    // // SECTION 4: IMMEDIATE AND UPPER ADDRESSING LOGIC (Emulated PC Logic)
    // // =========================================================================
    // printf("Executing Phase 4: Upper Immediate Verification...\n");

    // // Emulating the exact math value added by the RISC-V 'auipc' block instruction
    // // so the rolling checksum sequence remains matching
    // unsigned int pc_delta = 0x1000000;
    // accumulate_checksum(pc_delta);

    // =========================================================================
    // SECTION 5: FINAL ANALYSIS
    // =========================================================================
    printf("\n==================================================\n");
    printf("STRESS TEST CALCULATION FINISHED.\n");
    printf("Result Checksum (Decimal): %u\n", rolling_checksum);
    printf("Result Checksum (Hex):     0x%08X\n", rolling_checksum);
    printf("==================================================\n");

    return 0;
}