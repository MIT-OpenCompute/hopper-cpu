import sys
import serial

def main():
    port = '/dev/ttyUSB2'
    baudrate = 1000000  # 6 Million Baud

    print(f"Opening {port} at {baudrate} baud...")

    try:
        # Open the serial port
        # Using a small timeout lets the read loop cycle occasionally 
        # instead of blocking forever if no data arrives
        ser = serial.Serial(port, baudrate=baudrate, timeout=0.1)
        print("Connected! Listening for data (Ctrl+C to stop)...\n")
        
        while True:
            # Read whatever bytes are waiting in the OS buffer (up to 4096 bytes)
            if ser.in_waiting > 0:
                data = ser.read(ser.in_waiting)
                
                # Decode bytes to string. errors='replace' or 'ignore' prevents 
                # the script from crashing if it encounters noise/corrupted bytes.
                text = data.decode('utf-8', errors='replace')
                
                # Print directly to stdout immediately without extra newlines
                sys.stdout.write(text)
                sys.stdout.flush()

    except serial.SerialException as e:
        print(f"\nSerial Error: {e}")
        print("Check if the device is plugged in or if another process is using it.")
    except PermissionError:
        print(f"\nPermission Error: You may need to run this with sudo or add your user to the dialout group:")
        print(f"  sudo usermod -a -G dialout $USER")
    except KeyboardInterrupt:
        print("\nStopping reader script.")
    finally:
        if 'ser' in locals() and ser.is_open:
            ser.close()
            print("Serial port closed.")

if __name__ == '__main__':
    main()