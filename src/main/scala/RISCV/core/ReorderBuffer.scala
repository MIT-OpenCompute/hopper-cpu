package RISCV

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

object WriteMode extends ChiselEnum {
    val None, Register, Memory = Value
}

class BufferEntry extends Bundle {
    val value = UInt(32.W)
    val rd = UInt(32.W)
    val mode = WriteMode()
}

class ReorderBuffer() extends Module {
    val io = IO(new Bundle {
        val instruction = Input(InstructionBundle())
        val valid = Input(Bool())

        val full = Output(Bool())
        val write_value = Output(UInt(32.W))
        val write_mode = Output(WriteMode())
        val write_complete = Input(Bool())
    })

    val buffer = RegInit(VecInit(Seq.fill(256)(0.U.asTypeOf(new BufferEntry))))
    val head = RegInit(255.U(8.W))
    val tail = RegInit(0.U(8.W))

    io.full := tail === head

    val waiting_on_write = RegInit(false.B)

    val write_value = RegInit(0.U(32.W))
    val write_mode = RegInit(WriteMode.None)

    when(io.write_complete) {
        waiting_on_write := false.B
    }

    when(!waiting_on_write && tail =/= head - 1.U) {
        waiting_on_write := true.B

    }
}
