package RISCV

import chisel3._
import _root_.circt.stage.ChiselStage
import scala.math._

// class ReorderEntry extends Bundle {
//     val ready = Bool()
//     val instruction = UInt(32.W)
//     val memory_write = Bool()
//     val rd = UInt(32.W)
//     val rd_value = UInt(32.W)
// }

// class ReorderBuffer(val size: Int = 32, val key_size: Int = 5) extends Module {
//     val io = IO(new Bundle {
//         val empty = Output(Bool())
//         val full = Output(Bool())
        
//         val head = Output(UInt(key_size.W))

//         val push = Input(Bool())
//         val push_value = Output(ReorderEntry())

//         val pop = Input(Bool())
//     })

//     val buffer = RegInit(VecInit(Seq.fill(size)(ReorderEntry())))

//     val head_pointer = RegInit(0.U(key_size.W))
//     val tail_pointer = RegInit(0.U(key_size.W))

//     io.empty := head_pointer === tail_pointer
//     io.full := head_pointer === tail_pointer - 1.U

//     io.head := buffer(head_pointer)

//     when(io.push) {
//         val write_position = (head_pointer + 1.U) % size.U(key_size.W)

//         head_pointer := write_position
//         buffer(write_position) := io.push_value
//     }

//     when(io.pop) {
//         tail_pointer := tail_pointer + 1.U
//     }
// }