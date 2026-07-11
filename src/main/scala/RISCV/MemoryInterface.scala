package RISCV
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

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

    val mem_req   = Decoupled(new MemLineReq)   
    val mem_resp  = Input(UInt(128.W))
    val mem_valid = Input(Bool())      

    

})
  val icache = Module(new ICache())
  val dcache = Module(new DCache())
  val arbiter = Module(new CacheArbiter())



  val debug_buf = RegInit(VecInit(Seq.fill(4)(0.U(32.W))))

  val debug_word_off       = io.debug_req.address(3, 2)
  val debug_line_addr      = io.debug_req.address >> 4   
  val debug_line_byte_addr = Cat(debug_line_addr, 0.U(4.W))  

  object DebugState extends ChiselEnum {
    val D_IDLE, D_ISSUE, D_WAIT = Value
  }
  val debug_state         = RegInit(DebugState.D_IDLE)
  val debug_pending_addr  = RegInit(0.U(32.W))
  val debug_pending_wdata = RegInit(0.U(128.W))

  val debug_can_start = arbiter.io.idle && (debug_state === DebugState.D_IDLE)
  io.debug_ready := debug_can_start
  io.debug_valid := false.B
  io.debug_data  := 0.U   

  val debug_req_valid = WireDefault(false.B)
  val debug_req_write = WireDefault(true.B)
  val debug_req_addr  = WireDefault(0.U(32.W))
  val debug_req_wdata = WireDefault(0.U(128.W))

  
  icache.io.req   := io.icache_req
  icache.io.start := io.icache_start 
  io.icache_ready := icache.io.ready 
  io.icache_valid := icache.io.done
  io.icache_data  := icache.io.data

  dcache.io.req   := io.dcache_req
  dcache.io.start := io.dcache_start
  io.dcache_ready := dcache.io.ready 
  io.dcache_valid := dcache.io.done
  io.dcache_data  := dcache.io.data

  arbiter.io.icache_req.valid      := icache.io.miss
  arbiter.io.icache_req.bits.addr  := icache.io.line_addr
  arbiter.io.icache_req.bits.write := false.B
  arbiter.io.icache_req.bits.wdata := 0.U

  arbiter.io.dcache_req.valid      := (dcache.io.miss || dcache.io.wb) 
  arbiter.io.dcache_req.bits.addr  := Mux(dcache.io.wb, dcache.io.wb_addr, dcache.io.line_addr)
  arbiter.io.dcache_req.bits.write := dcache.io.wb
  arbiter.io.dcache_req.bits.wdata := dcache.io.wb_data


  val debug_owns_port = (debug_state === DebugState.D_ISSUE)

  io.mem_req.valid      := arbiter.io.mem_req.valid
  io.mem_req.bits.write := arbiter.io.mem_req.bits.write
  io.mem_req.bits.addr  := arbiter.io.mem_req.bits.addr
  io.mem_req.bits.wdata := arbiter.io.mem_req.bits.wdata

  arbiter.io.mem_req.ready :=  io.mem_req.ready
// Route the memory response signals straight into the Arbiter
  arbiter.io.mem_resp      := io.mem_resp
  arbiter.io.mem_valid     := io.mem_valid // Let the arbiter handle gating internally

  icache.io.line_result := io.mem_resp
  icache.io.line_valid  := arbiter.io.resp_to_icache
  
  dcache.io.line_result := io.mem_resp
  dcache.io.line_valid  := arbiter.io.resp_to_dcache
}

object MemoryInterface extends App {
    ChiselStage.emitSystemVerilogFile(
      new MemoryInterface(),
      firtoolOpts = Array(
        "-disable-all-randomization",
        "-strip-debug-info",
        "-default-layer-specialization=enable"
      ),
      args = Array("--target-dir", "generated")
    )
}
