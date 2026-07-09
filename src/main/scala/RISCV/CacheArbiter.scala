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
    
    // Track if the request has been successfully accepted by the downstream memory
    val mem_req_accepted = RegInit(false.B)

    val idle = !serving_icache && !serving_dcache

    val latched_req = RegInit(0.U.asTypeOf(new MemLineReq))

    // 1. Arbitration & Capture Logic
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
      // Monitor if downstream memory accepts our outbound token
      when(io.mem_req.valid && io.mem_req.ready) {
        mem_req_accepted := true.B
      }
    }

    // 2. Clear state only when valid data returns from memory
    when(io.mem_valid) {
      when(serving_icache) { serving_icache := false.B }
      when(serving_dcache) { serving_dcache := false.B }
      mem_req_accepted := false.B
    }

    // 3. Downstream Outbound Interface (Respecting Handshakes)
    // Keep valid asserted until memory accepts it
    io.mem_req.valid    := (serving_icache || serving_dcache) && !mem_req_accepted
    io.mem_req.bits     := latched_req

    // 4. Upstream Interface Handshaking
    // Only accept new cache packets when we are completely clear and ready to latch them
    io.icache_req.ready := idle
    io.dcache_req.ready := idle && !io.icache_req.valid

    // 5. Response Routing
    io.resp_to_icache   := io.mem_valid && serving_icache
    io.resp_to_dcache   := io.mem_valid && serving_dcache
    io.idle             := idle

    // 6. Arbiter Logging Snippet
    when(false.B) {
      printf(
        "ARBITER cycle: idle=%d serve_I=%d serve_D=%d | I_req=[v=%d r=%d a=%x w=%d wd=%x] | D_req=[v=%d r=%d a=%x w=%d wd=%x] | mem_req=[v=%d r=%d a=%x w=%d wd=%x] | mem_resp=[v=%d d=%x] | resp_to_I=%d resp_to_D=%d\n",
        io.idle,
        serving_icache,
        serving_dcache,
        // I-Cache Request Decoupled interface
        io.icache_req.valid,
        io.icache_req.ready,
        io.icache_req.bits.addr,
        io.icache_req.bits.write,
        io.icache_req.bits.wdata,
        // D-Cache Request Decoupled interface
        io.dcache_req.valid,
        io.dcache_req.ready,
        io.dcache_req.bits.addr,
        io.dcache_req.bits.write,
        io.dcache_req.bits.wdata,
        // Outbound Memory Request
        io.mem_req.valid,
        io.mem_req.ready,
        io.mem_req.bits.addr,
        io.mem_req.bits.write,
        io.mem_req.bits.wdata,
        // Inbound Memory Response
        io.mem_valid,
        io.mem_resp,
        // Response Routing signals
        io.resp_to_icache,
        io.resp_to_dcache
      )
    }
}