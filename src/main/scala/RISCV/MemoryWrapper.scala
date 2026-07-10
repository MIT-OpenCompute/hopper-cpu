package RISCV
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import chisel3.util.experimental.loadMemoryFromFileInline



class MemoryWrapper() extends Module {
  val io = IO(new Bundle {
    val icache_req   = Input(new MemReq)
    val icache_start = Input(Bool())
    val icache_ready = Output(Bool())
    val icache_valid = Output(Bool())
    val icache_data  = Output(UInt(32.W))

    val dcache_req   = Input(new MemReq)
    val dcache_start = Input(Bool())
    val dcache_ready = Output(Bool())
    val dcache_valid = Output(Bool())
    val dcache_data  = Output(UInt(32.W))

    val debug_req    = Input(new MemReq)
    val debug_start  = Input(Bool())
    val debug_ready  = Output(Bool())
    val debug_valid  = Output(Bool())
    val debug_data   = Output(UInt(32.W))

    val mem_req   = Decoupled(new MemLineReq)   
    val mem_resp  = Input(UInt(128.W))
    val mem_valid = Input(Bool()) 

    val address_vga = Output(UInt(32.W))
    val write_vga = Output(Bool())
    val write_value_vga = Output(UInt(32.W))
    val handshake_bypass = Output(Bool())

    val rxd = Input(Bool())
  })


  val mem = Module(new MemoryInterface())
  val hardwareTimer = Module(new HardwareTimer(100000000))
  val keyTracker = Module(new UartKeyboardTracker(100000000, 115200)) 
  keyTracker.io.rxd := io.rxd
  mem.io.icache_req := io.icache_req
  mem.io.icache_start := io.icache_start
  io.icache_ready := mem.io.icache_ready
  io.icache_valid := mem.io.icache_valid
  io.icache_data := mem.io.icache_data


  val is_vga     = io.dcache_req.address >= 0x10000000.U
  val is_htimer  = io.dcache_req.address === 0x8000004.U

  val KEYTRACKER_BASE = 0x08000008.U
  val is_keytracker = io.dcache_req.address >= KEYTRACKER_BASE &&
                      io.dcache_req.address < (KEYTRACKER_BASE + 0x2C.U)
  val keytracker_word = (io.dcache_req.address - KEYTRACKER_BASE)(5, 2)

  val keytracker_rdata = MuxLookup(keytracker_word, 0.U(32.W))(Seq(
    0.U  -> keyTracker.io.keyDown(31, 0),
    1.U  -> keyTracker.io.keyDown(63, 32),
    2.U  -> keyTracker.io.keyDown(95, 64),
    3.U  -> keyTracker.io.keyDown(127, 96),
    4.U  -> keyTracker.io.keyDown(159, 128),
    5.U  -> keyTracker.io.keyDown(191, 160),
    6.U  -> keyTracker.io.keyDown(223, 192),
    7.U  -> keyTracker.io.keyDown(255, 224),
    8.U  -> keyTracker.io.lastCode,
    9.U  -> Cat(0.U(30.W), keyTracker.io.eventValid, keyTracker.io.lastPressed),
    10.U -> keyTracker.io.numDown
  ))

  val is_excep = is_vga || is_htimer || is_keytracker


  io.address_vga := io.dcache_req.address - 0x10000000.U
  io.write_vga := is_vga && io.dcache_req.write && io.dcache_start
  io.write_value_vga := io.dcache_req.write_data

  io.handshake_bypass := is_excep

  mem.io.dcache_req := io.dcache_req
  mem.io.dcache_start := io.dcache_start && !is_excep
  io.dcache_ready := mem.io.dcache_ready
  io.dcache_valid := mem.io.dcache_valid
  io.dcache_data := mem.io.dcache_data

  when(is_htimer) {
    io.dcache_data := hardwareTimer.io.micros
  }.elsewhen(is_keytracker) {
    io.dcache_data := keytracker_rdata
  }


  mem.io.debug_req := io.debug_req
  mem.io.debug_start := io.debug_start
  io.debug_ready := mem.io.debug_ready
  io.debug_valid := mem.io.debug_valid
  io.debug_data := mem.io.debug_data

  io.mem_req       <> mem.io.mem_req
  mem.io.mem_resp := io.mem_resp
  mem.io.mem_valid := io.mem_valid


   //i needs a handshake bypass


}

