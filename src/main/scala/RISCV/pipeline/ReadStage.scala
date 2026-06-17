package RISCV

import chisel3._
import _root_.circt.stage.ChiselStage
import scala.math._

class ReadStage() extends Module {
    val io = IO(new Bundle {
		val instruction = Input(new InstructionBundle())
		val instruction_pointer = Input(UInt(32.W))
		val valid = Input(Bool())

		val register_read_a = Output(UInt(5.W))
		val register_value_a = Input(UInt(32.W))
		val register_read_b = Output(UInt(5.W))
		val register_value_b = Input(UInt(32.W))

		val next_instruction = Output(new InstructionBundle())
		val next_instruction_pointer = Output(UInt(32.W))
		val next_valid = Output(Bool())

		val flush = Input(Bool())
		val stall = Input(Bool())

		val raw_hazard_flush = Output(Bool())
		val program_pointer_target = Output(UInt(32.W))

		val out_a = Output(UInt(32.W))
		val out_b = Output(UInt(32.W))
    })

	val rd_0 = RegInit(0.U(5.W))
	val rd_1 = RegInit(0.U(5.W))

	rd_0 := 0.U

	when(io.valid && !io.stall) {
		rd_0 := io.instruction.rd
	}

	rd_1 := Mux(io.stall, rd_1, rd_0)

	val raw_hazard_flush = io.valid && ((rd_0 =/= 0.U && (rd_0 === io.instruction.rs1 || rd_0 === io.instruction.rs2)) || (rd_1 =/= 0.U && (rd_1 === io.instruction.rs1 || rd_1 === io.instruction.rs2)))
	io.raw_hazard_flush := raw_hazard_flush
	io.program_pointer_target := 0.U

	when(raw_hazard_flush) {
		io.program_pointer_target := io.instruction_pointer
	}
	
	val instruction = RegInit(0.U.asTypeOf(new InstructionBundle()))
	instruction := io.instruction
	io.next_instruction := instruction

	val out_a = RegInit(0.U(32.W))
	val out_b = RegInit(0.U(32.W))
	out_a := Mux(io.stall, out_a, io.register_value_a)
	out_b := Mux(io.stall, out_b, io.register_value_b)

	io.out_a := out_a
	io.out_b := out_b

	io.register_read_a := io.instruction.rs1
	io.register_read_b := io.instruction.rs2

	val next_instruction_pointer = RegInit(0.U(32.W))
	next_instruction_pointer := Mux(io.stall, next_instruction_pointer, io.instruction_pointer)
	io.next_instruction_pointer := next_instruction_pointer

	val valid = RegInit(false.B)
	valid := Mux(io.stall, valid, io.valid && !io.flush)
	io.next_valid := valid
}