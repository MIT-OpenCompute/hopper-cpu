package RISCV

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class InstructionDispatchQueueTest extends AnyFreeSpec with Matchers with ChiselSim {
    "IDQ add and pop instruction" in {
        simulate(new InstructionDispatchQueue()) { dut =>
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

            dut.io.alu_ready.poke(true.B)

            dut.io.broadcast_valid.poke(false.B)
            dut.io.broadcast_register.poke(0.U)
            dut.io.broadcast_value.poke(0.U)

            dut.clock.step(1)

            dut.io.valid.poke(false.B)

            dut.io.broadcast_valid.poke(true.B)
            dut.io.broadcast_register.poke(1.U)
            dut.io.broadcast_value.poke(2.U)

            dut.clock.step(1)

            dut.io.broadcast_valid.poke(false.B)

            dut.io.alu_out_valid.expect(true.B)
            dut.io.alu_out.opcode.expect("b0010011".U)

            dut.clock.step(1)

            dut.io.alu_out_valid.expect(false.B)

            dut.clock.step(1)
        }
    }
}
