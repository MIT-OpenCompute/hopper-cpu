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



    val fetch_stall = raw_stall || memory_stall || !io.execute
val fetch_stall_prev = RegNext(fetch_stall, true.B)
val fetch_op = Mux(jump_flush, FetchOp.RD,
               Mux(fetch_stall, FetchOp.ST,
               Mux(false.B, FetchOp.ST, FetchOp.DQ)))  // hold one extra cycle on release

    fetch.io.f_req.fetch_op := fetch_op
    fetch.io.f_req.redirect_addr := execute.io.pc_redirect.bits
    fetch.io.execute := io.execute
    
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
    
    execute.io.flush := RegNext(jump_flush)
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

    io.icache_start := fetch.io.icache_start



	
when(io.execute) {
printf("\n\n\n=== Fetch ===\n")
printf("fetch op %d\n",fetch_op.asUInt)
printf("f2d valid: %b\n",  fetch.io.f2d.valid)
printf("f2d pc: %d\n",  fetch.io.f2d.bits.pc)
printf("f2d inst: %b\n",  fetch.io.f2d.bits.inst)
printf("icache valid: %b\n",   fetch.io.icache_valid)
printf("jump_flush=%b fetch_op=%d redirect=%x\n", jump_flush, fetch_op.asUInt, execute.io.pc_redirect.bits)
// printf("decode consumed: %b\n",   decode_consumed)

printf("=== Decode ===\n")
printf("valid: %b\n",  decode.io.decoded.valid)
printf("pc: %d\n",  decode.io.decoded.bits.pc)
printf("opcode: %b\n",  decode.io.decoded.bits.opcode)
printf("func3:%b\n", decode.io.decoded.bits.func3)
printf("func7:%b\n", decode.io.decoded.bits.func7)
printf("rd:   %d\n", decode.io.decoded.bits.rd)
printf("rs1:  %d\n", decode.io.decoded.bits.rs1)
printf("rs2:  %d\n", decode.io.decoded.bits.rs2)
printf("imm:  %d\n", decode.io.decoded.bits.immediate)
printf("rd_wen:%b\n", decode.io.decoded.bits.rd_wen)

printf("=== Read ===\n")
printf("valid:%b\n", read.io.next_instruction.valid)
printf("pc:   %d\n", read.io.next_instruction.bits.pc)
printf("opcode:%b\n", read.io.next_instruction.bits.opcode)
printf("func3:%b\n", read.io.next_instruction.bits.func3)
printf("func7:%b\n", read.io.next_instruction.bits.func7)
printf("rd:   %d\n", read.io.next_instruction.bits.rd)
printf("rd_wen:%b\n", read.io.next_instruction.bits.rd_wen)
printf("rs1:  %d\n", read.io.next_instruction.bits.rs1)
printf("rs1_val:   %x\n", read.io.next_instruction.bits.rs1_val)
printf("rs2:  %d\n", read.io.next_instruction.bits.rs2)
printf("rs2_val:   %x\n", read.io.next_instruction.bits.rs2_val)
printf("imm:  %d\n", read.io.next_instruction.bits.immediate)
printf("raw_stall: %b\n", read.io.raw_hazard_stall)
printf("rum:  %b\n", rum)

printf("=== Execute ===\n")
printf("valid:%b\n", execute.io.next_instruction.valid)
printf("pc:   %d\n", execute.io.next_instruction.bits.pc)
printf("opcode:%b\n", execute.io.next_instruction.bits.opcode)
printf("func3:%b\n", execute.io.next_instruction.bits.func3)
printf("func7:%b\n", execute.io.next_instruction.bits.func7)
printf("rd:   %d\n", execute.io.next_instruction.bits.rd)
printf("rd_wen:%b\n", execute.io.next_instruction.bits.rd_wen)
printf("rd_val:%x\n", execute.io.next_instruction.bits.rd_val)
printf("rs1:  %d\n", execute.io.next_instruction.bits.rs1)
printf("rs1_val:   %x\n", execute.io.next_instruction.bits.rs1_val)
printf("rs2:  %d\n", execute.io.next_instruction.bits.rs2)
printf("rs2_val:   %x\n", execute.io.next_instruction.bits.rs2_val)
printf("imm:  %d\n", execute.io.next_instruction.bits.immediate)
printf("pc_redir:  %b -> %d\n", execute.io.pc_redirect.valid, execute.io.pc_redirect.bits)
printf("jmp_flush: %b\n", execute.io.jump_flush)
printf("mem_stall: %b\n", execute.io.memory_stall)

printf("=== Writeback ===\n")
printf("valid:%b\n", writeback.io.instruction.valid)
printf("pc:   %d\n", writeback.io.instruction.bits.pc)
printf("opcode:%b\n", writeback.io.instruction.bits.opcode)
printf("rd:   %d\n", writeback.io.instruction.bits.rd)
printf("rd_wen:%b\n", writeback.io.instruction.bits.rd_wen)
printf("rd_val:%x\n", writeback.io.instruction.bits.rd_val)
printf("write_en:  %b\n", writeback.io.write_enable)
printf("write_addr:%d\n", writeback.io.write_address)
printf("write_val: %x\n", writeback.io.write_val)
	
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