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
    })

    val memory = Module(new Memory())
	memory.io.read_1 := true.B
	memory.io.write_1 := false.B
	memory.io.write_value_1 := 0.U

	memory.io.btns := 0.U

    val core = Module(new Core())
    core.io.execute := io.execute
    
    memory.io.address_1 := core.io.program_memory_adress
    core.io.program_memory_value := memory.io.read_value_1

    memory.io.address_2 := core.io.memory_address
    memory.io.read_2 := core.io.memory_read
    memory.io.write_2 := core.io.memory_write
    memory.io.write_value_2 := core.io.memory_write_value
    core.io.memory_read_value := memory.io.read_value_2

    when(!io.execute) {
		printf("Loading...\n");

		when(io.flash) {
			memory.io.read_1 := false.B
			memory.io.write_1 := true.B
			memory.io.address_1 := io.flash_address
			memory.io.write_value_1 := io.flash_value
		}
	}
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