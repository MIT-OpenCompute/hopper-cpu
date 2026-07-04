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

class MainSpec extends AnyFreeSpec with Matchers with ChiselSim {
    // "Core should execute program.hex correctly" in {
    //     simulate(new Core()) { dut =>
	// 		val path = getClass.getResource("/program.hex").getPath
    //         val lines = Source.fromFile(path).getLines().toList

	// 		dut.io.flash.poke(true.B)

    //         lines.zipWithIndex.foreach { case (line, index) =>
	// 			val value = java.lang.Long.parseLong(line.trim, 16)

	// 			dut.io.flash_address.poke((index * 4).U)
	// 			dut.io.flash_value.poke(value.U)
	            
	// 			dut.clock.step(1)
	// 		}	

	// 		dut.io.flash.poke(false.B)

	// 		dut.io.execute.poke(true.B)

    //         dut.clock.step(16)
    //     }
    // }
	"Main should execute memory.hex correctly" in {
		simulate(new Main()) { dut =>
			val path  = getClass.getResource("/memory.hex").getPath
			val lines = scala.io.Source.fromFile(path).getLines().toList

			dut.io.execute.poke(false.B)
			dut.io.flash.poke(false.B)
			dut.clock.step(2)

			lines.zipWithIndex.foreach { case (line, index) =>
				val value = java.lang.Long.parseLong(line.trim, 16)
				val addr  = index * 4
				println(s"flashing addr=0x${addr.toHexString} value=0x${value.toHexString}")

				dut.io.flash.poke(true.B)
				dut.io.flash_address.poke(addr.U)
				dut.io.flash_value.poke(value.U)
				dut.clock.step()  
				assert(dut.io.debug_ready.peek().litToBoolean, s"debug not ready at addr=0x${addr.toHexString}")
				dut.io.flash.poke(false.B)
				dut.clock.step()
			}

			dut.clock.step(2)
			dut.io.execute.poke(true.B)
			dut.clock.step(50)  
		}
	}
}
