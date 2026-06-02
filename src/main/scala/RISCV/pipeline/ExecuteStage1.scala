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
		val next_instruction_pointer = Output(UInt(32.W))
		val next_rs1 = Output(UInt(32.W))
		val next_rs2 = Output(UInt(32.W))
		val out = Output(UInt(32.W))
		val next_valid = Output(Bool())

		val program_pointer_jump_flush = Output(Bool())
		val program_pointer_target = Output(UInt(32.W))

		val memory_read = Output(Bool())
		val memory_read_address = Output(UInt(32.W))
    })

	val instruction = RegInit(0.U.asTypeOf(new InstructionBundle()))
	instruction := io.instruction
	io.next_instruction := instruction

	val next_instruction_pointer = RegInit(0.U(32.W))
	next_instruction_pointer := io.instruction_pointer
	io.next_instruction_pointer := next_instruction_pointer

	val rs1 = RegInit(0.U(32.W))
	rs1 := io.rs1
	io.next_rs1 := rs1

	val rs2 = RegInit(0.U(32.W))
	rs2 := io.rs2
	io.next_rs2 := rs2

	val out = RegInit(0.U(32.W))
	out := 0.U
	io.out := out

	io.program_pointer_jump_flush := false.B
	io.program_pointer_target := 0.U

	io.memory_read := false.B
	io.memory_read_address := 0.U

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

			is("b0010011".U) {
				switch(io.instruction.func3) {
					// ADDI
					is("b000".U) {
						out := io.rs1 + io.instruction.immediate
					}

					// SLLI
					is("b001".U) {
						out := io.rs1 << io.instruction.immediate(5, 0)
					}

					// SRLI and SRAI
					is("b101".U) {
						when(io.instruction.immediate(10) === 1.U) { // SRAI
							out := (io.rs1.asSInt >> io.instruction.immediate(5, 0)).asUInt
						}.otherwise { // SLAI
							out := io.rs1 >> io.instruction.immediate(5, 0)
						}
					}

					// SLTI
					is("b010".U) {
						when(io.rs1.asSInt < io.instruction.immediate.asSInt) {
							out := 1.U;
						}.otherwise {
							out := 0.U;
						}
					}

					// SLTIU
					is("b011".U) {
						when(io.rs1 < io.instruction.immediate) {
							out := 1.U;
						}.otherwise {
							out := 0.U;
						}
					}

					// XORI
					is("b100".U) {
						out := io.rs1 ^ io.instruction.immediate
					}

					// ORI
					is("b110".U) {
						out := io.rs1 | io.instruction.immediate
					}

					// ANDI
					is("b111".U) {
						out := io.rs1 & io.instruction.immediate
					}
				}
			}

			is("b0110011".U) {
				switch(io.instruction.func3) {
					// ADD
					is("b0000000".U) {
						out := io.rs1 + io.rs2
					}

					// SUB
					is("b0110000".U) {
						out := io.rs1 - io.rs2
					}

					// SLL
					is("b001".U) {
						out := io.rs1 << io.rs2(5, 0)
					}

					// SRL and SRA
					is("b101".U) {
						when(io.instruction.immediate(10) === 1.U) { // SRA
							out := (io.rs1.asSInt >> io.rs2(5, 0)).asUInt
						}.otherwise { // SLA
							out := io.rs1 >> io.rs2(5, 0)
						}
					}

					// SLT
					is("b010".U) {
						when(io.rs1.asSInt < io.rs2.asSInt) {
							out := 1.U;
						}.otherwise {
							out := 0.U;
						}
					}

					// SLTU
					is("b011".U) {
						when(io.rs1 < io.rs2) {
							out := 1.U;
						}.otherwise {
							out := 0.U;
						}
					}

					// XOR
					is("b100".U) {
						out := io.rs1 ^ io.rs2
					}

					// OR
					is("b110".U) {
						out := io.rs1 | io.rs2
					}

					// AND
					is("b111".U) {
						out := io.rs1 & io.rs2
					}
				}
			}

			// JAL
			is("b1101111".U) {
				out := io.instruction_pointer + 4.U

				io.program_pointer_jump_flush := true.B
				io.program_pointer_target := (io.instruction_pointer.zext + io.instruction.immediate.asSInt).asUInt
			}

			// JALR
			is("b1100111".U) {
				out := io.instruction_pointer + 4.U

				io.program_pointer_jump_flush := true.B
				io.program_pointer_target := (io.rs1.zext + io.instruction.immediate.asSInt).asUInt & ~1.U(32.W)
			}

			is("b1100011".U) {
				switch(io.instruction.func3) {
					// BEQ
					is("b000".U) {
						when(io.rs1 === io.rs2) {
							io.program_pointer_jump_flush := true.B
							io.program_pointer_target := (io.instruction_pointer.zext + io.instruction.immediate.asSInt).asUInt
						}
					}

					// BNEQ
					is("b001".U) {
						when(io.rs1 =/= io.rs2) {
							io.program_pointer_jump_flush := true.B
							io.program_pointer_target := (io.instruction_pointer.zext + io.instruction.immediate.asSInt).asUInt
						}
					}

					// BLT
					is("b100".U) {
						when(io.rs1.asSInt < io.rs2.asSInt) {
							io.program_pointer_jump_flush := true.B
							io.program_pointer_target := (io.instruction_pointer.zext + io.instruction.immediate.asSInt).asUInt
						}
					}

					// BLTU
					is("b110".U) {
						when(io.rs1 < io.rs2) {
							io.program_pointer_jump_flush := true.B
							io.program_pointer_target := (io.instruction_pointer.zext + io.instruction.immediate.asSInt).asUInt
						}
					}

					// BGE
					is("b101".U) {
						when(io.rs1.asSInt >= io.rs2.asSInt) {
							io.program_pointer_jump_flush := true.B
							io.program_pointer_target := (io.instruction_pointer.zext + io.instruction.immediate.asSInt).asUInt
						}
					}

					// BGEU
					is("b111".U) {
						when(io.rs1 >= io.rs2) {
							io.program_pointer_jump_flush := true.B
							io.program_pointer_target := (io.instruction_pointer.zext + io.instruction.immediate.asSInt).asUInt
						}
					}
				}
			}
			is("b0000011".U) {
				switch(io.instruction.func3) {
					// LW
					is("b010".U) {
						io.memory_read := true.B
						io.memory_read_address := (io.rs1.zext + io.instruction.immediate.asSInt).asUInt / 4.U
					}
				}
			}
		}
	}

	val valid = RegInit(false.B)
	valid := io.valid
	io.next_valid := valid
}