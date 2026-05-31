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

	val out = RegInit(0.U)
	out := 0.U
	io.out := out

	io.program_pointer_jump_flush := false.B
	io.program_pointer_target := 0.U

	when(io.valid) {
		switch(io.instruction.opcode) {
			// LUI
			is("b0110111".U) {
				out := io.instruction.immediate;				
			}

			// AUIPC
			is("b0010111".U) {
				out := io.instruction_pointer + io.instruction.immediate;				
			}

			// SLTI
			is("b010_0010011".U) {
				when(io.rs1.asSInt < io.instruction.immediate.asSInt) {
					out := 1.U;
				}.otherwise {
					out := 0.U;
				}
			}

			// SLTIU
			is("b011_0010011".U) {
				when(io.rs1 < io.instruction.immediate) {
					out := 1.U;
				}.otherwise {
					out := 0.U;
				}
			}

			// XORI
			is("b100_0010011".U) {
				out := io.rs1 ^ io.instruction.immediate
			}

			// XORI
			is("b110_0010011".U) {
				out := io.rs1 | io.instruction.immediate
			}

			// ANDI
			is("b111_0010011".U) {
				out := io.rs1 & io.instruction.immediate
			}

			// ADD
			is("b0000000_000_0110011".U) {
				out := io.rs1 + io.rs2
			}

			// SUB
			is("b0110000_000_0110011".U) {
				out := io.rs1 - io.rs2
			}

			// SLLI
			is("b001_0010011".U) {
				out := io.rs1 << io.instruction.immediate(5, 0)
			}

			// SRLI and SRAI
			is("b101_0010011".U) {
				when(io.instruction.immediate(10) === 1.U) { // SRAI
					out := (io.rs1.asSInt >> io.instruction.immediate(5, 0)).asUInt
				}.otherwise { // SLAI
					out := io.rs1 >> io.instruction.immediate(5, 0)
				}
			}

			// SLL
			is("b001_0110011".U) {
				out := io.rs1 << io.rs2(5, 0)
			}

			// SRL and SRA
			is("b101_0110011".U) {
				when(io.instruction.immediate(10) === 1.U) { // SRA
					out := (io.rs1.asSInt >> io.rs2(5, 0)).asUInt
				}.otherwise { // SLA
					out := io.rs1 >> io.rs2(5, 0)
				}
			}

			// SLT
			is("b010_0110011".U) {
				when(io.rs1.asSInt < io.rs2.asSInt) {
					out := 1.U;
				}.otherwise {
					out := 0.U;
				}
			}

			// SLTU
			is("b011_0110011".U) {
				when(io.rs1 < io.rs2) {
					out := 1.U;
				}.otherwise {
					out := 0.U;
				}
			}

			// XOR
			is("b100_0110011".U) {
				out := io.rs1 ^ io.rs2
			}

			// OR
			is("b110_0110011".U) {
				out := io.rs1 | io.rs2
			}

			// AND
			is("b111_0110011".U) {
				out := io.rs1 | io.rs2
			}

			// JAL
			is("b1101111".U) {
				out := instruction_pointer + 4.U

				io.program_pointer_jump_flush := true.B
				io.program_pointer_target := (instruction_pointer + io.instruction.immediate.asSInt).asUInt
			}

			// JALR
			is("b000_1100111".U) {
				out := instruction_pointer + 4.U

				io.program_pointer_jump_flush := true.B
				io.program_pointer_target := (io.rs1 + io.instruction.immediate.asSInt).asUInt & 0xFFFFFFFEL.U
			}

			// BEQ
			is("b000_110011".U) {
				when(io.rs1 === io.rs2) {
					io.program_pointer_jump_flush := true.B
					io.program_pointer_target := (instruction_pointer + io.instruction.immediate.asSInt).asUInt
				}
			}

			// BNEQ
			is("b001_110011".U) {
				when(io.rs1 =/= io.rs2) {
					io.program_pointer_jump_flush := true.B
					io.program_pointer_target := (instruction_pointer + io.instruction.immediate.asSInt).asUInt
				}
			}

			// BLT
			is("b100_110011".U) {
				when(io.rs1.asSInt < io.rs2.asSInt) {
					io.program_pointer_jump_flush := true.B
					io.program_pointer_target := (instruction_pointer + io.instruction.immediate.asSInt).asUInt
				}
			}

			// BLTU
			is("b110_110011".U) {
				when(io.rs1 < io.rs2) {
					io.program_pointer_jump_flush := true.B
					io.program_pointer_target := (instruction_pointer + io.instruction.immediate.asSInt).asUInt
				}
			}

			// BGE
			is("b101_110011".U) {
				when(io.rs1.asSInt >= io.rs2.asSInt) {
					io.program_pointer_jump_flush := true.B
					io.program_pointer_target := (instruction_pointer + io.instruction.immediate.asSInt).asUInt
				}
			}

			// BGEU
			is("b111_110011".U) {
				when(io.rs1 >= io.rs2) {
					io.program_pointer_jump_flush := true.B
					io.program_pointer_target := (instruction_pointer + io.instruction.immediate.asSInt).asUInt
				}
			}
		}
	}

	val valid = RegInit(false.B)
	valid := io.valid
	io.next_valid := valid
}