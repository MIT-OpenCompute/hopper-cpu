cd generated
verilator --cc --exe --build -j 0 --threads 32 ../simulation/vga-image.cpp -f filelist.f --top Main
./obj_dir/VMain