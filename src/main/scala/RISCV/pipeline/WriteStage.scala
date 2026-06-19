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

		val stall = Input(Bool())
		// val rum = Output(UInt(32.W)) // register usage_map

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
			// LUI
			is("b0110111".U) {
				io.register_write := true.B;
				io.register_address := io.instruction.rd;
				io.register_value := io.value;
			}

			// AUIPC
			is("b0010111".U) {
				io.register_write := true.B;
				io.register_address := io.instruction.rd;
				io.register_value := io.value;
			}

			// SLTI, SLTIU, SLLI, SRLI, SRAI, XORI, ORI, ANDI
			is("b0010011".U) {
				io.register_write := true.B;
				io.register_address := io.instruction.rd;
				io.register_value := io.value;
			}

			// ADD, SUB, SLL, SRL, SRA, SLT, SLTU, XOR, OR, AND
			is("b0110011".U) {
				io.register_write := true.B;
				io.register_address := io.instruction.rd;
				io.register_value := io.value;
			}

			// JAL
			is("b1101111".U) {
				io.register_write := true.B;
				io.register_address := io.instruction.rd;
				io.register_value := io.value;
			}

			// JALR
			is("b1100111".U) {
				io.register_write := true.B;
				io.register_address := io.instruction.rd;
				io.register_value := io.value;
			}

			// LW
			is("b0000011".U) {
				io.register_write := true.B;
				io.register_address := io.instruction.rd;
				io.register_value := io.value;
			}
		}
	}


	val valid = RegInit(false.B)
	valid := Mux(io.stall, valid,  io.valid)
		// valid := io.valid && !io.stall

	io.next_valid := valid
}