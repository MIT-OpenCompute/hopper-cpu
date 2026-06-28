package RISCV

import chisel3._
import _root_.circt.stage.ChiselStage
import scala.math._

class Core() extends Module {
    val io = IO(new Bundle {
		val execute = Input(Bool())

    val icache_req = Output(new MemReq)
    val icache_start = Output(Bool())
    val icache_ready = Input(Bool())
    val icache_valid = Input(Bool())
    val icache_data = Input(UInt(32.W))

    val dcache_req = Output(new MemReq)
    val dcache_start = Output(Bool())
    val dcache_ready = Input(Bool())
    val dcache_valid = Input(Bool())
    val dcache_data = Input(UInt(32.W))    
    })


    val registers = Module(new Registers())
    registers.io.read_address_a := 0.U(5.W)
    registers.io.read_address_b := 0.U(5.W)

    val fetch = Module(new Fetch())
    val decode = Module(new Decode())
    val read = Module(new Read())
    val execute = Module(new Execute())
    val writeback = Module(new Writeback())


    val raw_stall = read.io.raw_hazard_stall
    val memory_stall = execute.io.memory_stall
    val jump_flush = execute.io.jump_flush


    val fetch_stall = raw_stall || memory_stall
    val fetch_op = Mux(jump_flush,FetchOp.RD, Mux(fetch_stall, FetchOp.ST, FetchOp.DQ))
    fetch.io.f_req.fetch_op := fetch_op
    fetch.io.f_req.redirect_addr := execute.io.pc_redirect.bits

    
    io.icache_req := fetch.io.icache_req
    io.icache_start := fetch.io.icache_start
    fetch.io.icache_ready := io.icache_ready
    fetch.io.icache_valid := io.icache_valid
    fetch.io.icache_data := io.icache_data

    

    decode.io.f2d := fetch.io.f2d
    decode.io.flush := jump_flush
    decode.io.stall := fetch_stall


    


    read.io.instruction := decode.io.decoded
    registers.io.read_address_a := read.io.register_read_a
    registers.io.read_address_b := read.io.register_read_b
    read.io.register_value_a := registers.io.out_a
    read.io.register_value_b := registers.io.out_b

    read.io.flush := jump_flush
    read.io.stall := memory_stall


    val rum = (execute.io.next_instruction.valid.asUInt << execute.io.next_instruction.bits.rd) |
          (read.io.next_instruction.valid.asUInt << read.io.next_instruction.bits.rd) |
          (writeback.io.write_enable.asUInt << writeback.io.write_address)
    read.io.rum := rum





    

    execute.io.instruction := read.io.next_instruction
    
    execute.io.flush := false.B
    execute.io.stall := false.B
    

    io.dcache_req := execute.io.dcache_req
    io.dcache_start := execute.io.dcache_start
    execute.io.dcache_ready := io.dcache_ready
    execute.io.dcache_valid := io.dcache_valid
    execute.io.dcache_data := io.dcache_data




    

    writeback.io.instruction := execute.io.next_instruction

    registers.io.write_enable := writeback.io.write_enable
    registers.io.write_address := writeback.io.write_address
    registers.io.in := writeback.io.write_val



	when(io.execute) {
		// printf("Program Pointer: %d\n", program_pointer);

		// printf("\n\n\n=== Fetch ===\n");
		// printf("Program Pointer: %d\n", program_pointer);
		// printf("Data: %b\n", fetch_stage.io.instruction);
		// printf("Valid: %b\n", fetch_stage.io.next_valid);

		// printf("=== Decode ===\n");
		// printf("Opcode: %b\n", decode_stage.io.decoded.opcode);
		// printf("Immediate: %b\n", decode_stage.io.decoded.immediate);
		// printf("Rd: %d\n", decode_stage.io.decoded.rd);
		// printf("Rs1: %d\n", decode_stage.io.decoded.rs1);
		// printf("Rs2: %d\n", decode_stage.io.decoded.rs2);
		// printf("Valid: %b\n", decode_stage.io.next_valid);

		// printf("=== Read ===\n");
		// printf("Opcode: %b\n", read_stage.io.next_instruction.opcode);
		// printf("A: %b\n", read_stage.io.out_a);
		// printf("B: %b\n", read_stage.io.out_b);
		// printf("Valid: %b\n", read_stage.io.next_valid);
		// printf("RAW Flush Requested: %b\n", read_stage.io.raw_hazard_flush);
		// printf("Jump Target: %d\n", read_stage.io.program_pointer_target);
		// printf("RUM: %b\n", rum);
		// printf("Write Adress: %b\n", read_stage.io.next_instruction.rd);
		// printf("R1: %b\n", read_stage.io.next_instruction.rs1);
		// printf("R2: %b\n", read_stage.io.next_instruction.rs2);
		// printf("RD: %b\n", read_stage.io.next_instruction.rd);

		// printf("=== Execute 1 ===\n");
		// printf("Opcode: %b\n", execute_stage_1.io.next_instruction.opcode);
		// printf("Func3: %b\n", execute_stage_1.io.instruction.func3);
		// printf("Out: %b\n", execute_stage_1.io.out);
		// printf("Valid: %b\n", execute_stage_1.io.next_valid);
		// printf("Read: %b\n", execute_stage_1.io.memory_read);
		// printf("Read Adress: %b\n", execute_stage_1.io.memory_read_address);
		// printf("Jump Flush Requested: %b\n", execute_stage_1.io.program_pointer_jump_flush);
		// printf("Memory Flush Requested: %b\n", execute_stage_1.io.memory_use_flush);
		// printf("Jump Target: %d\n", execute_stage_1.io.program_pointer_target);
		// printf("Write Adress: %b\n", execute_stage_1.io.next_instruction.rd);

		// printf("=== Execute 2 ===\n");
		// printf("Opcode: %b\n", execute_stage_2.io.next_instruction.opcode);
		// printf("Func3: %b\n", execute_stage_2.io.instruction.func3);
		// printf("Read Value: %b\n", execute_stage_2.io.memory_read_value);
		// printf("Write: %b\n", execute_stage_2.io.memory_write);
		// printf("Write Adress: %b\n", execute_stage_2.io.memory_write_address);
		// printf("Write Value: %b\n", execute_stage_2.io.memory_write_value);
		// printf("Out: %b\n", execute_stage_2.io.out);
		// printf("Valid: %b\n", execute_stage_2.io.next_valid);
		// printf("Write Adress: %b\n", execute_stage_2.io.next_instruction.rd);

		// printf("=== Write ===\n");
		// printf("Opcode: %b\n", write_stage.io.instruction.opcode);
		// printf("Write: %b\n", write_stage.io.register_write);
		// printf("Address: %b\n", write_stage.io.register_address);
		// printf("Value: %b\n", write_stage.io.register_value);
		// printf("Valid: %b\n", write_stage.io.next_valid);

		// printf("=== Dump ===\n");
		// printf("01: %b\n", registers.io.debug_1);
		// printf("02: %b\n", registers.io.debug_2);
		// printf("03: %b\n", registers.io.debug_3);
		// printf("04: %b\n", registers.io.debug_4);
		// printf("05: %b\n", registers.io.debug_5);
		// printf("06: %b\n", registers.io.debug_6);
		// printf("07: %b\n", registers.io.debug_7);
		// printf("08: %b\n", registers.io.debug_8);
		// printf("09: %b\n", registers.io.debug_9);
		// printf("10: %b\n", registers.io.debug_10);		
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