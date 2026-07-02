package RISCV

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class AluTest extends AnyFreeSpec with Matchers with ChiselSim {
    "Alu addi" in {
        simulate(new Alu()) { dut =>
            dut.io.instruction.rs1.poke(1.U)
            dut.io.instruction.rs1_value.poke(1.U)
            dut.io.instruction.rs1_valid.poke(true.B)
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

            dut.io.valid.poke(false.B)
            dut.io.next_ready.poke(true.B)

            dut.clock.step(1)

            dut.io.valid.poke(true.B)

            dut.clock.step(1)

            dut.io.valid.poke(false.B)

            dut.io.out.rd_value.expect(4.U)
            dut.io.out_valid.expect(true.B)
        }
    }

    "Alu next ready" in {
        simulate(new Alu()) { dut =>
            dut.io.instruction.rs1.poke(1.U)
            dut.io.instruction.rs1_value.poke(1.U)
            dut.io.instruction.rs1_valid.poke(true.B)
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

            dut.io.valid.poke(false.B)
            dut.io.next_ready.poke(false.B)

            dut.clock.step(1)

            dut.io.out_valid.expect(false.B)
            dut.io.ready.expect(false.B)

            dut.io.next_ready.poke(true.B)

            dut.clock.step(1)

            dut.io.ready.expect(true.B)
        }
    }
}
