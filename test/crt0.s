.section .text.crt0
.global _start
.global _exit

_start:
    li sp, 0x4000000

    /* zero .bss so newlib's static state (stdio structs etc.) starts clean */
    la t0, __bss_start
    la t1, __bss_end
bss_clear_loop:
    bge t0, t1, bss_clear_done
    sw zero, 0(t0)
    addi t0, t0, 4
    j bss_clear_loop
bss_clear_done:

    call main

    /* main() returning falls through to _exit via the syscalls.c stub */
    call _exit

halt_loop:
    j halt_loop