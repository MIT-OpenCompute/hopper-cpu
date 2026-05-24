package RISCV

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import scala.math._

class WriteStage() extends Module {
    val io = IO(new Bundle {
		val instruction = Input(new InstructionBundle())
		val value = Input(UInt(32.W))
		val valid = Input(Bool())

		val register_write = Output(Bool())
		val register_address = Output(UInt(5.W))
		val register_value = Output(UInt(32.W))
		val next_valid = Output(Bool())
    })

	io.register_write := false.B;
	io.register_address := 0.U;
	io.register_value := 0.U;

	when(io.valid) {
		switch(io.instruction.opcode) {
			is("b000_0010011".U) {
				io.register_write := true.B;
				io.register_address := io.instruction.rd;
				io.register_value := io.value;
			}
		}
	}

	val valid = RegInit(false.B)
	valid := io.valid
	io.next_valid := valid
}