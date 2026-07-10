package RISCV

import chisel3._
import _root_.circt.stage.ChiselStage
import scala.math._
import chisel3.util._


class Main() extends Module {
    val io = IO(new Bundle {
		val execute = Input(Bool())

    val flash = Input(Bool())
		val flash_address = Input(UInt(32.W))
		val flash_value = Input(UInt(32.W))
    val debug_ready = Output(Bool())


    val vga_clk = Input(Clock());
    val hsync = Output(Bool())
    val vsync = Output(Bool())
    val rgb = Output(UInt(12.W))
    val blanking = Output(Bool())

    val mem_req   = Decoupled(new MemLineReq)   
    val mem_resp  = Input(UInt(128.W))
    val mem_valid = Input(Bool()) 

    val debug_reg = Output(UInt(32.W))
    val debug_pc = Output(UInt(32.W))

    val rxd = Input(Bool())
    val txd = Output(Bool())


    })

    val memory = Module(new MemoryWrapper())
    val core = Module(new Core())
    io.debug_reg := core.io.debug_reg
    io.debug_ready := memory.io.debug_ready
    io.debug_pc := core.io.debug_pc
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
    core.io.handshake_bypass := memory.io.handshake_bypass

    core.io.execute := io.execute
    memory.io.debug_req.address    := io.flash_address
    memory.io.debug_req.write_data := io.flash_value
    memory.io.debug_req.op        := MemOp.SW
    memory.io.debug_req.read      := false.B
    memory.io.debug_req.write     := true.B
    memory.io.debug_start         := io.flash && !io.execute

    io.mem_req       <> memory.io.mem_req
    memory.io.mem_resp := io.mem_resp
    memory.io.mem_valid := io.mem_valid

    memory.io.rxd := io.rxd
    io.txd := memory.io.txd

    val vga_controller = Module(new VGAController())
    vga_controller.io.address := memory.io.address_vga
    vga_controller.io.write := memory.io.write_vga
    vga_controller.io.write_value := memory.io.write_value_vga
    vga_controller.io.read_clk := io.vga_clk
    io.hsync := vga_controller.io.hsync
    io.vsync := vga_controller.io.vsync
    io.rgb := vga_controller.io.rgb
    io.blanking := vga_controller.io.blanking



    

    

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