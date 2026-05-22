package RISCV

import chisel3._
import _root_.circt.stage.ChiselStage
import scala.math._

class Core() extends Module {
    val io = IO(new Bundle {
		val execute = Input(Bool())

		val flash = Input(Bool())
		val flash_address = Input(UInt(32.W))
		val flash_value = Input(UInt(32.W))
    })

	val program_pointer = RegInit(0.U(32.W))

    val registers = Module(new Registers())
    registers.io.write_enable := false.B
    registers.io.write_address := 0.U(5.W)
    registers.io.read_address_a := 0.U(5.W)
    registers.io.read_address_b := 0.U(5.W)
    registers.io.read_address_c := 0.U(5.W)
    registers.io.in := 0.U(32.W)

	val memory = Module(new Memory())
	memory.io.address_1 := program_pointer
	memory.io.read_1 := true.B
	memory.io.write_1 := false.B
	memory.io.write_value_1 := 0.U

	memory.io.address_2 := 0.U
	memory.io.write_2 := 0.U
	memory.io.write_value_2 := 0.U
	memory.io.read_2 := 0.U

	memory.io.btns := 0.U

	val decode_stage = Module(new DecoderStage())
	decode_stage.io.instruction := memory.io.read_value_1

	val read_stage = Module(new ReadStage())
	read_stage.io.instruction := decode_stage.io.decoded
	read_stage.io.register_value_a := registers.io.out_a
	read_stage.io.register_value_b := registers.io.out_b
	registers.io.read_address_a := read_stage.io.register_read_a
    registers.io.read_address_b := read_stage.io.register_read_b

	val execute_stage_1 = Module(new ExecuteStage1())
	execute_stage_1.io.instruction := read_stage.io.next_instruction
	execute_stage_1.io.rs1 := read_stage.io.out_a
	execute_stage_1.io.rs2 := read_stage.io.out_b

	when(io.execute) {
		program_pointer := program_pointer + 4.U

		printf("\n\n\n=== Memory ===\n");
		printf("Program Pointer: %d\n", program_pointer);
		printf("Data: %b\n", memory.io.read_value_1);

		printf("=== Decode ===\n");
		printf("Opcode: %b\n", decode_stage.io.decoded.opcode);
		printf("Immediate: %b\n", decode_stage.io.decoded.immediate);
		printf("Rs1: %d\n", decode_stage.io.decoded.rs1);
		printf("Rs2: %d\n", decode_stage.io.decoded.rs2);
		printf("Rsd: %d\n", decode_stage.io.decoded.rd);

		printf("=== Read ===\n");
		printf("A: %b\n", read_stage.io.out_a);
		printf("B: %b\n", read_stage.io.out_b);

		printf("=== Execute 1 ===\n");
		printf("Out: %b\n", execute_stage_1.io.out);
	}.otherwise {
		printf("Loading...\n");

		when(io.flash) {
			memory.io.read_1 := false.B
			memory.io.write_1 := true.B
			memory.io.address_1 := io.flash_address
			memory.io.write_value_1 := io.flash_value
		}
	}
}