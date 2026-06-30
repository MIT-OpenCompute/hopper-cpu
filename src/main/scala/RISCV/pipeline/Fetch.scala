package RISCV

import chisel3._
import chisel3.util._
// Note: this was kinda just translated from minispec to scala. I don't love the design but its also verified correct.
object FetchOp extends ChiselEnum {
  val ST, RD, DQ = Value // Dequeue, Stall, Redirect
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

  val pc           = RegInit(0.U(32.W))
  val ignoreInstr  = RegInit(false.B)
  val icache_valid_reg = RegNext(io.icache_valid, false.B)


  io.f2d.valid      := io.icache_valid && !ignoreInstr
  io.f2d.bits.pc   := pc
  io.f2d.bits.inst := io.icache_data


  io.icache_req.address    := pc
  io.icache_req.op         := MemOp.LW
  io.icache_req.write_data := 0.U
  io.icache_req.read       := true.B
  io.icache_req.write      := false.B
  io.icache_start          := false.B

  when(io.f_req.fetch_op =/= FetchOp.RD && icache_valid_reg) {
    ignoreInstr := false.B
  }

  when(false.B){
    printf("=== FETCH === pc=%x op=%d ignore=%b | icache_start=%b icache_ready=%b icache_valid=%b icache_valid_reg=%b | f2d_valid=%b f2d_pc=%x f2d_inst=%x\n",
    pc,
    io.f_req.fetch_op.asUInt,
    ignoreInstr,
    io.icache_start,
    io.icache_ready,
    io.icache_valid,
    icache_valid_reg,
    io.f2d.valid,
    io.f2d.bits.pc,
    io.f2d.bits.inst)
  }
  when(io.execute){
    switch(io.f_req.fetch_op) {

      is(FetchOp.DQ) { 
        io.icache_req.address := pc
        io.icache_start := io.icache_ready
        when(icache_valid_reg) {
          pc := pc + 4.U
          io.icache_req.address := pc + 4.U
        }
      }

      is(FetchOp.ST) { 
        io.icache_req.address := pc
        io.icache_start       := true.B
      }

      is(FetchOp.RD) { 
        pc                    := io.f_req.redirect_addr
        io.icache_req.address := io.f_req.redirect_addr
        io.icache_start       := io.icache_ready
        ignoreInstr           := true.B
      }
    }
  }
}