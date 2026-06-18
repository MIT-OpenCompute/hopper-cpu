package RISCV

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import scala.math._

class ExecuteStage2() extends Module {
    val io = IO(new Bundle {
		val instruction = Input(new InstructionBundle())
		val rs1 = Input(UInt(32.W))
		val rs2 = Input(UInt(32.W))
		val instruction_pointer = Input(UInt(32.W))
		val valid = Input(Bool())

		val previous_out = Input(UInt(32.W))
		val out = Output(UInt(32.W))
		val next_instruction = Output(new InstructionBundle())
		val next_valid = Output(Bool())

		val stall = Input(Bool())


		val memory_read_value = Input(UInt(32.W))
		val memory_write = Output(Bool())
		val memory_write_address = Output(UInt(32.W))
		val memory_write_value = Output(UInt(32.W))
    })

	val instruction = RegInit(0.U.asTypeOf(new InstructionBundle()))
	instruction := Mux(io.stall, instruction, io.instruction)
	io.next_instruction := instruction

	val out = RegInit(0.U(32.W))
	out := Mux(io.stall, out, io.previous_out)
	io.out := out

	io.memory_write := false.B
	io.memory_write_address := 0.U
	io.memory_write_value := 0.U

	when(io.valid) {
		switch(io.instruction.opcode) {
			is("b0000011".U) {
				switch(io.instruction.func3) {
					// LB
					is("b000".U) {
						val offset = (io.rs1.zext + io.instruction.immediate.asSInt).asUInt % 4.U

						switch(offset) {
							is(0.U) {
								out := io.memory_read_value & 0xFF.U
							}

							is(1.U) {
								out := io.memory_read_value >> 8.U & 0xFF.U
							}

							is(2.U) {
								out := io.memory_read_value >> 16.U & 0xFF.U
							}

							is(3.U) {
								out := io.memory_read_value >> 24.U & 0xFF.U
							}
						}
					}

					// LH
					is("b001".U) {
						when((io.rs1.zext + io.instruction.immediate.asSInt).asUInt % 4.U === 0.U) {
							out := io.memory_read_value & 0xFFFF.U
						}.otherwise {
							out := io.memory_read_value >> 16.U
						}
					}

					// LW
					is("b010".U) {
						out := io.memory_read_value
					}
				}
			}

			is("b0100011".U) {
				switch(io.instruction.func3) {
					// SB
					is("b000".U) {
						io.memory_write := true.B
						io.memory_write_address := (io.rs1.zext + io.instruction.immediate.asSInt).asUInt / 4.U
						
						switch((io.rs1.zext + io.instruction.immediate.asSInt).asUInt % 4.U) {
							is(0.U) {
								io.memory_write_value := io.memory_read_value(31, 8) ## io.rs2(7, 0)
							}

							is(1.U) {
								io.memory_write_value := io.memory_read_value(31, 16) ## io.rs2(15, 8) ## io.memory_read_value(7, 0)
							}

							is(2.U) {
								io.memory_write_value := io.memory_read_value(31, 24) ## io.rs2(23, 16) ## io.memory_read_value(15, 0)
							}

							is(3.U) {
								io.memory_write_value := io.rs2(31, 24) ## io.memory_read_value(23, 0)
							}
						}
					}

					// SH
					is("b001".U) {
						io.memory_write := true.B
						io.memory_write_address := (io.rs1.zext + io.instruction.immediate.asSInt).asUInt / 4.U
						
						when((io.rs1.zext + io.instruction.immediate.asSInt).asUInt % 4.U === 0.U) {
							io.memory_write_value := io.memory_read_value(31, 16) ## io.rs2(15, 0)
						}.otherwise {
							io.memory_write_value := io.rs2(31, 16) ## io.memory_read_value(15, 0)
						}
					}

					// SW
					is("b010".U) {
						printf("Strongingafdfsadfasdfasdf %d",io.rs2)
						io.memory_write := true.B
						io.memory_write_address := (io.rs1.zext + io.instruction.immediate.asSInt).asUInt / 4.U
						io.memory_write_value := io.rs2
					}
				}
			}
		}
	}

	val valid = RegInit(false.B)
	// valid := io.valid && !io.stall

	valid := Mux(io.stall, valid, io.valid)
	io.next_valid := valid
}