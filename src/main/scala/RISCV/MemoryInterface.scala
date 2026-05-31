package RISCV

import chisel3._
import _root_.circt.stage.ChiselStage
import chisel3.util.experimental.loadMemoryFromFileInline



//Maybe add mark
class MemReq extends Bundle {
    val address    = UInt(32.W)
    val write_data = UInt(32.W)
    val read       = Bool() //why tf
    val write      = Bool()
}

class MemoryInferface() extends Module {
    val io = IO(new Bundle {
        val req = Input(new MemReq)
        val start = Input(Bool())
        val ready = Output(Bool())
        val valid = Output(Bool())
        val data = Output(UInt(32.W))
        
    })

  // val cache = Module(new Cache())
  // val ready_reg = RegInit(true.B);
  // cache.io.req := io.req
  // cache.io.start := io.start
  // when(io.start) {
  //   ready_reg := false.B;
  // }.elsewhen(cache.io.valid) {
  //   when(cache.io.miss){
  //     //talk to memory
  //   }.otherwise{
  //     io.valid := true.B;
  //     io.data := cache.data;
  //     ready_reg := true.B
  //   }
  // }
    //ccache and normal mem here
  val memory = SyncReadMem(1024, UInt(32.W))
  io.ready = true.b
  
  val validReg = RegNext(io.start, false.B)
  io.valid := validReg

  io.data := memory.readWrite(
      io.req.address,
      io.req.write_data,
      io.start && (io.req.read || io.req.write),
      io.req.write
    )
}
