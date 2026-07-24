package RISCV

import chisel3._
import _root_.circt.stage.ChiselStage
import scala.math._

class Core() extends Module {
    val io = IO(new Bundle {
        val execute = Input(Bool())

        val program_memory_adress = Output(UInt(32.W))
        val program_memory_value = Input(UInt(32.W))
        val program_memory_valid = Input(Bool())
    })

    val program_pointer = RegInit(0.U(32.W))
    io.program_memory_adress := program_pointer

    val registers = Module(new Registers())
    registers.io.write_enable := false.B
    registers.io.write_address := 0.U(5.W)
    registers.io.in := 0.U(32.W)
    registers.io.read_address_a := 0.U(5.W)
    registers.io.read_address_b := 0.U(5.W)

    val fetch_stage = Module(new FetchStage())
    fetch_stage.io.execute := io.execute
    fetch_stage.io.program_pointer := program_pointer
    fetch_stage.io.flush := false.B
    fetch_stage.io.memory_read_value := io.program_memory_value
    fetch_stage.io.memory_read_valid := io.program_memory_valid

    val decode_stage = Module(new DecodeStage())
    decode_stage.io.instruction := fetch_stage.io.instruction
    decode_stage.io.instruction_pointer := fetch_stage.io.next_instruction_pointer
    decode_stage.io.valid := fetch_stage.io.next_valid
    decode_stage.io.flush := false.B

    fetch_stage.io.next_ready := decode_stage.io.ready

    val register_scoreboard = Module(new RegisterScoreboard())
    register_scoreboard.io.instruction := decode_stage.io.decoded
    register_scoreboard.io.valid := decode_stage.io.next_valid
    register_scoreboard.io.broadcast_free_value := 0.U
    register_scoreboard.io.broadcast_free_valid := false.B
    register_scoreboard.io.broadcast_free_register := 0.U

    register_scoreboard.io.read_result_1 := registers.io.out_a
    register_scoreboard.io.read_result_2 := registers.io.out_b
    registers.io.read_address_a := register_scoreboard.io.read_register_1
    registers.io.read_address_b := register_scoreboard.io.read_register_2

    decode_stage.io.next_ready := register_scoreboard.io.ready

    val instruction_dispatch_queue = Module(new InstructionDispatchQueue())
    instruction_dispatch_queue.io.instruction := register_scoreboard.io.next_instruction
    instruction_dispatch_queue.io.valid := register_scoreboard.io.next_valid
    instruction_dispatch_queue.io.broadcast_free_valid := false.B
    instruction_dispatch_queue.io.broadcast_free_register := 0.U
    instruction_dispatch_queue.io.broadcast_free_value := 0.U

    register_scoreboard.io.idq_ready := instruction_dispatch_queue.io.ready
    register_scoreboard.io.broadcast_mark_valid := instruction_dispatch_queue.io.broadcast_mark_valid
    register_scoreboard.io.broadcast_mark_register := instruction_dispatch_queue.io.broadcast_mark_register

    val alu_pe = Module(new Alu)
    alu_pe.io.instruction := instruction_dispatch_queue.io.alu_out
    alu_pe.io.valid := instruction_dispatch_queue.io.alu_out_valid

    instruction_dispatch_queue.io.alu_ready := alu_pe.io.ready

    val reorder_buffer = Module(new ReorderBuffer())
    reorder_buffer.io.buffer_entry.value := decode_stage.io.decoded.rd_value
    reorder_buffer.io.buffer_entry.rd := decode_stage.io.decoded.rd
    reorder_buffer.io.buffer_entry.program_pointer := decode_stage.io.decoded.instruction_pointer
    reorder_buffer.io.buffer_entry.mode := decode_stage.io.decoded.write_mode
    reorder_buffer.io.buffer_entry.complete := false.B
    reorder_buffer.io.valid := decode_stage.io.next_valid
    reorder_buffer.io.write_complete := true.B

    reorder_buffer.io.complete_pointer := alu_pe.io.out.reorder_pointer
    reorder_buffer.io.complete_valid := alu_pe.io.out_valid

    alu_pe.io.next_ready := true.B

    when(io.execute) {
        program_pointer := program_pointer + 4.U

        printf("Program Pointer: %d\n", program_pointer);
        printf("[Fetch] Memory Read Value: %b\n", fetch_stage.io.memory_read_value);
        printf("[Fetch] Instruction: %b\n", fetch_stage.io.instruction);
        printf("[Fetch] Next Instruction Pointer: %b\n", fetch_stage.io.next_instruction_pointer);
        printf("[Fetch] Next Valid: %b\n", fetch_stage.io.next_valid);
    }
}

object Core extends App {
    ChiselStage.emitSystemVerilogFile(
      new Core(),
      firtoolOpts = Array(
        "-disable-all-randomization",
        "-strip-debug-info",
        "-default-layer-specialization=enable"
      ),
      args = Array("--target-dir", "generated")
    )
}
