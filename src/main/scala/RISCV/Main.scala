package RISCV

import chisel3._
import _root_.circt.stage.ChiselStage
import scala.math._

class Main() extends Module {
    val io = IO(new Bundle {
		val execute = Input(Bool())

    val flash = Input(Bool())
		val flash_address = Input(UInt(32.W))
		val flash_value = Input(UInt(32.W))

    val debug_ready = Output(Bool())


    })

    val memory = Module(new MemoryInterface())
    val core = Module(new Core())
    io.debug_ready := memory.io.debug_ready
    memory.io.icache_req := core.io.icache_req
    memory.io.icache_start := core.io.icache_start
    core.io.icache_ready := memory.io.icache_ready
    core.io.icache_valid := memory.io.icache_valid
    core.io.icache_data := memory.io.icache_data

    memory.io.dcache_req := core.io.dcache_req
    memory.io.dcache_start := core.io.dcache_start
    core.io.dcache_ready := memory.io.dcache_ready
    core.io.dcache_valid := memory.io.dcache_valid
    core.io.dcache_data := memory.io.dcache_data

    core.io.execute := io.execute
    memory.io.debug_req.address    := io.flash_address
    memory.io.debug_req.write_data := io.flash_value
    memory.io.debug_req.op        := MemOp.SW
    memory.io.debug_req.read      := false.B
    memory.io.debug_req.write     := true.B
    memory.io.debug_start         := io.flash && !io.execute

    

    

  //   when(!io.execute) {
	// 	printf("Loading...\n");

	// 	when(io.flash) {
	// 		memory.io.read_1 := false.B
	// 		memory.io.write_1 := true.B
	// 		memory.io.address_1 := io.flash_address
	// 		memory.io.write_value_1 := io.flash_value
	// 	}
	// }
}

object Main extends App {
    ChiselStage.emitSystemVerilogFile(
      new Main(),
      firtoolOpts = Array(
        "-disable-all-randomization",
        "-strip-debug-info",
        "-default-layer-specialization=enable"
      ),
      args = Array("--target-dir", "generated")
    )
}