package RISCV

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import chisel3.util.experimental.loadMemoryFromFileInline

class MemLineReq extends Bundle {
  val addr    = UInt(32.W)
  val write   = Bool()
  val wdata   = UInt(128.W)  
}

class CacheArbiter() extends Module {
    val io = IO(new Bundle {
      val icache_req   = Flipped(Decoupled(new MemLineReq))
      val dcache_req   = Flipped(Decoupled(new MemLineReq))
      val mem_req      = Decoupled(new MemLineReq)
      val mem_resp     = Input(UInt(128.W))
      val mem_valid    = Input(Bool())
      val resp_to_icache = Output(Bool())
      val resp_to_dcache = Output(Bool())



        
    })



    val serving_icache = RegInit(false.B)
    val serving_dcache = RegInit(false.B)

    val icache_pending = io.icache_req.valid
    val dcache_pending = io.dcache_req.valid

    val idle = !serving_icache && !serving_dcache

    when(idle) {
      when(dcache_pending) {
        serving_dcache := true.B
      }.elsewhen(icache_pending) {
        serving_icache := true.B
      }
    }

    when(io.mem_valid && serving_icache) { serving_icache := false.B }
    when(io.mem_valid && serving_dcache) { serving_dcache := false.B }

    io.mem_req.valid := serving_icache || serving_dcache
    io.mem_req.bits  := Mux(serving_icache, io.icache_req.bits, io.dcache_req.bits)

    io.icache_req.ready := idle && icache_pending
    io.dcache_req.ready := idle && !icache_pending && dcache_pending

    io.resp_to_icache := io.mem_valid && serving_icache
    io.resp_to_dcache := io.mem_valid && serving_dcache

}
