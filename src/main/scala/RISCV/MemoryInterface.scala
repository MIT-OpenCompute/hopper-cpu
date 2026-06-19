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

//   val cache = Module(new Cache())
//   val memory = SyncReadMem(1024, UInt(128.W))

//   when(reset.asBool) {
//     for (i <- 0 until 1024) {
//         memory.write(i.U, 0.U(128.W))
//     }
// }
//   io.data := cache.io.data
//   val mem_req_addr = Wire(UInt(32.W))
//   mem_req_addr := 0.U  // default
//   when(cache.io.wb) { 
//     mem_req_addr := cache.io.wb_addr
//   }.elsewhen(cache.io.miss) {
//     mem_req_addr := cache.io.line_addr
//   }


//   val mem_out = memory.readWrite(
//       mem_req_addr,
//       cache.io.wb_data,
//       cache.io.miss || cache.io.wb,
//       cache.io.wb
//     )
  

//   io.ready := cache.io.ready;
//   // val ready_reg = RegInit(true.B);
//   cache.io.req := io.req
//   cache.io.start := io.start
//   cache.io.line_result := mem_out
//   cache.io.line_valid  := RegNext(cache.io.miss && !cache.io.wb, false.B)
  
//     //ccache and normal mem here

//   val validReg = RegNext(io.start, false.B)
//   io.valid := cache.io.done;

  val memory = SyncReadMem(4096, UInt(32.W))
  io.data := memory.readWrite(
      io.req.address,
      io.req.write_data,
      io.start && (io.req.read || io.req.write),
      io.req.write
    )
  io.ready := true.B
  io.valid := RegNext(io.start)

//   when(true.B) {
//     printf("cycle: start=%d ready=%d valid=%d data=%x | req=[addr=%x wdata=%x r=%d w=%d] | miss=%d wb=%d mem_addr=%x | line_result=%x line_valid=%d\n",
//         io.start,
//         io.ready,
//         io.valid,
//         io.data,
//         io.req.address,
//         io.req.write_data,
//         io.req.read,
//         io.req.write,
//         cache.io.miss,
//         cache.io.wb,
//         mem_req_addr,
//         mem_out,
//         cache.io.line_valid
//     )
// }
}
