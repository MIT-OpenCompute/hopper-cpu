// package RISCV

// import chisel3._
// import chisel3.util._
// import _root_.circt.stage.ChiselStage
// import chisel3.util.{AsyncResetSynchronizerShiftReg}


// class HardwareTimer extends Module {
//     val io = IO(new Bundle {
//         val read_clk = Input(Clock())  
//         val counter = Output(UInt(64.W))  
//     })

    
//     withClock(io.read_clk) {
//         val counter = RegInit(0.U(64.W))
//         counter := counter + 1.U
//         io.counter := AsyncResetSynchronizerShiftReg(counter, depth=2)
//     }
// }
