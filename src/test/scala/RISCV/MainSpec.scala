package RISCV

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import scala.collection.mutable
import scala.io.Source

class MainSpec extends AnyFreeSpec with Matchers with ChiselSim {

  "Main should execute memory via 128-bit bus protocol" in {
    simulate(new Main()) { dut =>
      import chisel3.simulator.PeekPokeAPI.{TestableClock, TestableBool, TestableUInt}

      val path  = getClass.getResource("/memory.hex").getPath
      val lines = scala.io.Source.fromFile(path).getLines().toList

      val memoryModel = mutable.Map[Long, BigInt]()
      lines.grouped(4).zipWithIndex.foreach { case (chunk, lineIdx) =>
        var packedLine: BigInt = 0
        chunk.zipWithIndex.foreach { case (hexStr, wordIdx) =>
          val wordVal = BigInt(hexStr.trim, 16)
          packedLine = packedLine | (wordVal << (wordIdx * 32))
        }
        val lineByteAddr = (lineIdx * 16).toLong
        memoryModel(lineByteAddr) = packedLine
        println(s"Loaded Mem Line [0x${lineByteAddr.toHexString}]: 0x${packedLine.toString(16)}")
      }

      // Mirrors AXI_ADDR_MASK / axi_window() in the C++ testbench.
      val AXI_ADDR_MASK = 0x07FFFFFFL
      def axiWindow(addr: Long): Long = addr & AXI_ADDR_MASK

      new TestableBool(dut.io.execute).poke(true.B)
      new TestableBool(dut.io.flash).poke(false.B)
      new TestableBool(dut.io.rxd).poke(true.B)
      new TestableUInt(dut.io.flash_address).poke(0.U)
      new TestableUInt(dut.io.flash_value).poke(0.U)
      new TestableBool(dut.io.mem_req.ready).poke(false.B)
      new TestableBool(dut.io.mem_valid).poke(false.B)
      new TestableUInt(dut.io.mem_resp).poke(0.U)
      new TestableClock(dut.clock).step(2)

      // --- State mirrors the C++ testbench's read/write in-flight tracking ---
      var readInProgress      = false
      var readLatencyCounter  = 0
      var activeReadAddr      = 0L

      var writeInProgress     = false
      var writeLatencyCounter = 0

      for (cycle <- 0 until 200) {
        val reqValid = new TestableBool(dut.io.mem_req.valid).peek().litToBoolean

        // 1. Process incoming handshake (matches C++ step 1)
        if (reqValid) {
          new TestableBool(dut.io.mem_req.ready).poke(true.B)
          val isWrite = new TestableBool(dut.io.mem_req.bits.write).peek().litToBoolean

          if (isWrite) {
            if (!writeInProgress) {
              val addr        = axiWindow(new TestableUInt(dut.io.mem_req.bits.addr).peek().litValue.toLong)
              val alignedAddr = (addr / 16) * 16
              val writeData   = new TestableUInt(dut.io.mem_req.bits.wdata).peek().litValue
              memoryModel(alignedAddr) = writeData
              println(s"[Cycle $cycle] WRITEBACK Captured at Addr: 0x${alignedAddr.toHexString} | Data: 0x${writeData.toString(16)}")
              writeInProgress     = true
              writeLatencyCounter = 1 // matches write_latency_counter = 1
            }
          } else {
            if (!readInProgress) {
              readInProgress     = true
              readLatencyCounter = 4 // matches read_latency_counter = 4
              activeReadAddr     = axiWindow(new TestableUInt(dut.io.mem_req.bits.addr).peek().litValue.toLong)
              println(s"[Cycle $cycle] READ Requested Line at Addr: 0x${activeReadAddr.toHexString}")
            }
          }
        } else {
          new TestableBool(dut.io.mem_req.ready).poke(false.B)
        }

        // 2. Return responses / manage timing (matches C++ step 2)
        if (writeInProgress) {
          if (writeLatencyCounter > 0) {
            writeLatencyCounter -= 1
            new TestableBool(dut.io.mem_valid).poke(false.B)
          } else {
            new TestableBool(dut.io.mem_valid).poke(true.B) // 1-cycle ack pulse
            writeInProgress = false
          }
        } else if (readInProgress) {
          if (readLatencyCounter > 0) {
            readLatencyCounter -= 1
            new TestableBool(dut.io.mem_valid).poke(false.B)
          } else {
            val alignedAddr   = (activeReadAddr / 16) * 16
            val responseData  = memoryModel.getOrElse(alignedAddr, BigInt(0))
            new TestableUInt(dut.io.mem_resp).poke(responseData.U(128.W))
            new TestableBool(dut.io.mem_valid).poke(true.B)
            readInProgress = false
          }
        } else {
          new TestableBool(dut.io.mem_valid).poke(false.B)
        }

        new TestableClock(dut.clock).step(1)
      }
    }
  }
}