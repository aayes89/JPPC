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

/**
 *
 * @author Slam
 */
public class CacheController {

    private static class CacheLine {

        int tag;
        int[] data = new int[16]; // 64 bytes per line (16 words)
        boolean valid;
        boolean dirty;
    }

    private final CacheLine[] cache;
    private final Bus bus;
    private final boolean writeThrough;
    private final int memorySize;

    public CacheController(Bus bus, int memorySize, boolean writeThrough) {
        this.bus = bus;
        this.memorySize = memorySize;
        this.writeThrough = writeThrough;
        this.cache = new CacheLine[128];
        for (int i = 0; i < cache.length; i++) {
            cache[i] = new CacheLine();
            cache[i].valid = false;
            cache[i].dirty = false;
        }
    }

    public long readWord(long address) {
        if (!isCacheable(address)) {
            return bus.readWord(address);
        }
        int index = getIndex((int)address);
        int tag = getTag((int)address);
        int offset = getWordOffset((int)address);
        CacheLine line = cache[(int)index];
        if (line.valid && line.tag == tag) {
            return line.data[offset];
        }
        loadLine((int)address, index, tag);
        return cache[index].data[offset];
    }

    public void writeWord(long address, long value) {
        if (!isCacheable(address)) {
            bus.writeWord(address, value);
            return;
        }
        int index = getIndex((int)address);
        int tag = getTag((int)address);
        int offset = getWordOffset((int)address);
        CacheLine line = cache[(int)index];
        if (line.valid && line.tag == tag) {
            line.data[offset] = (int)value;
            line.dirty = !writeThrough;
            if (writeThrough) {
                bus.writeWord(address, value);
            }
        } else {
            bus.writeWord(address, value);
        }
    }

    // Cargar línea completa desde memoria
    private void loadLine(int address, int index, int tag) {
        CacheLine line = cache[(int)index];
        if (line.valid && line.dirty) {
            writeBackLine(index);
        }
        long baseAddress = address & ~0x3F;
        for (int i = 0; i < 16; i++) {
            line.data[i] = bus.readWord(baseAddress + (i * 4));
        }
        line.valid = true;
        line.dirty = false;
        line.tag = tag;
    }

    private void writeBackLine(long index) {
        CacheLine line = cache[(int)index];
        if (!line.valid || !line.dirty) {
            return;
        }
        long baseAddress = (line.tag << 13) | (index << 6);
        for (int i = 0; i < 16; i++) {
            bus.writeWord(baseAddress + (i * 4), line.data[i]);
        }
        line.dirty = false;
    }

    private boolean isCacheable(long address) {
        return address < memorySize;
    }

    private int getTag(int address) {
        return address >>> 13;
    }

    private int getIndex(int address) {
        return (address >> 6) & 0x7F;
    }

    private int getWordOffset(int address) {
        return (address >> 2) & 0xF;
    }

    public void flush() {
        for (int i = 0; i < cache.length; i++) {
            CacheLine line = cache[i];
            if (line.valid && line.dirty) {
                writeBackLine(i);
            }
            line.valid = false;
            line.dirty = false;
        }
    }
}
