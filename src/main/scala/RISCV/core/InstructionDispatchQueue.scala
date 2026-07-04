package RISCV

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

class QueueEntry extends Bundle {
    val instruction = new InstructionBundle()
    val valid = Bool()
}

class InstructionDispatchQueue() extends Module {
    val io = IO(new Bundle {
        val instruction = Input(new InstructionBundle())
        val valid = Input(Bool())

        val alu_out = Output(new InstructionBundle())
        val alu_out_valid = Output(Bool())
        val alu_ready = Input(Bool())

        val ready = Output(Bool())

        val broadcast_free_valid = Input(Bool())
        val broadcast_free_register = Input(UInt(5.W))
        val broadcast_free_value = Input(UInt(32.W))

        val broadcast_mark_valid = Output(Bool())
        val broadcast_mark_register = Output(UInt(5.W))
    })

    val queue = RegInit(VecInit(Seq.fill(8)(0.U.asTypeOf(new QueueEntry))))

    val last_free_entry = WireInit(0.U(3.W))
    for (n <- 0 to 7) {
        when(queue(n).valid) {
            last_free_entry := n.U(3.W) + 1.U
        }
    }
    val full = queue(7.U).valid

    val first_valid_entry = WireInit(0.U(3.W))
    val first_valid_entry_valid = WireInit(false.B)
    for (n <- 0 to 7) {
        when(queue(7.U - n.U).valid && queue(7.U - n.U).instruction.rs1_valid && queue(7.U - n.U).instruction.rs2_valid && io.alu_ready) {
            first_valid_entry := 7.U - n.U
            first_valid_entry_valid := true.B
        }
    }

    when(io.broadcast_free_valid) {
        for (n <- 0 to 7) {
            when(queue(n.U).instruction.rs1 === io.broadcast_free_register) {
                queue(n.U).instruction.rs1_value := io.broadcast_free_value
                queue(n.U).instruction.rs1_valid := true.B
            }

            when(queue(n.U).instruction.rs2 === io.broadcast_free_register) {
                queue(n.U).instruction.rs2_value := io.broadcast_free_value
                queue(n.U).instruction.rs2_valid := true.B
            }
        }
    }

    io.alu_out := queue(first_valid_entry).instruction
    io.alu_out_valid := first_valid_entry_valid

    val broadcast_mark_valid = WireInit(false.B)
    io.broadcast_mark_valid := broadcast_mark_valid
    val broadcast_mark_register = WireInit(0.U)
    io.broadcast_mark_register := broadcast_mark_register

    when(first_valid_entry_valid) {
        for (n <- 1 to 7) {
            when(n.U > first_valid_entry) {
                queue((n - 1).U) := queue(n.U)

                broadcast_mark_valid := true.B
                broadcast_mark_register := queue(first_valid_entry).instruction.rd

                when(queue(n.U).instruction.rs1 === queue(first_valid_entry).instruction.rd) {
                    queue((n - 1).U).instruction.rs1_valid := false.B
                }

                when(queue(n.U).instruction.rs2 === queue(first_valid_entry).instruction.rd) {
                    queue((n - 1).U).instruction.rs2_valid := false.B
                }
            }
        }

        queue(7.U).valid := false.B
    }

    io.ready := !full

    when(io.valid && !full) {
        when(first_valid_entry_valid) {
            queue(last_free_entry - 1.U).instruction := io.instruction
            queue(last_free_entry - 1.U).valid := true.B

            when(broadcast_mark_valid && broadcast_mark_register === io.instruction.rs1) {
                queue(last_free_entry - 1.U).instruction.rs1_valid := false.B
            }

            when(broadcast_mark_valid && broadcast_mark_register === io.instruction.rs2) {
                queue(last_free_entry - 1.U).instruction.rs2_valid := false.B
            }
        }.otherwise {
            queue(last_free_entry).instruction := io.instruction
            queue(last_free_entry).valid := true.B

            when(broadcast_mark_valid && broadcast_mark_register === io.instruction.rs1) {
                queue(last_free_entry).instruction.rs1_valid := false.B
            }

            when(broadcast_mark_valid && broadcast_mark_register === io.instruction.rs2) {
                queue(last_free_entry).instruction.rs2_valid := false.B
            }
        }
    }

    // printf("\n\n")

    // printf(
    //   "First valid entry: %d First valid entry valid: %b Valid: %b\n",
    //   first_valid_entry,
    //   first_valid_entry_valid,
    //   io.valid
    // )

    // printf(
    //   "Last free entry: %d\n",
    //   last_free_entry
    // )

    // for (n <- 0 to 7) {
    //     printf(
    //       "Queue %d -> valid: %b opcode: %b rs1: %b rs2: %b \n",
    //       n.U,
    //       queue(n.U).valid,
    //       queue(n.U).instruction.opcode,
    //       queue(n.U).instruction.rs1_valid,
    //       queue(n.U).instruction.rs2_valid
    //     )
    // }

    // printf("\n\n")
}
