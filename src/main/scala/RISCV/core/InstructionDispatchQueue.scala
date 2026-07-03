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

        val broadcast_valid = Input(Bool())
        val broadcast_register = Input(UInt(5.W))
        val broadcast_value = Input(UInt(32.W))
    })

    val queue = RegInit(VecInit(Seq.fill(8)(0.U.asTypeOf(new QueueEntry))))

    val last_entry = WireInit(0.U(3.W))

    for (n <- 0 to 7) {
        when(queue(n).valid) {
            last_entry := n.U(3.W)
        }
    }

    val first_valid_entry = WireInit(0.U(3.W))
    val first_valid_entry_valid = WireInit(false.B)

    for (n <- 0 to 7) {
        when(queue(7.U - n.U).valid && queue(7.U - n.U).instruction.rs1_valid && queue(7.U - n.U).instruction.rs2_valid && io.alu_ready) {
            first_valid_entry := 7.U - n.U
            first_valid_entry_valid := true.B
        }
    }

    when(io.broadcast_valid) {
        for (n <- 0 to 7) {
            when(queue(n.U).instruction.rs1 === io.broadcast_register) {
                queue(n.U).instruction.rs1_value := io.broadcast_value
                queue(n.U).instruction.rs1_valid := true.B
            }

            when(queue(n.U).instruction.rs2 === io.broadcast_register) {
                queue(n.U).instruction.rs2_value := io.broadcast_value
                queue(n.U).instruction.rs2_valid := true.B
            }
        }
    }

    io.alu_out := queue(first_valid_entry).instruction
    io.alu_out_valid := first_valid_entry_valid

    when(first_valid_entry_valid) {
        for (n <- 1 to 7) {
            when(n.U > first_valid_entry) {
                queue((n - 1).U) := queue(n.U)

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

    io.ready := last_entry < 7.U

    when(io.valid && last_entry < 7.U) {
        when(first_valid_entry_valid) {
            queue(last_entry - 1.U).instruction := io.instruction
            queue(last_entry - 1.U).valid := true.B
        }.otherwise {
            queue(last_entry).instruction := io.instruction
            queue(last_entry).valid := true.B
        }
    }

    // printf("\n\n")

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
