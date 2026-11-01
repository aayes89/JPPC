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

import java.util.Arrays;

/**
 * RAM física big-endian. Las direcciones se espejan con (size-1), igual que el
 * modo real de Xenon donde 0x80000000+X y X apuntan a la misma RAM.
 *
 * @author Slam
 */
public class Memory {

    private final byte[] memory;
    private final long mask;
    private boolean debugMode = false;

    public Memory(int size) {
        if (Integer.bitCount(size) != 1) {
            throw new IllegalArgumentException("El tamaño de RAM debe ser potencia de 2");
        }
        this.memory = new byte[size];   // antes se reservaban 512MB dos veces
        this.mask = size - 1L;
    }

    private int idx(long address) {
        return (int) (address & mask);
    }

    public byte read(long address) {
        return memory[idx(address)];
    }

    public void write(long address, byte value) {
        memory[idx(address)] = value;
    }

    public boolean containsAddress(long address) {
        return address >= 0 && address < memory.length;
    }

    public void writeBlock(long address, byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Invalid data for writeBlock: address=0x" + Long.toHexString(address));
        }
        int memIndex = idx(address);
        if (memIndex + data.length > memory.length) {
            throw new IllegalArgumentException("Memory write out of bounds: address=0x" + Long.toHexString(address) + ", length=" + data.length);
        }
        if (debugMode) {
            System.out.println("Writing block: address=0x" + Long.toHexString(address) + ", length=" + data.length);
        }
        System.arraycopy(data, 0, memory, memIndex, data.length);
    }

    public int readMemoryWord(long address) {
        int i = idx(address);
        return ((memory[i] & 0xFF) << 24)
                | ((memory[(i + 1) & (int) mask] & 0xFF) << 16)
                | ((memory[(i + 2) & (int) mask] & 0xFF) << 8)
                | (memory[(i + 3) & (int) mask] & 0xFF);
    }

    public void writeMemoryWord(long address, int value) {
        int i = idx(address);
        memory[i] = (byte) (value >> 24);
        memory[(i + 1) & (int) mask] = (byte) (value >> 16);
        memory[(i + 2) & (int) mask] = (byte) (value >> 8);
        memory[(i + 3) & (int) mask] = (byte) value;
    }

    public void dumpMemory(long startAddress, int length) {
        System.out.println("Memory dump at 0x" + Long.toHexString(startAddress) + ":");
        for (int i = 0; i < length; i += 4) {
            System.out.printf("%08x: %08x%n", startAddress + i, readMemoryWord(startAddress + i));
        }
    }

    public void setDebugMode(boolean mode) {
        this.debugMode = mode;
    }

    public void fill(byte b) {
        Arrays.fill(memory, b);
    }

    int getMemorySize() {
        return memory.length;
    }
}
