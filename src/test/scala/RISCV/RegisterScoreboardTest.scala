package RISCV

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class RegisterScoreboardTest extends AnyFreeSpec with Matchers with ChiselSim {
    "Register Scoreboard read" in {
        simulate(new RegisterScoreboard()) { dut =>
            dut.io.instruction.rs1.poke(1.U)
            dut.io.instruction.rs1_value.poke(1.U)
            dut.io.instruction.rs1_valid.poke(false.B)
            dut.io.instruction.rs2.poke(0.U)
            dut.io.instruction.rs2_value.poke(0.U)
            dut.io.instruction.rs2_valid.poke(true.B)
            dut.io.instruction.rd.poke(3.U)
            dut.io.instruction.rd_value.poke(0.U)
            dut.io.instruction.immediate.poke(3.U)
            dut.io.instruction.opcode.poke("b0010011".U)
            dut.io.instruction.func3.poke(0.U)
            dut.io.instruction.func7.poke(0.U)
            dut.io.instruction.reorder_pointer.poke(0.U)
            dut.io.instruction.write_mode.poke(WriteMode.Register)
            dut.io.instruction.instruction_pointer.poke(0.U)
            dut.io.valid.poke(true.B)
            dut.io.idq_ready.poke(true.B)
            dut.io.broadcast_free_valid.poke(false.B)
            dut.io.broadcast_free_register.poke(0.U)
            dut.io.broadcast_free_value.poke(0.U)
            dut.io.broadcast_mark_valid.poke(false.B)
            dut.io.broadcast_mark_register.poke(0.U)
            dut.io.read_result_1.poke(0.U)
            dut.io.read_result_2.poke(0.U)
            dut.clock.step(1)

            dut.io.valid.poke(false.B)
            dut.io.read_result_1.poke(2.U)
            dut.io.read_result_2.poke(3.U)

            dut.io.next_valid.expect(true.B)
            dut.io.next_instruction.rs1_value.expect(2.U)
            dut.io.next_instruction.rs1_valid.expect(true.B)
            dut.io.next_instruction.rs2_valid.expect(true.B)

            dut.clock.step(1)
        }
    }
}
