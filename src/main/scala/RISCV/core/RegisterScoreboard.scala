package RISCV

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

class RegisterScoreboard() extends Module {
    val io = IO(new Bundle {
        val instruction = Input(new InstructionBundle())
        val valid = Input(Bool())

        val idq_ready = Input(Bool())
        val ready = Output(Bool())

        val broadcast_free_valid = Input(Bool())
        val broadcast_free_register = Input(UInt(5.W))
        val broadcast_free_value = Input(UInt(32.W))

        val broadcast_mark_valid = Input(Bool())
        val broadcast_mark_register = Input(UInt(5.W))

        val read_register_1 = Output(UInt(5.W))
        val read_result_1 = Input(UInt(32.W))
        val read_register_2 = Output(UInt(5.W))
        val read_result_2 = Input(UInt(32.W))

        val next_instruction = Output(new InstructionBundle())
        val next_valid = Output(Bool())
    })

    val in_use = RegInit(VecInit(Seq.fill(32)(false.B)))

    io.ready := io.idq_ready

    when(io.broadcast_free_valid) {
        in_use(io.broadcast_free_register) := false.B
    }

    when(io.broadcast_mark_valid) {
        in_use(io.broadcast_mark_register) := true.B
    }

    io.read_register_1 := io.instruction.rs1
    io.read_register_2 := io.instruction.rs2

    val held_instruction = RegInit(0.U.asTypeOf(new InstructionBundle()))
    val held_valid = RegInit(false.B)
    val held_result_1 = RegInit(0.U(32.W))
    val held_result_2 = RegInit(0.U(32.W))

    val held_requires_1 = RegInit(false.B)
    val held_requires_2 = RegInit(false.B)

    io.next_instruction := held_instruction
    io.next_valid := held_valid

    when(held_requires_1) {
        held_result_1 := io.read_result_1
        io.next_instruction.rs1_value := io.read_result_1
        io.next_instruction.rs1_valid := true.B

        held_requires_1 := false.B
    }

    when(held_requires_2) {
        held_result_2 := io.read_result_2
        io.next_instruction.rs2_value := io.read_result_2
        io.next_instruction.rs2_valid := true.B

        held_requires_2 := false.B
    }

    when(io.broadcast_free_valid && io.broadcast_mark_register === held_instruction.rs1) {
        io.next_instruction.rs1_valid := true.B
        io.next_instruction.rs1 := io.broadcast_free_value

        held_instruction.rs1_valid := true.B
        held_instruction.rs1 := io.broadcast_free_value
    }

    when(io.broadcast_free_valid && io.broadcast_mark_register === held_instruction.rs2) {
        io.next_instruction.rs2_valid := true.B
        io.next_instruction.rs2 := io.broadcast_free_value

        held_instruction.rs2_valid := true.B
        held_instruction.rs2 := io.broadcast_free_value
    }

    when(io.broadcast_mark_valid && io.broadcast_mark_register === held_instruction.rs1) {
        io.next_instruction.rs2_valid := false.B

        held_instruction.rs1_valid := false.B
    }

    when(io.broadcast_mark_valid && io.broadcast_mark_register === held_instruction.rs2) {
        io.next_instruction.rs2_valid := false.B

        held_instruction.rs2_valid := false.B
    }

    when(io.idq_ready) {
        held_instruction := io.instruction
        held_valid := io.valid

        held_requires_1 := !in_use(io.instruction.rs1) && !io.instruction.rs1_valid
        held_requires_2 := !in_use(io.instruction.rs2) && !io.instruction.rs2_valid

        when(io.broadcast_free_valid && io.broadcast_mark_register === io.instruction.rs1) {
            held_instruction.rs1_valid := true.B
            held_instruction.rs1 := io.broadcast_free_value
            held_requires_1 := false.B
        }

        when(io.broadcast_free_valid && io.broadcast_mark_register === io.instruction.rs2) {
            held_instruction.rs2_valid := true.B
            held_instruction.rs2 := io.broadcast_free_value
            held_requires_2 := false.B
        }

        when(io.broadcast_mark_valid && io.broadcast_mark_register === io.instruction.rs1) {
            held_instruction.rs1_valid := false.B
            held_requires_1 := false.B
        }

        when(io.broadcast_mark_valid && io.broadcast_mark_register === io.instruction.rs2) {
            held_instruction.rs2_valid := false.B
            held_requires_2 := false.B
        }
    }

    // printf("\n\n")

    // printf("IO valid: %b Held valid: %b\n", io.valid, held_valid)
    // printf("held_requires_1: %b\n", held_requires_1)

    // printf("\n\n")
}
