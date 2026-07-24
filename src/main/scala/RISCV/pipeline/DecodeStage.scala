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
        val next_valid = Output(Bool())

        val flush = Input(Bool())

        val next_ready = Input(Bool())
        val ready = Output(Bool())
    })

    val decoder = Module(new Decoder())
    decoder.io.instruction := io.instruction

    val rs1 = RegInit(0.U(5.W))
    val rs2 = RegInit(0.U(5.W))
    val rd = RegInit(0.U(5.W))
    val immediate = RegInit(0.U(32.W))
    val opcode = RegInit(0.U(7.W))
    val func3 = RegInit(0.U(3.W))
    val func7 = RegInit(0.U(7.W))
    val instruction_pointer = RegInit(0.U(32.W))
    val valid = RegInit(false.B)

    when(io.next_ready) {
        immediate := decoder.io.immediate
        rs1 := decoder.io.rs1
        rs2 := decoder.io.rs2
        rd := decoder.io.rd
        opcode := decoder.io.opcode
        func3 := decoder.io.func3
        func7 := decoder.io.func7
        instruction_pointer := io.instruction_pointer
        valid := io.valid && !io.flush
    }

    io.ready := io.next_ready
    io.decoded.rs1 := rs1
    io.decoded.rs1_value := 0.U
    io.decoded.rs1_valid := false.B
    io.decoded.rs2 := rs2
    io.decoded.rs2_value := 0.U
    io.decoded.rs2_valid := false.B
    io.decoded.rd := rd
    io.decoded.rd_value := 0.U
    io.decoded.immediate := immediate
    io.decoded.opcode := opcode
    io.decoded.func3 := func3
    io.decoded.func7 := func7
    io.decoded.reorder_pointer := 0.U
    io.decoded.write_mode := WriteMode.Register
    io.decoded.instruction_pointer := instruction_pointer
    io.next_valid := valid
}
