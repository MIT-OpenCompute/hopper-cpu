import serial

ser = serial.Serial('/dev/ttyACM0', 115200)

while True:
    if ser.read() == b'\xff':           # Wait for start byte
        data = ser.read(5)              # Read remaining 5 bytes
        rv, rh, lh, lv, checksum = data
        if (rv + rh + lh + lv) & 0xFF == checksum:
            print(f"RV:{rv} RH:{rh} LH:{lh} LV:{lv}")