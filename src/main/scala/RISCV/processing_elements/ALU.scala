package RISCV

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

class ALU(val width: Int = 32) extends Module {
    val io = IO(new Bundle {
        val instruction = Input(new InstructionBundle())
        val valid = Input(Bool())

        val out = Output(new InstructionBundle())
        val out_valid = Output(Bool())

        val consume = Input(Bool())
        val ready = Output(Bool())
    })

    val out = RegInit(new InstructionBundle())
    io.out := out
    val out_valid = RegInit(false.B)
    io.out_valid := out_valid

    io.ready := io.consume

    when(io.consume) {
        out := io.instruction
        out_valid := io.valid

        switch(io.instruction.opcode) {
            is("b0010011".U) {
                switch(io.instruction.func3) {
                    // ADDI
                    is("b000".U) {
                        out.rd_value := instruction.rs1_value + io.instruction.immediate
                    }
                }
            }
        }
    }
}
