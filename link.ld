ENTRY(_start)
SECTIONS {
  . = 0x0;
  .text   : { KEEP(*(.text.boot)) *(.text*) }
  .rodata : { *(.rodata*) *(.srodata*) }
  .data   : { *(.data*) *(.sdata*) }
  .bss    : { *(.bss*) *(.sbss*) *(COMMON) }
}