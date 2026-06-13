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

        val vga_clk = Input(Clock());
        val hsync = Output(Bool())
        val vsync = Output(Bool())
        val rgb = Output(UInt(12.W))
        val blanking = Output(Bool())

        val btns = Input(UInt(4.W))
    })

    val memory = Module(new Memory())
	memory.io.read_1 := true.B
	memory.io.write_1 := false.B
	memory.io.write_value_1 := 0.U

	memory.io.btns := io.btns

    val vga_controller = Module(new VGAController())
    vga_controller.io.address := memory.io.address_vga
    vga_controller.io.write := memory.io.write_vga
    vga_controller.io.write_value := memory.io.write_value_vga
    vga_controller.io.read_clk := io.vga_clk
    io.hsync := vga_controller.io.hsync
    io.vsync := vga_controller.io.vsync
    io.rgb := vga_controller.io.rgb
    io.blanking := vga_controller.io.blanking

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