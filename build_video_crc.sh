#!/bin/bash
set -e

GCC="./xpack-riscv-none-elf-gcc-15.2.0-1/bin/riscv-none-elf-gcc"
AS="./xpack-riscv-none-elf-gcc-15.2.0-1/bin/riscv-none-elf-as"
OBJCOPY="./xpack-riscv-none-elf-gcc-15.2.0-1/bin/riscv-none-elf-objcopy"
OBJDUMP="./xpack-riscv-none-elf-gcc-15.2.0-1/bin/riscv-none-elf-objdump"

SRC_C="./video/video_player.c"
SRC_S="./video/video_data.s"

OBJ_C="/tmp/video_player.o"
OBJ_S="/tmp/video_data.o"
ELF="/tmp/video_out.elf"
BIN="/tmp/video_out.bin"           # raw, unpatched
BIN_P="/tmp/video_out_crc.bin"     # padded + CRC patched
HEX="/tmp/video_out.hex"           # generated FROM the patched bin

echo "Compiling firmware..."
$GCC -c -O1 -march=rv32i -mabi=ilp32 \
     -ffreestanding -fno-delete-null-pointer-checks \
     "$SRC_C" -o "$OBJ_C"

echo "Assembling video data..."
$AS -march=rv32i -mabi=ilp32 "$SRC_S" -o "$OBJ_S"

echo "Linking..."
$GCC -march=rv32i -mabi=ilp32 -nostdlib \
     -Wl,--section-start=.text=0x0,--entry=_start \
     -o "$ELF" "$OBJ_C" "$OBJ_S" -lgcc

echo "Sanity: no compiler-inserted traps, _start at 0..."
if $OBJDUMP -d "$ELF" | grep -qw "unimp"; then
    echo "FATAL: unimp instruction in ELF (null-pointer UB got compiled in)"; exit 1
fi
$OBJDUMP -t "$ELF" | grep -w "_start" | grep -q "^00000000" \
    || { echo "FATAL: _start is not at address 0"; exit 1; }

echo "Converting to binary..."
$OBJCOPY -O binary "$ELF" "$BIN"

echo "Padding + patching CRC (also emits the hex)..."
python3 patch_crc.py "$BIN" "$BIN_P" "$HEX"

cp "$HEX" ./last_build.hex
echo "Saved patched hex: ./last_build.hex  (this is what the sim must load too)"

echo "Loading program..."
python3 load_program.py "$HEX" --port /dev/ttyUSB1

echo "Cleaning up..."
rm -f "$OBJ_C" "$OBJ_S" "$ELF"

echo "Done."