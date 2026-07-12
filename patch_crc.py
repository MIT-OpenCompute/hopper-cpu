#!/usr/bin/env python3
# patch_crc.py  <in.bin>  <out.bin>  [out.hex]
import sys, zlib, struct

MAGIC = 0x1BADC0DE

data = bytearray(open(sys.argv[1], 'rb').read())

# pad to 16-byte boundary (UART packer flushes only complete lines)
while len(data) % 16:
    data.append(0)

off = data.find(struct.pack('<I', MAGIC))
assert off != -1, "crc_info magic not found in binary"
assert data.find(struct.pack('<I', MAGIC), off + 4) == -1, "magic appears twice"
assert off % 4 == 0

struct.pack_into('<I', data, off + 4, len(data))  # length
struct.pack_into('<I', data, off + 8, 0)          # zero CRC field first
crc = zlib.crc32(bytes(data)) & 0xFFFFFFFF        # CRC with field zeroed
struct.pack_into('<I', data, off + 8, crc)        # patch it in

open(sys.argv[2], 'wb').write(data)
print(f"image {len(data)} bytes, crc32 0x{crc:08X}, info block at 0x{off:X}")

if len(sys.argv) > 3:
    with open(sys.argv[3], 'w') as f:
        for i in range(0, len(data), 4):
            f.write('%08x\n' % struct.unpack_from('<I', data, i)[0])