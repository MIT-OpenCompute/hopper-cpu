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

class DecodeStageSpec extends AnyFreeSpec with Matchers with ChiselSim {
    "Decode stage valid and decode" in {
        simulate(new DecodeStage()) { dut =>
			// Data not valid yet
			dut.io.instruction.poke(0.U)
			dut.io.valid.poke(false.B)
			dut.io.flush.poke(false.B)
			
			dut.io.next_valid.expect(false.B)

            dut.clock.step(1)

			// Now valid input but output not valid yet
			dut.io.instruction.poke(0x06400093L.U) // addi x1, x0, 100
			dut.io.valid.poke(true.B)
			
			dut.io.next_valid.expect(false.B)

			dut.clock.step(1)

			// Now decode is valid
			dut.io.instruction.poke(0.U)
			dut.io.valid.poke(false.B)
			
			dut.io.decoded.rd.expect(1.U)
			dut.io.decoded.rs1.expect(0.U)
			dut.io.decoded.immediate.expect(100.U)
			dut.io.decoded.opcode.expect(0b0010011L.U)
			dut.io.next_valid.expect(true.B)
        }
    }

	"Decode stage flush" in {
        simulate(new DecodeStage()) { dut =>
			// Request Flush
			dut.io.instruction.poke(0x06400093L.U) // addi x1, x0, 100
			dut.io.valid.poke(true.B)
			dut.io.flush.poke(true.B)

            dut.clock.step(1)

			// Value is no longer valid			
			dut.io.next_valid.expect(false.B)
        }
    }
}
