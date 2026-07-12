// package RISCV

// import chisel3._
// import _root_.circt.stage.ChiselStage
// import chisel3.util.experimental.loadMemoryFromFileInline

// val busWidth = 128;


// //Maybe add mark
// class HardwareMemReq extends Bundle {
//     val address    = UInt(32.W)
//     val write_data = UInt(busWidth.W)
//     val read       = Bool()
//     val write      = Bool()
// }

// class HardwareMemoryInferface() extends Module {
//     val io = IO(new Bundle {
//         val req = Input(new HardwareMemReq)
//         val ready = Output(Bool())
//         val valid = Output(Bool())
//         val data = Output(UInt(busWidth.W))
        
//     })

//     //code here for mig interface

// }
