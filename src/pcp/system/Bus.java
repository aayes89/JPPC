/*
 * The MIT License
 *
 * Copyright 2025 Slam.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package pcp.system;

import java.util.ArrayList;
import java.util.List;
import pcp.gpu.Device;

/**
 *
 * @author Slam
 */
public class Bus {

    private static class DeviceMapping {

        final Device device;
        final long startAddress;
        final long endAddress;

        DeviceMapping(Device device, long startAddress, long endAddress) {
            this.device = device;
            this.startAddress = startAddress;
            this.endAddress = endAddress;
        }
    }

    private final List<DeviceMapping> devices = new ArrayList<>();
    private final Memory memory;

    public Bus(Memory memory) {
        this.memory = memory;
    }

    public void attachDevice(Device device, long startAddress, long endAddress) {
        devices.add(new DeviceMapping(device, startAddress, endAddress));
    }

    public byte read(long address) {
        for (DeviceMapping mapping : devices) {
            if (address >= mapping.startAddress && address <= mapping.endAddress) {
                return mapping.device.read(address - mapping.startAddress);
            }
        }
        return memory.read(address);
    }

    public void write(long address, byte value) {
        for (DeviceMapping mapping : devices) {
            if (address >= mapping.startAddress && address <= mapping.endAddress) {
                mapping.device.write(address - mapping.startAddress, value);
                return;
            }
        }
        memory.write(address, value);
    }

    public int readWord(long address) {
        int value = 0;
        for (int i = 0; i < 4; i++) {
            value = (value << 8) | (read(address + i) & 0xFF);
        }
        return value;
    }

    // Método writeWord faltante
    public void writeWord(long address, long value) {
        for (DeviceMapping m : devices) {
            if (address >= m.startAddress && address <= m.endAddress) {
                m.device.writeWord(address - m.startAddress, (int) value);
                return;
            }
        }
        // por defecto: byte‑a‑byte en RAM
        write(address, (byte) (value >> 24));
        write(address + 1, (byte) (value >> 16));
        write(address + 2, (byte) (value >> 8));
        write(address + 3, (byte) value);
    }

    public int getMemorySize() {
        return memory.getMemorySize();
    }
}
