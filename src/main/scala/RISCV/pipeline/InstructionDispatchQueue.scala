package RISCV

import chisel3._
import _root_.circt.stage.ChiselStage
import scala.math._

class InstructionQueueEntry extends Bundle {
    val ready = Bool()
    val instruction = UInt(32.W)
    val memory_write = Bool()
    val rd = UInt(32.W)
    val rd_value = UInt(32.W)
}

class InstructionDispatchQueue() extends Module {
    val io = IO(new Bundle {

    })
}