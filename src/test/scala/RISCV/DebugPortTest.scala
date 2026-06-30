package RISCV

import chisel3._
import chisel3.simulator.scalatest.{HasCliOptions, Cli}
import chisel3.simulator._
import org.scalatest.flatspec.AnyFlatSpec

class DebugPortTest extends AnyFlatSpec with HasCliOptions with Cli.EmitVcd with ChiselSim {

  def tieOff(dut: MemoryInterface): Unit = {
    dut.io.icache_req.address.poke(0.U)
    dut.io.icache_req.op.poke(MemOp.LW)
    dut.io.icache_req.write_data.poke(0.U)
    dut.io.icache_req.read.poke(false.B)
    dut.io.icache_req.write.poke(false.B)
    dut.io.icache_start.poke(false.B)
    dut.io.dcache_req.address.poke(0.U)
    dut.io.dcache_req.op.poke(MemOp.LW)
    dut.io.dcache_req.write_data.poke(0.U)
    dut.io.dcache_req.read.poke(false.B)
    dut.io.dcache_req.write.poke(false.B)
    dut.io.dcache_start.poke(false.B)
    dut.io.debug_start.poke(false.B)
    dut.io.debug_req.address.poke(0.U)
    dut.io.debug_req.write_data.poke(0.U)
    dut.io.debug_req.op.poke(MemOp.SW)
    dut.io.debug_req.read.poke(false.B)
    dut.io.debug_req.write.poke(false.B)
  }

  def debugWrite(dut: MemoryInterface, addr: Int, data: Long, tag: String): Unit = {
    dut.io.debug_req.address.poke(addr.U)
    dut.io.debug_req.write_data.poke(data.U)
    dut.io.debug_req.op.poke(MemOp.SW)
    dut.io.debug_req.read.poke(false.B)
    dut.io.debug_req.write.poke(true.B)
    dut.io.debug_start.poke(true.B)
    dut.clock.step()
    assert(dut.io.debug_valid.peek().litToBoolean, s"$tag: debug_valid not asserted")
    dut.io.debug_start.poke(false.B)
    println(s"$tag: wrote 0x${data.toHexString} to 0x${addr.toHexString}")
  }

  // read back via icache to verify data actually reached backing memory
  def icacheRead(dut: MemoryInterface, addr: Int, expected: Long, tag: String): Unit = {
    dut.io.icache_req.address.poke(addr.U)
    dut.io.icache_req.op.poke(MemOp.LW)
    dut.io.icache_req.read.poke(true.B)
    dut.io.icache_req.write.poke(false.B)
    dut.io.icache_req.write_data.poke(0.U)
    dut.io.icache_start.poke(true.B)
    dut.clock.step()
    dut.io.icache_start.poke(false.B)

    var cycles = 0
    while (!dut.io.icache_valid.peek().litToBoolean && cycles < 30) {
      dut.clock.step(); cycles += 1
    }
    assert(dut.io.icache_valid.peek().litToBoolean, s"$tag: icache never valid")
    val got = dut.io.icache_data.peek().litValue.toLong & 0xFFFFFFFFL
    assert(got == expected, s"$tag: expected 0x${expected.toHexString} got 0x${got.toHexString}")
    println(s"$tag: PASS read 0x${got.toHexString} ($cycles cycles)")
    dut.clock.step()
  }

  it should "write a word via debug and read it back via icache" in {
    simulate(new MemoryInterface()) { dut =>
      tieOff(dut)
      dut.clock.step(2)
      debugWrite(dut, 0x00, 0xDEADBEEFL, "write 0x00")
      dut.clock.step(2)
      icacheRead(dut, 0x00, 0xDEADBEEFL, "read 0x00")
    }
  }

  it should "write all 4 words in a cache line and read each back" in {
    simulate(new MemoryInterface()) { dut =>
      tieOff(dut)
      dut.clock.step(2)
      debugWrite(dut, 0x00, 0x11111111L, "write word 0")
      debugWrite(dut, 0x04, 0x22222222L, "write word 1")
      debugWrite(dut, 0x08, 0x33333333L, "write word 2")
      debugWrite(dut, 0x0C, 0x44444444L, "write word 3")
      dut.clock.step(2)
      icacheRead(dut, 0x00, 0x11111111L, "read word 0")
      icacheRead(dut, 0x04, 0x22222222L, "read word 1")
      icacheRead(dut, 0x08, 0x33333333L, "read word 2")
      icacheRead(dut, 0x0C, 0x44444444L, "read word 3")
    }
  }

  it should "write to two different cache lines and read both back" in {
    simulate(new MemoryInterface()) { dut =>
      tieOff(dut)
      dut.clock.step(2)
      debugWrite(dut, 0x00,  0xAAAAAAAAL, "write line 0")
      debugWrite(dut, 0x100, 0xBBBBBBBBL, "write line 1")
      dut.clock.step(2)
      icacheRead(dut, 0x00,  0xAAAAAAAAL, "read line 0")
      icacheRead(dut, 0x100, 0xBBBBBBBBL, "read line 1")
    }
  }
}