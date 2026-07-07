#!/bin/bash
set -e

GCC="./xpack-riscv-none-elf-gcc-15.2.0-1/bin/riscv-none-elf-gcc"
AS="./xpack-riscv-none-elf-gcc-15.2.0-1/bin/riscv-none-elf-as"
OBJCOPY="./xpack-riscv-none-elf-gcc-15.2.0-1/bin/riscv-none-elf-objcopy"

SRC_C="step_test.c"
SRC_S="tiny_data.s"

OBJ_C="/tmp/step_test.o"
OBJ_S="/tmp/tiny_data.o"
ELF="/tmp/step_test.elf"
BIN="/tmp/step_test.bin"
HEX="/tmp/step_test.hex"

echo "Compiling firmware..."
$GCC -c -O1 -march=rv32i -mabi=ilp32 "$SRC_C" -o "$OBJ_C"

echo "Assembling tiny embedded data..."
$AS -march=rv32i -mabi=ilp32 "$SRC_S" -o "$OBJ_S"

echo "Linking..."
$GCC -march=rv32i -mabi=ilp32 -nostdlib \
    -Wl,--section-start=.text=0x0,--entry=_start \
    -o "$ELF" "$OBJ_C" "$OBJ_S" -lgcc

echo "Converting to binary..."
$OBJCOPY -O binary "$ELF" "$BIN"

echo "Converting to hex..."
python convert.py "$BIN"

echo "Loading program..."
python load_program.py "${BIN%.bin}.hex" --port /dev/ttyUSB1

echo "Cleaning up..."
rm -f "$OBJ_C" "$OBJ_S" "$ELF" "$BIN"

echo "Done."