package RISCV
import chisel3._
import _root_.circt.stage.ChiselStage
import scala.math._

class ReadStage() extends Module {
  val io = IO(new Bundle {
    val instruction = Input(Valid(new InstructionBundle()))
    val register_read_a = Output(UInt(5.W))
    val register_value_a = Input(UInt(32.W))
    val register_read_b = Output(UInt(5.W))
    val register_value_b = Input(UInt(32.W))
    val next_instruction = Output(Valid(new InstructionBundle()))
    val flush = Input(Bool())
    val stall = Input(Bool())
    val rum  = Input(UInt(32.W))
    val raw_hazard_stall = Output(Bool())
  })

  val rum_t = io.rum & "hFFFFFFFE".U
  val raw_hazard = io.instruction.valid && (rum_t(io.instruction.bits.rs1) || rum_t(io.instruction.bits.rs2))

  io.raw_hazard_stall := raw_hazard

  io.register_read_a := io.instruction.bits.rs1
  io.register_read_b := io.instruction.bits.rs2

  val bundle = RegInit(0.U.asTypeOf(new InstructionBundle()))
  val valid = RegInit(false.B)

  when(io.flush) {
    valid := false.B
  }.elsewhen(raw_hazard) {
    valid := false.B  
  }.elsewhen(io.stall) {
    valid := valid    
  }.otherwise {
    bundle := io.instruction.bits
    bundle.rs1_val := io.register_value_a
    bundle.rs2_val := io.register_value_b
    valid  := io.instruction.valid
  }

  io.next_instruction.bits  := bundle
  io.next_instruction.valid := valid
}