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

		val memory_read_value = Input(UInt(32.W))
		val memory_write = Output(Bool())
		val memory_write_address = Output(UInt(32.W))
		val memory_write_value = Output(UInt(32.W))
    })

	val instruction = RegInit(0.U.asTypeOf(new InstructionBundle()))
	instruction := io.instruction
	io.next_instruction := instruction

	val out = RegInit(0.U(32.W))
	out := io.previous_out
	io.out := out

	io.memory_write := false.B
	io.memory_write_address := 0.U
	io.memory_write_value := 0.U

	when(io.valid) {
		switch(io.instruction.opcode) {
			is("b0000011".U) {
				switch(io.instruction.func3) {
					// LW
					is("b010".U) {
						out := io.memory_read_value
					}
				}
			}

			is("b0100011".U) {
				switch(io.instruction.func3) {
					// SW
					is("b010".U) {
						io.memory_write := true.B
						io.memory_write_address := (io.rs1.zext + io.instruction.immediate.asSInt).asUInt / 4.U
						io.memory_write_value := io.rs2
					}
				}
			}
		}
	}

	val valid = RegInit(false.B)
	valid := io.valid
	io.next_valid := valid
}