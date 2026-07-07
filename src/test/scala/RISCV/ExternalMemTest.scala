package RISCV
import chisel3._
import chisel3.simulator.scalatest.{HasCliOptions, Cli}
import chisel3.simulator._
import org.scalatest.flatspec.AnyFlatSpec

class MemoryInterfaceExternalMemTest extends AnyFlatSpec with HasCliOptions with Cli.EmitVcd with ChiselSim {

  // ==========================================================================
  // Behavioral memory model replacing ddr3_line_memory_cdc_v2 / real DDR3 for
  // simulation. Services io.mem_req/io.mem_resp/io.mem_valid every cycle.
  //
  // - Single-outstanding (matches the real single-outstanding protocol):
  //   accepts one request, goes "busy" for `latency` cycles, then responds.
  // - Reads pulse mem_valid with mem_resp data for exactly one cycle.
  // - Writes complete SILENTLY (no mem_valid pulse) -- matches
  //   ddr3_line_memory's own contract, which the arbiter/debug port rely on
  //   (req.ready reasserting is the only completion signal for writes).
  // - Lines are keyed by 16-byte-aligned address; unwritten lines default to
  //   all-zero, matching real (post-calibration) DRAM behavior closely enough
  //   for this purpose.
  //
  // `latency` is deliberately a parameter -- try several different values
  // (small and large) since a timing-DEPENDENT RTL bug (not a setup/hold
  // violation, since real hardware showed positive slack, but a logical race
  // sensitive to relative event ordering) might only show up at specific
  // relative timings between mem_valid and other signals.
  // ==========================================================================
  class SimpleMemModel(latency: Int) {
    private val lines = scala.collection.mutable.Map[BigInt, BigInt]().withDefaultValue(BigInt(0))
    private var busyCyclesLeft: Int = -1
    private var pendingIsWrite: Boolean = false
    private var pendingLineAddr: BigInt = 0

    def service(dut: MemoryInterface): Unit = {
      // `ready` reflects OUR OWN capacity to accept a new request, computed
      // independently of whatever the requester's `valid` is currently doing.
      // This matters because MemoryInterface's debug port drops its own
      // `valid` immediately after acceptance (D_ISSUE -> D_WAIT) and then
      // waits for `ready` to reassert a SECOND time as the write-completion
      // signal -- it does not hold `valid` high for that. A model that only
      // reacts to a fresh `valid` pulse would never see one to react to at
      // completion time, and would deadlock waiting forever.
      val readyNow = (busyCyclesLeft < 0)
      dut.io.mem_req.ready.poke(readyNow.B)

      if (busyCyclesLeft < 0) {
        if (readyNow && dut.io.mem_req.valid.peek().litToBoolean) {
          val rawAddr = dut.io.mem_req.bits.addr.peek().litValue
          val write   = dut.io.mem_req.bits.write.peek().litToBoolean
          val wdata   = dut.io.mem_req.bits.wdata.peek().litValue
          val lineAddr = rawAddr & ~BigInt(15)   // align down to 16 bytes

          pendingLineAddr = lineAddr
          pendingIsWrite  = write
          if (write) lines(lineAddr) = wdata
          busyCyclesLeft = latency
        }
        dut.io.mem_valid.poke(false.B)
      } else {
        if (busyCyclesLeft == 0) {
          if (!pendingIsWrite) {
            dut.io.mem_resp.poke(lines(pendingLineAddr).U(128.W))
            dut.io.mem_valid.poke(true.B)
          } else {
            dut.io.mem_valid.poke(false.B)   // writes complete silently
          }
          busyCyclesLeft = -1   // ready will show true again starting next cycle
        } else {
          dut.io.mem_valid.poke(false.B)
          busyCyclesLeft -= 1
        }
      }
    }
  }

  def stepMem(dut: MemoryInterface, mem: SimpleMemModel): Unit = {
    mem.service(dut)
    dut.clock.step()
  }

  def tieOffICache(dut: MemoryInterface): Unit = {
    dut.io.icache_req.address.poke(0.U)
    dut.io.icache_req.op.poke(MemOp.LW)
    dut.io.icache_req.write_data.poke(0.U)
    dut.io.icache_req.read.poke(false.B)
    dut.io.icache_req.write.poke(false.B)
    dut.io.icache_start.poke(false.B)
  }

  def tieOffDebugIdle(dut: MemoryInterface): Unit = {
    dut.io.debug_req.address.poke(0.U)
    dut.io.debug_req.op.poke(MemOp.SW)
    dut.io.debug_req.write_data.poke(0.U)
    dut.io.debug_req.read.poke(false.B)
    dut.io.debug_req.write.poke(true.B)
    dut.io.debug_start.poke(false.B)
  }

  def tieOffDcacheIdle(dut: MemoryInterface): Unit = {
    dut.io.dcache_req.address.poke(0.U)
    dut.io.dcache_req.op.poke(MemOp.LW)
    dut.io.dcache_req.write_data.poke(0.U)
    dut.io.dcache_req.read.poke(true.B)
    dut.io.dcache_req.write.poke(false.B)
    dut.io.dcache_start.poke(false.B)
  }

  def debugWrite(dut: MemoryInterface, mem: SimpleMemModel, addr: Int, data: Long, tag: String): Unit = {
    var readyCycles = 0
    while (!dut.io.debug_ready.peek().litToBoolean && readyCycles < 200) {
      stepMem(dut, mem)
      readyCycles += 1
    }
    assert(dut.io.debug_ready.peek().litToBoolean, s"$tag: debug never ready (waited $readyCycles cycles)")

    dut.io.debug_req.address.poke(addr.U)
    dut.io.debug_req.write_data.poke(data.U)
    dut.io.debug_req.op.poke(MemOp.SW)
    dut.io.debug_req.read.poke(false.B)
    dut.io.debug_req.write.poke(true.B)
    dut.io.debug_start.poke(true.B)
    stepMem(dut, mem)
    dut.io.debug_start.poke(false.B)

    var cycles = 0
    while (!dut.io.debug_valid.peek().litToBoolean && cycles < 200) {
      stepMem(dut, mem)
      cycles += 1
    }
    assert(dut.io.debug_valid.peek().litToBoolean, s"$tag: debug never valid (waited $cycles cycles)")
    println(s"$tag PASS (ready after $readyCycles, valid after $cycles cycles)")
  }

  def cacheRead(dut: MemoryInterface, mem: SimpleMemModel, addr: Int, expected: Long, tag: String, trace: Boolean = false): Unit = {
    var readyCycles = 0
    while (!dut.io.dcache_ready.peek().litToBoolean && readyCycles < 200) {
      stepMem(dut, mem)
      readyCycles += 1
    }
    assert(dut.io.dcache_ready.peek().litToBoolean, s"$tag: dcache never ready (waited $readyCycles cycles)")

    dut.io.dcache_req.address.poke(addr.U)
    dut.io.dcache_req.op.poke(MemOp.LW)
    dut.io.dcache_req.write_data.poke(0.U)
    dut.io.dcache_req.read.poke(true.B)
    dut.io.dcache_req.write.poke(false.B)
    dut.io.dcache_start.poke(true.B)
    stepMem(dut, mem)
    dut.io.dcache_start.poke(false.B)

    var cycles = 0
    while (!dut.io.dcache_valid.peek().litToBoolean && cycles < 200) {
      if (trace && cycles < 40) {
        val memReqValid = dut.io.mem_req.valid.peek().litToBoolean
        val memReqReady = dut.io.mem_req.ready.peek().litToBoolean
        val memReqAddr  = dut.io.mem_req.bits.addr.peek().litValue
        val memReqWrite = dut.io.mem_req.bits.write.peek().litToBoolean
        val memValid    = dut.io.mem_valid.peek().litToBoolean
        val dcMiss      = dut.dcache.io.miss.peek().litToBoolean
        val dcDone      = dut.dcache.io.done.peek().litToBoolean
        val dcLineValid = dut.dcache.io.line_valid.peek().litToBoolean
        val dcState     = dut.dcache.state.peek().litValue
        val arbIdle     = dut.arbiter.io.idle.peek().litToBoolean
        val arbServingD = dut.arbiter.serving_dcache.peek().litToBoolean
        val arbRespD    = dut.arbiter.io.resp_to_dcache.peek().litToBoolean
        println(s"  [$tag cycle=$cycles] mem_req(v=$memReqValid,r=$memReqReady,addr=0x${memReqAddr.toString(16)},w=$memReqWrite) " +
                s"mem_valid=$memValid || dcache(miss=$dcMiss,done=$dcDone,line_valid=$dcLineValid,state=$dcState) " +
                s"|| arbiter(idle=$arbIdle,serving_dcache=$arbServingD,resp_to_dcache=$arbRespD)")
      }
      stepMem(dut, mem)
      cycles += 1
    }
    assert(dut.io.dcache_valid.peek().litToBoolean, s"$tag: dcache never valid (waited $cycles cycles)")
    val got = dut.io.dcache_data.peek().litValue
    assert(got == BigInt(expected),
      s"$tag: expected 0x${expected.toHexString}, got 0x${got.toString(16)}")
    println(s"$tag PASS")
  }

  // ==========================================================================
  // The actual repro: 8 debug writes (word-packed addressing, 4 words share
  // each 16-byte line -- values 0..3 -> line 0, values 4..7 -> line 1),
  // THEN dcache reads of index 0 and index 4 -- exactly the hardware
  // sequence that failed (match0 correct, match4 wrong).
  //
  // Run this multiple times with different `latency` values below to probe
  // for a timing-dependent race rather than a fixed logic bug.
  // ==========================================================================
  def runRepro(latencyCycles: Int): Unit = {
    simulate(new MemoryInterface()) { dut =>
      tieOffICache(dut)
      tieOffDebugIdle(dut)
      tieOffDcacheIdle(dut)

      val mem = new SimpleMemModel(latency = latencyCycles)
      stepMem(dut, mem)
      stepMem(dut, mem)

      for (i <- 0 until 8) {
        debugWrite(dut, mem, i * 4, i.toLong, s"[latency=$latencyCycles] debug write idx=$i (addr=0x${(i*4).toHexString})")
      }

      cacheRead(dut, mem, 0,  0L, s"[latency=$latencyCycles] read index 0 (word 0 of line 0)", trace = (latencyCycles == 1))
      cacheRead(dut, mem, 16, 4L, s"[latency=$latencyCycles] read index 4 (word 0 of line 1)")
    }
  }

  it should "reproduce hardware failure: word-packed writes then second-line dcache read (latency=5)" in {
    runRepro(5)
  }

  it should "reproduce hardware failure: word-packed writes then second-line dcache read (latency=20)" in {
    runRepro(20)
  }

  it should "reproduce hardware failure: word-packed writes then second-line dcache read (latency=1)" in {
    runRepro(1)
  }

  it should "reproduce hardware failure: word-packed writes then second-line dcache read (latency=50)" in {
    runRepro(50)
  }
}