package RISCV
import chisel3._
import chisel3.util._

object FetchOp extends ChiselEnum {
  val ST, RD, DQ = Value // Stall, Redirect, Dequeue
}

class FetchReq extends Bundle {
  val fetch_op      = FetchOp()
  val redirect_addr = UInt(32.W)
}

class F2D extends Bundle {
  val pc   = UInt(32.W)
  val inst = UInt(32.W)
}

class Fetch() extends Module {
  val io = IO(new Bundle {
    val f_req        = Input(new FetchReq)
    val f2d          = Output(Valid(new F2D))
    val execute      = Input(Bool())
    val icache_req   = Output(new MemReq)
    val icache_start = Output(Bool())
    val icache_ready = Input(Bool())
    val icache_valid = Input(Bool())
    val icache_data  = Input(UInt(32.W))
  })

  val pc          = RegInit(0.U(32.W))
  val ignoreInstr = RegInit(false.B)
  val was_dq      = RegInit(false.B)
  val f2d_reg     = RegInit(0.U.asTypeOf(new F2D))
  val f2d_held    = RegInit(false.B)
  val in_flight   = RegInit(false.B)

  // defaults
  io.icache_req.address    := pc
  io.icache_req.op         := MemOp.LW
  io.icache_req.write_data := 0.U
  io.icache_req.read       := true.B
  io.icache_req.write      := false.B
  io.icache_start          := false.B

  // f2d output: stable until DQ clears it
  io.f2d.valid := f2d_held && !ignoreInstr
  io.f2d.bits  := f2d_reg

  // track in-flight requests
  when(io.icache_start) {
    in_flight := true.B
  }
  when(io.icache_valid) {
    in_flight := false.B
  }

  // latch icache response, discarding stale pre-redirect responses
  when(io.icache_valid && io.f_req.fetch_op =/= FetchOp.RD) {
    when(!ignoreInstr) {
      f2d_reg.inst := io.icache_data
      f2d_held     := true.B
      when(was_dq) {
        f2d_reg.pc := pc 
        pc         := pc + 4.U
        was_dq     := false.B
        when(in_flight){
          ignoreInstr := true.B
        }
      }.otherwise {
        f2d_reg.pc := pc
      }
    }.otherwise {
      ignoreInstr := false.B
      was_dq      := false.B
    }
  }

  when(io.execute) {
    switch(io.f_req.fetch_op) {

      is(FetchOp.DQ) {
        // clear held when decode is consuming (not stalled)
        when(f2d_held) {
          f2d_held := false.B
          was_dq   := true.B
        }
        // issue next request when nothing in flight
        when(!in_flight) {
          io.icache_req.address := pc
          io.icache_start       := io.icache_ready
        }
      }

      is(FetchOp.ST) {
        when(!f2d_held && !in_flight) {
          io.icache_req.address := pc
          io.icache_start       := io.icache_ready
        }
      }

      is(FetchOp.RD) {
        pc                    := io.f_req.redirect_addr
        io.icache_req.address := io.f_req.redirect_addr
        io.icache_start       := io.icache_ready
        ignoreInstr           := true.B
        was_dq                := false.B
        f2d_held              := false.B
        in_flight             := false.B
      }
    }
  }

  when(false.B) {
    printf("=== FETCH === pc=%x op=%d ignore=%b held=%b was_dq=%b in_flight=%b | icache_start=%b icache_ready=%b icache_valid=%b | f2d_valid=%b f2d_pc=%x f2d_inst=%x\n",
      pc,
      io.f_req.fetch_op.asUInt,
      ignoreInstr,
      f2d_held,
      was_dq,
      in_flight,
      io.icache_start,
      io.icache_ready,
      io.icache_valid,
      io.f2d.valid,
      io.f2d.bits.pc,
      io.f2d.bits.inst)
  }
}