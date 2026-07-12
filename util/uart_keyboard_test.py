#!/usr/bin/env python3
"""
Test script for UartKeyboardTracker.

Protocol (must match the Chisel module):
    byte 0 (event type): 0x01 = key DOWN, 0x00 = key UP
    byte 1 (keycode)   : HID usage ID, 0-255

Two modes:
  --selftest   Sends a scripted press/release sequence on a few keys with
               delays, so you can just watch the LEDs/7-seg and confirm
               the link works end-to-end -- no real keyboard needed.
  --live       Forwards your actual keyboard's press/release events in
               real time (uses the third-party `keyboard` package).

Install dependencies:
    pip install pyserial
    pip install keyboard   # only needed for --live mode

Usage:
    python3 uart_keyboard_test.py --port /dev/ttyUSB1 --selftest
    python3 uart_keyboard_test.py --port COM5 --live
"""

import argparse
import sys
import time

import serial

BAUD_RATE =1000000

# ---- HID usage IDs for common keys (USB HID Usage Tables, keyboard page) --
HID_KEYCODES = {
    'a': 0x04, 'b': 0x05, 'c': 0x06, 'd': 0x07, 'e': 0x08, 'f': 0x09,
    'g': 0x0A, 'h': 0x0B, 'i': 0x0C, 'j': 0x0D, 'k': 0x0E, 'l': 0x0F,
    'm': 0x10, 'n': 0x11, 'o': 0x12, 'p': 0x13, 'q': 0x14, 'r': 0x15,
    's': 0x16, 't': 0x17, 'u': 0x18, 'v': 0x19, 'w': 0x1A, 'x': 0x1B,
    'y': 0x1C, 'z': 0x1D,
    '1': 0x1E, '2': 0x1F, '3': 0x20, '4': 0x21, '5': 0x22,
    '6': 0x23, '7': 0x24, '8': 0x25, '9': 0x26, '0': 0x27,
    'enter': 0x28, 'esc': 0x29, 'backspace': 0x2A, 'tab': 0x2B,
    'space': 0x2C, '-': 0x2D, '=': 0x2E, '[': 0x2F, ']': 0x30,
    '\\': 0x31, ';': 0x33, "'": 0x34, '`': 0x35, ',': 0x36,
    '.': 0x37, '/': 0x38, 'caps lock': 0x39,
    'f1': 0x3A, 'f2': 0x3B, 'f3': 0x3C, 'f4': 0x3D, 'f5': 0x3E, 'f6': 0x3F,
    'f7': 0x40, 'f8': 0x41, 'f9': 0x42, 'f10': 0x43, 'f11': 0x44, 'f12': 0x45,
    'right': 0x4F, 'left': 0x50, 'down': 0x51, 'up': 0x52,
    'left ctrl': 0xE0, 'left shift': 0xE1, 'left alt': 0xE2, 'left windows': 0xE3,
    'right ctrl': 0xE4, 'right shift': 0xE5, 'right alt': 0xE6, 'right windows': 0xE7,
}


def send_event(ser: serial.Serial, code: int, pressed: bool) -> None:
    ser.write(bytes([1 if pressed else 0, code & 0xFF]))


def run_selftest(ser: serial.Serial) -> None:
    """Scripted press/release sequence -- no physical keyboard required.
    Watch D0 (last keycode + DP for held/released) and LED[15:0] (which of
    keycodes 0-15 are currently down -- note most of these test keys are
    above 15, so LEDs will mostly stay dark; that's expected, it's just
    exercising the link)."""
    test_sequence = ['h', 'e', 'l', 'l', 'o']

    print(f"Sending test sequence: {test_sequence}")
    for ch in test_sequence:
        code = HID_KEYCODES[ch]
        print(f"  PRESS '{ch}' (0x{code:02X})")
        send_event(ser, code, True)
        time.sleep(0.3)
        print(f"  RELEASE '{ch}' (0x{code:02X})")
        send_event(ser, code, False)
        time.sleep(0.3)

    print("\nHolding multiple keys at once (checks numDown / keyDown table)...")
    held = ['left shift', 'a', 's']
    for ch in held:
        code = HID_KEYCODES[ch]
        print(f"  PRESS '{ch}' (0x{code:02X}) -- holding")
        send_event(ser, code, True)
        time.sleep(0.2)
    print("  (numDown on D1 should read 3 now)")
    time.sleep(2)
    for ch in held:
        code = HID_KEYCODES[ch]
        print(f"  RELEASE '{ch}' (0x{code:02X})")
        send_event(ser, code, False)
        time.sleep(0.2)

    print("\nSelf-test done.")


def run_live(ser: serial.Serial) -> None:
    """Forwards real keyboard press/release events in real time."""
    try:
        import keyboard
    except ImportError:
        print("The --live mode needs the 'keyboard' package: pip install keyboard")
        print("(On Linux this also typically needs to be run as root/sudo.)")
        sys.exit(1)

    def on_event(event: "keyboard.KeyboardEvent") -> None:
        name = event.name
        code = HID_KEYCODES.get(name)
        if code is None:
            print(f"  (no HID mapping for '{name}', skipping)")
            return
        pressed = event.event_type == 'down'
        print(f"  {'PRESS' if pressed else 'RELEASE':7s} '{name}' (0x{code:02X})")
        send_event(ser, code, pressed)

    print("Live mode: forwarding your keyboard to the FPGA. Ctrl+C to quit.")
    keyboard.hook(on_event)
    keyboard.wait()


def main() -> None:
    parser = argparse.ArgumentParser(description="Test UartKeyboardTracker over serial")
    parser.add_argument('--port', required=True, help="Serial port, e.g. /dev/ttyUSB1 or COM5")
    parser.add_argument('--baud', type=int, default=BAUD_RATE)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--selftest', action='store_true', help="Run a scripted test sequence")
    mode.add_argument('--live', action='store_true', help="Forward real keyboard events live")
    args = parser.parse_args()

    with serial.Serial(args.port, args.baud, timeout=1) as ser:
        print(f"Opened {args.port} @ {args.baud} baud")
        time.sleep(0.2)  # let the port settle
        if args.selftest:
            run_selftest(ser)
        else:
            run_live(ser)


if __name__ == '__main__':
    main()