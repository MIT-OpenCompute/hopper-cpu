import sys

with open(sys.argv[1], "rb") as f:
    data = f.read()

output = sys.argv[1].rsplit(".", 1)[0] + ".hex"

with open(output, "w") as f:
    for i in range(0, len(data), 4):
        word = data[i:i+4]
        val = int.from_bytes(word, "little")
        f.write(f"{val:08x}\n")