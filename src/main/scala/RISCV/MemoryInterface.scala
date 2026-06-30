package RISCV
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import chisel3.util.experimental.loadMemoryFromFileInline

object MemOp extends ChiselEnum {
  val LW,LH,LB,LBU,LHU,SW,SH,SB = Value
}

class MemReq extends Bundle {
  val address    = UInt(32.W)
  val write_data = UInt(32.W)
  val op         = MemOp()
  val read       = Bool()
  val write      = Bool()
}

class MemoryInterface() extends Module {
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
  })

  val icache = Module(new ICache())
  val dcache = Module(new DCache())
  val arbiter = Module(new CacheArbiter())
  val memory  = SyncReadMem(1024, UInt(128.W))

  // debug port state — accumulate 4 words into a line then write directly to memory
  val debug_buf     = RegInit(VecInit(Seq.fill(4)(0.U(32.W))))
  val debug_valid_r = RegInit(false.B)

  // word index and line index from byte address
  val debug_word_off  = io.debug_req.address(3, 2)
  val debug_line_addr = io.debug_req.address >> 4

  io.debug_ready := true.B   // always ready, direct write
  io.debug_valid := false.B
  io.debug_data  := 0.U

  when(io.debug_start && io.debug_req.write) {
    // patch the word into the buffer
    debug_buf(debug_word_off) := io.debug_req.write_data
    // write the full line directly to memory, bypassing arbiter
    val patched = Wire(Vec(4, UInt(32.W)))
    for (i <- 0 until 4) { patched(i) := debug_buf(i) }
    patched(debug_word_off) := io.debug_req.write_data
    memory.write(debug_line_addr, patched.asUInt)
    io.debug_valid := true.B
  }

  // normal cache path — caches blocked during debug
  icache.io.req   := io.icache_req
  icache.io.start := io.icache_start && !io.debug_start
  io.icache_ready := icache.io.ready && !io.debug_start
  io.icache_valid := icache.io.done
  io.icache_data  := icache.io.data

  dcache.io.req   := io.dcache_req
  dcache.io.start := io.dcache_start && !io.debug_start
  io.dcache_ready := dcache.io.ready && !io.debug_start
  io.dcache_valid := dcache.io.done
  io.dcache_data  := dcache.io.data

  arbiter.io.icache_req.valid      := icache.io.miss && !io.debug_start
  arbiter.io.icache_req.bits.addr  := icache.io.line_addr
  arbiter.io.icache_req.bits.write := false.B
  arbiter.io.icache_req.bits.wdata := 0.U

  arbiter.io.dcache_req.valid      := (dcache.io.miss || dcache.io.wb) && !io.debug_start
  arbiter.io.dcache_req.bits.addr  := Mux(dcache.io.wb, dcache.io.wb_addr, dcache.io.line_addr)
  arbiter.io.dcache_req.bits.write := dcache.io.wb
  arbiter.io.dcache_req.bits.wdata := dcache.io.wb_data

  // mux arbiter vs debug for the single memory port
  // debug uses memory.write (separate port) so readWrite is free for arbiter
  val mem_out = memory.readWrite(
    arbiter.io.mem_req.bits.addr,
    arbiter.io.mem_req.bits.wdata,
    arbiter.io.mem_req.valid && !io.debug_start,
    arbiter.io.mem_req.bits.write
  )
  arbiter.io.mem_req.ready := !io.debug_start
  arbiter.io.mem_resp      := mem_out
  arbiter.io.mem_valid     := RegNext(arbiter.io.mem_req.valid && !arbiter.io.mem_req.bits.write && !io.debug_start, false.B)

  icache.io.line_result := mem_out
  icache.io.line_valid  := arbiter.io.resp_to_icache
  dcache.io.line_result := mem_out
  dcache.io.line_valid  := arbiter.io.resp_to_dcache
}

