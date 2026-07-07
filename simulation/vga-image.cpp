#include "VMain.h"
#include "verilated.h"
#include <cstdio>
#include <vector>
#include <fstream>
#include <cstdlib>
#include <string>
#include <memory>
#include <map>

static constexpr int H_VISIBLE = 640;
static constexpr int H_FRONT   = 16;
static constexpr int H_SYNC    = 96;
static constexpr int H_BACK    = 48;
static constexpr int H_TOTAL   = H_VISIBLE + H_FRONT + H_SYNC + H_BACK;

static constexpr int V_VISIBLE = 480;
static constexpr int V_FRONT   = 10;
static constexpr int V_SYNC    = 2;
static constexpr int V_BACK    = 33;
static constexpr int V_TOTAL   = V_VISIBLE + V_FRONT + V_SYNC + V_BACK;

int main(int argc, char** argv) {
    Verilated::commandArgs(argc, argv);
    auto dut = std::make_unique<VMain>();

    // Clean up legacy unused ports and initialize standard inputs
    dut->io_execute = 0;
    dut->io_flash   = 0;
    dut->io_flash_address = 0;
    dut->io_flash_value   = 0;
    dut->reset      = 1;
    dut->clock      = 0;
    dut->io_vga_clk = 0;

    // ------------------------------------------------------------------
    // NEW PARADIGM: Preload Hex File into a Mock DDR3 Memory Array
    // ------------------------------------------------------------------
    // Since the CPU expects a 128-bit bus, we map 32-bit addresses to 128-bit blocks
    std::map<uint32_t, std::vector<uint8_t>> mock_ddr3;

    std::ifstream file("/home/arya/Documents/Github/RISC-V/programs/hello.hex");
    if (!file.is_open()) {
        printf("Error: Could not open hello.hex file!\n");
        return -1;
    }

    std::string line;
    uint32_t current_byte_addr = 0;

    while (std::getline(file, line)) {
        if (line.empty()) continue;
        uint32_t instruction = std::stoul(line, nullptr, 16);

        // Align our raw instruction byte address to a 128-bit (16-byte) boundary allocation block
        uint32_t line_base_addr = (current_byte_addr / 16) * 16;
        uint32_t byte_offset    = current_byte_addr % 16;

        // Create the 16-byte row chunk if it doesn't exist yet
        if (mock_ddr3.find(line_base_addr) == mock_ddr3.end()) {
            mock_ddr3[line_base_addr] = std::vector<uint8_t>(16, 0);
        }

        // Pack instructions in Little-Endian layout within the 128-bit lane
        mock_ddr3[line_base_addr][byte_offset + 0] = (instruction >> 0)  & 0xFF;
        mock_ddr3[line_base_addr][byte_offset + 1] = (instruction >> 8)  & 0xFF;
        mock_ddr3[line_base_addr][byte_offset + 2] = (instruction >> 16) & 0xFF;
        mock_ddr3[line_base_addr][byte_offset + 3] = (instruction >> 24) & 0xFF;

        current_byte_addr += 4; // Advance by 4 bytes (one 32-bit word)
    }
    printf("Preloaded %d instructions into mock DDR3 space.\n", current_byte_addr / 4);

    // Reset the processor core
    for (int i = 0; i < 10; i++) {
        dut->clock ^= 1;
        dut->io_vga_clk = dut->clock;
        dut->eval();
    }
    dut->reset = 0;
    dut->io_execute = 1; // Assert execution mode immediately since memory is preloaded

    std::vector<uint8_t> pixels(H_VISIBLE * V_VISIBLE * 3, 0);
    bool prev_vsync = 1;
    int pixelIdx = 0;

    // Track state variables for memory pipeline simulator
    bool read_in_progress = false;
    int  read_latency_counter = 0;
    uint32_t active_read_addr = 0;

    while (1) {
        pixelIdx = 0;

        // Sync Phase: Run until we hit the falling edge of VSYNC
        while (true) {
            // Memory Controller Handshake Logic on Positive Clock Edge
            dut->clock = 1; 
            dut->io_vga_clk = 1; 

            // Handle CPU requests
            if (dut->io_mem_req_valid) {
                // Assert ready immediately to simulate an optimal memory gateway
                dut->io_mem_req_ready = 1; 
                
                if (!read_in_progress && !dut->io_mem_req_bits_write) {
                    read_in_progress = true;
                    // Simulate a standard 4-cycle random-access read latency penalty
                    read_latency_counter = 4; 
                    active_read_addr = dut->io_mem_req_bits_addr;
                }
            } else {
                dut->io_mem_req_ready = 0;
            }

            // Process delayed read operations
            if (read_in_progress) {
                if (read_latency_counter > 0) {
                    read_latency_counter--;
                    dut->io_mem_valid = 0;
                } else {
                    // Latency has expired! Fetch data payload and signal back to Chisel core
                    dut->io_mem_valid = 1;
                    
                    // Direct pointer access to our mock memory block array map
                    uint32_t target_aligned_addr = (active_read_addr / 16) * 16;
                    
                    if (mock_ddr3.find(target_aligned_addr) != mock_ddr3.end()) {
                        auto& data_row = mock_ddr3[target_aligned_addr];
                        // Reconstruct standard multi-word fields cleanly out into the wide WDATA block ports
                        dut->io_mem_resp[0] = *(uint32_t*)&data_row[0];
                        dut->io_mem_resp[1] = *(uint32_t*)&data_row[4];
                        dut->io_mem_resp[2] = *(uint32_t*)&data_row[8];
                        dut->io_mem_resp[3] = *(uint32_t*)&data_row[12];
                    } else {
                        // Return clear zeros if accessing uninitialized spaces
                        dut->io_mem_resp[0] = 0; dut->io_mem_resp[1] = 0;
                        dut->io_mem_resp[2] = 0; dut->io_mem_resp[3] = 0;
                    }
                    read_in_progress = false; // Reset read pipeline handle
                }
            } else {
                dut->io_mem_valid = 0;
            }

            dut->eval();

            bool vsync = dut->io_vsync;

            // Falling Edge Clock Phase
            dut->clock = 0; 
            dut->io_vga_clk = 0; 
            dut->eval();

            if (prev_vsync && !vsync) break;
            prev_vsync = vsync;
        }
        prev_vsync = 0;

        // Frame Capture Phase loop execution
        for (int cycle = 0; cycle < H_TOTAL * V_TOTAL; cycle++) {
            dut->clock = 1; 
            dut->io_vga_clk = 1;

            // Maintain memory pipeline controller ticks inside visual loops as well
            if (dut->io_mem_req_valid) {
                dut->io_mem_req_ready = 1;
                if (!read_in_progress && !dut->io_mem_req_bits_write) {
                    read_in_progress = true;
                    read_latency_counter = 4;
                    active_read_addr = dut->io_mem_req_bits_addr;
                }
            } else {
                dut->io_mem_req_ready = 0;
            }

            if (read_in_progress) {
                if (read_latency_counter > 0) {
                    read_latency_counter--;
                    dut->io_mem_valid = 0;
                } else {
                    dut->io_mem_valid = 1;
                    uint32_t target_aligned_addr = (active_read_addr / 16) * 16;
                    if (mock_ddr3.find(target_aligned_addr) != mock_ddr3.end()) {
                        auto& data_row = mock_ddr3[target_aligned_addr];
                        dut->io_mem_resp[0] = *(uint32_t*)&data_row[0];
                        dut->io_mem_resp[1] = *(uint32_t*)&data_row[4];
                        dut->io_mem_resp[2] = *(uint32_t*)&data_row[8];
                        dut->io_mem_resp[3] = *(uint32_t*)&data_row[12];
                    } else {
                        dut->io_mem_resp[0] = 0; dut->io_mem_resp[1] = 0;
                        dut->io_mem_resp[2] = 0; dut->io_mem_resp[3] = 0;
                    }
                    read_in_progress = false;
                }
            } else {
                dut->io_mem_valid = 0;
            }

            dut->eval();

            bool vsync    = dut->io_vsync;
            bool blanking = dut->io_blanking;
            uint16_t rgb12 = dut->io_rgb;

            dut->clock = 0; 
            dut->io_vga_clk = 0; 
            dut->eval();

            if (prev_vsync && !vsync) {
                printf("vsync mid-frame at cycle %d — counter mismatch!\n", cycle);
            }
            prev_vsync = vsync;

            if (!blanking && pixelIdx < H_VISIBLE * V_VISIBLE) {
                pixels[pixelIdx * 3 + 0] = ((rgb12 >> 8) & 0xF) * 17;
                pixels[pixelIdx * 3 + 1] = ((rgb12 >> 4) & 0xF) * 17;
                pixels[pixelIdx * 3 + 2] = ((rgb12 >> 0) & 0xF) * 17;
                pixelIdx++;
            }
        }

        printf("Captured %d pixels (expected %d)\n", pixelIdx, H_VISIBLE * V_VISIBLE);

        FILE* f = fopen("frame.ppm", "wb");
        if (!f) { perror("fopen"); return 1; }
        fprintf(f, "P6\n%d %d\n255\n", H_VISIBLE, V_VISIBLE);
        fwrite(pixels.data(), 1, pixels.size(), f);
        fclose(f);
        if (system("ffmpeg -i frame.ppm frame.png -y") != 0) {
             // fallback notification if ffmpeg tool tracking fails natively
             printf("Frame dumped out to local disk as frame.ppm safely.\n");
        }
    }

    dut->final();
    return 0;
}