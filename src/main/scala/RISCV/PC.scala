package RISCV

import chisel3._
import _root_.circt.stage.ChiselStage
import scala.math._

class PC() extends Module {
    val io = IO(new Bundle {
        val pc_in = Input(UInt(32.W))
        val pc_out = Output(UInt(32.W))
    })
    val pc_reg = RegInit(0.U(32.W))
    pc_reg := io.pc_in
    io.pc_out := pc_reg
}