`timescale 1ns / 1ps
module Top(
    input  wire        clk,
    input  wire        btnC,
    input  wire        btnR,
    input  wire        btnU,
    input  wire        btnD,
    input  wire        btnL,
    input  wire        RsRx,
    output wire        RsTx,
    output wire [15:0] led,
    output wire        vgaHSync,
    output wire        vgaVSync,
    output wire [3:0]  vgaRed,
    output wire [3:0]  vgaGreen,
    output wire [3:0]  vgaBlue
);
    // -------------------------------------------------------
    // Clock divider: 100MHz -> 25MHz
    // -------------------------------------------------------
      wire cpu_clk;
      wire clk_25;
      wire locked;
      clk_wiz_0 instance_name
       (
        // Clock out ports
        .clk_out1(clk_25),     // output clk_out1
        .clk_out2(cpu_clk),     // output clk_out2
        // Status and control signals
        .reset(btnC), // input reset
        .locked(locked),       // output locked
        .clk_in1(clk)      // input clk_in1
    );
//    reg [1:0] clk_div;
//    always @(posedge clk) begin
//        if (btnC) clk_div <= 2'h0;
//        else      clk_div <= clk_div + 2'h1;
//    end
//    wire clk_25 = clk_div[1];
    wire [3:0] btns = {btnU,btnR,btnL,btnD};
    // -------------------------------------------------------
    // UART program loader
    // -------------------------------------------------------
    wire        cpu_reset;
    wire        debug_write;
    wire [31:0] debug_write_address;
    wire [31:0] debug_write_data;

    uart_program_loader loader (
        .clk                 (clk_25),
        .rst_n               (~btnC),
        .rx                  (RsRx),
        .cpu_reset           (cpu_reset),
        .debug_write         (debug_write),
        .debug_write_address (debug_write_address),
        .debug_write_data    (debug_write_data)
    );

    wire reset    = btnC | cpu_reset;
    wire execute  = ~reset;

    // -------------------------------------------------------
    // CPU + VGA
    // -------------------------------------------------------
    wire [11:0] rgb;
    wire        blanking;
    wire [31:0] debug_1, debug_2;

    Main cpu (
        .clock                  (cpu_clk),
        .reset                  (reset),
        .io_execute             (execute),
        .io_flash         (debug_write),
        .io_flash_address (debug_write_address),
        .io_flash_value    (debug_write_data),
//        .io_debug_1             (debug_1),
//        .io_debug_2             (debug_2),
        .io_hsync               (vgaHSync),
        .io_vsync               (vgaVSync),
        .io_rgb                 (rgb),
        .io_blanking            (blanking),
        .io_vga_clk             (clk_25)
//        .io_btns                (btns)
//        .io_tx                  (RsTx)
    );

    assign vgaRed   = blanking ? 4'h0 : rgb[11:8];
    assign vgaGreen = blanking ? 4'h0 : rgb[7:4];
    assign vgaBlue  = blanking ? 4'h0 : rgb[3:0];

    // debug_1 = register 1, debug_2 = program counter
    assign led  = debug_1[15:0];
    assign RsTx = 1'b1;
endmodule