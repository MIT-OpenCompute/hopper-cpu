#!/bin/bash
if [ -z "$1" ]; then
    echo "Usage: $0 <source.c>"
    exit 1
fi
SRC="$1"
BASENAME="${SRC%.*}"

TOOLDIR="./xpack-riscv-none-elf-gcc-15.2.0-1/bin"
GCC="$TOOLDIR/riscv-none-elf-gcc"
OBJCOPY="$TOOLDIR/riscv-none-elf-objcopy"
OBJDUMP="$TOOLDIR/riscv-none-elf-objdump"

OBJ="/tmp/build_out.o"
ELF="/tmp/build_out.elf"
BIN="/tmp/build_out.bin"
HEX="/tmp/build_out.hex"
LDS="./link.ld"

echo "Compiling $SRC..."
$GCC -c -O1 -march=rv32i -mabi=ilp32 -ffreestanding "$SRC" -o "$OBJ" || exit 1

echo "Linking (with pinned boot section)..."
$GCC -march=rv32i -mabi=ilp32 -nostdlib -T "$LDS" \
    -o "$ELF" "$OBJ" -lgcc || exit 1

echo "Sanity: _start must be at address 0..."
$OBJDUMP -t "$ELF" | grep -w "_start" | grep -q "^00000000" \
    || { echo "FATAL: _start is not at address 0 — check .text.boot section tag"; exit 1; }

echo "Converting to binary..."
$OBJCOPY -O binary "$ELF" "$BIN" || exit 1

echo "Converting to hex..."
python convert.py "$BIN" "$HEX" || exit 1

echo "Loading program..."
python load_program.py "$HEX" --port /dev/ttyUSB1

echo "Cleaning up..."
rm -f "$OBJ" "$ELF" "$BIN" #"$HEX"
echo "Done."