package RISCV

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

class VGAController extends Module {
    val H_VISIBLE = 640
    val H_FRONT = 16
    val H_SYNC = 96
    val H_BACK = 48
    val H_TOTAL = H_VISIBLE + H_FRONT + H_SYNC + H_BACK

    val V_VISIBLE = 480
    val V_FRONT = 10
    val V_SYNC = 2
    val V_BACK = 33
    val V_TOTAL = V_VISIBLE + V_FRONT + V_SYNC + V_BACK

    val io = IO(new Bundle {
        val address = Input(UInt(32.W))
        val write = Input(Bool())
        val write_value = Input(UInt(8.W))
        val read_clk = Input(Clock())

        val hsync = Output(Bool())
        val vsync = Output(Bool())
        val rgb = Output(UInt(12.W))
        val blanking = Output(Bool())
    })

    val memory = SyncReadMem(320 * 240, UInt(8.W))

    when(io.write) {
        memory.write(io.address, io.write_value)
    }

    io.hsync := true.B
    io.vsync := true.B
    io.rgb := 0.U
    io.blanking := true.B

    withClockAndReset(io.read_clk, reset) {
        val hCount = RegInit(0.U(10.W))
        val vCount = RegInit(0.U(10.W))


        when(hCount === (H_TOTAL - 1).U) {
            hCount := 0.U

            when(vCount === (V_TOTAL - 1).U) {
                vCount := 0.U
            }.otherwise {
                vCount := vCount + 1.U
            }
        }.otherwise {
            hCount := hCount + 1.U
        }

        val hSyncStart = (H_VISIBLE + H_FRONT).U
        val hSyncEnd = (H_VISIBLE + H_FRONT + H_SYNC).U
        val vSyncStart = (V_VISIBLE + V_FRONT).U
        val vSyncEnd = (V_VISIBLE + V_FRONT + V_SYNC).U

        io.hsync := !(hCount >= hSyncStart && hCount < hSyncEnd)
        io.vsync := !(vCount >= vSyncStart && vCount < vSyncEnd)

        val hActive = hCount < H_VISIBLE.U
        val vActive = vCount < V_VISIBLE.U
        val active = hActive && vActive

        io.blanking := !active

        val read_address = WireInit(0.U(32.W))
        val vCountShifted = Mux(active, vCount,vCount + 1.U) / 2.U
        val vCountMult = (vCountShifted << 8) + (vCountShifted << 6);
        when(active) {
            
            read_address := vCountMult + hCount / 2.U + 1.U
        }.otherwise {
            read_address := vCountMult
        }

        val color = memory.read(read_address, true.B, io.read_clk)
        val pixel = color(7, 5) ## color(5) ## color(4, 2) ## color(2) ## color(1, 0) ## color(0) ## color(0)

        io.rgb := Mux(active, pixel, 0.U)
    }  
}
