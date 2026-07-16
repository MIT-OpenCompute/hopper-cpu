package RISCV

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage


class MALU(val width: Int = 32) extends Module {
    val io = IO(new Bundle {
        val func7 = Input(UInt(7.W));
        val func3 = Input(UInt(3.W));
        val a = Input(UInt(width.W)); // First operand
        val b = Input(UInt(width.W)); // Second operand
        val output = Output(UInt(width.W)); // Result of the operation
    })
    io.output := 0.U;
    val m_alu = Wire(UInt(width.W));
    m_alu := 0.U


  



    val a_s = io.a.asSInt
    val b_s = io.b.asSInt

    switch(io.func3) {
        //MUL
        is("b000".U) {
                m_alu := ((a_s * b_s).asUInt)(31, 0)
        }
        // //MULH
        // is("b001".U) {
        //     m_alu := (a_s * b_s).asUInt(63, 32)
        // }
        // //MULHSU
        // is("b010".U) {
        //     val a_ext = Cat(io.a(31), io.a).asSInt
        //     val b_ext = Cat(0.U(1.W), io.b).asSInt
        //     m_alu := (a_ext * b_ext).asUInt(63, 32)
        // } 
        // //MULHU
        // is("b011".U) {
        //     m_alu := (io.a * io.b)(63, 32)
        // }
        // //DIV
        // is("b100".U) {
        //     when(io.b === 0.U) {
        //         m_alu := Fill(width, 1.U)
        //     }.elsewhen(io.a === (1.U << (width-1)) && b_s === (-1).S) {
        //         m_alu := io.a
        //     }.otherwise {
        //         m_alu := (a_s / b_s).asUInt
        //     }
        // }
        // //DIVU
        // is("b101".U) {
        //     when(io.b === 0.U) {
        //         m_alu := Fill(width, 1.U)
        //     }.otherwise {
        //         m_alu := io.a / io.b
        //     }
        // }
        // //REM
        // is("b110".U) {
        //     when(io.b === 0.U) {
        //         m_alu := io.a
        //     }.elsewhen(io.a === (1.U << (width-1)) && b_s === (-1).S){
        //         m_alu := 0.U
        //     }.otherwise {
        //         m_alu := (a_s % b_s).asUInt
        //     }
        // }
        // //REMU
        // is("b111".U) {
        //     when(io.b === 0.U) {
        //         m_alu := io.a
        //     }.otherwise {
        //         m_alu := io.a % io.b
        //     }
        // }
    }
    
   
    io.output := m_alu

  }