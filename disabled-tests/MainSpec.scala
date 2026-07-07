package RISCV

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import scala.io.Source
import chisel3.simulator.PeekPokeAPI._
import scala.collection.mutable

class MainSpec extends AnyFreeSpec with Matchers with ChiselSim {

  "Main should execute memory via 128-bit bus protocol" in {
    simulate(new Main()) { dut =>
      
      // 1. Load the raw 32-bit words from hex file
      val path  = getClass.getResource("/memory.hex").getPath
      val lines = scala.io.Source.fromFile(path).getLines().toList

      // 2. Pack 32-bit hex entries into a 128-bit line memory map 
      // Key: Byte Address (aligned to 16 bytes / 128 bits)
      val memoryModel = mutable.Map[Long, BigInt]()
      
      lines.grouped(4).zipWithIndex.foreach { case (chunk, lineIdx) =>
        var packedLine: BigInt = 0
        // Match the layout order of your Verilog line packer: {word3, word2, word1, word0}
        chunk.zipWithIndex.foreach { case (hexStr, wordIdx) =>
          val wordVal = BigInt(hexStr.trim, 16)
          packedLine = packedLine | (wordVal << (wordIdx * 32))
        }
        val lineByteAddr = (lineIdx * 16).toLong
        memoryModel(lineByteAddr) = packedLine
        println(s"Loaded Mem Line [0x${lineByteAddr.toHexString}]: 0x${packedLine.toString(16)}")
      }

      // 3. Initialize Control Lines (Circumvent Flash entirely)
      dut.io.execute.poke(true.B) // CPU runs instantly
      dut.io.flash.poke(false.B)
      dut.io.flash_address.poke(0.U)
      dut.io.flash_value.poke(0.U)
      
      // Default bus handshake lines down
      dut.io.mem_req.ready.poke(false.B)
      dut.io.mem_valid.poke(false.B)
      dut.io.mem_resp.poke(0.U)
      dut.clock.step(2)

      // 4. Simulated Execution Phase Loop
      for (cycle <- 0 until 1000) {
        
        // Handle incoming requests from the Core/MemoryWrapper
        if (dut.io.mem_req.valid.peek().litToBoolean) {
          // Drive ready high to capture the address transaction step
          dut.io.mem_req.ready.poke(true.B)
          
          // Capture the target memory block address requested by the core
          val requestedAddr = dut.io.mem_req.bits.address.peek().litValue.toLong
          
          // Mask address down to 128-bit boundary consistency checking
          val alignedAddr = requestedAddr & ~0xFL 
          val responseData = memoryModel.getOrElse(alignedAddr, BigInt(0))

          println(s"[Cycle $cycle] CPU Requested Line Read at Addr: 0x${requestedAddr.toHexString}")
          
          // Advance 1 cycle to close out the address command handshake phase
          dut.clock.step(1)
          dut.io.mem_req.ready.poke(false.B)
          
          // Mimic DDR3 Controller Latency Delay: provide data back 
          dut.io.mem_resp.poke(responseData.U)
          dut.io.mem_valid.poke(true.B)
          
          dut.clock.step(1)
          dut.io.mem_valid.poke(false.B)
          
        } else {
          // Drop ready if there is no pending request
          dut.io.mem_req.ready.poke(false.B)
          dut.clock.step(1)
        }
        
        // Debug prints to monitor CPU registers over time
        val currentPC  = dut.io.debug_pc.peek().litValue
        val currentReg = dut.io.debug_reg.peek().litValue
        if (cycle % 10 == 0) {
           println(s"--- Cycle: $cycle | PC: 0x${currentPC.toHexString} | DebugReg: $currentReg ---")
        }
      }
    }
  }
}