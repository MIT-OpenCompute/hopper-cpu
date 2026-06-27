package RISCV
import chisel3._
import _root_.circt.stage.ChiselStage
import scala.math._


class Writeback() extends Module {
  val io = IO(new Bundle {
    val instruction = Input(Valid(new InstructionBundle()))

    val write_enable = Output(Bool())
    val write_address = Output(UInt(5.W))
    val write_val = Output(UInt(32.W))

  })

  val rd  = RegNext(io.instruction.bits.rd)
  val rd_val = RegNext(io.instruction.bits.rd_val)
  val rd_wen = RegNext(io.instruction.bits.rd_wen)

  io.write_enable := false.B
  io.write_address := 0.U
  io.write_val := 0.U
  when(RegNext(io.instruction.valid)){
    io.write_enable := rd_wen
    io.write_address := rd
    io.write_val :=rd_val

  }