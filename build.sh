#!/bin/bash
if [ -z "$1" ]; then
echo "Usage: $0 <source.c>"
exit 1
fi
SRC="$1"
BASENAME="${SRC%.*}"
GCC="./xpack-riscv-none-elf-gcc-15.2.0-1/bin/riscv-none-elf-gcc"
OBJ="/tmp/build_out.o"
ELF="/tmp/build_out.elf"
BIN="/tmp/build_out.bin"
HEX="/tmp/build_out.hex"
echo "Compiling $SRC..."
$GCC -c -O1 -march=rv32i -mabi=ilp32 "$SRC" -o "$OBJ" || exit 1
echo "Linking..."
$GCC -march=rv32i -mabi=ilp32 -nostdlib \
-Wl,--section-start=.text=0x0,--entry=_start \
-o "$ELF" "$OBJ" -lgcc || exit 1
echo "Converting to binary..."
./xpack-riscv-none-elf-gcc-15.2.0-1/bin/riscv-none-elf-objcopy \
-O binary "$ELF" "$BIN" || exit 1
echo "Converting to hex..."
python convert.py "$BIN" "$HEX" || exit 1
echo "Loading program..."
<<<<<<< HEAD
python load_program.py "$HEX" --port /dev/ttyUSB1
echo "Cleaning up..."
rm -f "$OBJ" "$ELF" "$BIN" #"$HEX"
echo "Done."
=======
python load_program.py "$HEX" --port /dev/ttyUSB3
echo "Cleaning up..."
rm -f "$OBJ" "$ELF" "$BIN" #"$HEX"
echo "Done."
>>>>>>> 9f3f1ccb74f58cfae7e2055ad46a2933e7b06a25
