package RISCV

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import scala.io.Source
import java.nio.file.{Files, Paths}
import java.nio.ByteBuffer
import chisel3.simulator.PeekPokeAPI.TestableRecord

class MainSpec extends AnyFreeSpec with Matchers with ChiselSim {
    "Main should execute Store and Load Instructions correctly" in {
        simulate(new Main()) { dut =>
            // Load hex file, one instruction per line
            val instructions = Source
                .fromFile("./programs/hello.hex")
                .getLines()
                .filter(_.trim.nonEmpty)
                .map(line => java.lang.Long.parseLong(line.trim, 16))
                .toSeq

            dut.io.debug_write.poke(true.B)

            instructions.zipWithIndex.foreach { case (instr, idx) =>
                dut.io.debug_write_address.poke(idx.U)
                dut.io.debug_write_data.poke(instr.U(32.W))
                dut.clock.step(1)
            }

            dut.io.debug_write.poke(false.B)
            dut.clock.step(1)

            dut.io.execute.poke(true.B)
            dut.clock.step(64)
        }
    }

    // "Main should execute Store and Load Instructions correctly" in {
    //     simulate(new Main()) { dut =>
    //         dut.io.debug_write.poke(true.B)

    //         dut.io.debug_write_address.poke(0.U)
    //         dut.io.debug_write_data.poke(0x00004137L.U)
    //         dut.clock.step(1)

    //         dut.io.debug_write_address.poke(1.U)
    //         dut.io.debug_write_data.poke(0x000041b7L.U)
    //         dut.clock.step(1)

    //         dut.io.debug_write_address.poke(2.U)
    //         dut.io.debug_write_data.poke(0x00408093L.U)
    //         dut.clock.step(1)

    //         dut.io.debug_write_address.poke(3.U)
    //         dut.io.debug_write_data.poke(0x00218133L.U)
    //         dut.clock.step(1)

    //         dut.io.debug_write_address.poke(4.U)
    //         dut.io.debug_write_data.poke(0x00110023L.U)
    //         dut.clock.step(1)

    //         dut.io.debug_write_address.poke(5.U)
    //         dut.io.debug_write_data.poke(0xff5ff06fL.U)
    //         dut.clock.step(1)

    //         // dut.io.debug_write_address.poke(0.U)
    //         // dut.io.debug_write_data.poke(0b000000000111_00000_000_00001_0010011.U) // ADDI
    //         // dut.clock.step(1)

    //         // dut.io.debug_write_address.poke(1.U)
    //         // dut.io.debug_write_data.poke(0b0000000_00001_00000_010_00000_0100011.U) // SW
    //         // dut.clock.step(1)

    //         // dut.io.debug_write_address.poke(2.U)
    //         // dut.io.debug_write_data.poke(0b000000000000_00000_010_00010_0000011.U) // LW
    //         // dut.clock.step(1)

    //         dut.io.debug_write.poke(false.B)
    //         dut.clock.step(1)

    //         dut.io.execute.poke(true.B)
    //         dut.clock.step(24)
    //     }
    // }
}
