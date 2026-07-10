package usbhost

import chisel3._
import chisel3.util._

/** ---------------------------------------------------------------------
 *  MAX3421E register addresses / bit positions.
 *  Cross-checked against the MAX3421E datasheet (Rev 4, 7/13), Table 2
 *  (host-mode register map) and Figure 5 (SPI command byte). Register
 *  addresses and the MODE/HCTL/HIRQ/USBIRQ bit positions below are
 *  taken directly from that table. HXFR token encodings are inferred
 *  from its bit layout (HS, ISO, OUTNIN, SETUP, EP[3:0]) plus common
 *  usage -- if a control transfer or the poll loop misbehaves, this is
 *  the first place to recheck against the MAX3421E Programming Guide.
 *  ------------------------------------------------------------------- */
object Max3421 {
  // Register addresses -> bits [7:3] of the SPI command byte
  val RCVFIFO  = 1.U(5.W)
  val SNDFIFO  = 2.U(5.W)
  val SUDFIFO  = 4.U(5.W)
  val RCVBC    = 6.U(5.W)
  val SNDBC    = 7.U(5.W)
  val USBIRQ   = 13.U(5.W)
  val USBIEN   = 14.U(5.W)
  val USBCTL   = 15.U(5.W)
  val CPUCTL   = 16.U(5.W)
  val PINCTL   = 17.U(5.W)
  val REVISION = 18.U(5.W)
  val HIRQ     = 25.U(5.W)
  val HIEN     = 26.U(5.W)
  val MODE     = 27.U(5.W)
  val PERADDR  = 28.U(5.W)
  val HCTL     = 29.U(5.W)
  val HXFR     = 30.U(5.W)
  val HRSL     = 31.U(5.W)

  // Command byte (Figure 5) = { Reg4..Reg0, 0, DIR, ACKSTAT }
  // DIR is bit1 (1=write,0=read), NOT bit2 -- fixed after datasheet review.
  def cmdByte(addr: UInt, write: Bool): UInt =
    Cat(addr, 0.U(1.W), write, 0.U(1.W))

  // USBIRQ (R13) bits
  val OSCOKIRQ = 0x01.U(8.W)

  // MODE (R27) bits: DPPULLDN DMPULLDN DELAYISO SEPIRQ SOFKAENAB HUBPRE SPEED HOST
  val MODE_HOST      = 0x01
  val MODE_SOFKAENAB = 0x08
  val MODE_DMPULLDN  = 0x40
  val MODE_DPPULLDN  = 0x80
  val MODE_INIT      = (MODE_HOST | MODE_SOFKAENAB | MODE_DPPULLDN | MODE_DMPULLDN).U(8.W)

  // PINCTL (R17) bits: EP3INAK EP2INAK EP0INAK FDUPSPI INTLEVEL POSINT GPXB GPXA
  // Your board wires MISO/MOSI as separate pins (see XDC) -> use full-duplex (FDUPSPI=1),
  // not the power-on default of half-duplex, or the MISO pin never drives.
  val PINCTL_FDUPSPI = 0x10.U(8.W)

  // CPUCTL (R16) bits: PULSEWID1 PULSEWID0 0 0 0 0 0 IE
  val CPUCTL_IE = 0x01.U(8.W)

  // HCTL (R29) bits: SNDTOG1 SNDTOG0 RCVTOG1 RCVTOG0 SIGRSM BUSSAMPLE FRMRST BUSRST
  val HCTL_BUSRST = 0x01.U(8.W)

  // HIRQ (R25) bits: HXFRDNIRQ FRAMEIRQ CONNIRQ SUSDNIRQ SNDBAVIRQ RCVDAVIRQ RSMREQIRQ BUSEVENTIRQ
  val HIRQ_RCVDAV  = 0x04
  val HIRQ_HXFRDN  = 0x80
  val HIRQ_CONNIRQ = 0x20

  // HXFR (R30) bits: HS ISO OUTNIN SETUP EP[3:0]
  // "HS" = handshake stage (used for the zero-length status stage of a
  // control transfer), NOT high-speed -- this chip is FS/LS only.
  val HXFR_SETUP = 0x10.U(8.W) // OR with EP (0 for EP0)
  val HXFR_IN    = 0x00.U(8.W) // plain IN, OR with EP -- used for interrupt/bulk IN data stage
  val HXFR_OUT   = 0x20.U(8.W) // OR with EP
  val HXFR_INHS  = 0x80.U(8.W) // handshake IN, OR with EP -- control-transfer status stage
  val HXFR_OUTHS = 0xA0.U(8.W) // OR with EP

  // HRSL (R31) low nibble: result code. 0 = success; nonzero includes NAK,
  // which is the normal/expected response from a keyboard between keypresses.
  val HRSL_CODE_MASK = 0x0F.U(8.W)
  val HRSL_SUCCESS   = 0x00.U(8.W)

  val KBD_EP = 1.U(8.W) // typical boot-keyboard interrupt-IN endpoint number
}

/** ---------------------------------------------------------------------
 *  Bit-banged SPI master, mode 0 (CPOL=0, CPHA=0): sample MISO on the
 *  rising edge, change MOSI on the falling edge. `halfPeriodCycles` is
 *  measured in KeyboardParser's clock cycles.
 *  ------------------------------------------------------------------- */
class SpiMaster(halfPeriodCycles: Int) extends Module {
  val io = IO(new Bundle {
    val start  = Input(Bool())
    val txByte = Input(UInt(8.W))
    val rxByte = Output(UInt(8.W))
    val busy   = Output(Bool())
    val done   = Output(Bool())
    val miso   = Input(Bool())
    val mosi   = Output(Bool())
    val sclk   = Output(Bool())
  })

  val hp = halfPeriodCycles.max(1)
  val cnt      = RegInit(0.U(log2Ceil(hp + 1).W))
  val sclkReg  = RegInit(false.B)
  val edgeCnt  = RegInit(0.U(5.W)) // 0..15 (16 half-periods per byte)
  val shiftOut = RegInit(0.U(8.W))
  val shiftIn  = RegInit(0.U(8.W))
  val busyReg  = RegInit(false.B)
  val doneReg  = RegInit(false.B)

  io.sclk   := sclkReg
  io.mosi   := shiftOut(7)
  io.rxByte := shiftIn
  io.busy   := busyReg
  io.done   := doneReg

  doneReg := false.B

  when(!busyReg) {
    sclkReg := false.B
    when(io.start) {
      busyReg  := true.B
      shiftOut := io.txByte
      edgeCnt  := 0.U
      cnt      := 0.U
    }
  }.elsewhen(cnt === (hp - 1).U) {
    cnt     := 0.U
    sclkReg := !sclkReg
    when(!sclkReg) {
      // about to rise: sample MISO
      shiftIn := Cat(shiftIn(6, 0), io.miso)
    }.otherwise {
      // about to fall: present next MOSI bit
      shiftOut := Cat(shiftOut(6, 0), 0.U(1.W))
    }
    when(edgeCnt === 15.U) {
      busyReg := false.B
      doneReg := true.B
    }.otherwise {
      edgeCnt := edgeCnt + 1.U
    }
  }.otherwise {
    cnt := cnt + 1.U
  }
}

/** ---------------------------------------------------------------------
 *  Top level: drives the MAX3421E over SPI, brings a boot-protocol
 *  keyboard up (reset -> mode -> bus reset -> SET_ADDRESS ->
 *  SET_CONFIGURATION -> SET_PROTOCOL(boot)), then repeatedly issues
 *  interrupt-IN transfers and republishes the 8-byte HID report.
 *  ------------------------------------------------------------------- */
class KeyboardParser(val clockFreq: Int = 100000000, val sclkDiv: Int = 8) extends Module {
  val io = IO(new Bundle {
    val spi_miso  = Input(Bool())
    val spi_mosi  = Output(Bool())
    val spi_sclk  = Output(Bool())
    val usb_ss_b  = Output(Bool())
    val usb_rst_b = Output(Bool())
    val usb_int   = Input(Bool())

    val modifiers   = Output(UInt(8.W))
    val keycodes    = Output(Vec(6, UInt(8.W)))
    val reportValid = Output(Bool()) // one-cycle pulse on new report
    val enumDone    = Output(Bool()) // high once past enumeration, in poll loop
  })

  // ---- SPI engine -------------------------------------------------
  val halfPeriod = (sclkDiv / 2).max(1) // /8 total -> 4-cycle half period
  val spi = Module(new SpiMaster(halfPeriod))
  spi.io.miso := io.spi_miso
  io.spi_mosi := spi.io.mosi
  io.spi_sclk := spi.io.sclk

  val csReg = RegInit(true.B) // active low, idle high
  io.usb_ss_b := csReg

  // ---- generic byte-burst helper (holds CS low across N bytes) ----
  val burstBuf   = Reg(Vec(9, UInt(8.W))) // cmd byte + up to 8 data bytes
  val burstLen   = RegInit(0.U(4.W))
  val burstIdx   = RegInit(0.U(4.W))
  val burstBusy  = RegInit(false.B)
  val burstDone  = RegInit(false.B)
  val burstRx    = Reg(Vec(9, UInt(8.W)))

  burstDone := false.B
  spi.io.start := false.B
  spi.io.txByte := burstBuf(burstIdx)

  when(burstBusy) {
    csReg := false.B
    when(!spi.io.busy && !spi.io.done) {
      spi.io.start := true.B
    }
    when(spi.io.done) {
      burstRx(burstIdx) := spi.io.rxByte
      when(burstIdx === (burstLen - 1.U)) {
        burstBusy := false.B
        burstDone := true.B
        csReg     := true.B
      }.otherwise {
        burstIdx := burstIdx + 1.U
      }
    }
  }

  def startBurst(bytes: Seq[UInt]): Unit = {
    for (i <- bytes.indices) burstBuf(i) := bytes(i)
    burstLen  := bytes.length.U
    burstIdx  := 0.U
    burstBusy := true.B
  }

  def writeReg(addr: UInt, data: UInt): Unit =
    startBurst(Seq(Max3421.cmdByte(addr, true.B), data))

  def readReg(addr: UInt): Unit =
    startBurst(Seq(Max3421.cmdByte(addr, false.B), 0.U))

  // ---- timers -------------------------------------------------------
  def cyclesFor(ms: Int): Int = (clockFreq / 1000) * ms
  val delayCnt = RegInit(0.U(32.W))
  val delayTarget = RegInit(0.U(32.W))
  def startDelay(ms: Int): Unit = { delayCnt := 0.U; delayTarget := cyclesFor(ms).U }
  val delayDone = delayCnt >= delayTarget
  delayCnt := Mux(delayDone, delayCnt, delayCnt + 1.U)

  // ---- control-transfer setup packets (standard USB, EP0) ----------
  // [bmRequestType, bRequest, wValueL, wValueH, wIndexL, wIndexH, wLengthL, wLengthH]
  val setupAddr  = VecInit(Seq(0x00, 0x05, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00).map(_.U(8.W)))
  val setupCfg   = VecInit(Seq(0x00, 0x09, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00).map(_.U(8.W)))
  val setupProto = VecInit(Seq(0x21, 0x0B, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00).map(_.U(8.W))) // boot protocol
  val ctrlStep   = RegInit(0.U(2.W)) // 0=SET_ADDRESS 1=SET_CONFIGURATION 2=SET_PROTOCOL

  // ---- report output -------------------------------------------------
  val modReg  = RegInit(0.U(8.W))
  val keyRegs = RegInit(VecInit(Seq.fill(6)(0.U(8.W))))
  val validPulse = RegInit(false.B)
  io.modifiers   := modReg
  io.keycodes    := keyRegs
  io.reportValid := validPulse
  validPulse := false.B

  // ---- top FSM --------------------------------------------------------
  val sReset :: sWaitOsc :: sSetPinctl :: sSetCpuctl :: sSetMode :: sBusRstOn :: sBusRstWait ::
    sBusRstOff :: sSettle :: sCtrlSudfifo :: sCtrlSudfifoWait :: sCtrlSetupXfr ::
    sCtrlSetupWait :: sCtrlStatusXfr :: sCtrlStatusWait :: sCtrlNext :: sSetPeraddr ::
    sSetPeraddrWait :: sPollWait :: sPollIssue :: sPollIssueWait :: sPollCheckResult ::
    sPollReadBC :: sPollReadBCWait :: sPollReadFifo :: sPollReadFifoWait :: sPollClearIrq ::
    sPollClearIrqWait :: sPollIdle :: Nil = Enum(29)

  val state = RegInit(sReset)
  io.enumDone := state >= sPollWait

  io.usb_rst_b := true.B

  switch(state) {
    is(sReset) {
      io.usb_rst_b := false.B
      when(!delayDone) { /* hold reset */ }
      startDelay(2) // hold USB_RESET_B low ~2ms
      state := sWaitOsc
    }
    is(sWaitOsc) {
      // poll USBIRQ.OSCOKIRQ until the MAX3421E's crystal has stabilized
      when(!burstBusy && !burstDone) { readReg(Max3421.USBIRQ) }
      when(burstDone) {
        when((burstRx(1) & Max3421.OSCOKIRQ) =/= 0.U) { state := sSetPinctl }
      }
    }
    is(sSetPinctl) {
      // Full-duplex mode: your board wires MISO/MOSI separately (see XDC),
      // so this must NOT stay at the half-duplex power-on default.
      when(!burstBusy && !burstDone) { writeReg(Max3421.PINCTL, Max3421.PINCTL_FDUPSPI) }
      when(burstDone) { state := sSetCpuctl }
    }
    is(sSetCpuctl) {
      // Enable the INT pin (not otherwise required by this FSM, since every
      // wait below polls HIRQ directly, but harmless and useful if you later
      // want to gate the poll loop off usb_int instead of the timer).
      when(!burstBusy && !burstDone) { writeReg(Max3421.CPUCTL, Max3421.CPUCTL_IE) }
      when(burstDone) { state := sSetMode }
    }
    is(sSetMode) {
      when(!burstBusy && !burstDone) { writeReg(Max3421.MODE, Max3421.MODE_INIT) }
      when(burstDone) { state := sBusRstOn }
    }
    is(sBusRstOn) {
      when(!burstBusy && !burstDone) { writeReg(Max3421.HCTL, Max3421.HCTL_BUSRST) }
      when(burstDone) { startDelay(50); state := sBusRstWait }
    }
    is(sBusRstWait) {
      when(delayDone) { state := sBusRstOff }
    }
    is(sBusRstOff) {
      when(!burstBusy && !burstDone) { writeReg(Max3421.HCTL, 0.U) }
      when(burstDone) { startDelay(10); state := sSettle }
    }
    is(sSettle) {
      when(delayDone) { ctrlStep := 0.U; state := sCtrlSudfifo }
    }

    // ---- generic control transfer: write 8 setup bytes to SUDFIFO,
    //      issue SETUP token, then a zero-length status-stage IN ----
    is(sCtrlSudfifo) {
      val pkt = MuxLookup(ctrlStep, setupAddr)(Seq(0.U -> setupAddr, 1.U -> setupCfg, 2.U -> setupProto))
      when(!burstBusy && !burstDone) {
        startBurst(Seq(Max3421.cmdByte(Max3421.SUDFIFO, true.B)) ++ (0 until 8).map(i => pkt(i)))
      }
      when(burstDone) { state := sCtrlSudfifoWait }
    }
    is(sCtrlSudfifoWait) { state := sCtrlSetupXfr } // burst already completed; separate state kept for clarity
    is(sCtrlSetupXfr) {
      when(!burstBusy && !burstDone) { writeReg(Max3421.HXFR, Max3421.HXFR_SETUP) }
      when(burstDone) { state := sCtrlSetupWait }
    }
    is(sCtrlSetupWait) {
      when(!burstBusy && !burstDone) { readReg(Max3421.HIRQ) }
      when(burstDone && (burstRx(1) & Max3421.HIRQ_HXFRDN.U) =/= 0.U) {
        writeReg(Max3421.HIRQ, Max3421.HIRQ_HXFRDN.U) // ack
        state := sCtrlStatusXfr
      }
    }
    is(sCtrlStatusXfr) {
      // zero-length status-stage IN on EP0 uses the *handshake* IN token
      when(!burstBusy && !burstDone) { writeReg(Max3421.HXFR, Max3421.HXFR_INHS) }
      when(burstDone) { state := sCtrlStatusWait }
    }
    is(sCtrlStatusWait) {
      when(!burstBusy && !burstDone) { readReg(Max3421.HIRQ) }
      when(burstDone && (burstRx(1) & Max3421.HIRQ_HXFRDN.U) =/= 0.U) {
        writeReg(Max3421.HIRQ, Max3421.HIRQ_HXFRDN.U)
        state := sCtrlNext
      }
    }
    is(sCtrlNext) {
      when(ctrlStep === 0.U) {
        // just finished SET_ADDRESS: device is now at address 1, point PERADDR at it
        state := sSetPeraddr
      }.elsewhen(ctrlStep === 2.U) {
        state := sPollIdle // done with SET_PROTOCOL, go poll
      }.otherwise {
        ctrlStep := ctrlStep + 1.U
        state := sCtrlSudfifo
      }
    }
    is(sSetPeraddr) {
      when(!burstBusy && !burstDone) { writeReg(Max3421.PERADDR, 1.U) }
      when(burstDone) { startDelay(2); state := sSetPeraddrWait } // recovery time after SET_ADDRESS
    }
    is(sSetPeraddrWait) {
      when(delayDone) { ctrlStep := 1.U; state := sCtrlSudfifo }
    }

    // ---- steady-state: interrupt-IN poll on the keyboard endpoint ----
    // Timer-gated (~10ms) rather than usb_int-gated: simpler, and matches
    // typical boot-keyboard interrupt intervals. NAK between keypresses is
    // the expected common case, not an error -- checked via HRSL below.
    is(sPollIdle) {
      startDelay(10)
      state := sPollWait
    }
    is(sPollWait) {
      when(delayDone) { state := sPollIssue }
    }
    is(sPollIssue) {
      when(!burstBusy && !burstDone) {
        writeReg(Max3421.HXFR, Max3421.HXFR_IN | Max3421.KBD_EP)
      }
      when(burstDone) { state := sPollIssueWait }
    }
    is(sPollIssueWait) {
      when(!burstBusy && !burstDone) { readReg(Max3421.HIRQ) }
      when(burstDone) {
        when((burstRx(1) & Max3421.HIRQ_HXFRDN.U) =/= 0.U) {
          writeReg(Max3421.HIRQ, Max3421.HIRQ_HXFRDN.U) // ack HXFRDNIRQ
          state := sPollCheckResult
        }
      }
    }
    is(sPollCheckResult) {
      // transfer finished, but that includes NAK -- check HRSL's result code
      when(!burstBusy && !burstDone) { readReg(Max3421.HRSL) }
      when(burstDone) {
        when((burstRx(1) & Max3421.HRSL_CODE_MASK) === Max3421.HRSL_SUCCESS) {
          state := sPollReadBC // got real data
        }.otherwise {
          state := sPollIdle // NAK (or other non-success) -- just retry after the delay
        }
      }
    }
    is(sPollReadBC) {
      when(!burstBusy && !burstDone) { readReg(Max3421.RCVBC) }
      when(burstDone) { state := sPollReadBCWait }
    }
    is(sPollReadBCWait) { state := sPollReadFifo }
    is(sPollReadFifo) {
      // read 8 bytes: cmd byte + 8 dummy clock-out bytes
      when(!burstBusy && !burstDone) {
        startBurst(Seq(Max3421.cmdByte(Max3421.RCVFIFO, false.B)) ++ Seq.fill(8)(0.U(8.W)))
      }
      when(burstDone) { state := sPollReadFifoWait }
    }
    is(sPollReadFifoWait) {
      // burstRx(1..8) = report bytes: [mod, reserved, key1..key6]
      modReg := burstRx(1)
      for (i <- 0 until 6) keyRegs(i) := burstRx(3 + i)
      validPulse := true.B
      state := sPollClearIrq
    }
    is(sPollClearIrq) {
      when(!burstBusy && !burstDone) { writeReg(Max3421.HIRQ, Max3421.HIRQ_RCVDAV.U) }
      when(burstDone) { state := sPollClearIrqWait }
    }
    is(sPollClearIrqWait) { state := sPollIdle }
  }
}

object KeyboardParser extends App {
    ChiselStage.emitSystemVerilogFile(
      new KeyboardParser(),
      firtoolOpts = Array(
        "-disable-all-randomization",
        "-strip-debug-info",
        "-default-layer-specialization=enable"
      ),
      args = Array("--target-dir", "generated")
    )
}