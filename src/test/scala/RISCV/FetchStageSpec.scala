package RISCV

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import scala.io.Source
import java.nio.file.{Files, Paths}
import java.nio.ByteBuffer
import chisel3.simulator.PeekPokeAPI.TestableRecord
import scala.io.Source

class FetchStageSpec extends AnyFreeSpec with Matchers with ChiselSim {
    "Fetch Stage should halt correctly" in {
        simulate(new FetchStage()) { dut =>
			// The fetch stage halts when waiting for execute to be true form the core
			// Additionally, there should be no valid result yet
			dut.io.execute.poke(false.B)
			dut.io.program_pointer.poke(0.U)
			dut.io.memory_read_value.poke(0.U)
			dut.io.next_halting.poke(false.B)
			
			dut.io.instruction.expect(0.U)
			dut.io.next_valid.expect(false.B)
			dut.io.halting.expect(true.B)

            dut.clock.step(1)

			// Now we execute, the stage is no longer halting, but no valid value yet
			// We also are passing in 1 as the read memory value
			dut.io.execute.poke(true.B)
			dut.io.memory_read_value.poke(1.U)
			
			dut.io.instruction.expect(1.U)
			dut.io.next_valid.expect(false.B)
			dut.io.halting.expect(false.B)

			dut.clock.step(1)

			// Now value should be valid
			dut.io.instruction.expect(1.U)
			dut.io.next_valid.expect(true.B)
			dut.io.halting.expect(false.B)
        }
    }
}
