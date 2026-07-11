package RISCV


import chisel3._
import chisel3.util._


class UartTx(val clockFreq: Int, val baudRate: Int) extends Module {
  val io = IO(new Bundle {
    val data  = Input(UInt(8.W))
    val valid = Input(Bool())
    val ready = Output(Bool())
    val txd   = Output(Bool())
  })

  val cyclesPerBit = (clockFreq / baudRate).U
  val counter      = RegInit(0.U(16.W))
  val bitIdx       = RegInit(0.U(4.W))
  val shiftReg     = RegInit(0.U(10.W))
  val busy         = RegInit(false.B)

  io.ready := !busy
  io.txd   := true.B
 when(io.valid & !busy) {
    // printf(p"Sending Out: ${io.data}\n")
  }
  when(busy) {
    io.txd := shiftReg(0)
    when(counter === 0.U) {
      counter  := cyclesPerBit - 1.U
      shiftReg := shiftReg >> 1
      bitIdx   := bitIdx + 1.U
      when(bitIdx === 9.U) {
        busy := false.B
      }
    }.otherwise {
      counter := counter - 1.U
    }
  }.elsewhen(io.valid) {
    shiftReg := Cat(1.U(1.W), io.data, 0.U(1.W))
    busy     := true.B
    counter  := cyclesPerBit - 1.U
    bitIdx   := 0.U
    io.txd   := false.B  
  }
 
}