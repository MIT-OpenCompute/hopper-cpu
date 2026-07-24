package RISCV

import chisel3._
import _root_.circt.stage.ChiselStage
import scala.math._

class FetchStage() extends Module {
    val io = IO(new Bundle {
        val execute = Input(Bool())
        val program_pointer = Input(UInt(32.W))
        val memory_read_value = Input(UInt(32.W))
        val memory_read_valid = Input(Bool())

        val instruction = Output(UInt(32.W))
        val next_instruction_pointer = Output(UInt(32.W))
        val next_valid = Output(Bool())

        val flush = Input(Bool())

        val next_ready = Input(Bool())
    })

    val next_instruction = RegInit(0.U(32.W))
    val next_instruction_pointer = RegInit(0.U(32.W))
    val valid = RegInit(false.B)

    when(io.next_ready) {
        next_instruction := io.memory_read_value
        next_instruction_pointer := io.program_pointer
        valid := io.execute && io.memory_read_valid && !io.flush
    }

    io.instruction := next_instruction
    io.next_instruction_pointer := next_instruction_pointer
    io.next_valid := valid
}
