package RISCV

import chisel3._
import _root_.circt.stage.ChiselStage

/* 
 * The register file module for RISC-V
 * 32 registers, each 32 bits wide
 * Dual read ports, single write port
 * Register x0 is hardwired to 0
 * 
 * This register file supports a simultaneous combinational read from two registers via the two read addresses and output ports
 * as well as a synchronous write to one register on the rising edge of the clock when write_enable is high.
 * 
 * I/O:
    * in: 32-bit input data to write to register
    * write_address: 5-bit address of register to write to
    * read_address_a: 5-bit address of register to read from (port A)
    * read_address_b: 5-bit address of register to read from (port B)
    * write_enable: boolean signal to enable writing to register
    * out_a: 32-bit output data from read port A
    * out_b: 32-bit output data from read port B
 *
 */

class Registers() extends Module {
    val io = IO(new Bundle {
        val write_enable = Input(Bool())
        val write_address = Input(UInt(5.W))
        val in  = Input(UInt(32.W))

        val read_address_a = Input(UInt(5.W))
        val read_address_b = Input(UInt(5.W))
        
		val out_a = Output(UInt(32.W))
        val out_b = Output(UInt(32.W))

        val debug_1 = Output(UInt(32.W));
        val debug_2 = Output(UInt(32.W));
        val debug_3 = Output(UInt(32.W));
        val debug_4 = Output(UInt(32.W));
        val debug_5 = Output(UInt(32.W));
        val debug_6 = Output(UInt(32.W));
        val debug_7 = Output(UInt(32.W));
        val debug_8 = Output(UInt(32.W));
        val debug_9 = Output(UInt(32.W));
        val debug_10 = Output(UInt(32.W));
    })

    val regs = RegInit(VecInit(Seq.fill(32.toInt)(0.U(32.W))))

    // Dual read ports
    io.out_a := regs(io.read_address_a)
    io.out_b := regs(io.read_address_b)

    io.debug_1 := regs(1);
    io.debug_2 := regs(2);
    io.debug_3 := regs(3);
    io.debug_4 := regs(4);
    io.debug_5 := regs(5);
    io.debug_5 := regs(5);
    io.debug_6 := regs(6);
    io.debug_7 := regs(7);
    io.debug_8 := regs(8);
    io.debug_9 := regs(9);
    io.debug_10 := regs(10);

    // Uncomment to print the register contents every time they are accessed
    //printf("Regs: [%d]=%d, [%d]=%d, WE=%b, WA=%d, IN=%d\n", io.read_address_a, io.out_a, io.read_address_b, io.out_b, io.write_enable, io.write_address, io.in)

    // Single write port
    when (io.write_enable && (io.write_address =/= 0.U)) {
        regs(io.write_address) := io.in
    }
}
