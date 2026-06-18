package RISCV

import chisel3._
import _root_.circt.stage.ChiselStage
import scala.math._

class FetchStage() extends Module {
    val io = IO(new Bundle {
		val execute = Input(Bool())
		val program_pointer = Input(UInt(32.W))
		val memory_read_value = Input(UInt(32.W))

		val memory_read_address = Output(UInt(32.W))
		val instruction = Output(UInt(32.W))
		val next_instruction_pointer = Output(UInt(32.W))
		val next_valid = Output(Bool())

		val stall = Input(Bool())
		val flush = Input(Bool())
    })

	io.memory_read_address := io.program_pointer
	io.instruction := io.memory_read_value

	val next_instruction_pointer = RegInit(0.U(32.W))
	next_instruction_pointer := Mux(io.stall, next_instruction_pointer, io.program_pointer)
	io.next_instruction_pointer := next_instruction_pointer

	val valid = RegInit(false.B)
	// valid := io.execute && !io.flush && !io.stall
	valid := Mux(io.stall, valid, io.execute && !io.flush)
	io.next_valid := valid
}