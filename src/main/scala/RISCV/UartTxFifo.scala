package RISCV


import chisel3._
import chisel3.util._


class UartTxFifo(val clockFreq: Int, val baudRate: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(UInt(8.W)))
    val out = Output(Bool())
  })

  val fifo = Queue(io.in, entries = 256, flow = true)
  val uartTx = Module(new UartTx(clockFreq, baudRate)) 
  io.out := uartTx.io.txd

  uartTx.io.data := fifo.bits
  uartTx.io.valid := fifo.valid
  fifo.ready := uartTx.io.ready

  
 
}