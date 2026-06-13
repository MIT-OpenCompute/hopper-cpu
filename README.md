# MIT OpenCompute RISC-V Project

This is the MIT OpenCompute Laboratory’s RISC-V Project.  
We are designing a 32-bit CPU that implements the RV32I RISC-V ISA.

---

## Project Structure

CPU HDL files are stored in: ```main\scala\RISCV```


### ALU.scala

Defines the Arithmetic Logic Unit (ALU) hardware.

The ALU takes two operands, `a` and `b`, and sends the result of the operation to `output`.  
It also accepts two control inputs:
- A 4-bit operation code.
- A sign-flag boolean, used only for comparisons.

#### Operation Codes

| Code  | Operation               |
|-------|--------------------------|
| 0000  | Addition                |
| 0001  | Multiplication          |
| 0010  | Comparison (gt, eq, lt) |
| 0011  | Bitwise AND             |
| 0100  | Bitwise OR              |
| 0101  | Bitwise XOR             |
| 0110  | Bitwise NOT (outputs NOT a) |
| 0111  | Logical shift left      |
| 1000  | Logical shift right     |
| 1001  | Arithmetic shift right  |

The sign flag determines whether operands are treated as signed or unsigned values for comparison.

---

### Decoder.scala

Implements the RISC-V instruction decoder that extracts key fields from a 32-bit instruction and formats them for downstream units such as the ALU, register file, or control logic.

The decoder:
- Identifies the instruction format (R, I, S, B, U, or J) based on the opcode (bits 6–0).
- Outputs:
  - `rs1`, `rs2`, and `rd` register indices
  - The operation code (`operation`)
  - The immediate value (`immediate`)

For each format, the corresponding immediate field is assembled and sign-extended (or zero-filled) to 32 bits by left-shifting the extracted bits and padding with zeros as needed.  
This ensures that all immediates output by the decoder are consistently 32 bits wide, ready for arithmetic or address calculations without additional shifting logic later in the datapath.

---

### Registers.scala

Defines the register file hardware.

The CPU register file supports:
- Dual independent combinational reads.
- A single independent synchronous write.

This allows two registers to be read in one clock cycle while a third register is written simultaneously.

The module also includes a third, debugging-only read port called the **C port**.  
This port is used exclusively for reading register values from testbenches.  
It is not part of the actual hardware and should not be used in the CPU datapath.

---

## Running Testbenches

This project uses `sbt`.

To run a specific testbench, such as `ALUSpec.scala`, execute:
```bash
sbt "testOnly RISCV.ALUSpec"
```

## Writing a Testbench

Use the following template as a guide for creating new testbenches:

```
package RISCV

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class MainTemplateSpec extends AnyFreeSpec with Matchers with ChiselSim {

  "Main should execute an instruction correctly (template)" in {
    simulate(new Main()) { dut =>
      // 1. Initialize registers (example: write value to x1)
      dut.io.write_enable.poke(true.B)
      dut.io.write_address.poke(1.U)
      dut.io.in.poke(42.U)
      dut.clock.step(1)
      dut.io.write_enable.poke(false.B)

      // 2. Load an instruction (replace with your own encoding)
      val instr = "b000000000000_00000_000_00000_0000000".U(32.W)
      dut.io.instruction.poke(instr)

      // 3. Step one clock to execute
      dut.clock.step(1)

      // 4. Read back a register value to verify the result
      //    Always use the C port of the register file for reading.
      dut.io.read_address_c.poke(1.U)   // Which register to read
      dut.io.out_c.expect(42.U)      // Expected result (example)
    }
  }
}
```

## Compile Verilator
cd simulation
verilator --cc --sv --exe --build -j 0 -CFLAGS "-I./obj_dir" -Wall vga-image.cpp VGAController.sv
./obj_dir/VVGAController
nix-shell -p imagemagick --run "convert frame.ppm frame.png"

.\xpack-riscv-none-elf-gcc-15.2.0-1\bin\riscv-none-elf-gcc.exe -S -O0 -march=rv32i -mabi=ilp32 ./programs/hello.c -o ./programs/hello.s

## Compiling a C program

.\xpack-riscv-none-elf-gcc-15.2.0-1\bin\riscv-none-elf-gcc.exe -c -O0 -march=rv32i -mabi=ilp32 ./programs/hello.c -o ./programs/hello.o

.\xpack-riscv-none-elf-gcc-15.2.0-1\bin\riscv-none-elf-gcc.exe -march=rv32i -mabi=ilp32 -nostdlib "-Wl,--section-start=.text=0x0,--entry=_start" -o ./programs/hello.elf ./programs/hello.o

.\xpack-riscv-none-elf-gcc-15.2.0-1\bin\riscv-none-elf-objcopy.exe -O binary ./programs/hello.elf ./programs/hello.bin

python convert.py

python.exe .\load_program.py .\programs\test.hex --port COM6

## New VGA Testing
```
cd generated
verilator --cc --exe --build -j 0 ../simulation/vga-image.cpp -f filelist.f --top VGAController
./obj_dir/VVGAController
ffmpeg -i frame.ppm frame.png -y
```