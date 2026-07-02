package RISCV

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

class Alu(val width: Int = 32) extends Module {
    val io = IO(new Bundle {
        val instruction = Input(new InstructionBundle())
        val valid = Input(Bool())

        val out = Output(new InstructionBundle())
        val out_valid = Output(Bool())

        val ready = Output(Bool())
        val next_ready = Input(Bool())
    })

    val out = RegInit(0.U.asTypeOf(new InstructionBundle))
    io.out := out
    val out_valid = RegInit(false.B)
    io.out_valid := out_valid

    io.ready := io.next_ready

    when(io.next_ready) {
        out := io.instruction
        out_valid := io.valid

        switch(io.instruction.opcode) {
            is("b0010011".U) {
                switch(io.instruction.func3) {
                    // ADDI
                    is("b000".U) {
                        out.rd_value := io.instruction.rs1_value + io.instruction.immediate
                    }
                }
            }
        }
    }
}
