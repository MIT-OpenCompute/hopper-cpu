./xpack-riscv-none-elf-gcc-15.2.0-1/bin/riscv-none-elf-gcc -c -O0 -march=rv32i -mabi=ilp32 ./programs/hello.c -o ./programs/hello.o

./xpack-riscv-none-elf-gcc-15.2.0-1/bin/riscv-none-elf-gcc -march=rv32i -mabi=ilp32 -nostdlib "-Wl,--section-start=.text=0x0,--entry=_start" -o ./programs/hello.elf ./programs/hello.o

./xpack-riscv-none-elf-gcc-15.2.0-1/bin/riscv-none-elf-objcopy -O binary ./programs/hello.elf ./programs/hello.bin

python convert.py ./programs/hello.bin ./programs/hello.hex || exit 1

rm ./programs/hello.bin
rm ./programs/hello.elf
rm ./programs/hello.o