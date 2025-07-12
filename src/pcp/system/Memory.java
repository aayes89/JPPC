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
 *
 * @author Slam
 */
public class Memory {

    private byte[] memory = new byte[536870912]; // 512 MB
    private static final long MEMORY_SIZE = 536870912L;
    private static final long MEMORY_MASK = 0x1FFFFFFF; // 512 MB - 1
    private boolean debugMode = false;

    public Memory(int size) {
        this.memory = new byte[size];
    }

    public byte read(long address) {
        if (address < 0 || address >= memory.length) {
            return 0;
        }
        return memory[(int) address];
    }

    public void write(long address, byte value) {
        if (address >= 0 && address < memory.length) {
            memory[(int) address] = value;
        }
    }

    public boolean containsAddress(long address) {
        return address >= 0 && address < memory.length;
    }

    public void writeBlock(long address, byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Invalid data for writeBlock: address=0x"
                    + Long.toHexString(address));
        }
        int memIndex = (int) (address & MEMORY_MASK);
        if (memIndex < 0 || memIndex + data.length > MEMORY_SIZE) {
            throw new IllegalArgumentException("Memory write out of bounds: address=0x"
                    + Long.toHexString(address) + ", length=" + data.length);
        }
        if (debugMode) {
            System.out.println("Writing block: address=0x" + Long.toHexString(address)
                    + ", length=" + data.length + ", first 4 bytes=0x"
                    + (data.length >= 4 ? String.format("%02x%02x%02x%02x",
                                    data[0], data[1], data[2], data[3]) : "N/A"));
        }
        System.arraycopy(data, 0, memory, memIndex, data.length);
    }

    public int readMemoryWord(long address) {
        int memIndex = (int) (address & MEMORY_MASK);
        if (memIndex < 0 || memIndex + 3 >= MEMORY_SIZE) {
            throw new IllegalArgumentException("Memory read out of bounds: address=0x"
                    + Long.toHexString(address));
        }
        int value = ((memory[memIndex] & 0xFF) << 24)
                | ((memory[memIndex + 1] & 0xFF) << 16)
                | ((memory[memIndex + 2] & 0xFF) << 8)
                | (memory[memIndex + 3] & 0xFF);
        if (debugMode) {
            System.out.println("Reading word: address=0x" + Long.toHexString(address)
                    + ", value=0x" + Integer.toHexString(value));
        }
        return value;
    }

    public void writeMemoryWord(long address, int value) {
        int memIndex = (int) (address & MEMORY_MASK);
        if (memIndex < 0 || memIndex + 3 >= MEMORY_SIZE) {
            throw new IllegalArgumentException("Memory write out of bounds: address=0x"
                    + Long.toHexString(address));
        }
        if (debugMode) {
            System.out.println("Writing word: address=0x" + Long.toHexString(address)
                    + ", value=0x" + Integer.toHexString(value));
        }
        memory[memIndex] = (byte) (value >> 24);
        memory[memIndex + 1] = (byte) (value >> 16);
        memory[memIndex + 2] = (byte) (value >> 8);
        memory[memIndex + 3] = (byte) value;
    }

    public void dumpMemory(long startAddress, int length) {
        int startIndex = (int) (startAddress & MEMORY_MASK);
        if (startIndex < 0 || startIndex + length > MEMORY_SIZE) {
            throw new IllegalArgumentException("Invalid memory dump range: address=0x"
                    + Long.toHexString(startAddress) + ", length=" + length);
        }
        System.out.println("Memory dump at 0x" + Long.toHexString(startAddress) + ":");
        for (int i = 0; i < length; i += 4) {
            if (startIndex + i + 3 >= MEMORY_SIZE) {
                break;
            }
            int word = ((memory[startIndex + i] & 0xFF) << 24)
                    | ((memory[startIndex + i + 1] & 0xFF) << 16)
                    | ((memory[startIndex + i + 2] & 0xFF) << 8)
                    | (memory[startIndex + i + 3] & 0xFF);
            System.out.printf("%08x: %08x\n", startAddress + i, word);
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
