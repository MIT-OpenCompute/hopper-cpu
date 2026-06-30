package RISCV

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

class BufferEntry extends Bundle {
    val value = UInt(32.W)
    val rd = UInt(32.W)
    val program_pointer = UInt(32.W)
    val mode = WriteMode()
    val complete = Bool()
}

class ReorderBuffer() extends Module {
    val io = IO(new Bundle {
        val buffer_entry = Input(new BufferEntry())
        val valid = Input(Bool())

        val complete_pointer = Input(UInt(8.W))
        val complete_valid = Input(Bool())

        val full = Output(Bool())
        val write_value = Output(UInt(32.W))
        val write_address = Output(UInt(32.W))
        val write_mode = Output(WriteMode())
        val write_complete = Input(Bool())
    })

    val buffer = RegInit(VecInit(Seq.fill(256)(0.U.asTypeOf(new BufferEntry))))
    val head = RegInit(0.U(8.W))
    val tail = RegInit(0.U(8.W))

    val full = RegInit(false.B)
    io.full := full

    val empty = tail === head

    val waiting_on_write = RegInit(false.B)

    val write_value = RegInit(0.U(32.W))
    io.write_value := write_value
    val write_address = RegInit(0.U(32.W))
    io.write_address := write_address
    val write_mode = RegInit(WriteMode.None)
    io.write_mode := write_mode

    when(io.write_complete) {
        waiting_on_write := false.B
        write_mode := WriteMode.None
    }

    when(!empty && !waiting_on_write && buffer(tail).complete) {
        write_value := buffer(tail).value
        write_address := buffer(tail).rd
        val entry_write_mode = buffer(tail).mode
        write_mode := entry_write_mode

        when(entry_write_mode === WriteMode.Memory) {
            waiting_on_write := true.B
        }

        when(!io.valid) {
            full := false.B
        }

        tail := (tail + 1.U) % 256.U
    }

    when(io.complete_valid) {
        buffer(io.complete_pointer).complete := true.B
    }

    when(!io.full && io.valid) {
        buffer(head) := io.buffer_entry

        head := (head + 1.U) % 256.U

        when((head + 1.U) % 256.U === tail) {
            full := true.B
        }
    }

    // printf("Head: %d Tail %d\n", head, tail)
}
