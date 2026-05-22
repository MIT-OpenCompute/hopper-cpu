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

		val next_instruction = Output(new InstructionBundle())
		val out = Output(UInt(32.W))
    })

	val instruction = RegInit(0.U.asTypeOf(new InstructionBundle()))
	instruction := io.instruction
	io.next_instruction := instruction

	io.out := 0.U

	switch(io.instruction.opcode) {
		is("b000_0010011".U) {
			io.out := io.rs1 + io.instruction.immediate;
		}
	}
}