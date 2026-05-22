package RISCV

import chisel3._
import _root_.circt.stage.ChiselStage
import scala.math._

class ReadStage() extends Module {
    val io = IO(new Bundle {
		val instruction = Input(new InstructionBundle())

		val register_read_a = Output(UInt(5.W))
		val register_value_a = Input(UInt(32.W))
		val register_read_b = Output(UInt(5.W))
		val register_value_b = Input(UInt(32.W))

		val next_instruction = Output(new InstructionBundle())
		val out_a = Output(UInt(32.W))
		val out_b = Output(UInt(32.W))
    })
	
	val instruction = RegInit(0.U.asTypeOf(new InstructionBundle()))
	instruction := io.instruction
	io.next_instruction := instruction

	val out_a = RegInit(0.U(32.W))
	val out_b = RegInit(0.U(32.W))
	out_a := io.register_value_a
	out_b := io.register_value_b

	io.out_a := out_a
	io.out_b := out_b

	io.register_read_a := io.instruction.rs1
	io.register_read_b := io.instruction.rs2
}