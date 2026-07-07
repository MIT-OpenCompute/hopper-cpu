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
      
      // Explicitly pull in the exact underlying extension classes to avoid implicit ambiguity
      import chisel3.simulator.PeekPokeAPI.{TestableClock, TestableBool, TestableUInt}

      // 1. Load the raw 32-bit words from hex file
      val path  = getClass.getResource("/memory.hex").getPath
      val lines = scala.io.Source.fromFile(path).getLines().toList

      // 2. Pack 32-bit hex entries into a 128-bit line memory map 
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

      // 3. Initialize Control Lines (Explicit wrappers to completely sidestep compiler implicit errors)
      new TestableBool(dut.io.execute).poke(true.B) 
      new TestableBool(dut.io.flash).poke(false.B)
      new TestableUInt(dut.io.flash_address).poke(0.U)
      new TestableUInt(dut.io.flash_value).poke(0.U)
      
      // Default bus handshake lines down
      new TestableBool(dut.io.mem_req.ready).poke(false.B)
      new TestableBool(dut.io.mem_valid).poke(false.B)
      new TestableUInt(dut.io.mem_resp).poke(0.U)
      new TestableClock(dut.clock).step(2)

      // 4. Simulated Execution Phase Loop
      for (cycle <- 0 until 80) {
        
        // Handle incoming requests from the Core/MemoryWrapper
        if (new TestableBool(dut.io.mem_req.valid).peek().litToBoolean) {
          new TestableBool(dut.io.mem_req.ready).poke(true.B)
          
          val requestedAddr = new TestableUInt(dut.io.mem_req.bits.addr).peek().litValue.toLong
          
          // Mask address down to 128-bit boundary consistency checking
          val alignedAddr = requestedAddr & ~0xFL 
          val responseData = memoryModel.getOrElse(alignedAddr, BigInt(0))

          println(s"[Cycle $cycle] CPU Requested Line Read at Addr: 0x${requestedAddr.toHexString}")
          
          // Advance 1 cycle to close out the address command handshake phase
          new TestableClock(dut.clock).step(1)
          new TestableBool(dut.io.mem_req.ready).poke(false.B)
          
          // Mimic DDR3 Controller Latency Delay: provide data back 
          new TestableUInt(dut.io.mem_resp).poke(responseData.U(128.W))
          new TestableBool(dut.io.mem_valid).poke(true.B)
          
          new TestableClock(dut.clock).step(1)
          new TestableBool(dut.io.mem_valid).poke(false.B)
          
        } else {
          new TestableBool(dut.io.mem_req.ready).poke(false.B)
          new TestableClock(dut.clock).step(1)
        }
        
        // Debug prints to monitor CPU registers over time
        val currentPC  = new TestableUInt(dut.io.debug_pc).peek().litValue
        val currentReg = new TestableUInt(dut.io.debug_reg).peek().litValue
        if (cycle % 10 == 0) {
           println(s"--- Cycle: $cycle | PC: 0x${currentPC.toString(16)} | DebugReg: $currentReg ---")
        }
      }
    }
  }
}