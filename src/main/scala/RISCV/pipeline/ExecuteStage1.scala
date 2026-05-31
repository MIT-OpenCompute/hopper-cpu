package RISCV

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import scala.math._

class ExecuteStage1() extends Module {
    val io = IO(new Bundle {
		val instruction = Input(new InstructionBundle())
		val rs1 = Input(UInt(32.W))
		val rs2 = Input(UInt(32.W))
		val instruction_pointer = Input(UInt(32.W))
		val valid = Input(Bool())

		val next_instruction = Output(new InstructionBundle())
		val out = Output(UInt(32.W))
		val next_valid = Output(Bool())

		val program_pointer_jump_flush = Output(Bool())
		val program_pointer_target = Output(UInt(32.W))
    })

	val instruction = RegInit(0.U.asTypeOf(new InstructionBundle()))
	instruction := io.instruction
	io.next_instruction := instruction

	io.out := 0.U
	io.program_pointer_jump_flush := false.B
	io.program_pointer_target := 0.U

	when(io.valid) {
		switch(io.instruction.opcode) {
			is("b000_0010011".U) { // ADDI
				io.out := io.rs1 + io.instruction.immediate;

				// TODO: Temporary test
				io.program_pointer_jump_flush := true.B
				io.program_pointer_target := 0.U
			}
		}
	}

	// TODO: trigger flush when JAL or JALR or any jump

	val valid = RegInit(false.B)
	valid := io.valid
	io.next_valid := valid
}