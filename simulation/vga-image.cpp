#include "VMain.h"
#include "verilated.h"
#include <cstdio>
#include <vector>
#include <fstream>

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

    dut->io_execute = 0;
    dut->io_flash = 0;
    dut->io_flash_address = 0;
    dut->io_flash_value = 0;
    dut->io_btns = 0;

    dut->reset = 1;
    dut->clock = 0;
    dut->io_vga_clk = 0;

    for (int i = 0; i < 4; i++) {
        dut->clock ^= 1;
        dut->io_vga_clk = dut->clock;
        dut->eval();
    }

    dut->reset = 0;

    std::vector<uint8_t> pixels(H_VISIBLE * V_VISIBLE * 3, 0);

    int hCount = 0;
    int vCount = 0;

    std::ifstream file("/home/liamh/RISC-V/programs/frame-test.hex");

    std::string line;
    int address = 0;

    while (std::getline(file, line)) {
        if (line.empty()) continue;

        uint32_t value = std::stoul(line, nullptr, 16);

        dut->io_flash = 1;
        dut->io_flash_address = address;
        dut->io_flash_value = value;

        printf("Writing %x at %d\n", value, address);

        dut->clock = 1;
        dut->io_vga_clk = 1;
        dut->eval();
        dut->clock = 0;
        dut->io_vga_clk = 0;
        dut->eval();

        address++;
    }

    dut->io_flash = 0;
    dut->io_flash_address = 0;
    dut->io_flash_value = 0;

    for (int i = 0; i < 4; i++) {
        dut->clock ^= 1;
        dut->io_vga_clk = dut->clock;
        dut->eval();
    }

    dut->io_execute = 1;

    // for (int i = 0; i < 2 * 8; i++) {
    //     dut->clock ^= 1;
    //     dut->io_vga_clk = dut->clock;
    //     dut->eval();
    // }

    for (int cycle = 0; cycle < H_TOTAL * V_TOTAL; cycle++) {
        dut->clock = 1;
        dut->io_vga_clk = 1;
        dut->eval();

        if (!dut->io_blanking) {
            int x = hCount;
            int y = vCount;

            if (x < H_VISIBLE && y < V_VISIBLE) {
                uint16_t rgb12 = dut->io_rgb;
                
                int idx = (y * H_VISIBLE + x) * 3;
                
                pixels[idx + 0] = ((rgb12 >> 8) & 0xF) * 17;
                pixels[idx + 1] = ((rgb12 >> 4) & 0xF) * 17;
                pixels[idx + 2] = ((rgb12 >> 0) & 0xF) * 17;
            }
        }

        dut->clock = 0;
        dut->io_vga_clk = 0;
        dut->eval();

        if (hCount == H_TOTAL - 1) {
            hCount = 0;
            vCount = (vCount == V_TOTAL - 1) ? 0 : vCount + 1;
        } else {
            hCount++;
        }
    }

    FILE* f = fopen("frame.ppm", "wb");

    if (!f) { perror("fopen"); return 1; }
    
    fprintf(f, "P6\n%d %d\n255\n", H_VISIBLE, V_VISIBLE);
    
    fwrite(pixels.data(), 1, pixels.size(), f);
    
    fclose(f);

    dut->final();

    printf("Wrote frame.ppm (%dx%d)\n", H_VISIBLE, V_VISIBLE);
    
    return 0;
}