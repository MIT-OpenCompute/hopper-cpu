package RISCV

import chisel3._
import _root_.circt.stage.ChiselStage
import chisel3.util.experimental.loadMemoryFromFileInline

class Memory() extends Module {
    val io = IO(new Bundle {
        val address_1 = Input(UInt(32.W))
        val write_1 = Input(Bool())
        val write_value_1 = Input(UInt(32.W))
        val read_1 = Input(Bool())
        val read_value_1 = Output(UInt(32.W))

        val address_2 = Input(UInt(32.W))
        val write_2 = Input(Bool())
        val write_value_2 = Input(UInt(32.W))
        val read_2 = Input(Bool())
        val read_value_2 = Output(UInt(32.W))

        val address_vga = Output(UInt(32.W))
        val write_vga = Output(Bool())
        val write_value_vga = Output(UInt(32.W))

        val btns = Input(UInt(4.W))
    })

    val memory = SyncReadMem(4096, UInt(32.W))

    io.read_value_1 := memory.readWrite(
      io.address_1,
      io.write_value_1,
      io.read_1 || io.write_1,
      io.write_1
    )

    val is_vga = io.address_2 >= 0x1000.U
    io.address_vga := io.address_2 - 0x1000.U
    io.write_vga := is_vga && io.write_2
    io.write_value_vga := io.write_value_2

    io.read_value_2 := memory.readWrite(
      io.address_2,
      io.write_value_2,
      (io.read_2 || io.write_2) && !is_vga,
      io.write_2
    )

    val is_btns = RegInit(false.B)
    is_btns := io.read_2 && io.address_2 === 0x12c00000.U // 0x4B000000

    when(is_btns) {
        io.read_value_2 := io.btns
    }
}
