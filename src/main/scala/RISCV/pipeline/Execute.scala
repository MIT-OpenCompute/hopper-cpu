package RISCV
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

object ExecState extends ChiselEnum {
    val IDLE, MEM_WAIT = Value
}

class Execute() extends Module {
    val io = IO(new Bundle {
        val instruction = Input(Valid(new InstructionBundle()))
        val next_instruction = Output(Valid(new InstructionBundle()))
        val flush = Input(Bool())
        val stall = Input(Bool())

        val pc_redirect = Output(Valid(UInt(32.W)))

        val dcache_req = Output(new MemReq)
        val dcache_start = Output(Bool())
        val dcache_ready = Input(Bool())
        val dcache_valid = Input(Bool())
        val dcache_data = Input(UInt(32.W))
        val handshake_bypass = Input(Bool())

        val memory_stall = Output(Bool())
        val jump_flush = Output(Bool())
    })

    val alu = Module(new ALU())

    val state = RegInit(ExecState.IDLE)
    val bundle = RegInit(0.U.asTypeOf(new InstructionBundle()))
    val valid = RegInit(false.B)

    // defaults
    io.pc_redirect.valid := false.B
    io.pc_redirect.bits := 0.U
    io.dcache_start := false.B
    io.dcache_req.address := 0.U
    io.dcache_req.write_data := 0.U
    io.dcache_req.op := MemOp.LW
    io.dcache_req.read := false.B
    io.dcache_req.write := false.B
    io.memory_stall := false.B
    io.next_instruction.valid := false.B
    io.next_instruction.bits := bundle
    io.jump_flush := false.B

  alu.io.func7 := io.instruction.bits.func7
  alu.io.func3 := io.instruction.bits.func3
  alu.io.a := io.instruction.bits.rs1_val
  alu.io.b := 0.U
  // when(true.B){
  //   printf("EXECUTE %b\n",io.instruction.valid)
  //   printf("Exec flush %b stall %b" , io.flush, io.stall)
  //   when(io.instruction.valid){
  //     printf("EXEC opcode: %b", io.instruction.bits.opcode)
  //   }
  // }

  // when(inst.rs1 === 19.U || inst.rs2 === 19.U || inst.rd === 19.U){
  //         printf("inst.opcode: %b  inst.func7: %b inst.func3 %b inst rs1: %d rs2  %d rd %d rs1 valpc: %d\n",inst.opcode, inst.func7, inst.func3, inst.rs1, inst.rs2, inst.rd, inst.pc )
  // }
  val flush_delay = RegNext(io.jump_flush, false.B)
  switch(state) {
    is(ExecState.IDLE) {
      when(io.flush) {
        valid := false.B
      }.elsewhen(io.stall) {
        valid := valid
        bundle := bundle
      }.elsewhen(io.instruction.valid) {
        val inst = io.instruction.bits
        val pc_plus_4 = inst.pc + 4.U
        val pc_plus_imm = inst.pc + inst.immediate
        val addr = inst.rs1_val + inst.immediate

        
        bundle := inst
        bundle.rd_wen := false.B
        bundle.rd_val := 0.U
        valid := true.B
  
        switch(inst.opcode) {
          

          // ALU reg-imm / reg-reg
          is("b0010011".U, "b0110011".U) {
            val neg   = Mux(inst.opcode === "b0110011".U && inst.func7(5) && !(inst.func3===5.U), -inst.rs2_val, inst.rs2_val)
            val alu_b = Mux(inst.opcode === "b0010011".U, inst.immediate, neg)
            alu.io.b := alu_b
            bundle.rd_val := alu.io.output
            bundle.rd_wen := true.B
    //          printf("ALU pc=%x rd=%d rs1=%x rs2_val=%x imm=%x result=%x\n",
    // inst.pc, inst.rd, inst.rs1_val, inst.rs2_val, inst.immediate, alu.io.output)
            // when(io.instruction.valid){
            //     // printf("EXEC ALUUUUUUUUUUUUUUUUUUu: %b", io.instruction.bits.opcode)
            // }
            
          }

          // Branch
          is("b1100011".U) {

            val eq = inst.rs1_val === inst.rs2_val
            val lt_signed = inst.rs1_val.asSInt < inst.rs2_val.asSInt
            val lt_unsigned = inst.rs1_val < inst.rs2_val
            val lt_sel = Mux(inst.func3(1), lt_unsigned, lt_signed)
            val lt_eq_sel = Mux(inst.func3(2), lt_sel, eq)
            val take_branch = lt_eq_sel ^ inst.func3(0)
            val target = Mux(take_branch, pc_plus_imm, pc_plus_4)
            io.pc_redirect.valid := take_branch
            io.pc_redirect.bits := target
            bundle.rd_wen := false.B
            io.jump_flush := take_branch
            // printf("BRANCH  redir pc: %d inst pc: %d taken %d \n",pc_plus_imm, inst.pc, take_branch)

    //           printf("BRANCH rs1=%x rs2=%x take=%b target=%x flush=%b imm= %x pc=%x\n",
    // inst.rs1_val, inst.rs2_val, take_branch, target, io.jump_flush, inst.immediate, inst.pc)
           
          }

                        // Branch
                        is("b1100011".U) {
                            val eq = inst.rs1_val === inst.rs2_val
                            val lt_signed = inst.rs1_val.asSInt < inst.rs2_val.asSInt
                            val lt_unsigned = inst.rs1_val < inst.rs2_val
                            val lt_sel = Mux(inst.func3(1), lt_unsigned, lt_signed)
                            val lt_eq_sel = Mux(inst.func3(2), lt_sel, eq)
                            val take_branch = lt_eq_sel ^ inst.func3(0)
                            val target = Mux(take_branch, pc_plus_imm, pc_plus_4)
                            io.pc_redirect.valid := take_branch
                            io.pc_redirect.bits := target
                            bundle.rd_wen := false.B
                            io.jump_flush := take_branch
                            //           printf("BRANCH rs1=%x rs2=%x take=%b target=%x flush=%b imm= %x pc=%x\n",
                            // inst.rs1_val, inst.rs2_val, take_branch, target, io.jump_flush, inst.immediate, inst.pc)

                        }

          // JAL
          is("b1101111".U) {
                        // printf("JAL target pc: %d inst pc: %d\n",pc_plus_imm, inst.pc)

            bundle.rd_val := pc_plus_4
            bundle.rd_wen := true.B
            bundle.pc := pc_plus_imm
            io.pc_redirect.valid := true.B
            io.pc_redirect.bits := pc_plus_imm
            io.jump_flush := true.B
          }

          // JALR
          is("b1100111".U) {
                                    // printf("JALR target pc: %d inst pc: %d\n",pc_plus_imm, inst.pc)

            val target = addr & ~1.U(32.W)
            bundle.rd_val := pc_plus_4
            bundle.rd_wen := true.B
            io.pc_redirect.valid := true.B
            io.pc_redirect.bits  := target
            io.jump_flush := true.B
          }

                        // AUIPC
                        is("b0010111".U) {
                            bundle.rd_val := pc_plus_imm
                            bundle.rd_wen := true.B

                        }

          // Store
          is("b0100011".U) {
            // printf("STORING STORING %x addr: %x inst: %d\n",inst.rs2_val, addr, inst.pc)
            io.dcache_req.address := addr
       
            io.dcache_req.write_data := inst.rs2_val
            io.dcache_req.read := false.B
            io.dcache_req.write := true.B
            io.dcache_req.op := MuxLookup(inst.func3, MemOp.SW)(Seq(
              "b000".U -> MemOp.SB,
              "b001".U -> MemOp.SH,
              "b010".U -> MemOp.SW
            ))
            io.dcache_start := true.B
            io.memory_stall := true.B
            bundle.rd_wen := false.B
            when(!io.handshake_bypass){
              state := ExecState.MEM_WAIT
            }.otherwise{
              // printf("\nHANDSHAKE BYPASSING \n")
              valid := true.B
              io.memory_stall := false.B

                        // JALR
                        is("b1100111".U) {
                            val target = addr & ~1.U(32.W)
                            bundle.rd_val := pc_plus_4
                            bundle.rd_wen := true.B
                            io.pc_redirect.valid := true.B
                            io.pc_redirect.bits := target
                            io.jump_flush := true.B
                        }

                        // Load
                        is("b0000011".U) {
                            io.dcache_req.address := addr
                            io.dcache_req.read := true.B
                            io.dcache_req.write := false.B
                            //  printf("LOADING LOADING %x\n",addr)
                            io.dcache_req.op := MuxLookup(inst.func3, MemOp.LW)(
                              Seq(
                                "b000".U -> MemOp.LB,
                                "b001".U -> MemOp.LH,
                                "b010".U -> MemOp.LW,
                                "b100".U -> MemOp.LBU,
                                "b101".U -> MemOp.LHU
                              )
                            )
                            io.dcache_start := true.B
                            io.memory_stall := true.B
                            bundle.rd_wen := true.B

                            when(!io.handshake_bypass) {
                                state := ExecState.MEM_WAIT
                            }.otherwise {
                                valid := true.B
                                bundle.rd_val := io.dcache_data
                                io.memory_stall := false.B
                            }

                        }

                        // Store
                        is("b0100011".U) {
                            // printf("STORING STORING %x addr: %x inst: %d\n",inst.rs2_val, addr, inst.pc)
                            io.dcache_req.address := addr

                            io.dcache_req.write_data := inst.rs2_val
                            io.dcache_req.read := false.B
                            io.dcache_req.write := true.B
                            io.dcache_req.op := MuxLookup(inst.func3, MemOp.SW)(
                              Seq(
                                "b000".U -> MemOp.SB,
                                "b001".U -> MemOp.SH,
                                "b010".U -> MemOp.SW
                              )
                            )
                            io.dcache_start := true.B
                            io.memory_stall := true.B
                            bundle.rd_wen := false.B
                            when(!io.handshake_bypass) {
                                state := ExecState.MEM_WAIT
                            }.otherwise {
                                // printf("\nHANDSHAKE BYPASSING \n")
                                valid := true.B
                                io.memory_stall := false.B

                            }
                        }

                        // FENCE — treat as NOP
                        is("b0001111".U) {
                            bundle.rd_wen := false.B
                        }
                    }
                }
                .otherwise {
                    valid := false.B
                }
        }

        is(ExecState.MEM_WAIT) {
            io.memory_stall := true.B
            // io.dcache_start := true.B
            when(io.flush) {
                state := ExecState.IDLE
                valid := false.B
            }.elsewhen(io.dcache_valid || io.handshake_bypass) {
                bundle.rd_val := io.dcache_data
                state := ExecState.IDLE
                when(io.dcache_data.asUInt === 100.U) {
                    // printf("LOADED LOADED %x\n", io.dcache_data)
                }
                io.memory_stall := false.B
                io.next_instruction.valid := true.B
                io.next_instruction.bits := bundle
                io.next_instruction.bits.rd_val := io.dcache_data
                valid := false.B
            }
        }
    }

    is(ExecState.MEM_WAIT) {
      io.memory_stall := true.B
      // io.dcache_start := true.B
      when(io.flush) {
        state := ExecState.IDLE
        valid := false.B
      }.elsewhen(io.dcache_valid || io.handshake_bypass) {
        bundle.rd_val := io.dcache_data
        state := ExecState.IDLE
        when(io.dcache_data.asUInt === 100.U){
        // printf("LOADED LOADED %x\n",io.dcache_data)
        }
        io.memory_stall := false.B
        io.next_instruction.valid := true.B
        io.next_instruction.bits := bundle
    }
}
