package RISCV


import chisel3._
import chisel3.util._

class UartRx(val clockFreq: Int, val baudRate: Int) extends Module {
  val io = IO(new Bundle {
    val rxd   = Input(Bool())
    val data  = Output(UInt(8.W))
    val valid = Output(Bool())
  })

  val cyclesPerBit = (clockFreq / baudRate).U
  val halfBit      = (clockFreq / baudRate / 2).U

  val counter  = RegInit(0.U(16.W))
  val bitIdx   = RegInit(0.U(4.W))
  val shiftReg = RegInit(0.U(8.W))
  val dataReg  = RegInit(0.U(8.W))
  val validReg = RegInit(false.B)

  val sIdle :: sStart :: sData :: sStop :: Nil = Enum(4)
  val state = RegInit(sIdle)

  val rxSync = RegNext(RegNext(io.rxd, true.B), true.B)

  io.data  := dataReg
  io.valid := validReg
  validReg := false.B

  switch(state) {
    is(sIdle) {
      when(!rxSync) {
        counter := halfBit - 1.U
        state   := sStart
      }
    }

    is(sStart) {
      when(counter === 0.U) {
        when(!rxSync) {
          counter := cyclesPerBit - 1.U
          bitIdx  := 0.U
          state   := sData
        }.otherwise {
          state := sIdle
        }
      }.otherwise {
        counter := counter - 1.U
      }
    }

    is(sData) {
      when(counter === 0.U) {
        shiftReg := Cat(rxSync, shiftReg(7, 1))
        counter  := cyclesPerBit - 1.U
        bitIdx   := bitIdx + 1.U
        when(bitIdx === 7.U) {
          state := sStop
        }
      }.otherwise {
        counter := counter - 1.U
      }
    }

    is(sStop) {
      when(counter === 0.U) {
        when(rxSync) {
          dataReg  := shiftReg
          validReg := true.B
        }
        state := sIdle
      }.otherwise {
        counter := counter - 1.U
      }
    }
  }
  when(io.valid) {
    printf(p"Byte Recieved: ${io.data}\n")
}
}