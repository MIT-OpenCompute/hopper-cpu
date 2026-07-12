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
    "Fetch stage valid and fetch" in {
        simulate(new FetchStage()) { dut =>
			// Execute is not set yet, so the data should not be valid
			dut.io.execute.poke(false.B)
			dut.io.program_pointer.poke(0.U)
			dut.io.memory_read_value.poke(0.U)
			dut.io.flush.poke(false.B)
			
			dut.io.memory_read_address.expect(0.U)
			dut.io.instruction.expect(0.U)
			dut.io.next_valid.expect(false.B)

            dut.clock.step(1)

			// Now we trigger execute and pass in a data value
			dut.io.execute.poke(true.B)
			dut.io.memory_read_value.poke(1.U)
			
			dut.io.memory_read_address.expect(0.U)
			dut.io.instruction.expect(1.U)
			dut.io.next_valid.expect(false.B)

			dut.clock.step(1)

			// Now value should be valid
			dut.io.memory_read_address.expect(0.U)
			dut.io.instruction.expect(1.U)
			dut.io.next_valid.expect(true.B)
        }
    }

	"Fetch stage flush" in {
        simulate(new FetchStage()) { dut =>
			// Request flush
			dut.io.execute.poke(true.B)
			dut.io.program_pointer.poke(0.U)
			dut.io.memory_read_value.poke(0.U)
			dut.io.flush.poke(true.B)
			
			dut.io.memory_read_address.expect(0.U)
			dut.io.instruction.expect(0.U)
			dut.io.next_valid.expect(false.B)

            dut.clock.step(1)

			// No longer valid			
			dut.io.next_valid.expect(false.B)

			dut.clock.step(1)
        }
    }
}
