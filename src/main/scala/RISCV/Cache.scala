package RISCV

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import chisel3.util.experimental.loadMemoryFromFileInline


object CacheState extends ChiselEnum {
  val IDLE,LOOKUP, WRITEBACK, MISS = Value
}


class Cache() extends Module {
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
    // when(io.line_valid) {
    //     data_array.write(cache_index, io.line_result)  
    //       switch(word_offset){
    //         is(0.U){
    //             io.data := io.line_result(31,0)
    //         }
    //         is(1.U){
    //             io.data := io.line_result(63,32)
    //         }
    //         is(2.U){
    //             io.data := io.line_result(95,64)
    //         }
    //         is(3.U){
    //             io.data := io.line_result(127,96)
    //         }
    //     }
        
    // }

    // memory.readWrite(
    //   io.req.address,
    //   io.req.write_data,
    //   io.start && (io.req.read || io.req.write),
    //   io.req.write
    // )
    val meta_out = meta_array.read(cache_index, read_enable) 
    val status = meta_out(meta_out.getWidth-1, meta_out.getWidth-2) 
    val tag = meta_out(meta_out.getWidth-3, 0)    

 



    io.ready:= state === CacheState.IDLE // or hit
    io.wb := false.B
    io.wb_data := data_out
    io.wb_addr := Cat(tag, cache_index)
    //  Cat(tag, 0.U((LOG_LINE_WIDTH_WORDS + LOG_CACHE_SETS + 2).W))
    io.line_addr:= line_addr


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
                printf("WRITING \n")
                switch(word_offset) {
                    is(0.U) { updated_line := Cat(data_out(127, 32),  current_mem_req.write_data) }
                    is(1.U) { updated_line := Cat(data_out(127, 64),  current_mem_req.write_data, data_out(31,  0)) }
                    is(2.U) { updated_line := Cat(data_out(127, 96),  current_mem_req.write_data, data_out(63,  0)) }
                    is(3.U) { updated_line := Cat(current_mem_req.write_data, data_out(95, 0)) }
                }
                data_array.write(cache_index, updated_line)
                meta_array.write(cache_index, Cat("b11".U(2.W), cache_tag))
            }.otherwise {
                            io.done := true.B
                                printf("READING HIT READGING HIT %x index: %d  meta: %b\n",data_out, cache_index, meta_out)

                // read
                switch(word_offset) {
                    is(0.U) { io.data := data_out(31,  0) }
                    is(1.U) { io.data := data_out(63,  32) }
                    is(2.U) { io.data := data_out(95,  64) }
                    is(3.U) { io.data := data_out(127, 96) }
                }
            }
        }.otherwise { // miss
            when(status === "b11".U) { // valid + dirty
                state  := CacheState.MISS
                io.wb  := true.B
            }.otherwise {    // invalid or not dirty
                state := CacheState.MISS
            }
            io.miss := true.B
        }
    }



    is(CacheState.MISS) {
        when(!io.line_valid) {
            io.miss := true.B
        }.otherwise {
             //valid not dirty

            when(current_mem_req.write) {
                val updated_line = Wire(UInt(128.W))
                updated_line := io.line_result
                switch(word_offset) {
                    is(0.U) { updated_line := Cat(io.line_result(127, 32),  current_mem_req.write_data) }
                    is(1.U) { updated_line := Cat(io.line_result(127, 64),  current_mem_req.write_data, io.line_result(31,  0)) }
                    is(2.U) { updated_line := Cat(io.line_result(127, 96),  current_mem_req.write_data, io.line_result(63,  0)) }
                    is(3.U) { updated_line := Cat(current_mem_req.write_data, io.line_result(95, 0)) }
                }
                printf("WRITING MISS WRITING MISSS %x index: %d \n",updated_line, cache_index)
                data_array.write(cache_index, updated_line)
                meta_array.write(cache_index, Cat("b11".U(2.W), cache_tag)) // dirty
            }.otherwise {
                data_array.write(cache_index, io.line_result)
                meta_array.write(cache_index, Cat("b10".U(2.W), cache_tag))//valid not dirsty
                switch(word_offset) {
                    is(0.U) { io.data := io.line_result(31,  0) }
                    is(1.U) { io.data := io.line_result(63,  32) }
                    is(2.U) { io.data := io.line_result(95,  64) }
                    is(3.U) { io.data := io.line_result(127, 96) }

                }
                            io.done := true.B

            }
            state   := CacheState.IDLE
        }
    }
}   
  when(true.B) {
    printf("cycle: state=%d | req=[addr=%x wdata=%x r=%d w=%d] | idx=%x tag=%x word_off=%x | meta=[status=%b tag=%x] | data_out=%x | done=%d miss=%d wb=%d | line_valid=%d line_result=%x | wb_addr=%x line_addr=%x\n",
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
     

     // just need fsm and a way to get data into the cache
    //ccache and normal mem here figure out div 4


}
