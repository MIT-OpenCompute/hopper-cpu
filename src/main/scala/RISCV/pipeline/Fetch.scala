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
  val req_pc      = RegInit(0.U(32.W))  
  val ignoreInstr = RegInit(false.B)    
  val f2d_reg     = RegInit(0.U.asTypeOf(new F2D))
  val f2d_held    = RegInit(false.B)
  val in_flight   = RegInit(false.B)

  io.icache_req.address    := pc
  io.icache_req.op         := MemOp.LW
  io.icache_req.write_data := 0.U
  io.icache_req.read       := true.B
  io.icache_req.write      := false.B
  io.icache_start          := false.B

  io.f2d.valid := f2d_held
  io.f2d.bits  := f2d_reg

  val redirecting = io.execute && io.f_req.fetch_op === FetchOp.RD
  val can_issue = io.icache_ready && !in_flight

  def issue(addr: UInt): Unit = {
    io.icache_req.address := addr
    io.icache_start:= true.B
    req_pc := addr
    pc := addr + 4.U
  }

  when(io.execute) {
    switch(io.f_req.fetch_op) {

      is(FetchOp.DQ) {
        f2d_held := false.B         
        when(can_issue) { issue(pc) }
      }

      is(FetchOp.ST) {
        when(!f2d_held && can_issue) { issue(pc) }
      }

      is(FetchOp.RD) {
        f2d_held := false.B
        when(can_issue) {
          issue(io.f_req.redirect_addr)
        }.otherwise {
          pc := io.f_req.redirect_addr  
        }

        ignoreInstr := in_flight && !io.icache_valid
      }
    }
  }

  when(io.icache_valid) {
    when(ignoreInstr) {
      ignoreInstr := false.B          
    }.elsewhen(!redirecting) {
      f2d_reg.pc   := req_pc
      f2d_reg.inst := io.icache_data
      f2d_held     := true.B
    }

  }
 
  in_flight := Mux(io.icache_start, true.B,
               Mux(io.icache_valid, false.B, in_flight))

  when(false.B) {
    printf("=== FETCH === pc=%x req_pc=%x op=%d ignore=%b held=%b in_flight=%b | start=%b ready=%b valid=%b | f2d_v=%b f2d_pc=%x f2d_inst=%x\n",
      pc, req_pc, io.f_req.fetch_op.asUInt, ignoreInstr, f2d_held, in_flight,
      io.icache_start, io.icache_ready, io.icache_valid,
      io.f2d.valid, io.f2d.bits.pc, io.f2d.bits.inst)
  }
}