package RISCV

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

class InstructionDispatchQueue() extends Module {
    val io = IO(new Bundle {
        val instruction = Input(UInt(32.W))
        val rs1 = Output(UInt(5.W))
        val rs2 = Output(UInt(5.W))
        val rd = Output(UInt(5.W))
        val immediate = Output(UInt(32.W))
        val opcode = Output(UInt(7.W))
        val func3 = Output(UInt(3.W))
        val func7 = Output(UInt(7.W))
    })
}
