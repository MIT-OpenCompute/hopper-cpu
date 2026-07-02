package RISCV
import chisel3._
import _root_.circt.stage.ChiselStage
import scala.math._
import chisel3.util._ 

class InstructionBundle extends Bundle {
  val rs1 = UInt(5.W)
  val rs1_val = UInt(32.W)
  val rs2 = UInt(5.W)
  val rs2_val = UInt(32.W)
  val rd = UInt(5.W)
  val rd_val = UInt(32.W)
  val rd_wen = Bool()
  val immediate = UInt(32.W)
  val opcode = UInt(7.W)
  val func3 = UInt(3.W)
  val func7 = UInt(7.W)
  val pc = UInt(32.W)
}

class Decode() extends Module {
  val io = IO(new Bundle {
    val f2d     = Input(Valid(new F2D))
    val decoded = Output(Valid(new InstructionBundle()))
    val flush   = Input(Bool())
    val stall   = Input(Bool())

    // val register_read_a = Output(UInt(5.W))
    // val register_read_b = Output(UInt(5.W))
  })

  val decoder = Module(new Decoder())
  decoder.io.instruction := io.f2d.bits.inst

  val rs1 = RegInit(0.U(5.W))
  val rs2 = RegInit(0.U(5.W))
  // io.register_read_a := rs1
  // io.register_read_b := rs2
  val rd  = RegInit(0.U(5.W))
  val immediate = RegInit(0.U(32.W))
  val opcode = RegInit(0.U(7.W))
  val func3 = RegInit(0.U(3.W))
  val func7 = RegInit(0.U(7.W))
  val pc = RegInit(0.U(32.W))
  val valid = RegInit(false.B)

  when(io.flush) {
    valid := false.B
  }.elsewhen(!io.stall) {
    rs1 := decoder.io.rs1
    rs2 := decoder.io.rs2
    rd := decoder.io.rd
    immediate := decoder.io.immediate
    opcode := decoder.io.opcode
    func3 := decoder.io.func3
    func7 := decoder.io.func7
    pc := io.f2d.bits.pc
    valid := io.f2d.valid
  }


  io.decoded.bits.rs1 := rs1
  io.decoded.bits.rs2 := rs2
  io.decoded.bits.rs1_val := 0.U
  io.decoded.bits.rs2_val := 0.U
  io.decoded.bits.rd_val := 0.U
  io.decoded.bits.rd_wen := false.B
  io.decoded.bits.rd := rd
  io.decoded.bits.immediate := immediate
  io.decoded.bits.opcode := opcode
  io.decoded.bits.func3 := func3
  io.decoded.bits.func7 := func7
  io.decoded.bits.pc := pc
  io.decoded.valid := valid
  printf("DECODE: stall=%b flush=%b f2d_valid=%b f2d_pc=%x | out_valid=%b out_pc=%x out_opcode=%b rd = %d inst = %x\n",
  io.stall,
  io.flush,
  io.f2d.valid,
  io.f2d.bits.pc,
  io.decoded.valid,
  io.decoded.bits.pc,
  io.decoded.bits.opcode,
  io.decoded.bits.rd, RegNext( io.f2d.bits.inst))
}