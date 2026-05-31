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
		val next_valid = Output(Bool())

		val flush = Input(Bool())
    })

	io.memory_read_address := io.program_pointer
	io.instruction := io.memory_read_value

	val valid = RegInit(false.B)
	valid := io.execute
	io.next_valid := valid && !io.flush
}