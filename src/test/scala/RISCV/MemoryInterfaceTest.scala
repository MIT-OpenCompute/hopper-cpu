package RISCV

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.collection.mutable

class MemoryInterfaceTest extends AnyFlatSpec with Matchers {

  "MemoryInterface" should "correctly handle interleaved ICache and DCache transactions" in {
    simulate(new MemoryInterface()) { dut =>
      
      // Initialize inputs safely
      dut.io.icache_start.poke(false.B)
      dut.io.dcache_start.poke(false.B)
      dut.io.debug_start.poke(false.B)
      dut.io.mem_req.ready.poke(false.B)
      dut.io.mem_valid.poke(false.B)
      dut.clock.step(5)

      // Mock main memory store
      val mockMemory = mutable.Map[BigInt, BigInt]()

      // Pre-populate mock memory line with identifiable data
      mockMemory(BigInt("1000", 16) >> 4) = BigInt("DDDDDDDDCCCCCCCCBBBBBBBBAAAAAAAA", 16)
      mockMemory(BigInt("2000", 16) >> 4) = BigInt("11111111111111111111111111111111", 16)
      mockMemory(BigInt("3000", 16) >> 4) = BigInt("22222222222222222222222222222222", 16)

      // Fixed Memory Bus Driver to mimic MainSpec timing exactly
      def stepMemoryBus(): Unit = {
        if (dut.io.mem_req.valid.peek().litToBoolean) {
          dut.io.mem_req.ready.poke(true.B)
          val addr = dut.io.mem_req.bits.addr.peek().litValue
          val isWrite = dut.io.mem_req.bits.write.peek().litToBoolean
          
          if (isWrite) {
            val wdata = dut.io.mem_req.bits.wdata.peek().litValue
            mockMemory(addr) = wdata
            
            dut.clock.step(1)
            dut.io.mem_req.ready.poke(false.B)
            
            dut.io.mem_valid.poke(true.B)
            dut.clock.step(1)
            dut.io.mem_valid.poke(false.B)
          } else {
            val rdata = mockMemory.getOrElse(addr, BigInt(0))
            
            dut.clock.step(1)
            dut.io.mem_req.ready.poke(false.B)
            
            dut.io.mem_resp.poke(rdata.U(128.W))
            dut.io.mem_valid.poke(true.B)
            dut.clock.step(1)
            dut.io.mem_valid.poke(false.B)
          }
        } else {
          dut.io.mem_req.ready.poke(false.B)
          dut.clock.step(1)
        }
      }

      println("--- Test 1: Cold ICache Miss & Fetch ---")
      dut.io.icache_req.address.poke("x0000_1004".U) 
      dut.io.icache_req.op.poke(MemOp.LW)
      dut.io.icache_req.write.poke(false.B)
      dut.io.icache_req.read.poke(true.B)
      
      dut.io.icache_start.poke(true.B)
      dut.clock.step(1)
      dut.io.icache_start.poke(false.B)

      var timeout = 0
      while (!dut.io.icache_valid.peek().litToBoolean && timeout < 50) {
        stepMemoryBus()
        timeout += 1
      }
      
      dut.io.icache_valid.expect(true.B)
      dut.io.icache_data.expect("xBBBB_BBBB".U) 
      println("Test 1 Passed: ICache line fetched cleanly.")

      println("--- Test 2: DCache Store Hit & Allocation ---")
      dut.io.dcache_req.address.poke("x0000_1000".U) 
      dut.io.dcache_req.write_data.poke("x7777_7777".U)
      dut.io.dcache_req.op.poke(MemOp.SW)
      dut.io.dcache_req.write.poke(true.B)
      dut.io.dcache_req.read.poke(false.B)
      
      dut.io.dcache_start.poke(true.B)
      dut.clock.step(1)
      dut.io.dcache_start.poke(false.B)

      timeout = 0
      while (!dut.io.dcache_valid.peek().litToBoolean && timeout < 50) {
        stepMemoryBus()
        timeout += 1
      }
      dut.io.dcache_valid.expect(true.B)
      println("Test 2 Passed: Line allocated and modified in DCache.")

      println("--- Test 3: Simultaneous Read Requests (Arbiter Contention) ---")
      dut.io.icache_req.address.poke("x0000_2000".U)
      dut.io.icache_req.write.poke(false.B)
      dut.io.icache_req.read.poke(true.B)
      
      dut.io.dcache_req.address.poke("x0000_3000".U)
      dut.io.dcache_req.write.poke(false.B)
      dut.io.dcache_req.read.poke(true.B)

      dut.io.icache_start.poke(true.B)
      dut.io.dcache_start.poke(true.B)
      dut.clock.step(1)
      dut.io.icache_start.poke(false.B)
      dut.io.dcache_start.poke(false.B)

      timeout = 0
      var icacheDone = false
      var dcacheDone = false
      
      while ((!icacheDone || !dcacheDone) && timeout < 100) {
        stepMemoryBus()
        if (dut.io.icache_valid.peek().litToBoolean) icacheDone = true
        if (dut.io.dcache_valid.peek().litToBoolean) dcacheDone = true
        timeout += 1
      }

      icacheDone shouldBe true
      dcacheDone shouldBe true
      println("Test 3 Passed: Contention resolved smoothly without dropouts.")
    }
  }
}