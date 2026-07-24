package RISCV

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class ReorderBufferTest extends AnyFreeSpec with Matchers with ChiselSim {
    "ReorderBuffer add and retire single entry" in {
        simulate(new ReorderBuffer()) { dut =>
            dut.io.valid.poke(false.B)
            dut.io.buffer_entry.mode.poke(WriteMode.None)
            dut.io.buffer_entry.program_pointer.poke(0.U)
            dut.io.buffer_entry.value.poke(0.U)
            dut.io.buffer_entry.rd.poke(0.U)
            dut.io.write_complete.poke(false.B)

            dut.clock.step(1)

            dut.io.valid.poke(true.B)
            dut.io.buffer_entry.mode.poke(WriteMode.Register)
            dut.io.buffer_entry.program_pointer.poke(0.U)
            dut.io.buffer_entry.value.poke(5.U)
            dut.io.buffer_entry.rd.poke(1.U)

            dut.clock.step(1)

            dut.io.valid.poke(false.B)

            dut.io.complete_valid.poke(true.B)
            dut.io.complete_pointer.poke(0.U)

            dut.clock.step(1)

            dut.io.complete_valid.poke(false.B)

            dut.clock.step(1)

            dut.io.full.expect(false.B)
            dut.io.write_value.expect(5.U)
            dut.io.write_address.expect(1.U)
            dut.io.write_mode.expect(WriteMode.Register)
        }
    }

    "ReorderBuffer add and retire two entries" in {
        simulate(new ReorderBuffer()) { dut =>
            dut.io.valid.poke(false.B)
            dut.io.buffer_entry.mode.poke(WriteMode.None)
            dut.io.buffer_entry.program_pointer.poke(0.U)
            dut.io.buffer_entry.value.poke(0.U)
            dut.io.buffer_entry.rd.poke(0.U)
            dut.io.write_complete.poke(false.B)

            dut.clock.step(1)

            dut.io.valid.poke(true.B)
            dut.io.buffer_entry.mode.poke(WriteMode.Register)
            dut.io.buffer_entry.program_pointer.poke(0.U)
            dut.io.buffer_entry.value.poke(5.U)
            dut.io.buffer_entry.rd.poke(1.U)

            dut.clock.step(1)

            dut.io.buffer_entry.mode.poke(WriteMode.Register)
            dut.io.buffer_entry.program_pointer.poke(1.U)
            dut.io.buffer_entry.value.poke(2.U)
            dut.io.buffer_entry.rd.poke(3.U)

            dut.clock.step(1)

            dut.io.valid.poke(false.B)

            dut.io.complete_valid.poke(true.B)
            dut.io.complete_pointer.poke(1.U)

            dut.clock.step(1)

            dut.io.complete_pointer.poke(0.U)

            dut.clock.step(1)

            dut.io.complete_valid.poke(false.B)

            dut.clock.step(1)

            dut.io.full.expect(false.B)
            dut.io.write_value.expect(5.U)
            dut.io.write_address.expect(1.U)
            dut.io.write_mode.expect(WriteMode.Register)

            dut.clock.step(1)

            dut.io.full.expect(false.B)
            dut.io.write_value.expect(2.U)
            dut.io.write_address.expect(3.U)
            dut.io.write_mode.expect(WriteMode.Register)
        }
    }

    "ReorderBuffer add and retire single memory entry" in {
        simulate(new ReorderBuffer()) { dut =>
            dut.io.valid.poke(false.B)
            dut.io.buffer_entry.mode.poke(WriteMode.None)
            dut.io.buffer_entry.program_pointer.poke(0.U)
            dut.io.buffer_entry.value.poke(0.U)
            dut.io.buffer_entry.rd.poke(0.U)
            dut.io.write_complete.poke(false.B)

            dut.clock.step(1)

            dut.io.valid.poke(true.B)
            dut.io.buffer_entry.mode.poke(WriteMode.Memory)
            dut.io.buffer_entry.program_pointer.poke(0.U)
            dut.io.buffer_entry.value.poke(5.U)
            dut.io.buffer_entry.rd.poke(1.U)

            dut.clock.step(1)

            dut.io.valid.poke(false.B)

            dut.io.complete_valid.poke(true.B)
            dut.io.complete_pointer.poke(0.U)

            dut.clock.step(1)

            dut.io.complete_valid.poke(false.B)

            dut.clock.step(10)

            dut.io.full.expect(false.B)
            dut.io.write_value.expect(5.U)
            dut.io.write_address.expect(1.U)
            dut.io.write_mode.expect(WriteMode.Memory)

            dut.io.write_complete.poke(true.B)

            dut.clock.step(1)

            dut.io.write_complete.poke(false.B)

            dut.io.write_mode.expect(WriteMode.None)
        }
    }

    "ReorderBuffer test full" in {
        simulate(new ReorderBuffer()) { dut =>
            dut.io.valid.poke(false.B)
            dut.io.buffer_entry.mode.poke(WriteMode.None)
            dut.io.buffer_entry.program_pointer.poke(0.U)
            dut.io.buffer_entry.value.poke(0.U)
            dut.io.buffer_entry.rd.poke(0.U)
            dut.io.write_complete.poke(false.B)

            dut.clock.step(1)

            dut.io.valid.poke(true.B)
            dut.io.buffer_entry.mode.poke(WriteMode.Register)
            dut.io.buffer_entry.program_pointer.poke(0.U)
            dut.io.buffer_entry.value.poke(5.U)
            dut.io.buffer_entry.rd.poke(1.U)

            dut.clock.step(256)

            dut.io.valid.poke(false.B)

            dut.io.full.expect(true.B)
        }
    }
}
