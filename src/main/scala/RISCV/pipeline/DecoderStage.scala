package RISCV

import chisel3._
import _root_.circt.stage.ChiselStage
import scala.math._

class InstructionBundle extends Bundle {
	val rs1 = UInt(5.W)
	val rs2 = UInt(5.W)
	val rd = UInt(5.W)
	val immediate = UInt(32.W)
	val opcode = UInt(7.W)
	val func3 = UInt(3.W)
	val func7 = UInt(7.W)
}

class DecoderStage() extends Module {
    val io = IO(new Bundle {
		val instruction = Input(UInt(32.W))
		val valid = Input(Bool())

		val decoded = Output(new InstructionBundle())
		val next_valid = Output(Bool())
    })

	val decoder = Module(new Decoder())
	decoder.io.instruction := io.instruction

	val rs1 = RegInit(0.U(5.W))
	rs1 := decoder.io.rs1
	val rs2 = RegInit(0.U(5.W))
	rs2 := decoder.io.rs2
	val rd = RegInit(0.U(5.W))
	rd := decoder.io.rd
	val immediate = RegInit(0.U(32.W))
	immediate := decoder.io.immediate
	val opcode = RegInit(0.U(7.W))
	opcode := decoder.io.opcode
	val func3 = RegInit(0.U(3.W))
	func3 := decoder.io.func3
	val func7 = RegInit(0.U(7.W))
	func7 := decoder.io.func7

	io.decoded.rs1 := rs1
	io.decoded.rs2 := rs2
	io.decoded.rd := rd
	io.decoded.immediate := immediate
	io.decoded.opcode := opcode
	io.decoded.func3 := func3
	io.decoded.func7 := func7

	val valid = RegInit(false.B)
	valid := io.valid
	io.next_valid := valid
}