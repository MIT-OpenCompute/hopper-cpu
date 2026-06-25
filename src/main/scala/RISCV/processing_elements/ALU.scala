package RISCV

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

class ALU(val width: Int = 32) extends Module {
    val io = IO(new Bundle {
        val instruction = Input(new InstructionBundle())
        val rs1 = Input(UInt(32.W))
        val rs2 = Input(UInt(32.W))
        val instruction_pointer = Input(UInt(32.W))
        val valid = Input(Bool())

        val next_instruction = Output(new InstructionBundle())
        val next_instruction_pointer = Output(UInt(32.W))
        val next_rs1 = Output(UInt(32.W))
        val next_rs2 = Output(UInt(32.W))
        val next_rd = Output(UInt(32.W))
        val next_valid = Output(Bool())

        val consume = Input(Bool())
        val ready = Output(Bool())
    })

    switch(io.instruction.opcode) {
        // LUI
        is("b0110111".U) {
            io.next_rd := io.instruction.immediate;
        }

        // AUIPC
        is("b0010111".U) {
            io.next_rd := io.instruction_pointer + io.instruction.immediate;
        }

        is("b0010011".U) {
            switch(io.instruction.func3) {
                // ADDI
                is("b000".U) {
                    io.next_rd := io.rs1 + io.instruction.immediate
                }

                // SLLI
                is("b001".U) {
                    io.next_rd := io.rs1 << io.instruction.immediate(5, 0)
                }

                // SRLI and SRAI
                is("b101".U) {
                    when(io.instruction.immediate(10) === 1.U) { // SRAI
                        io.next_rd := (io.rs1.asSInt >> io.instruction.immediate(5, 0)).asUInt
                    }.otherwise { // SLAI
                        io.next_rd := io.rs1 >> io.instruction.immediate(5, 0)
                    }
                }

                // SLTI
                is("b010".U) {
                    when(io.rs1.asSInt < io.instruction.immediate.asSInt) {
                        io.next_rd := 1.U;
                    }.otherwise {
                        io.next_rd := 0.U;
                    }
                }

                // SLTIU
                is("b011".U) {
                    when(io.rs1 < io.instruction.immediate) {
                        io.next_rd := 1.U;
                    }.otherwise {
                        io.next_rd := 0.U;
                    }
                }

                // XORI
                is("b100".U) {
                    io.next_rd := io.rs1 ^ io.instruction.immediate
                }

                // ORI
                is("b110".U) {
                    io.next_rd := io.rs1 | io.instruction.immediate
                }

                // ANDI
                is("b111".U) {
                    io.next_rd := io.rs1 & io.instruction.immediate
                }
            }
        }

        is("b0110011".U) {
            switch(io.instruction.func3) {
                // ADD
                is("b0000000".U) {
                    io.next_rd := io.rs1 + io.rs2
                }

                // SUB
                is("b0110000".U) {
                    io.next_rd := io.rs1 - io.rs2
                }

                // SLL
                is("b001".U) {
                    io.next_rd := io.rs1 << io.rs2(5, 0)
                }

                // SRL and SRA
                is("b101".U) {
                    when(io.instruction.immediate(10) === 1.U) { // SRA
                        io.next_rd := (io.rs1.asSInt >> io.rs2(5, 0)).asUInt
                    }.otherwise { // SLA
                        io.next_rd := io.rs1 >> io.rs2(5, 0)
                    }
                }

                // SLT
                is("b010".U) {
                    when(io.rs1.asSInt < io.rs2.asSInt) {
                        io.next_rd := 1.U;
                    }.otherwise {
                        io.next_rd := 0.U;
                    }
                }

                // SLTU
                is("b011".U) {
                    when(io.rs1 < io.rs2) {
                        io.next_rd := 1.U;
                    }.otherwise {
                        io.next_rd := 0.U;
                    }
                }

                // XOR
                is("b100".U) {
                    io.next_rd := io.rs1 ^ io.rs2
                }

                // OR
                is("b110".U) {
                    io.next_rd := io.rs1 | io.rs2
                }

                // AND
                is("b111".U) {
                    io.next_rd := io.rs1 & io.rs2
                }
            }
        }
    }
}
