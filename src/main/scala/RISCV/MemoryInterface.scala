package RISCV

import chisel3._
import _root_.circt.stage.ChiselStage
import chisel3.util.experimental.loadMemoryFromFileInline



object MemOp extends ChiselEnum {
  val LW,LH,LB,LBU,LHU,SW,SH,SB = Value
}

//Maybe add mark
class MemReq extends Bundle {
    val address = UInt(32.W)
    val write_data = UInt(32.W)
    val op =  MemOp()
    val read = Bool() //why tf
    val write = Bool()
}



class MemoryInterface() extends Module {
  val io = IO(new Bundle {
    val icache_req = Input(new MemReq)
    val icache_start = Input(Bool())
    val icache_ready = Output(Bool())
    val icache_valid = Output(Bool())
    val icache_data  = Output(UInt(32.W))

    val dcache_req = Input(new MemReq)
    val dcache_start = Input(Bool())
    val dcache_ready = Output(Bool())
    val dcache_valid = Output(Bool())
    val dcache_data  = Output(UInt(32.W))
  })

  val icache = Module(new ICache())
  val dcache = Module(new DCache())
  val arbiter = Module(new CacheArbiter())
  val memory  = SyncReadMem(1024, UInt(128.W))


  icache.io.req := io.icache_req
  icache.io.start := io.icache_start
  io.icache_ready := icache.io.ready
  io.icache_valid := icache.io.done
  io.icache_data := icache.io.data

  dcache.io.req := io.dcache_req
  dcache.io.start := io.dcache_start
  io.dcache_ready := dcache.io.ready
  io.dcache_valid := dcache.io.done
  io.dcache_data := dcache.io.data

 
  arbiter.io.icache_req.valid := icache.io.miss
  arbiter.io.icache_req.bits.addr := icache.io.line_addr
  arbiter.io.icache_req.bits.write := false.B
  arbiter.io.icache_req.bits.wdata := 0.U

  
  arbiter.io.dcache_req.valid := dcache.io.miss || dcache.io.wb
  arbiter.io.dcache_req.bits.addr := Mux(dcache.io.wb, dcache.io.wb_addr, dcache.io.line_addr)
  arbiter.io.dcache_req.bits.write := dcache.io.wb
  arbiter.io.dcache_req.bits.wdata := dcache.io.wb_data

 
  val mem_out = memory.readWrite(
    arbiter.io.mem_req.bits.addr,
    arbiter.io.mem_req.bits.wdata,
    arbiter.io.mem_req.valid,
    arbiter.io.mem_req.bits.write
  )
  arbiter.io.mem_req.ready := true.B  
  arbiter.io.mem_resp := mem_out
  arbiter.io.mem_valid := RegNext(arbiter.io.mem_req.valid && !arbiter.io.mem_req.bits.write, false.B)

  
  icache.io.line_result := mem_out
  icache.io.line_valid := arbiter.io.resp_to_icache

  dcache.io.line_result := mem_out
  dcache.io.line_valid := arbiter.io.resp_to_dcache
}