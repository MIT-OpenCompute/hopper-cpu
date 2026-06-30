package RISCV

import chisel3._
import _root_.circt.stage.ChiselStage
import scala.math._

object WriteMode extends ChiselEnum {
    val None, Register, Memory = Value
}

class InstructionBundle extends Bundle {
    val rs1 = UInt(5.W)
    val rs1_value = UInt(32.W)
    val rs1_valid = Bool()
    val rs2 = UInt(5.W)
    val rs2_value = UInt(32.W)
    val rs2_valid = Bool()
    val rd = UInt(5.W)
    val rd_value = UInt(32.W)
    val immediate = UInt(32.W)
    val opcode = UInt(7.W)
    val func3 = UInt(3.W)
    val func7 = UInt(7.W)
    val reorder_pointer = UInt(8.W)
    val write_mode = WriteMode()
    val instruction_pointer = UInt(32.W)
}

class DecodeStage() extends Module {
    val io = IO(new Bundle {
        val instruction = Input(UInt(32.W))
        val instruction_pointer = Input(UInt(32.W))
        val valid = Input(Bool())

        val decoded = Output(new InstructionBundle())
        val next_instruction_pointer = Output(UInt(32.W))
        val next_valid = Output(Bool())

        val flush = Input(Bool())
    })

    val decoder = Module(new Decoder())
    decoder.io.instruction := io.instruction

    val rs1 = RegInit(0.U(5.W))
    rs1 := decoder.io.rs1
    val rs2 = RegInit(0.U(5.W))
    rs2 := decoder.io.rs2
    val rd = RegInit(0.U(5.W))
    rd := decoder.io.rd
    val immediate = RegInit(0.U(32.W))
    immediate := decoder.io.immediate
    val opcode = RegInit(0.U(7.W))
    opcode := decoder.io.opcode
    val func3 = RegInit(0.U(3.W))
    func3 := decoder.io.func3
    val func7 = RegInit(0.U(7.W))
    func7 := decoder.io.func7

    io.decoded.rs1 := rs1
    io.decoded.rs1_value := 0.U
    io.decoded.rs2 := rs2
    io.decoded.rs2_value := 0.U
    io.decoded.rd := rd
    io.decoded.rd_value := 0.U
    io.decoded.immediate := immediate
    io.decoded.opcode := opcode
    io.decoded.func3 := func3
    io.decoded.func7 := func7

    val next_instruction_pointer = RegInit(0.U(32.W))
    next_instruction_pointer := io.instruction_pointer
    io.next_instruction_pointer := next_instruction_pointer

    val valid = RegInit(false.B)
    valid := io.valid && !io.flush
    io.next_valid := valid
}
