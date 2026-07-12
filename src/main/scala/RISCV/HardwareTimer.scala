package RISCV

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage



class HardwareTimer(val clockFreq: Int) extends Module {
    val io = IO(new Bundle {
        val micros  = Output(UInt(32.W))


    })

        val cyclesPerMicro = clockFreq / 1000000
        val cycleCounter = RegInit(0.U(32.W))
        val microsReg    = RegInit(0.U(32.W))

        io.micros := microsReg

        when(true.B) {
            when(cycleCounter === (cyclesPerMicro - 1).U) {
                cycleCounter := 0.U
                microsReg    := microsReg + 1.U
            }.otherwise {
                cycleCounter := cycleCounter + 1.U
            }
        }
        
}
 
