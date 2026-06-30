package RISCV

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import chisel3.util.experimental.loadMemoryFromFileInline


object CacheState extends ChiselEnum {
  val IDLE,LOOKUP, WRITEBACK, MISS = Value
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
    val lookup_address = Mux(io.start, io.req.address, current_mem_req.address)

    byte_offset := getByteOffset(lookup_address)
    cache_index := getIndex(lookup_address)
    word_offset := getWordOffset(lookup_address)
    cache_tag := getTag(lookup_address)
    line_addr := getLineAddr(lookup_address)
    val read_enable = io.start || state === CacheState.LOOKUP
    val data_out = Wire(UInt((32 * LINE_WIDTH_WORDS).W))
    data_out := data_array.read(cache_index, read_enable)  

when(reset.asBool) {
    for (i <- 0 until CACHE_SETS) {
        data_array.write(i.U, 0.U)
        meta_array.write(i.U, 0.U)  // also zeros out valid/dirty bits which is important
    }
}

    io.done := false.B
    io.miss := false.B
    io.data := 0.U

    val meta_out = meta_array.read(cache_index, read_enable) 
    val status = meta_out(meta_out.getWidth-1, meta_out.getWidth-2) 
    val tag = meta_out(meta_out.getWidth-3, 0)    

    io.ready:= state === CacheState.IDLE // or hit
    io.wb := false.B
    io.wb_data := data_out
    // FIX 3: wb_addr was missing the lower bits
    io.wb_addr := Cat(tag, cache_index, 0.U((LOG_LINE_WIDTH_WORDS + 2).W))
    io.line_addr:= line_addr

    val words = VecInit(
    (0 until 4).map(i => data_out(32*i + 31, 32*i))
    )

switch(state) {
    is(CacheState.IDLE) {
        when(io.start) {
            current_mem_req := io.req
            state := CacheState.LOOKUP
        }
    }

    is(CacheState.LOOKUP) {
        when(cache_tag === tag && status(1) === 1.U) { // hit
            state := CacheState.IDLE

            when(current_mem_req.write) {
                val updated_line = Wire(UInt(128.W))
                updated_line := data_out
                val lwords = VecInit((0 until 4).map(i => data_out(32*i + 31, 32*i)))
                val old_word = lwords(word_offset)

                val updated_word = Wire(UInt(32.W))
                updated_word := old_word

                switch(current_mem_req.op) {
                    is(MemOp.SW) {
                        updated_word := current_mem_req.write_data
                    }

                    is(MemOp.SH) {
                        when(byte_offset === 0.U) {
                            updated_word := Cat(
                                old_word(31,16),
                                current_mem_req.write_data(15,0)
                            )
                        }.otherwise {
                            updated_word := Cat(
                                current_mem_req.write_data(15,0),
                                old_word(15,0)
                            )
                        }
                    }

                    is(MemOp.SB) {
                        when(byte_offset === 0.U) {
                            updated_word := Cat(
                                old_word(31,8),
                                current_mem_req.write_data(7,0)
                            )
                        }.elsewhen(byte_offset === 1.U) {
                            updated_word := Cat(
                                old_word(31,16),
                                current_mem_req.write_data(7,0),
                                old_word(7,0)
                            )
                        }.elsewhen(byte_offset === 2.U) {
                            updated_word := Cat(
                                old_word(31,24),
                                current_mem_req.write_data(7,0),
                                old_word(15,0)
                            )
                        }.otherwise {
                            updated_word := Cat(
                                current_mem_req.write_data(7,0),
                                old_word(23,0)
                            )
                        }
                    }
                }

                switch(word_offset) {
                    is(0.U) { updated_line := Cat(data_out(127, 32),  updated_word) }
                    is(1.U) { updated_line := Cat(data_out(127, 64),  updated_word, data_out(31,  0)) }
                    is(2.U) { updated_line := Cat(data_out(127, 96),  updated_word, data_out(63,  0)) }
                    is(3.U) { updated_line := Cat(updated_word, data_out(95, 0)) }
                }

                data_array.write(cache_index, updated_line)
                meta_array.write(cache_index, Cat("b11".U(2.W), cache_tag))
                // FIX 4: signal done on write hit
                io.done := true.B
            }.otherwise {
                io.done := true.B
                // printf("READING HIT READGING HIT %x index: %d  meta: %b\n",data_out, cache_index, meta_out)

                val word_data = words(word_offset)
                switch(current_mem_req.op){
                    is(MemOp.LW){
                        io.data := word_data
                    }
                    is(MemOp.LH){
                        when(byte_offset === 0.U){
                            io.data := ((word_data(15,0)).asSInt.pad(32)).asUInt
                        }.otherwise{
                            io.data := ((word_data(31,16)).asSInt.pad(32)).asUInt
                        }
                    }
                    is(MemOp.LHU){
                        when(byte_offset === 0.U){
                            io.data := ((word_data(15,0)).asUInt.pad(32)).asUInt
                        }.otherwise{
                            io.data := ((word_data(31,16)).asUInt.pad(32)).asUInt
                        }
                    }
                    is(MemOp.LB){
                        when(byte_offset === 0.U){
                            io.data := word_data(7,0).asSInt.pad(32).asUInt
                        }.elsewhen(byte_offset === 1.U){
                            io.data := word_data(15,8).asSInt.pad(32).asUInt
                        }.elsewhen(byte_offset === 2.U){
                            io.data := word_data(23,16).asSInt.pad(32).asUInt
                        }.otherwise{
                            io.data := word_data(31,24).asSInt.pad(32).asUInt
                        }
                    }
                    is(MemOp.LBU){
                        when(byte_offset === 0.U){
                            io.data := word_data(7,0).asUInt.pad(32).asUInt
                        }.elsewhen(byte_offset === 1.U){
                            io.data := word_data(15,8).asUInt.pad(32).asUInt
                        }.elsewhen(byte_offset === 2.U){
                            io.data := word_data(23,16).asUInt.pad(32).asUInt
                        }.otherwise{
                            io.data := word_data(31,24).asUInt.pad(32).asUInt
                        }
                    }
                }
            }
        }.otherwise { 
            when(status === "b11".U) { 
                state := CacheState.WRITEBACK
            }.otherwise {            
                state := CacheState.MISS
            }
        }
    }

    is(CacheState.WRITEBACK) {
        io.wb := true.B
        state := CacheState.MISS
    }

    is(CacheState.MISS) {
        when(!io.line_valid) {
            io.miss := true.B
        }.otherwise {
            when(current_mem_req.write) {
                val updated_line = Wire(UInt(128.W))
                updated_line := io.line_result
                val lwords = VecInit((0 until 4).map(i => io.line_result(32*i + 31, 32*i)))
                val old_word = lwords(word_offset)

                val updated_word = Wire(UInt(32.W))
                updated_word := old_word

                switch(current_mem_req.op) {
                    is(MemOp.SW) {
                        updated_word := current_mem_req.write_data
                    }

                    is(MemOp.SH) {
                        when(byte_offset === 0.U) {
                            updated_word := Cat(
                                old_word(31,16),
                                current_mem_req.write_data(15,0)
                            )
                        }.otherwise {
                            updated_word := Cat(
                                current_mem_req.write_data(15,0),
                                old_word(15,0)
                            )
                        }
                    }

                    is(MemOp.SB) {
                        when(byte_offset === 0.U) {
                            updated_word := Cat(
                                old_word(31,8),
                                current_mem_req.write_data(7,0)
                            )
                        }.elsewhen(byte_offset === 1.U) {
                            updated_word := Cat(
                                old_word(31,16),
                                current_mem_req.write_data(7,0),
                                old_word(7,0)
                            )
                        }.elsewhen(byte_offset === 2.U) {
                            updated_word := Cat(
                                old_word(31,24),
                                current_mem_req.write_data(7,0),
                                old_word(15,0)
                            )
                        }.otherwise {
                            updated_word := Cat(
                                current_mem_req.write_data(7,0),
                                old_word(23,0)
                            )
                        }
                    }
                }

                switch(word_offset) {
                    is(0.U) { updated_line := Cat(io.line_result(127, 32),  updated_word) }
                    is(1.U) { updated_line := Cat(io.line_result(127, 64),  updated_word, io.line_result(31,  0)) }
                    is(2.U) { updated_line := Cat(io.line_result(127, 96),  updated_word, io.line_result(63,  0)) }
                    is(3.U) { updated_line := Cat(updated_word, io.line_result(95, 0)) }
                }

                // printf("WRITING MISS WRITING MISSS %x index: %d \n",updated_line, cache_index)
                data_array.write(cache_index, updated_line)
                meta_array.write(cache_index, Cat("b11".U(2.W), cache_tag)) // dirty
                io.done := true.B
            }.otherwise {
                data_array.write(cache_index, io.line_result)
                meta_array.write(cache_index, Cat("b10".U(2.W), cache_tag))//valid not dirty
                val lwords = VecInit((0 until 4).map(i => io.line_result(32*i + 31, 32*i)))
                val updated_word = lwords(word_offset)
                switch(current_mem_req.op){
                    is(MemOp.LW){
                        io.data := updated_word
                    }
                    is(MemOp.LH){
                        when(byte_offset === 0.U){
                            io.data := ((updated_word(15,0)).asSInt.pad(32)).asUInt
                        }.otherwise{
                            io.data := ((updated_word(31,16)).asSInt.pad(32)).asUInt
                        }
                    }
                    is(MemOp.LHU){
                        when(byte_offset === 0.U){
                            io.data := ((updated_word(15,0)).asUInt.pad(32)).asUInt
                        }.otherwise{
                            io.data := ((updated_word(31,16)).asUInt.pad(32)).asUInt
                        }
                    }
                    is(MemOp.LB){
                        when(byte_offset === 0.U){
                            io.data := updated_word(7,0).asSInt.pad(32).asUInt
                        }.elsewhen(byte_offset === 1.U){
                            io.data := updated_word(15,8).asSInt.pad(32).asUInt
                        }.elsewhen(byte_offset === 2.U){
                            io.data := updated_word(23,16).asSInt.pad(32).asUInt
                        }.otherwise{
                            io.data := updated_word(31,24).asSInt.pad(32).asUInt
                        }
                    }
                    is(MemOp.LBU){
                        when(byte_offset === 0.U){
                            io.data := updated_word(7,0).asUInt.pad(32).asUInt
                        }.elsewhen(byte_offset === 1.U){
                            io.data := updated_word(15,8).asUInt.pad(32).asUInt
                        }.elsewhen(byte_offset === 2.U){
                            io.data := updated_word(23,16).asUInt.pad(32).asUInt
                        }.otherwise{
                            io.data := updated_word(31,24).asUInt.pad(32).asUInt
                        }
                    }
                }
                io.done := true.B
            }
            state := CacheState.IDLE
        }
    }
}   
  when(true.B) {
    printf("DCACHE cycle: state=%d | req=[addr=%x wdata=%x r=%d w=%d] | idx=%x tag=%x word_off=%x | meta=[status=%b tag=%x] | data_out=%x | done=%d miss=%d wb=%d | line_valid=%d line_result=%x | wb_addr=%x line_addr=%x\n",
        state.asUInt,
        current_mem_req.address,
        current_mem_req.write_data,
        current_mem_req.read,
        current_mem_req.write,
        cache_index,
        cache_tag,
        word_offset,
        status,
        tag,
        data_out,
        io.done,
        io.miss,
        io.wb,
        io.line_valid,
        io.line_result,
        io.wb_addr,
        io.line_addr
    )
}
}