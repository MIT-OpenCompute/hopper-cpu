package RISCV

import chisel3._
import _root_.circt.stage.ChiselStage
import scala.math._

class Core() extends Module {
    val io = IO(new Bundle {
		val execute = Input(Bool())

		val flash = Input(Bool())
		val flash_address = Input(UInt(32.W))
		val flash_value = Input(UInt(32.W))
    })

	val program_pointer = RegInit(0.U(32.W))

    val registers = Module(new Registers())
    registers.io.write_enable := false.B
    registers.io.write_address := 0.U(5.W)
    registers.io.in := 0.U(32.W)
    registers.io.read_address_a := 0.U(5.W)
    registers.io.read_address_b := 0.U(5.W)

	val memory = Module(new Memory())
	memory.io.read_1 := true.B
	memory.io.write_1 := false.B
	memory.io.write_value_1 := 0.U

	memory.io.btns := 0.U

	// ==== FETCH ====

	val fetch_stage = Module(new FetchStage())
	fetch_stage.io.execute := io.execute
	fetch_stage.io.program_pointer := program_pointer
	fetch_stage.io.memory_read_value := memory.io.read_value_1

	memory.io.address_1 := fetch_stage.io.memory_read_address

	// ==== DECODE ====
	
	val decode_stage = Module(new DecodeStage())
	decode_stage.io.instruction := fetch_stage.io.instruction
	decode_stage.io.instruction_pointer := fetch_stage.io.next_instruction_pointer
	decode_stage.io.valid := fetch_stage.io.next_valid

	// ==== READ ====

	val read_stage = Module(new ReadStage())
	read_stage.io.instruction := decode_stage.io.decoded
	read_stage.io.register_value_a := registers.io.out_a
	read_stage.io.register_value_b := registers.io.out_b
	read_stage.io.instruction_pointer := decode_stage.io.next_instruction_pointer
	read_stage.io.valid := decode_stage.io.next_valid

	registers.io.read_address_a := read_stage.io.register_read_a
    registers.io.read_address_b := read_stage.io.register_read_b

	// ==== EXECUTE 1 ====

	val execute_stage_1 = Module(new ExecuteStage1())
	execute_stage_1.io.instruction := read_stage.io.next_instruction
	execute_stage_1.io.rs1 := read_stage.io.out_a
	execute_stage_1.io.rs2 := read_stage.io.out_b
	execute_stage_1.io.instruction_pointer := read_stage.io.next_instruction_pointer
	execute_stage_1.io.valid := read_stage.io.next_valid

	fetch_stage.io.flush := execute_stage_1.io.program_pointer_jump_flush || read_stage.io.raw_hazard_flush || execute_stage_1.io.memory_use_flush
	decode_stage.io.flush := execute_stage_1.io.program_pointer_jump_flush || read_stage.io.raw_hazard_flush || execute_stage_1.io.memory_use_flush
	read_stage.io.flush := execute_stage_1.io.program_pointer_jump_flush || read_stage.io.raw_hazard_flush || execute_stage_1.io.memory_use_flush
	execute_stage_1.io.flush := execute_stage_1.io.memory_use_flush

	when(io.execute) {
		when(execute_stage_1.io.program_pointer_jump_flush || execute_stage_1.io.memory_use_flush) {
			program_pointer := execute_stage_1.io.program_pointer_target
		}.otherwise {
			when(read_stage.io.raw_hazard_flush) {
				program_pointer := read_stage.io.program_pointer_target
			}.otherwise {
				program_pointer := program_pointer + 4.U
			}
		}
	}

	// ==== EXECUTE 2 ====

	val execute_stage_2 = Module(new ExecuteStage2())
	execute_stage_2.io.instruction := execute_stage_1.io.next_instruction
	execute_stage_2.io.rs1 := execute_stage_1.io.next_rs1
	execute_stage_2.io.rs2 := execute_stage_1.io.next_rs2
	execute_stage_2.io.instruction_pointer := execute_stage_1.io.next_instruction_pointer
	execute_stage_2.io.valid := execute_stage_1.io.next_valid
	execute_stage_2.io.previous_out := execute_stage_1.io.out

	execute_stage_2.io.memory_read_value := memory.io.read_value_2

	when(execute_stage_2.io.memory_write) {
		memory.io.read_2 := false.B
		memory.io.write_2 := true.B
		memory.io.address_2 := execute_stage_2.io.memory_write_address
		memory.io.write_value_2 := execute_stage_2.io.memory_write_value
	}.otherwise {
		when(execute_stage_1.io.memory_read) {
			memory.io.read_2 := true.B
			memory.io.write_2 := false.B
			memory.io.address_2 := execute_stage_1.io.memory_read_address
			memory.io.write_value_2 := 0.U
		}.otherwise {
			memory.io.read_2 := false.B
			memory.io.write_2 := false.B
			memory.io.address_2 := 0.U
			memory.io.write_value_2 := 0.U
		}
	}

	// ==== WRITE ====

	val write_stage = Module(new WriteStage())
	write_stage.io.instruction := execute_stage_2.io.next_instruction
	write_stage.io.value := execute_stage_2.io.out
	write_stage.io.valid := execute_stage_2.io.next_valid

	registers.io.write_enable := write_stage.io.register_write
	registers.io.write_address := write_stage.io.register_address
	registers.io.in := write_stage.io.register_value

	when(io.execute) {
		printf("\n\n\n=== Fetch ===\n");
		printf("Program Pointer: %d\n", program_pointer);
		printf("Data: %b\n", fetch_stage.io.instruction);
		printf("Valid: %b\n", fetch_stage.io.next_valid);

		printf("=== Decode ===\n");
		printf("Opcode: %b\n", decode_stage.io.decoded.opcode);
		printf("Immediate: %b\n", decode_stage.io.decoded.immediate);
		printf("Rd: %d\n", decode_stage.io.decoded.rd);
		printf("Rs1: %d\n", decode_stage.io.decoded.rs1);
		printf("Rs2: %d\n", decode_stage.io.decoded.rs2);
		printf("Valid: %b\n", decode_stage.io.next_valid);

		printf("=== Read ===\n");
		printf("A: %b\n", read_stage.io.out_a);
		printf("B: %b\n", read_stage.io.out_b);
		printf("Valid: %b\n", read_stage.io.next_valid);
		printf("RAW Flush Requested: %b\n", read_stage.io.raw_hazard_flush);
		printf("Jump Target: %d\n", read_stage.io.program_pointer_target);

		printf("=== Execute 1 ===\n");
		printf("Opcode: %b\n", execute_stage_1.io.instruction.opcode);
		printf("Func3: %b\n", execute_stage_1.io.instruction.func3);
		printf("Out: %b\n", execute_stage_1.io.out);
		printf("Valid: %b\n", execute_stage_1.io.next_valid);
		printf("Read: %b\n", execute_stage_1.io.memory_read);
		printf("Read Adress: %b\n", execute_stage_1.io.memory_read_address);
		printf("Jump Flush Requested: %b\n", execute_stage_1.io.program_pointer_jump_flush);
		printf("Memory Flush Requested: %b\n", execute_stage_1.io.memory_use_flush);
		printf("Jump Target: %d\n", execute_stage_1.io.program_pointer_target);

		printf("=== Execute 2 ===\n");
		printf("Opcode: %b\n", execute_stage_2.io.instruction.opcode);
		printf("Func3: %b\n", execute_stage_2.io.instruction.func3);
		printf("Read Value: %b\n", memory.io.read_value_2);
		printf("Write: %b\n", execute_stage_2.io.memory_write);
		printf("Write Adress: %b\n", execute_stage_2.io.memory_write_address);
		printf("Write Value: %b\n", execute_stage_2.io.memory_write_value);
		printf("Out: %b\n", execute_stage_2.io.out);
		printf("Valid: %b\n", execute_stage_2.io.next_valid);

		printf("=== Write ===\n");
		printf("Write: %b\n", write_stage.io.register_write);
		printf("Address: %b\n", write_stage.io.register_address);
		printf("Value: %b\n", write_stage.io.register_value);
		printf("Valid: %b\n", write_stage.io.next_valid);

		printf("=== Dump ===\n");
		printf("01: %b\n", registers.io.debug_1);
		printf("02: %b\n", registers.io.debug_2);
		printf("03: %b\n", registers.io.debug_3);
		printf("04: %b\n", registers.io.debug_4);
		printf("05: %b\n", registers.io.debug_5);
		printf("06: %b\n", registers.io.debug_6);
		printf("07: %b\n", registers.io.debug_7);
		printf("08: %b\n", registers.io.debug_8);
		printf("09: %b\n", registers.io.debug_9);
		printf("10: %b\n", registers.io.debug_10);
	}.otherwise {
		printf("Loading...\n");

		when(io.flash) {
			memory.io.read_1 := false.B
			memory.io.write_1 := true.B
			memory.io.address_1 := io.flash_address
			memory.io.write_value_1 := io.flash_value
		}
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