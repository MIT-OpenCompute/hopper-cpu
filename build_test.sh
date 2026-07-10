#!/bin/bash
set -e

GCC="./xpack-riscv-none-elf-gcc-15.2.0-1/bin/riscv-none-elf-gcc"
AS="./xpack-riscv-none-elf-gcc-15.2.0-1/bin/riscv-none-elf-as"
OBJCOPY="./xpack-riscv-none-elf-gcc-15.2.0-1/bin/riscv-none-elf-objcopy"

SRC_CRT0="./test/crt0.s"
SRC_SYSCALLS="./test/syscalls.c"
SRC_TEST="./test/test.c"
LDSCRIPT="./test/linker.ld"

OBJ_CRT0="/tmp/crt0.o"
OBJ_SYSCALLS="/tmp/syscalls.o"
OBJ_TEST="/tmp/test.o"
ELF="/tmp/test_out.elf"
BIN="/tmp/test_out.bin"

echo "Assembling crt0..."
$AS -march=rv32i -mabi=ilp32 "$SRC_CRT0" -o "$OBJ_CRT0"

echo "Compiling syscalls..."
$GCC -c -O1 -march=rv32i -mabi=ilp32 "$SRC_SYSCALLS" -o "$OBJ_SYSCALLS"

echo "Compiling test..."
$GCC -c -O1 -march=rv32i -mabi=ilp32 "$SRC_TEST" -o "$OBJ_TEST"

echo "Linking (with newlib via -lc -lgcc)..."
$GCC -march=rv32i -mabi=ilp32 -nostartfiles \
  -T "$LDSCRIPT" \
  -o "$ELF" "$OBJ_CRT0" "$OBJ_SYSCALLS" "$OBJ_TEST" -lc -lgcc

echo "Converting to binary..."
$OBJCOPY -O binary "$ELF" "$BIN"

echo "Converting to hex..."
python convert.py "$BIN"
cp "${BIN%.bin}.hex" ./last_test_build.hex
echo "Saved a copy of the hex for inspection: ./last_test_build.hex"

echo "Loading program..."
python load_program.py "${BIN%.bin}.hex" --port /dev/ttyUSB1

echo "Cleaning up..."
rm -f "$OBJ_CRT0" "$OBJ_SYSCALLS" "$OBJ_TEST" "$ELF" "$BIN"

echo "Done."