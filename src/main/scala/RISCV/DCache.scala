package RISCV

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

object CacheState extends ChiselEnum {
  val IDLE, LOOKUP, WRITEBACK, MISS = Value
}

class DCache() extends Module {
    val io = IO(new Bundle {
        val req = Input(new MemReq)
        val start = Input(Bool())
        val ready = Output(Bool())
        val done = Output(Bool())
        val miss = Output(Bool())
        val data = Output(UInt(32.W))

        val wb = Output(Bool())
        val wb_data = Output(UInt(128.W))
        val wb_addr = Output(UInt(32.W))

        val line_result = Input(UInt(128.W))
        val line_addr = Output(UInt(32.W))
        val line_valid = Input(Bool())
    })

    val CACHE_SETS = 64
    val LINE_WIDTH_WORDS = 4
    val LOG_CACHE_SETS = log2Up(CACHE_SETS)
    val LOG_LINE_WIDTH_WORDS = log2Up(LINE_WIDTH_WORDS)
    
    val byte_offset = Wire(UInt(2.W))
    val cache_index = Wire(UInt(LOG_CACHE_SETS.W))
    val word_offset = Wire(UInt(LOG_LINE_WIDTH_WORDS.W))
    val cache_tag = Wire(UInt((32-LOG_CACHE_SETS-LOG_LINE_WIDTH_WORDS-2).W))
    val line_addr = Wire(UInt((32-LOG_LINE_WIDTH_WORDS-2).W))

    def getByteOffset(addr: UInt): UInt = addr(1, 0)
    def getWordOffset(addr: UInt): UInt = addr(LOG_LINE_WIDTH_WORDS + 1, 2)
    def getIndex(addr: UInt): UInt = addr(LOG_LINE_WIDTH_WORDS + LOG_CACHE_SETS + 1, LOG_LINE_WIDTH_WORDS + 2)
    def getTag(addr: UInt): UInt = addr(31, LOG_LINE_WIDTH_WORDS + LOG_CACHE_SETS + 2)
    def getLineAddr(addr: UInt): UInt = addr(31, LOG_LINE_WIDTH_WORDS + 2)

    val meta_array = SyncReadMem(CACHE_SETS, UInt(((32-LOG_CACHE_SETS-LOG_LINE_WIDTH_WORDS)).W)) // status(2bit) + tag
    val data_array = SyncReadMem(CACHE_SETS, UInt((32 * LINE_WIDTH_WORDS).W))
    val state = RegInit(CacheState.IDLE)
    val current_mem_req = RegInit(0.U.asTypeOf(new MemReq))
    
    // Address Selection: Address lookup mapping 
    val lookup_address = Mux(io.start, io.req.address, current_mem_req.address)

    // Pipeline alignment registers to sync with BRAM read latency 
    val lookup_address_reg = RegNext(lookup_address)
    byte_offset := getByteOffset(lookup_address_reg)
    cache_index := getIndex(lookup_address_reg)
    word_offset := getWordOffset(lookup_address_reg)
    cache_tag   := getTag(lookup_address_reg)
    line_addr   := getLineAddr(lookup_address_reg)

    // Addressing index inputs straight out to the asynchronous memory blocks
    val raw_index = getIndex(lookup_address)
    val read_enable = io.start || state === CacheState.LOOKUP
    
    val data_out = data_array.read(raw_index, read_enable)
    val meta_out = meta_array.read(raw_index, read_enable) 

    val data_wr_en   = WireDefault(false.B)
    val data_wr_data = WireDefault(0.U((32 * LINE_WIDTH_WORDS).W))
    val meta_wr_en   = WireDefault(false.B)
    val meta_wr_data = WireDefault(0.U(meta_array.t.getWidth.W))
    val write_addr   = getIndex(current_mem_req.address)

    io.done := false.B
    io.miss := false.B
    io.data := 0.U

    val status = meta_out(meta_out.getWidth-1, meta_out.getWidth-2) 
    val tag    = meta_out(meta_out.getWidth-3, 0)    

    // Hardware Stability Fixes: Local registers to buffer writeback values safely
    val wb_data_reg = RegInit(0.U(128.W))
    val wb_addr_reg = RegInit(0.U(32.W))

    // FIX: Tracking bit to make sure line_valid went low after a WRITEBACK 
    val valid_cleared = RegInit(true.B)

    io.ready     := state === CacheState.IDLE
    io.wb        := state === CacheState.WRITEBACK
    io.wb_data   := wb_data_reg
    io.wb_addr   := wb_addr_reg
    io.line_addr := Cat(getLineAddr(current_mem_req.address), 0.U((LOG_LINE_WIDTH_WORDS + 2).W))

    val words = VecInit((0 until 4).map(i => data_out(32*i + 31, 32*i)))

    switch(state) {
        is(CacheState.IDLE) {
            when(io.start) {
                current_mem_req := io.req
                state := CacheState.LOOKUP
                valid_cleared := true.B // Reset tracking flag
            }
        }

        is(CacheState.LOOKUP) {
            when(cache_tag === tag && status(1) === 1.U) { // HIT
                state := CacheState.IDLE

                when(current_mem_req.write) {
                    val updated_line = Wire(UInt(128.W))
                    updated_line := data_out
                    val lwords = VecInit((0 until 4).map(i => data_out(32*i + 31, 32*i)))
                    val old_word = lwords(word_offset)
                    val updated_word = WireDefault(old_word)

                    switch(current_mem_req.op) {
                        is(MemOp.SW) { updated_word := current_mem_req.write_data }
                        is(MemOp.SH) {
                            when(byte_offset === 0.U) {
                                updated_word := Cat(old_word(31,16), current_mem_req.write_data(15,0))
                            }.otherwise {
                                updated_word := Cat(current_mem_req.write_data(15,0), old_word(15,0))
                            }
                        }
                        is(MemOp.SB) {
                            when(byte_offset === 0.U) {
                                updated_word := Cat(old_word(31,8), current_mem_req.write_data(7,0))
                            }.elsewhen(byte_offset === 1.U) {
                                updated_word := Cat(old_word(31,16), current_mem_req.write_data(7,0), old_word(7,0))
                            }.elsewhen(byte_offset === 2.U) {
                                updated_word := Cat(old_word(31,24), current_mem_req.write_data(7,0), old_word(15,0))
                            }.otherwise {
                                updated_word := Cat(current_mem_req.write_data(7,0), old_word(23,0))
                            }
                        }
                    }

                    switch(word_offset) {
                        is(0.U) { updated_line := Cat(data_out(127, 32),  updated_word) }
                        is(1.U) { updated_line := Cat(data_out(127, 64),  updated_word, data_out(31,  0)) }
                        is(2.U) { updated_line := Cat(data_out(127, 96),  updated_word, data_out(63,  0)) }
                        is(3.U) { updated_line := Cat(updated_word, data_out(95, 0)) }
                    }

                    data_wr_en   := true.B
                    data_wr_data := updated_line
                    meta_wr_en   := true.B
                    meta_wr_data := Cat("b11".U(2.W), getTag(current_mem_req.address))
                                    // printf("\n\n\n\n\n\n DIRTY DIRTY DIRTY DIRT CACHEK data %b \n\n\n\n\n\n", Cat("b11".U(2.W), getTag(current_mem_req.address)))

                    io.done      := true.B
                }.otherwise {
                    io.done := true.B
                    val word_data = words(word_offset)
                    switch(current_mem_req.op){
                        is(MemOp.LW)  { io.data := word_data }
                        is(MemOp.LH)  { io.data := Mux(byte_offset === 0.U, word_data(15,0).asSInt.pad(32).asUInt, word_data(31,16).asSInt.pad(32).asUInt) }
                        is(MemOp.LHU) { io.data := Mux(byte_offset === 0.U, word_data(15,0).pad(32), word_data(31,16).pad(32)) }
                        is(MemOp.LB)  {
                            switch(byte_offset) {
                                is(0.U) { io.data := word_data(7,0).asSInt.pad(32).asUInt }
                                is(1.U) { io.data := word_data(15,8).asSInt.pad(32).asUInt }
                                is(2.U) { io.data := word_data(23,16).asSInt.pad(32).asUInt }
                                is(3.U) { io.data := word_data(31,24).asSInt.pad(32).asUInt }
                            }
                        }
                        is(MemOp.LBU) {
                            switch(byte_offset) {
                                is(0.U) { io.data := word_data(7,0).pad(32) }
                                is(1.U) { io.data := word_data(15,8).pad(32) }
                                is(2.U) { io.data := word_data(23,16).pad(32) }
                                is(3.U) { io.data := word_data(31,24).pad(32) }
                            }
                        }
                    }
                }
            }.otherwise { // MISS
                when(status === "b11".U) { 
                    wb_data_reg := data_out
                    wb_addr_reg := Cat(tag, getIndex(current_mem_req.address), 0.U((LOG_LINE_WIDTH_WORDS + 2).W))
                    state       := CacheState.WRITEBACK
                    valid_cleared := false.B // We are entering writeback; handshake needs clearing afterwards
                }.otherwise {            
                    state := CacheState.MISS
                    valid_cleared := true.B // Normal miss, no writeback to pollute wire
                }
            }
        }

        is(CacheState.WRITEBACK) {
            when(io.line_valid) {
                // printf("\n\n\n\n\n\n IN WRITEBACK IN TWRIT E BACK\n\n\n\n\n\n")
                state := CacheState.MISS
            }
        }

        is(CacheState.MISS) {
            // FIX: If we haven't seen line_valid drop to 0 after writeback, force it to clear first
            when(!valid_cleared) {
                when(!io.line_valid) {
                    valid_cleared := true.B
                }
                io.miss := true.B // Hold miss out high to Arbiter
            }.otherwise {
                when(!io.line_valid) {
                    io.miss := true.B
                }.otherwise {
                    // Safe to proceed: line_valid is high and it's explicitly for the READ request
                    when(current_mem_req.write) {
                        val updated_line = Wire(UInt(128.W))
                        updated_line := io.line_result
                        val lwords = VecInit((0 until 4).map(i => io.line_result(32*i + 31, 32*i)))
                        val old_word = lwords(word_offset)
                        val updated_word = WireDefault(old_word)

                        switch(current_mem_req.op) {
                            is(MemOp.SW) { updated_word := current_mem_req.write_data }
                            is(MemOp.SH) {
                                when(byte_offset === 0.U) {
                                    updated_word := Cat(old_word(31,16), current_mem_req.write_data(15,0))
                                }.otherwise {
                                    updated_word := Cat(current_mem_req.write_data(15,0), old_word(15,0))
                                }
                            }
                            is(MemOp.SB) {
                                when(byte_offset === 0.U) {
                                    updated_word := Cat(old_word(31,8), current_mem_req.write_data(7,0))
                                }.elsewhen(byte_offset === 1.U) {
                                    updated_word := Cat(old_word(31,16), current_mem_req.write_data(7,0), old_word(7,0))
                                }.elsewhen(byte_offset === 2.U) {
                                    updated_word := Cat(old_word(31,24), current_mem_req.write_data(7,0), old_word(15,0))
                                }.otherwise {
                                    updated_word := Cat(current_mem_req.write_data(7,0), old_word(23,0))
                                }
                            }
                        }

                        switch(word_offset) {
                            is(0.U) { updated_line := Cat(io.line_result(127, 32),  updated_word) }
                            is(1.U) { updated_line := Cat(io.line_result(127, 64),  updated_word, io.line_result(31,  0)) }
                            is(2.U) { updated_line := Cat(io.line_result(127, 96),  updated_word, io.line_result(63,  0)) }
                            is(3.U) { updated_line := Cat(updated_word, io.line_result(95, 0)) }
                        }

                        data_wr_en   := true.B
                        data_wr_data := updated_line
                        meta_wr_en   := true.B
                        meta_wr_data := Cat("b11".U(2.W), getTag(current_mem_req.address))
// printf("\n\n\n\n\n\n DIRTY DIRTY DIRTY DIRT CACHEK data %b \n\n\n\n\n\n", Cat("b11".U(2.W), getTag(current_mem_req.address)))
                        io.done      := true.B
                    }.otherwise {
                        data_wr_en   := true.B
                        data_wr_data := io.line_result
                        meta_wr_en   := true.B
                        meta_wr_data := Cat("b10".U(2.W), getTag(current_mem_req.address))
                        val lwords = VecInit((0 until 4).map(i => io.line_result(32*i + 31, 32*i)))
                        val updated_word = lwords(word_offset)
                        
                        switch(current_mem_req.op){
                            is(MemOp.LW)  { io.data := updated_word }
                            is(MemOp.LH)  { io.data := Mux(byte_offset === 0.U, updated_word(15,0).asSInt.pad(32).asUInt, updated_word(31,16).asSInt.pad(32).asUInt) }
                            is(MemOp.LHU) { io.data := Mux(byte_offset === 0.U, updated_word(15,0).pad(32), updated_word(31,16).pad(32)) }
                            is(MemOp.LB)  {
                                switch(byte_offset) {
                                    is(0.U) { io.data := updated_word(7,0).asSInt.pad(32).asUInt }
                                    is(1.U) { io.data := updated_word(15,8).asSInt.pad(32).asUInt }
                                    is(2.U) { io.data := updated_word(23,16).asSInt.pad(32).asUInt }
                                    is(3.U) { io.data := updated_word(31,24).asSInt.pad(32).asUInt }
                                }
                            }
                            is(MemOp.LBU) {
                                switch(byte_offset) {
                                    is(0.U) { io.data := updated_word(7,0).pad(32) }
                                    is(1.U) { io.data := updated_word(15,8).pad(32) }
                                    is(2.U) { io.data := updated_word(23,16).pad(32) }
                                    is(3.U) { io.data := updated_word(31,24).pad(32) }
                                }
                            }
                        }
                        io.done := true.B
                    }
                    state := CacheState.IDLE
                }
            }
        }
    }

    when(data_wr_en) { data_array.write(write_addr, data_wr_data) }
    when(meta_wr_en) { meta_array.write(write_addr, meta_wr_data) }
    // printf("\n\nMETA WRITE addr %x  data: %b\n\n ",write_addr,meta_wr_data)}


    when(false.B) {
        printf(
            "DCACHE cycle: state=%d ready=%d done=%d miss=%d | req_v=%d req_w=%d req_op=%d req_addr=%x req_data=%x | lookup_addr=%x idx=%d tag=%x word_off=%d byte_off=%d | array_meta=%x array_data=%x | wb_v=%d wb_addr=%x wb_data=%x | line_in_v=%d line_in_addr=%x line_in_data=%x\n",
            state.asUInt,
            io.ready,
            io.done,
            io.miss,
            // Upstream CPU Core request details
            io.start,
            current_mem_req.write,
            current_mem_req.op.asUInt,
            current_mem_req.address,
            current_mem_req.write_data,
            // Decoded internal pipeline fields
            lookup_address_reg,
            cache_index,
            cache_tag,
            word_offset,
            byte_offset,
            // Current SRAM/BRAM array outputs
            meta_out,
            data_out,
            // Outbound Writeback signals
            io.wb,
            io.wb_addr,
            io.wb_data,
            // Inbound Arbiter Line refills
            io.line_valid,
            io.line_addr,
            io.line_result
        )
    }
}