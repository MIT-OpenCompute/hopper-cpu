package RISCV

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

/*
The reorder buffer is meant to track the true ordering of instructions so we can make sure that our modifcations to the persistent sate of the processor anr memory
are in order. Otherwise, exceptions would leave the processor in a non deterministic state.

The buffer is a cyclic buffer that tracks a head and tail pointer. We have the size of the buffer hardcoded at 256 for now. Each entry in the buffer corresponds
to an instruction and contains information about how to write to memory or registers. The entry also tracks wether the instruction is complete. The reorder buffer
"retires" completed instructions at the tail in order. When an instruciton is retired it requests a write to the registers or memory.

The reorder buffer can fill up, hence the "full" signal. This would cause the reorder buffer to stall. The reorder buffer also must pause retiring when waiting for the
active memory write to finish.
 */
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

    val buffer = RegInit(VecInit(Seq.fill(256)(0.U.asTypeOf(new BufferEntry()))))
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
