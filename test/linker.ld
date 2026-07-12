MEMORY
{
  DRAM (rwx) : ORIGIN = 0x00000000, LENGTH = 128M
}

SECTIONS
{
  . = 0x0;

  .text : {
    *(.text.crt0)      /* keep crt0's _start first in the image */
    *(.text*)
  } > DRAM

  .rodata : { *(.rodata*) } > DRAM

  .data : { *(.data*) } > DRAM

  .bss : {
    __bss_start = .;
    *(.bss*)
    *(COMMON)
    __bss_end = .;
  } > DRAM

  . = ALIGN(16);
  __heap_start = .;
  __heap_end = 0x03F00000;   /* stop well below the 0x4000000 stack */

  /DISCARD/ : { *(.comment) *(.riscv.attributes) }
}