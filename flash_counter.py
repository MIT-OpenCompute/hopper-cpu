#!/usr/bin/env python3
"""
DDR3/Cache Hardware Verification Script
Generates a known mathematical sequence (Word = Addr ^ 0xDEADBEEF)
and sends it to the FPGA for Phase 1 (Write) and Phase 2 (Verify) testing.

Usage:
    python pattern_test.py --words 1000 --port COM3
"""

import serial
import time
import sys
import argparse

parser = argparse.ArgumentParser(description="Generate and send known pattern for DDR3 test")
parser.add_argument("--port",  default="COM3", help="Serial port (default: COM3)")
parser.add_argument("--baud",  default=115200, type=int, help="Baud rate")
parser.add_argument("--words", default=1000, type=int, help="Number of words to test")
parser.add_argument("--delay", default=0.002, type=float, help="Delay between bytes sent")
args = parser.parse_args()

print(f"Generating {args.words} words of pattern test data...")

words = []
for i in range(args.words):
    addr = i * 4
    word_val = addr ^ 0xDEADBEEF
    words.append(word_val)

try:
    with serial.Serial(args.port, args.baud, timeout=2) as ser:
        print(f"Opened {args.port} at {args.baud} baud")
        print("\n--- PHASE 1: WRITING ---")
        
        for i, w in enumerate(words):
            # Send little-endian
            ser.write(w.to_bytes(4, byteorder='little'))
            time.sleep(args.delay)
            
            # Progress tracking
            if i % 100 == 0 or i == len(words) - 1:
                pct = (i + 1) / len(words) * 100
                print(f"\r[{'#' * int(pct/2):<50}] {pct:.1f}% ({i+1}/{len(words)} words)", end='')

        print("\n\n--- PHASE 2: VERIFICATION ---")
        print("Sending EOF Sequence (0xFF 0xFF 0xFF 0xFF) to trigger cache reads...")
        ser.write(b'\xFF\xFF\xFF\xFF\xFF')
        time.sleep(1)

        print("\nDone! Check the LEDs on the Basys3 board:")
        print(" -> If all 16 LEDs are solid ON, the memory hierarchy works perfectly!")
        print(" -> If they are blinking, a cache miss/fetch failed.")

except serial.SerialException as e:
    print(f"\nSerial error: {e}")
    sys.exit(1)





#     #!/usr/bin/env python3
# import serial, time, sys, argparse

# parser = argparse.ArgumentParser()
# parser.add_argument("--port", default="COM3")
# args = parser.parse_args()

# # Generate 100 words of PURE 1s
# # (We use 0xFFFFFFFE to prevent accidentally triggering the EOF sequence)
# words = [0xFFFFFFFE for _ in range(100)]

# try:
#     with serial.Serial(args.port, 115200, timeout=2) as ser:
#         print("Sending all-1s stress test to find stuck pins...")
#         for w in words:
#             ser.write(w.to_bytes(4, byteorder='little'))
#             time.sleep(0.2)
            
#         print("Triggering Verify...")
#         ser.write(b'\xFF\xFF\xFF\xFF\xFF')

# except Exception as e:
#     print(e)