package RISCV
import chisel3._
import chisel3.simulator.scalatest.{HasCliOptions, Cli}
import chisel3.simulator._
import org.scalatest.flatspec.AnyFlatSpec

class MemoryInterfaceTest extends AnyFlatSpec with HasCliOptions with Cli.EmitVcd with ChiselSim {

  // Tie off icache so it never issues requests during dcache-only tests
  def tieOffICache(dut: MemoryInterface): Unit = {
    dut.io.icache_req.address.poke(0.U)
    dut.io.icache_req.op.poke(MemOp.LW)
    dut.io.icache_req.write_data.poke(0.U)
    dut.io.icache_req.read.poke(false.B)
    dut.io.icache_req.write.poke(false.B)
    dut.io.icache_start.poke(false.B)
  }

  def doRead(dut: MemoryInterface, addr: Int, expectedData: Long, tag: String): Unit = {
    var readyCycles = 0
    while (!dut.io.dcache_ready.peek().litToBoolean && readyCycles < 20) {
      dut.clock.step()
      readyCycles += 1
    }
    assert(dut.io.dcache_ready.peek().litToBoolean, s"$tag: never got ready")

    dut.io.dcache_req.address.poke(addr.U)
    dut.io.dcache_req.op.poke(MemOp.LW)
    dut.io.dcache_req.write_data.poke(0.U)
    dut.io.dcache_req.read.poke(true.B)
    dut.io.dcache_req.write.poke(false.B)
    dut.io.dcache_start.poke(true.B)
    dut.clock.step()
    dut.io.dcache_start.poke(false.B)

    var cycles = 0
    while (!dut.io.dcache_valid.peek().litToBoolean && cycles < 20) {
      dut.clock.step()
      cycles += 1
    }

    assert(dut.io.dcache_valid.peek().litToBoolean, s"$tag: never got valid")
    assert(dut.io.dcache_data.peek().litValue == BigInt(expectedData),
      s"$tag: expected 0x${expectedData.toHexString}, got 0x${dut.io.dcache_data.peek().litValue.toString(16)}")
    println(s"$tag PASS (ready after $readyCycles, valid after $cycles cycles)")
  }

  def doWrite(dut: MemoryInterface, addr: Int, data: Long, tag: String): Unit = {
    var readyCycles = 0
    while (!dut.io.dcache_ready.peek().litToBoolean && readyCycles < 20) {
      dut.clock.step()
      readyCycles += 1
    }
    assert(dut.io.dcache_ready.peek().litToBoolean, s"$tag: never got ready")

    dut.io.dcache_req.address.poke(addr.U)
    dut.io.dcache_req.op.poke(MemOp.SW)
    dut.io.dcache_req.write_data.poke(data.U)
    dut.io.dcache_req.read.poke(false.B)
    dut.io.dcache_req.write.poke(true.B)
    dut.io.dcache_start.poke(true.B)
    dut.clock.step()
    dut.io.dcache_start.poke(false.B)

    var cycles = 0
    while (!dut.io.dcache_ready.peek().litToBoolean && cycles < 20) {
      dut.clock.step()
      cycles += 1
    }

    assert(dut.io.dcache_ready.peek().litToBoolean, s"$tag: never got ready after write")
    println(s"$tag PASS (ready after $readyCycles, valid after $cycles cycles)")
  }

  def doReadOp(
    dut: MemoryInterface,
    addr: Int,
    op: MemOp.Type,
    expectedData: Long,
    tag: String
  ): Unit = {
    var readyCycles = 0
    while (!dut.io.dcache_ready.peek().litToBoolean && readyCycles < 20) {
      dut.clock.step()
      readyCycles += 1
    }
    assert(dut.io.dcache_ready.peek().litToBoolean, s"$tag: never got ready")

    dut.io.dcache_req.address.poke(addr.U)
    dut.io.dcache_req.op.poke(op)
    dut.io.dcache_req.write_data.poke(0.U)
    dut.io.dcache_req.read.poke(true.B)
    dut.io.dcache_req.write.poke(false.B)
    dut.io.dcache_start.poke(true.B)
    dut.clock.step()
    dut.io.dcache_start.poke(false.B)

    var cycles = 0
    while (!dut.io.dcache_valid.peek().litToBoolean && cycles < 20) {
      dut.clock.step()
      cycles += 1
    }

    assert(dut.io.dcache_valid.peek().litToBoolean, s"$tag: never got valid")
    assert(
      dut.io.dcache_data.peek().litValue == BigInt(expectedData),
      s"$tag: expected 0x${expectedData.toHexString}, got 0x${dut.io.dcache_data.peek().litValue.toString(16)}"
    )
    println(s"$tag PASS")
  }

  it should "miss then hit on repeated read (cold miss + warm hit)" in {
    simulate(new MemoryInterface()) { dut =>
      tieOffICache(dut)
      dut.clock.step(2)
      doRead(dut, 0x00, 0L, "Cold miss addr=0x00")
      doRead(dut, 0x00, 0L, "Warm hit addr=0x00")
    }
  }

  it should "handle reads to different words in the same cache line" in {
    simulate(new MemoryInterface()) { dut =>
      tieOffICache(dut)
      dut.clock.step(2)
      doRead(dut, 0x00, 0L, "Fill line - word 0")
      doRead(dut, 0x04, 0L, "Same line - word 1")
      doRead(dut, 0x08, 0L, "Same line - word 2")
      doRead(dut, 0x0C, 0L, "Same line - word 3")
    }
  }

  it should "handle reads to two different cache lines without conflict" in {
    simulate(new MemoryInterface()) { dut =>
      tieOffICache(dut)
      dut.clock.step(2)
      doRead(dut, 0x00,  0L, "Line 0, set 0")
      doRead(dut, 0x100, 0L, "Line 1, set 1")
      doRead(dut, 0x00,  0L, "Hit line 0")
      doRead(dut, 0x100, 0L, "Hit line 1")
    }
  }

  it should "evict and writeback a dirty line on conflict" in {
    simulate(new MemoryInterface()) { dut =>
      tieOffICache(dut)
      dut.clock.step(2)
      doRead(dut, 0x000,  0L, "Fill set 0 with tag 0")
      doRead(dut, 0x1000, 0L, "Evict tag 0, fill set 0 with tag 1")
      doRead(dut, 0x000,  0L, "Re-fill set 0 with tag 0")
    }
  }

  it should "not assert valid without a request" in {
    simulate(new MemoryInterface()) { dut =>
      tieOffICache(dut)
      dut.io.dcache_start.poke(false.B)
      dut.clock.step(5)
      assert(!dut.io.dcache_valid.peek().litToBoolean, "valid should not fire without start")
    }
  }

  it should "write then read back same word" in {
    simulate(new MemoryInterface()) { dut =>
      tieOffICache(dut)
      dut.clock.step(2)
      doWrite(dut, 0x00, 0xDEADBEEFL, "Write 0xDEADBEEF to 0x00")
      doRead(dut,  0x00, 0xDEADBEEFL, "Read back 0xDEADBEEF from 0x00")
    }
  }

  it should "write to different words in same cache line and read back" in {
    simulate(new MemoryInterface()) { dut =>
      tieOffICache(dut)
      dut.clock.step(2)
      doWrite(dut, 0x00, 0x11111111L, "Write word 0")
      doWrite(dut, 0x04, 0x22222222L, "Write word 1")
      doWrite(dut, 0x08, 0x33333333L, "Write word 2")
      doWrite(dut, 0x0C, 0x44444444L, "Write word 3")
      doRead(dut,  0x00, 0x11111111L, "Read back word 0")
      doRead(dut,  0x04, 0x22222222L, "Read back word 1")
      doRead(dut,  0x08, 0x33333333L, "Read back word 2")
      doRead(dut,  0x0C, 0x44444444L, "Read back word 3")
    }
  }

  it should "write then evict then read back from memory" in {
    simulate(new MemoryInterface()) { dut =>
      tieOffICache(dut)
      dut.clock.step(2)
      doWrite(dut, 0x000,  0xCAFEBABEL, "Write to set 0 tag 0")
      doRead(dut,  0x1000, 0L,           "Evict set 0 - bring in tag 1")
      doRead(dut,  0x000,  0xCAFEBABEL, "Read back written value after eviction")
    }
  }

  it should "overwrite a value and read back new value" in {
    simulate(new MemoryInterface()) { dut =>
      tieOffICache(dut)
      dut.clock.step(2)
      doWrite(dut, 0x00, 0x11111111L, "First write")
      doWrite(dut, 0x00, 0xABCDABCDL, "Overwrite")
      doRead(dut,  0x00, 0xABCDABCDL, "Read back overwritten value")
    }
  }

  it should "write and read back 1000 random addresses" in {
    simulate(new MemoryInterface()) { dut =>
      tieOffICache(dut)
      dut.clock.step(2)
      val rng = new scala.util.Random(42)
      val written = scala.collection.mutable.Map[Int, Long]()

      for (_ <- 0 until 1000) {
        val addr = (rng.nextInt(256) * 4)
        val data = rng.nextLong() & 0xFFFFFFFFL
        doWrite(dut, addr, data, s"stress write addr=0x${addr.toHexString} data=0x${data.toHexString}")
        written(addr) = data
      }

      for ((addr, data) <- written) {
        doRead(dut, addr, data, s"stress read addr=0x${addr.toHexString} expected=0x${data.toHexString}")
      }
    }
  }

  it should "interleave reads and writes to same cache line" in {
    simulate(new MemoryInterface()) { dut =>
      tieOffICache(dut)
      dut.clock.step(2)
      doWrite(dut, 0x00, 0xAAAAAAAAL, "write word 0")
      doRead(dut,  0x00, 0xAAAAAAAAL, "read word 0")
      doWrite(dut, 0x04, 0xBBBBBBBBL, "write word 1")
      doRead(dut,  0x00, 0xAAAAAAAAL, "word 0 still intact")
      doRead(dut,  0x04, 0xBBBBBBBBL, "read word 1")
      doWrite(dut, 0x08, 0xCCCCCCCCL, "write word 2")
      doRead(dut,  0x00, 0xAAAAAAAAL, "word 0 still intact after word 2 write")
      doRead(dut,  0x04, 0xBBBBBBBBL, "word 1 still intact after word 2 write")
      doRead(dut,  0x08, 0xCCCCCCCCL, "read word 2")
    }
  }

  it should "survive write after eviction and reload" in {
    simulate(new MemoryInterface()) { dut =>
      tieOffICache(dut)
      dut.clock.step(2)
      doWrite(dut, 0x000, 0xDEADBEEFL, "write set 0 tag 0")
      doRead(dut,  0x1000, 0L,          "load set 0 tag 1 - evicts tag 0")
      doRead(dut,  0x000,  0xDEADBEEFL, "reload set 0 tag 0 - check writeback worked")
      doWrite(dut, 0x000, 0xCAFEBABEL, "overwrite set 0 tag 0")
      doRead(dut,  0x000, 0xCAFEBABEL, "verify overwrite")
    }
  }

  it should "write all words in a line then evict and reload" in {
    simulate(new MemoryInterface()) { dut =>
      tieOffICache(dut)
      dut.clock.step(2)
      doWrite(dut, 0x00, 0x11111111L, "write word 0")
      doWrite(dut, 0x04, 0x22222222L, "write word 1")
      doWrite(dut, 0x08, 0x33333333L, "write word 2")
      doWrite(dut, 0x0C, 0x44444444L, "write word 3")
      doRead(dut,  0x1000, 0L, "evict line")
      doRead(dut,  0x00, 0x11111111L, "reload word 0")
      doRead(dut,  0x04, 0x22222222L, "reload word 1")
      doRead(dut,  0x08, 0x33333333L, "reload word 2")
      doRead(dut,  0x0C, 0x44444444L, "reload word 3")
    }
  }

  it should "support SB and LB at aligned word addresses" in {
    simulate(new MemoryInterface()) { dut =>
      tieOffICache(dut)
      dut.clock.step(2)

      doWrite(dut, 0x00, 0x11223344L, "init word")

      dut.io.dcache_req.address.poke(0x00.U)
      dut.io.dcache_req.op.poke(MemOp.SB)
      dut.io.dcache_req.write_data.poke(0x80.U)
      dut.io.dcache_req.read.poke(false.B)
      dut.io.dcache_req.write.poke(true.B)
      dut.io.dcache_start.poke(true.B)
      dut.clock.step()
      dut.io.dcache_start.poke(false.B)

      doReadOp(dut, 0x00, MemOp.LB, 0xFFFFFF80L, "LB sign extend byte0")
    }
  }

  it should "support SH and LH on halfword-aligned addresses only" in {
    simulate(new MemoryInterface()) { dut =>
      tieOffICache(dut)
      dut.clock.step(2)

      doWrite(dut, 0x00, 0x12345678L, "init word")

      dut.io.dcache_req.address.poke(0x00.U)
      dut.io.dcache_req.op.poke(MemOp.SH)
      dut.io.dcache_req.write_data.poke(0x8001.U)
      dut.io.dcache_req.read.poke(false.B)
      dut.io.dcache_req.write.poke(true.B)
      dut.io.dcache_start.poke(true.B)
      dut.clock.step()
      dut.io.dcache_start.poke(false.B)

      doReadOp(dut, 0x00, MemOp.LH, 0xFFFF8001L, "LH sign extend low halfword")
    }
  }
}