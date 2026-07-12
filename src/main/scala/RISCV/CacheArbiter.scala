package RISCV
import chisel3._
import chisel3.util._

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
      val idle           = Output(Bool())
    })

    val serving_icache = RegInit(false.B)
    val serving_dcache = RegInit(false.B)
    
    val mem_req_accepted = RegInit(false.B)

    val idle = !serving_icache && !serving_dcache

    val latched_req = RegInit(0.U.asTypeOf(new MemLineReq))

    when(idle) {
      when(io.dcache_req.valid) {
        serving_dcache   := true.B
        latched_req      := io.dcache_req.bits
        mem_req_accepted := false.B
      }.elsewhen(io.icache_req.valid) {
        serving_icache   := true.B
        latched_req      := io.icache_req.bits
        mem_req_accepted := false.B
      }
    }.otherwise {
      when(io.mem_req.valid && io.mem_req.ready) {
        mem_req_accepted := true.B
      }
    }

    when(io.mem_valid) {
      when(serving_icache) { serving_icache := false.B }
      when(serving_dcache) { serving_dcache := false.B }
      mem_req_accepted := false.B
    }

    io.mem_req.valid    := (serving_icache || serving_dcache) && !mem_req_accepted
    io.mem_req.bits     := latched_req


    io.icache_req.ready := idle
    io.dcache_req.ready := idle && !io.icache_req.valid

    // 5. Response Routing
    io.resp_to_icache   := io.mem_valid && serving_icache
    io.resp_to_dcache   := io.mem_valid && serving_dcache
    io.idle             := idle
  
    when(false.B) {
      
      printf(
        "ARBITER cycle: idle=%d serve_I=%d serve_D=%d | I_req=[v=%d r=%d a=%x w=%d wd=%x] | D_req=[v=%d r=%d a=%x w=%d wd=%x] | mem_req=[v=%d r=%d a=%x w=%d wd=%x] | mem_resp=[v=%d d=%x] | resp_to_I=%d resp_to_D=%d\n",
        io.idle,
        serving_icache,
        serving_dcache,
        io.icache_req.valid,
        io.icache_req.ready,
        io.icache_req.bits.addr,
        io.icache_req.bits.write,
        io.icache_req.bits.wdata,
        io.dcache_req.valid,
        io.dcache_req.ready,
        io.dcache_req.bits.addr,
        io.dcache_req.bits.write,
        io.dcache_req.bits.wdata,
        io.mem_req.valid,
        io.mem_req.ready,
        io.mem_req.bits.addr,
        io.mem_req.bits.write,
        io.mem_req.bits.wdata,
        io.mem_valid,
        io.mem_resp,
        io.resp_to_icache,
        io.resp_to_dcache
      )
    }
}