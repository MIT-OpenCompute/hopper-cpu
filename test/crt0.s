.section .text.crt0
.global _start
.global _exit

_start:
    li sp, 0x4000000

    /* debug marker: entered _start, sp is set */
    li t2, 0x70000008
    li t3, 0x01
    sw t3, 0(t2)

    /* zero .bss so newlib's static state (stdio structs etc.) starts clean */
    la t0, __bss_start
    la t1, __bss_end
bss_clear_loop:
    bge t0, t1, bss_clear_done
    sw zero, 0(t0)
    addi t0, t0, 4
    j bss_clear_loop
bss_clear_done:

    /* debug marker: bss cleared */
    li t3, 0x02
    sw t3, 0(t2)

    /* debug marker: about to call main */
    li t3, 0x03
    sw t3, 0(t2)

    call main

    /* debug marker: main() returned (should not happen - test.c loops forever) */
    li t2, 0x70000008
    li t3, 0xFF
    sw t3, 0(t2)

    /* main() returning falls through to _exit via the syscalls.c stub */
    call _exit

halt_loop:
    j halt_loop