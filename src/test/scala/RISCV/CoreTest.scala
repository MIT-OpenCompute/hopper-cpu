package RISCV

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class CoreTest extends AnyFreeSpec with Matchers with ChiselSim {
    "Core" in {
        simulate(new Core()) { dut =>
            dut.io.execute.poke(true.B)
            dut.io.program_memory_value.poke(0xdeadfaceL.U)

            dut.clock.step(1)

            dut.io.program_memory_value.poke(0x00a00093L.U) // addi x1, x0, 10
            dut.io.program_memory_valid.poke(true.B)

            dut.clock.step(1)

            dut.io.program_memory_value.poke(0xdeadfaceL.U)
            dut.io.program_memory_valid.poke(false.B)

            dut.clock.step(1)
        }
    }
}
