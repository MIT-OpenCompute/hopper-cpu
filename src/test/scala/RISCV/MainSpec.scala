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

      new TestableBool(dut.io.execute).poke(true.B) 
      new TestableBool(dut.io.flash).poke(false.B)
      new TestableUInt(dut.io.flash_address).poke(0.U)
      new TestableUInt(dut.io.flash_value).poke(0.U)
      
      new TestableBool(dut.io.mem_req.ready).poke(false.B)
      new TestableBool(dut.io.mem_valid).poke(false.B)
      new TestableUInt(dut.io.mem_resp).poke(0.U)
      new TestableClock(dut.clock).step(2)

     for (cycle <- 0 until 30000) {
  if (new TestableBool(dut.io.mem_req.valid).peek().litToBoolean) {
    // 1. Acknowledge the request immediately
    new TestableBool(dut.io.mem_req.ready).poke(true.B)
    
    val requestedAddr = new TestableUInt(dut.io.mem_req.bits.addr).peek().litValue.toLong
    val alignedAddr   = requestedAddr & ~0xFL 
    val isWrite       = new TestableBool(dut.io.mem_req.bits.write).peek().litToBoolean

    if (isWrite) {
      // --- WRITEBACK HANDLING ---
      val writeData = new TestableUInt(dut.io.mem_req.bits.wdata).peek().litValue
      
      // Update our simulation memory array with the new dirty cache line
      memoryModel(alignedAddr) = writeData
      println(s"[Cycle $cycle] WRITEBACK Captured at Addr: 0x${alignedAddr.toHexString} | Data: 0x${writeData.toString(16)}\n")
      
      // Move past the capture edge
      new TestableClock(dut.clock).step(1)
      new TestableBool(dut.io.mem_req.ready).poke(false.B)
      
      // Pulse response valid to tell the Arbiter/Cache the write transaction is complete
      new TestableBool(dut.io.mem_valid).poke(true.B)
      new TestableClock(dut.clock).step(1)
      new TestableBool(dut.io.mem_valid).poke(false.B)

    } else {
      // --- READ LINE HANDLING ---
      val responseData = memoryModel.getOrElse(alignedAddr, BigInt(0))
      println(s"[Cycle $cycle] READ Requested Line at Addr: 0x${requestedAddr.toHexString}")
      
      new TestableClock(dut.clock).step(1)
      new TestableBool(dut.io.mem_req.ready).poke(false.B)
      
      // Return the requested line data
      new TestableUInt(dut.io.mem_resp).poke(responseData.U(128.W))
      new TestableBool(dut.io.mem_valid).poke(true.B)
      
      new TestableClock(dut.clock).step(1)
      new TestableBool(dut.io.mem_valid).poke(false.B)
    }
    
  } else {
    new TestableBool(dut.io.mem_req.ready).poke(false.B)
    new TestableClock(dut.clock).step(1)
  }
}
    }
  }
}