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

static constexpr uint32_t AXI_ADDR_MASK = 0x07FFFFFF;


static constexpr long long CYCLE_LIMIT = -1; 

static inline uint32_t axi_window(uint32_t addr) {
    return addr & AXI_ADDR_MASK;
}

// Commits a write request's 128-bit wdata into the mock DDR3 row at the aligned address.
static void handle_mem_write(std::unique_ptr<VMain>& dut,
                              std::map<uint32_t, std::vector<uint8_t>>& mock_ddr3) {
    uint32_t addr = axi_window(dut->io_mem_req_bits_addr);
    uint32_t line_base_addr = (addr / 16) * 16;

    if (mock_ddr3.find(line_base_addr) == mock_ddr3.end()) {
        mock_ddr3[line_base_addr] = std::vector<uint8_t>(16, 0);
    }
    auto& data_row = mock_ddr3[line_base_addr];

    *(uint32_t*)&data_row[0]  = dut->io_mem_req_bits_wdata[0];
    *(uint32_t*)&data_row[4]  = dut->io_mem_req_bits_wdata[1];
    *(uint32_t*)&data_row[8]  = dut->io_mem_req_bits_wdata[2];
    *(uint32_t*)&data_row[12] = dut->io_mem_req_bits_wdata[3];

    uint32_t raw = dut->io_mem_req_bits_addr;
    if (raw > AXI_ADDR_MASK) {
        static bool warned = false;
        if (!warned) {
            printf("WARNING: access above 27-bit AXI window: addr=0x%08X (%s) -> aliases to 0x%08X\n",
                   raw, dut->io_mem_req_bits_write ? "write" : "read", axi_window(raw));
            warned = true;
        }
    }
}

int main(int argc, char** argv) {
    Verilated::commandArgs(argc, argv);
    auto dut = std::make_unique<VMain>();

    long long total_cycles = 0;
    bool limited = CYCLE_LIMIT >= 0;

    auto limit_reached = [&]() {
        return limited && total_cycles >= CYCLE_LIMIT;
    };

    dut->io_execute = 0;
    dut->io_flash   = 0;
    dut->io_flash_address = 0;
    dut->io_flash_value   = 0;
    dut->reset      = 1;
    dut->clock      = 0;
    dut->io_vga_clk = 0;
    dut->io_rxd = 1;

    std::map<uint32_t, std::vector<uint8_t>> mock_ddr3;

    std::ifstream file("/home/arya/Documents/Github/hopper-cpu/programs/doom.hex");
    if (!file.is_open()) {
        printf("Error: Could not open hello.hex file!\n");
        return -1;
    }

    std::string line;
    uint32_t current_byte_addr = 0;

    while (std::getline(file, line)) {
        if (line.empty()) continue;
        uint32_t instruction = std::stoul(line, nullptr, 16);

        uint32_t line_base_addr = (axi_window(current_byte_addr) / 16) * 16;
        uint32_t byte_offset    = current_byte_addr % 16;

        if (mock_ddr3.find(line_base_addr) == mock_ddr3.end()) {
            mock_ddr3[line_base_addr] = std::vector<uint8_t>(16, 0);
        }

        mock_ddr3[line_base_addr][byte_offset + 0] = (instruction >> 0)  & 0xFF;
        mock_ddr3[line_base_addr][byte_offset + 1] = (instruction >> 8)  & 0xFF;
        mock_ddr3[line_base_addr][byte_offset + 2] = (instruction >> 16) & 0xFF;
        mock_ddr3[line_base_addr][byte_offset + 3] = (instruction >> 24) & 0xFF;

        current_byte_addr += 4;
    }
    printf("Preloaded %d instructions into mock DDR3 space.\n", current_byte_addr / 4);
    if (limited) {
        printf("Cycle limit set: will stop after %lld cycles.\n", CYCLE_LIMIT);
    } else {
        printf("No cycle limit set: running forever.\n");
    }

    for (int i = 0; i < 10; i++) {
        dut->clock ^= 1;
        dut->io_vga_clk = dut->clock;
        dut->eval();
    }
    dut->reset = 0;
    dut->io_execute = 1;
    std::vector<uint8_t> pixels(H_VISIBLE * V_VISIBLE * 3, 0);
    bool prev_vsync = 1;
    int pixelIdx = 0;

    // --- State variables for tracking memory operations ---
    bool read_in_progress = false;
    int  read_latency_counter = 0;
    uint32_t active_read_addr = 0;

    bool write_in_progress = false;
    int  write_latency_counter = 0;

    while (!limit_reached()) {
        pixelIdx = 0;

        while (true) {
            dut->clock = 1;
            dut->io_vga_clk = 1;

            // 1. Process Incoming Handshakes
            if (dut->io_mem_req_valid) {
                dut->io_mem_req_ready = 1;

                if (dut->io_mem_req_bits_write) {
                    if (!write_in_progress) {
                        handle_mem_write(dut, mock_ddr3);
                        write_in_progress = true;
                        write_latency_counter = 1; // 1-cycle latency response for writes
                    }
                } else if (!read_in_progress) {
                    read_in_progress = true;
                    read_latency_counter = 4;
                    active_read_addr = axi_window(dut->io_mem_req_bits_addr);
                }
            } else {
                dut->io_mem_req_ready = 0;
            }

            // 2. Return Responses / Manage Timing
            if (write_in_progress) {
                if (write_latency_counter > 0) {
                    write_latency_counter--;
                    dut->io_mem_valid = 0;
                } else {
                    dut->io_mem_valid = 1; // Pulse valid high for write acknowledgement
                    write_in_progress = false;
                }
            } else if (read_in_progress) {
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

            bool vsync = dut->io_vsync;

            dut->clock = 0;
            dut->io_vga_clk = 0;
            dut->eval();

            total_cycles++;
            if (limit_reached()) break;

            if (prev_vsync && !vsync) break;
            prev_vsync = vsync;
        }
        prev_vsync = 0;
        if (limit_reached()) break;

        for (int cycle = 0; cycle < H_TOTAL * V_TOTAL; cycle++) {
            dut->clock = 1;
            dut->io_vga_clk = 1;

            // 1. Process Incoming Handshakes
            if (dut->io_mem_req_valid) {
                dut->io_mem_req_ready = 1;

                if (dut->io_mem_req_bits_write) {
                    if (!write_in_progress) {
                        handle_mem_write(dut, mock_ddr3);
                        write_in_progress = true;
                        write_latency_counter = 1;
                    }
                } else if (!read_in_progress) {
                    read_in_progress = true;
                    read_latency_counter = 4;
                    active_read_addr = dut->io_mem_req_bits_addr;
                }
            } else {
                dut->io_mem_req_ready = 0;
            }

            // 2. Return Responses / Manage Timing
            if (write_in_progress) {
                if (write_latency_counter > 0) {
                    write_latency_counter--;
                    dut->io_mem_valid = 0;
                } else {
                    dut->io_mem_valid = 1;
                    write_in_progress = false;
                }
            } else if (read_in_progress) {
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

            total_cycles++;

            if (prev_vsync && !vsync) {
                // printf("vsync mid-frame at cycle %d — counter mismatch!\n", cycle);
            }
            prev_vsync = vsync;

            if (!blanking && pixelIdx < H_VISIBLE * V_VISIBLE) {
                pixels[pixelIdx * 3 + 0] = ((rgb12 >> 8) & 0xF) * 17;
                pixels[pixelIdx * 3 + 1] = ((rgb12 >> 4) & 0xF) * 17;
                pixels[pixelIdx * 3 + 2] = ((rgb12 >> 0) & 0xF) * 17;
                pixelIdx++;
            }

            if (limit_reached()) break;
        }

        // printf("Captured %d pixels (expected %d)\n", pixelIdx, H_VISIBLE * V_VISIBLE);

        FILE* f = fopen("frame.ppm", "wb");
        if (!f) { perror("fopen"); return 1; }
        fprintf(f, "P6\n%d %d\n255\n", H_VISIBLE, V_VISIBLE);
        fwrite(pixels.data(), 1, pixels.size(), f);
        fclose(f);
        if (system("ffmpeg -i frame.ppm frame.png -y > /dev/null 2>&1") != 0) {
             printf("Frame dumped out to local disk as frame.ppm safely.\n");
        }
    }

    if (limited) {
        printf("Stopped after %lld cycles (limit=%lld).\n", total_cycles, CYCLE_LIMIT);
    }

    dut->final();
    return 0;
}