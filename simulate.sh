cd generated
verilator --cc --exe --build -j 0 ../simulation/vga-image.cpp -f filelist.f --top Main
./obj_dir/VMain
ffmpeg -i frame.ppm frame.png -y