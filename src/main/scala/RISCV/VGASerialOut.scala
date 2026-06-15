package RISCV


import chisel3._
import chisel3.util._



class VgaSerialOut(val clockFreq: Int, val baudRate: Int,
                   val W: Int = 320, val H: Int = 240) extends Module {
  val io = IO(new Bundle {
    val memAddr    = Output(UInt(17.W))
    val memData    = Input(UInt(8.W))
    val vsyncPulse = Input(Bool())
    val txData     = Output(UInt(8.W))
    val txValid    = Output(Bool())
    val txReady    = Input(Bool())
  })

  val PIXELS = (W * H)

  val idle :: preload :: sending :: Nil = Enum(3)
  val state    = RegInit(idle)
  val pixelIdx = RegInit(0.U(17.W))

  // defaults
  io.memAddr := pixelIdx
  io.txData  := io.memData
  io.txValid := false.B

  switch(state) {

    is(idle) {
      when(io.vsyncPulse) {
        pixelIdx := 0.U
        state    := preload
      }
    }

    // one cycle for SyncReadMem to respond to address 0
    is(preload) {
      io.memAddr := 0.U
      state      := sending
    }

    is(sending) {
      io.memAddr := pixelIdx
      when(io.txReady) {
        io.txValid := true.B
        when(pixelIdx === (PIXELS - 1).U) {
          pixelIdx := 0.U
          state    := idle
        }.otherwise {
          pixelIdx := pixelIdx + 1.U
        }
      }
    }
  }
}