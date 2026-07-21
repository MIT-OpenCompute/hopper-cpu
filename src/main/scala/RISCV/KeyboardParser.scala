
package RISCV
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage


class UartKeyboardTracker(val clockFreq: Int, val baudRate: Int) extends Module {
  val io = IO(new Bundle {
    val rxd = Input(Bool())
    val keyDown = Output(UInt(256.W))
    val lastCode = Output(UInt(8.W)) 
    val lastPressed = Output(Bool())    
    val eventValid = Output(Bool())    
    val numDown = Output(UInt(9.W)) 
  })

  val uart = Module(new UartRx(clockFreq, baudRate))
  uart.io.rxd := io.rxd

  val keyState = RegInit(VecInit(Seq.fill(256)(false.B)))
  io.keyDown := keyState.asUInt
  io.numDown := PopCount(keyState.asUInt)

  
  val lastCodeReg = RegInit(0.U(8.W))
  val lastPressedReg = RegInit(false.B)
  val eventValidReg  = RegInit(false.B)
  io.lastCode    := lastCodeReg
  io.lastPressed := lastPressedReg
  io.eventValid  := eventValidReg
  eventValidReg := false.B 

  
  val sWaitType :: sWaitCode :: Nil = Enum(2)
  val state = RegInit(sWaitType)
  val eventType = RegInit(false.B)

  switch(state) {
    is(sWaitType) {
      when(uart.io.valid) {
        eventType := uart.io.data(0) 
        state := sWaitCode
      }
    }
    is(sWaitCode) {
      when(uart.io.valid) {
        val code = uart.io.data
        keyState(code) := eventType
        lastCodeReg := code
        lastPressedReg := eventType
        eventValidReg := true.B
        state := sWaitType
      }
    }
  }
}            io.line_valid,

object UartKeyboardTracker extends App {
    ChiselStage.emitSystemVerilogFile(
      new UartKeyboardTracker(100000000,115200),
      firtoolOpts = Array(
        "-disable-all-randomization",
        "-strip-debug-info",
        "-default-layer-specialization=enable"
      ),
      args = Array("--target-dir", "generated")
    )
}