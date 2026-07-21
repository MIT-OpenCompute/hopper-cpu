package RISCV
import chisel3._
import chisel3.util._

object FetchOp extends ChiselEnum {
  val ST, RD, DQ = Value // Stall, Redirect, Dequeue
}

class FetchReq extends Bundle {
  val fetch_op = FetchOp()
  val redirect_addr = UInt(32.W)
}

class F2D extends Bundle {
  val pc = UInt(32.W)
  val inst = UInt(32.W)
}

class Fetch() extends Module {
  val io = IO(new Bundle {
    val f_req = Input(new FetchReq)
    val f2d = Output(Valid(new F2D))
    val execute = Input(Bool())
    val icache_req = Output(new MemReq)
    val icache_start = Output(Bool())
    val icache_ready = Input(Bool())
    val icache_valid = Input(Bool())
    val icache_data  = Input(UInt(32.W))
  })

  val pc = RegInit(0.U(32.W))
  val req_pc = RegInit(0.U(32.W))
  val ignoreInstr = RegInit(false.B)
  val in_flight = RegInit(false.B)

  
  val f2d0 = Reg(new F2D)
  val f2d1 = Reg(new F2D)
  val v0 = RegInit(false.B)
  val v1 = RegInit(false.B)

  io.icache_req.address := pc
  io.icache_req.op := MemOp.LW
  io.icache_req.write_data := 0.U
  io.icache_req.read := true.B
  io.icache_req.write := false.B
  io.icache_start := false.B

  io.f2d.valid := v0
  io.f2d.bits := f2d0

  val redirecting = io.execute && io.f_req.fetch_op === FetchOp.RD
  val dequeuing = io.execute && io.f_req.fetch_op === FetchOp.DQ
  val pop = dequeuing && v0


  val pending= v0.asUInt +& v1.asUInt +& in_flight.asUInt
  val effective_pending = pending - pop.asUInt
  val can_issue = io.icache_ready && (effective_pending <= 1.U)

  def issue(addr: UInt): Unit = {
    io.icache_req.address := addr
    io.icache_start := true.B
    req_pc := addr
    pc:= addr + 4.U
  }

  val in_flight_w = Mux(io.icache_start, true.B,Mux(io.icache_valid, false.B, in_flight))

  when(io.execute) {
    switch(io.f_req.fetch_op) {
      is(FetchOp.DQ) { when(can_issue) { issue(pc) } }
      is(FetchOp.ST) { when(can_issue) { issue(pc) } }
      is(FetchOp.RD) {
        when(can_issue) {
          issue(io.f_req.redirect_addr)
        }.otherwise {
          pc := io.f_req.redirect_addr
        }
        ignoreInstr := in_flight && !io.icache_valid
      }
    }
  }

  val push = io.icache_valid && !ignoreInstr && !redirecting

  when(io.icache_valid && ignoreInstr) { ignoreInstr := false.B }

  when(redirecting) {
    v0 := false.B        
    v1 := false.B
  }.otherwise {
    when(pop) {
      when(push) {
        when(v1) {
          f2d0 := f2d1
          f2d1.pc := req_pc
          f2d1.inst := io.icache_data
        }.otherwise {
          f2d0.pc := req_pc
          f2d0.inst := io.icache_data
          v0 := true.B
          v1 := false.B
        }
      }.otherwise {
        when(v1) { f2d0 := f2d1; v1 := false.B }
         .otherwise { v0 := false.B }
      }
    }.otherwise {
      when(push) {
        when(!v0) {
          f2d0.pc := req_pc
          f2d0.inst := io.icache_data
          v0 := true.B
        }.elsewhen(!v1) {
          f2d1.pc := req_pc
          f2d1.inst := io.icache_data
          v1 := true.B
        }
      }
    }
  }
  in_flight := in_flight_w
}

// Parts of this file were generated from a previous Fetch Module I wrote in Bluespec
// and didn't want to write from scratch. TBH though, I'm not a big fan and will probably redo it later 