package RISCV

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import scala.math._
import os.read



class Main() extends Module {
    val io = IO(new Bundle {
        val execute = Input(Bool());

        val vga_clk = Input(Clock());
        val debug_write = Input(Bool());
        val debug_write_address = Input(UInt(32.W));
        val debug_write_data = Input(UInt(32.W));

        val hsync = Output(Bool())
        val vsync = Output(Bool())
        val rgb = Output(UInt(12.W))
        val blanking = Output(Bool())

        val btns = Input(UInt(4.W))

        val debug_1 = Output(UInt(32.W))
        val debug_2 = Output(UInt(32.W))
    })

  

    val program_pointer = RegInit(0.U(32.W))

    val registers = Module(new Registers())
    registers.io.write_enable := false.B
    registers.io.write_address := 0.U(5.W)
    registers.io.read_address_a := 0.U(5.W)
    registers.io.read_address_b := 0.U(5.W)
    registers.io.read_address_c := 0.U(5.W)
    registers.io.in := 0.U(32.W)



    val memory = Module(new Memory())
    memory.io.btns := io.btns

    val vga_controller = Module(new VGAController())
    vga_controller.io.address := memory.io.address_vga
    vga_controller.io.write := memory.io.write_vga
    vga_controller.io.write_value := memory.io.write_value_vga
    vga_controller.io.read_clk := io.vga_clk
    io.hsync := vga_controller.io.hsync
    io.vsync := vga_controller.io.vsync
    io.rgb := vga_controller.io.rgb
    io.blanking := vga_controller.io.blanking

    val decoder = Module(new Decoder())
    decoder.io.instruction := 0.U;

    // 0 - Load Instruction   1 - Execute Instruction A   2 - Execute Instruction B
    val stage = RegInit(0.U(3.W));



    memory.io.write_1 := false.B
    memory.io.read_1 := false.B
    memory.io.address_1 := 0.U
    memory.io.write_value_1 := 0.U

    // memory.io.write_2 := false.B
    // memory.io.read_2 := false.B
    // memory.io.address_2 := 0.U
    // memory.io.write_value_2 := 0.U



    when(io.debug_write) {
        memory.io.write_1 := true.B
        memory.io.address_1 := io.debug_write_address
        memory.io.write_value_1 := io.debug_write_data
    }

    val immediate_buffer = RegInit(0.U(32.W));
    val rs1_buffer = RegInit(0.U(5.W));
    val rs2_buffer = RegInit(0.U(5.W));
    val rd_buffer = RegInit(0.U(5.W));
    val opcode_buffer = RegInit(0.U(7.W));
    val funct3_buffer = RegInit(0.U(3.W));
    val funct7_buffer = RegInit(0.U(7.W));
    val out_a_buffer =  RegInit(0.U(32.W));
    val out_b_buffer =  RegInit(0.U(32.W));

    io.debug_1 := program_pointer
    io.debug_2 := stage ## opcode_buffer

    val alu = Module(new ALU())
    alu.io.func7 := funct7_buffer;
    alu.io.func3 := funct3_buffer;
    alu.io.a := out_a_buffer;
    alu.io.b := 0.U(32.W)
    alu.io.isM := false.B
    when(io.execute) {
        printf("\n");
        printf("Stage: %d\n", stage);

        when(stage =/= 0.U) {
            printf("Program Pointer: %d\n", program_pointer);
            printf("Data 1: %b\n", memory.io.read_value_1);
            // printf("Data 2: %b\n", memory.io.read_value_2);
            printf("Register 1: %b\n", registers.io.debug_1);
            printf("Register 2: %b\n", registers.io.debug_2);
            printf("Register 3: %b\n", registers.io.debug_3);
            printf("Register 4: %b\n", registers.io.debug_4);
            printf("Register 5: %b\n", registers.io.debug_5);
            printf("Register 6: %b\n", registers.io.debug_6);
            printf("Register 7: %b\n", registers.io.debug_7);
            printf("Register 8: %b\n", registers.io.debug_8);
            printf("Register 9: %b\n", registers.io.debug_9);
            printf("Register10: %b\n", registers.io.debug_10);
        }

        stage := stage + 1.U;

        when(stage === 0.U){
            memory.io.read_1 := true.B
            memory.io.address_1 := program_pointer / 4.U
        }

        when(stage === 1.U) {
            decoder.io.instruction := memory.io.read_value_1

            immediate_buffer := decoder.io.immediate
            rs1_buffer := decoder.io.rs1
            rs2_buffer := decoder.io.rs2
            rd_buffer := decoder.io.rd
            opcode_buffer := decoder.io.opcode
            funct3_buffer := decoder.io.func3
            funct7_buffer := decoder.io.func7
            registers.io.read_address_a := decoder.io.rs1
            registers.io.read_address_b := decoder.io.rs2
            out_a_buffer := registers.io.out_a
            out_b_buffer := registers.io.out_b


        }

        when(stage === 2.U) {



            val pc_plus_4 = program_pointer + 4.U
            val pc_plus_imm = program_pointer + immediate_buffer
            val addr = out_a_buffer + immediate_buffer

            switch(opcode_buffer){
                //Load
                is("b0000011".U) {

                    program_pointer := pc_plus_4;
                    memory.io.read_1 := true.B
                    memory.io.address_1 := (addr) / 4.U;
                    // memory.io.read_2 := true.B
                    // memory.io.address_2 := (addr) / 4.U + 1.U;

                }
                //Store
                is("b0100011".U){

                    program_pointer := pc_plus_4;
                    memory.io.read_1 := true.B
                    memory.io.address_1 := (addr) / 4.U;
                    // memory.io.read_2 := true.B
                    // memory.io.address_2 := (addr) / 4.U + 1.U;

                }
                //ALU Imm,Reg
                is("b0010011".U, "b0110011".U){

                    registers.io.write_address := rd_buffer
                    registers.io.write_enable := true.B
                    program_pointer := pc_plus_4
                    stage := 0.U
                    val neg = Mux(opcode_buffer === "b0110011".U && funct7_buffer(5), - out_b_buffer, out_b_buffer)
                    val alu_b = Mux(opcode_buffer === "b0010011".U, immediate_buffer, neg)
                    alu.io.b := alu_b
                    alu.io.isM := opcode_buffer === "b0110011".U && funct7_buffer === "b0000001".U
                    registers.io.in := alu.io.output

                }
                //Branch
                is("b1100011".U){
                    stage := 0.U;
                    val eq = out_a_buffer === out_b_buffer
                    val lt_signed = out_a_buffer.asSInt < out_b_buffer.asSInt
                    val lt_unsigned = out_a_buffer < out_b_buffer
                    val lt_sel = Mux(funct3_buffer(1), lt_unsigned,lt_signed)
                    val lt_eq_sel = Mux(funct3_buffer(2),lt_sel,eq)
                    val take_branch = lt_eq_sel ^ funct3_buffer(0)
                    program_pointer := Mux(take_branch, pc_plus_imm, pc_plus_4)
                }
                //LUI
                is("b0110111".U){
                    registers.io.write_address := rd_buffer;
                    registers.io.write_enable := true.B;
                    registers.io.in := immediate_buffer;

                    program_pointer := pc_plus_4;
                    stage := 0.U;

                    // printf(
                    //   "[LUI] Rd: %d Immediate: %b\n",
                    //   decoder.io.rd,
                    //   decoder.io.immediate
                    // );
                }
                //AUIPC
                is("b0010111".U){
                    registers.io.write_address := rd_buffer;
                    registers.io.write_enable := true.B;
                    registers.io.in := pc_plus_imm;

                    program_pointer := pc_plus_4;
                    stage := 0.U;

                    // printf(
                    //   "[AUIPC] Rd: %d Immediate: %b\n",
                    //   decoder.io.rd,
                    //   decoder.io.immediate
                    // );
                }
                //JAL
                is("b1101111".U){
                    registers.io.write_address := rd_buffer;
                    registers.io.write_enable := true.B;
                    registers.io.in := pc_plus_4

                    program_pointer := pc_plus_imm;
                    stage := 0.U;

                    // printf(
                    //   "[JAL] Rd: %d Immediate: %b\n",
                    //   decoder.io.rd,
                    //   decoder.io.immediate
                    // );
                }
                //JALR
                is("b1100111".U){
                    registers.io.read_address_a := rs1_buffer;

                    registers.io.write_address := rd_buffer;
                    registers.io.write_enable := true.B;
                    registers.io.in :=pc_plus_4;

                    program_pointer := addr & ~1.U(32.W)
                    stage := 0.U;

                    // printf(
                    //   "[JALR] RS1: %d Rd: %d Immediate: %b\n",
                    //   decoder.io.rs1,
                    //   decoder.io.rd,
                    //   decoder.io.immediate
                    // );
                    
                }
                //FENCE
                is("b0001111".U){
                    program_pointer := pc_plus_4;
                    stage := 0.U;
                    printf("[FENCE]");

                }
               
            }
      

        }

        when(stage === 3.U) {
            stage := 0.U
            val addr = out_a_buffer + immediate_buffer
            val byte_offset = addr(1, 0)  
            val shift_amount = byte_offset ## 0.U(3.W)  

            switch(opcode_buffer){
                //Loads
                is("b0000011".U){

                    registers.io.read_address_a := rs1_buffer
                    registers.io.write_address := rd_buffer
                    registers.io.write_enable := true.B

                    val raw_data = (memory.io.read_value_1 >> shift_amount)

                    switch(funct3_buffer){
                        //LB
                        is("b000".U){ 
                            registers.io.in := Fill(24, raw_data(7)) ## raw_data(7, 0)
                        }
                        //LH
                        is("b001".U){
                            registers.io.in := Fill(16, raw_data(15)) ## raw_data(15, 0)
                        }
                        //LW
                        is("b010".U){
                            registers.io.in := raw_data
                        }
                        //LBU
                        is("b100".U){
                            registers.io.in := 0.U(24.W) ## raw_data(7, 0)
                        }
                        //LHU
                        is("b101".U){
                            registers.io.in := 0.U(16.W) ## raw_data(15, 0)
                        }
                    }
                }
                is("b0100011".U){
                    registers.io.read_address_a := rs1_buffer
                    registers.io.read_address_b := rs2_buffer

                    memory.io.write_1 := true.B
                    memory.io.address_1 := addr / 4.U
                    // memory.io.write_2 := true.B
                    // memory.io.address_2 := addr / 4.U + 1.U

                    val value_1 = WireDefault(memory.io.read_value_1)
                    // val value_2 = WireDefault(memory.io.read_value_2)

                    switch(funct3_buffer){
                        is("b000".U) { // SB
                            switch(byte_offset) {
                                is(0.U) { value_1 := Cat(memory.io.read_value_1(31, 8), out_b_buffer(7, 0)) }
                                is(1.U) { value_1 := Cat(memory.io.read_value_1(31, 16), out_b_buffer(7, 0), memory.io.read_value_1(7, 0)) }
                                is(2.U) { value_1 := Cat(memory.io.read_value_1(31, 24), out_b_buffer(7, 0), memory.io.read_value_1(15, 0)) }
                                is(3.U) { value_1 := Cat(out_b_buffer(7, 0), memory.io.read_value_1(23, 0)) }
                            }
                        }
                        is("b001".U){ //SH
                            switch(byte_offset) {
                                is(0.U) { value_1 := Cat(memory.io.read_value_1(31, 16), out_b_buffer(15, 0)) }
                                is(1.U) { value_1 := Cat(memory.io.read_value_1(31, 24), out_b_buffer(15, 0), memory.io.read_value_1(7, 0)) }
                                is(2.U) { value_1 := Cat(out_b_buffer(15, 0), memory.io.read_value_1(15, 0)) }
                                is(3.U) {
                                    value_1 := Cat(out_b_buffer(7, 0), memory.io.read_value_1(23, 0))
                                    // value_2 := Cat(memory.io.read_value_2(31, 8), out_b_buffer(15, 8))
                                }
                            }
                        }
                        is("b010".U) { // SW
                            switch(byte_offset) {
                                is(0.U) { value_1 := out_b_buffer }
                                is(1.U) {
                                    value_1 := Cat(out_b_buffer(23, 0), memory.io.read_value_1(7, 0))
                                    // value_2 := Cat(memory.io.read_value_2(31, 8), out_b_buffer(31, 24))
                                }
                                is(2.U) {
                                    value_1 := Cat(out_b_buffer(15, 0), memory.io.read_value_1(15, 0))
                                    // value_2 := Cat(memory.io.read_value_2(31, 16), out_b_buffer(31, 16))
                                }
                                is(3.U) {
                                    value_1 := Cat(out_b_buffer(7, 0), memory.io.read_value_1(23, 0))
                                    // value_2 := Cat(memory.io.read_value_2(31, 24), out_b_buffer(31, 8))
                                }
                            }
                        }
                    }
                    memory.io.write_value_1 := value_1
                    // memory.io.write_value_2 := value_2

                }
            }

        
        }
    }
}

object Main extends App {
    ChiselStage.emitSystemVerilogFile(
      new Main(),
      firtoolOpts = Array(
        "-disable-all-randomization",
        "-strip-debug-info",
        "-default-layer-specialization=enable"
      ),
      args = Array("--target-dir", "generated")
    )
}
