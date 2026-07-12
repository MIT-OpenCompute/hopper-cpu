
package RISCV
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

/** ---------------------------------------------------------------------
 *  UartKeyboardTracker
 *
 *  Consumes a simple 2-byte-per-event protocol over UART and maintains a
 *  live press/release table for every HID keycode (0-255):
 *
 *    byte 0 (event type): 0x01 = key DOWN, 0x00 = key UP
 *    byte 1 (keycode)   : HID usage ID, 0-255 (e.g. 0x04='a', 0xE0=LeftCtrl)
 *
 *  So to report "key 0x04 pressed" you send the two bytes [0x01, 0x04];
 *  to report it released, [0x00, 0x04]. keyDown(0x04) stays true the
 *  entire time the key is held, exactly like a real keyboard matrix scan
 *  -- this is the actual state-tracking piece, not just an echo of the
 *  last byte received.
 *
 *  Assumes your existing UartRx module (from your message) is available
 *  in this package with the same IO -- it is NOT redefined here, just
 *  instantiated.
 *  ------------------------------------------------------------------- */
class UartKeyboardTracker(val clockFreq: Int, val baudRate: Int) extends Module {
  val io = IO(new Bundle {
    val rxd = Input(Bool())
    val keyDown = Output(UInt(256.W))
    val lastCode    = Output(UInt(8.W)) // keycode of the most recent event
    val lastPressed = Output(Bool())    // true if that event was a press
    val eventValid  = Output(Bool())    // one-cycle pulse on every event
    val numDown     = Output(UInt(9.W)) // how many keys are currently held (0-256)
  })

  val uart = Module(new UartRx(clockFreq, baudRate))
  uart.io.rxd := io.rxd

  val keyState = RegInit(VecInit(Seq.fill(256)(false.B)))
  io.keyDown := keyState.asUInt
  io.numDown := PopCount(keyState.asUInt)

  
  val lastCodeReg    = RegInit(0.U(8.W))
  val lastPressedReg = RegInit(false.B)
  val eventValidReg  = RegInit(false.B)
  io.lastCode    := lastCodeReg
  io.lastPressed := lastPressedReg
  io.eventValid  := eventValidReg
  eventValidReg := false.B 

  
  val sWaitType :: sWaitCode :: Nil = Enum(2)
  val state     = RegInit(sWaitType)
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
        lastCodeReg     := code
        lastPressedReg  := eventType
        eventValidReg   := true.B
        state := sWaitType
      }
    }
  }
}
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