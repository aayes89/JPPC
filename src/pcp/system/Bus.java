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
 * Bus del sistema. Las direcciones se normalizan a 32 bits sin signo (antes,
 * 0x80000000+ llegaba como long negativo y nunca coincidía con un dispositivo
 * ni con la RAM). Los dispositivos tienen prioridad sobre la RAM.
 *
 * @author Slam
 */
public class Bus {

    private static final long ADDR_MASK = 0xFFFFFFFFL;

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
    private boolean traceUnmapped = false;
    private int unmappedLogged = 0;
    private final java.util.Map<String, Integer> seen = new java.util.HashMap<>();

    /** Registra accesos a direcciones sin dispositivo fuera de la RAM (para descubrir el hardware que espera el firmware). */
    public void setTraceUnmapped(boolean t) {
        traceUnmapped = t;
    }

    private void logUnmapped(String kind, long address, long value) {
        if (!traceUnmapped || unmappedLogged >= 200) {
            return;
        }
        String key = kind + address;
        int n = seen.merge(key, 1, Integer::sum);
        if (n > 2) {
            return; // no repetir el mismo acceso (bucles de espera)
        }
        boolean ramMirror = address >= 0x80000000L && address < 0xA0000000L;
        if (address >= memory.getMemorySize() && !ramMirror) {
            unmappedLogged++;
            System.out.printf("[bus] %s no mapeado 0x%08x valor=0x%x%n", kind, address, value);
        }
    }

    public Bus(Memory memory) {
        this.memory = memory;
    }

    public void attachDevice(Device device, long startAddress, long endAddress) {
        devices.add(new DeviceMapping(device, startAddress & ADDR_MASK, endAddress & ADDR_MASK));
    }

    /** 0x80000000|dirección-física es el espejo "real mode" de la dirección física (< 512 MB). */
    private static long canon(long address) {
        return (address >= 0x80000000L && address < 0xA0000000L) ? (address & 0x1FFFFFFFL) : address;
    }

    private DeviceMapping find(long address) {
        for (int i = 0; i < devices.size(); i++) {
            DeviceMapping m = devices.get(i);
            if (address >= m.startAddress && address <= m.endAddress) {
                return m;
            }
        }
        return null;
    }

    public byte read(long address) {
        address = canon(address & ADDR_MASK);
        DeviceMapping m = find(address);
        if (m == null) {
            logUnmapped("lectura", address, 0);
        }
        return m != null ? m.device.read(address - m.startAddress) : memory.read(address);
    }

    public void write(long address, byte value) {
        address = canon(address & ADDR_MASK);
        DeviceMapping m = find(address);
        if (m != null) {
            m.device.write(address - m.startAddress, value);
        } else {
            logUnmapped("escritura", address, value & 0xFF);
            memory.write(address, value);
        }
    }

    public int readWord(long address) {
        address = canon(address & ADDR_MASK);
        DeviceMapping m = find(address);
        if (m == null) {
            logUnmapped("lectura32", address, 0);
        }
        return m != null ? m.device.readWord(address - m.startAddress) : memory.readMemoryWord(address);
    }

    public void writeWord(long address, long value) {
        address = canon(address & ADDR_MASK);
        DeviceMapping m = find(address);
        if (m != null) {
            m.device.writeWord(address - m.startAddress, (int) value);
        } else {
            logUnmapped("escritura32", address, value & 0xFFFFFFFFL);
            memory.writeMemoryWord(address, (int) value);
        }
    }

    public int readHalf(long address) {
        return ((read(address) & 0xFF) << 8) | (read(address + 1) & 0xFF);
    }

    public void writeHalf(long address, int value) {
        write(address, (byte) (value >> 8));
        write(address + 1, (byte) value);
    }

    public int getMemorySize() {
        return memory.getMemorySize();
    }
}
