#!/bin/bash
set -e

GCC="./xpack-riscv-none-elf-gcc-15.2.0-1/bin/riscv-none-elf-gcc"
AS="./xpack-riscv-none-elf-gcc-15.2.0-1/bin/riscv-none-elf-as"
OBJCOPY="./xpack-riscv-none-elf-gcc-15.2.0-1/bin/riscv-none-elf-objcopy"

SRC_C="video_player.c"
SRC_S="video_data.s"

OBJ_C="/tmp/video_player.o"
OBJ_S="/tmp/video_data.o"
ELF="/tmp/video_out.elf"
BIN="/tmp/video_out.bin"
HEX="/tmp/video_out.hex"

# video_data.s uses .incbin "video.bin" with a path relative to the
# assembler's working directory, so run this script from the folder
# that contains video_player.c, video_data.s, video_meta.h, video.bin.

echo "Compiling firmware..."
$GCC -c -O1 -march=rv32i -mabi=ilp32 "$SRC_C" -o "$OBJ_C"

echo "Assembling video data (embeds video.bin into the image)..."
$AS -march=rv32i -mabi=ilp32 "$SRC_S" -o "$OBJ_S"

echo "Linking..."
$GCC -march=rv32i -mabi=ilp32 -nostdlib \
    -Wl,--section-start=.text=0x0,--entry=_start \
    -o "$ELF" "$OBJ_C" "$OBJ_S" -lgcc

echo "Converting to binary..."
$OBJCOPY -O binary "$ELF" "$BIN"

echo "Converting to hex..."
python convert.py "$BIN" "$HEX"

echo "Loading program..."
python load_program.py "$HEX" --port /dev/ttyUSB1

echo "Cleaning up..."
rm -f "$OBJ_C" "$OBJ_S" "$ELF" "$BIN"

echo "Done."