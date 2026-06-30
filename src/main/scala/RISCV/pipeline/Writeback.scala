package RISCV
import chisel3._
import _root_.circt.stage.ChiselStage
import scala.math._
import chisel3.util._

class Writeback() extends Module {
  val io = IO(new Bundle {
    val instruction    = Input(Valid(new InstructionBundle()))
    val write_enable    = Output(Bool())
    val write_address   = Output(UInt(5.W))
    val write_val       = Output(UInt(32.W))

  })

  io.write_enable  := io.instruction.valid && io.instruction.bits.rd_wen
  io.write_address := io.instruction.bits.rd
  io.write_val     := io.instruction.bits.rd_val


}