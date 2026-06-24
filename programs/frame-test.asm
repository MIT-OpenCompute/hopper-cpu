addi x3, x3, 0
addi x1, x0, 0xE0
lui x2, 0x4
add x4, x2, x3
sw x1, 0(x4)
addi x3, x3, 1
jal x0, -12