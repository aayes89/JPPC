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
package pcp.gpu;

/**
 *
 * @author Slam
 */
public interface Device {

    boolean containsAddress(long address);

    byte read(long offs);          // acceso de 8 bit

    void write(long offs, byte v);

    default int readWord(long offs) {
        return ((read(offs) & 0xFF) << 24)
                | ((read(offs + 1) & 0xFF) << 16)
                | ((read(offs + 2) & 0xFF) << 8)
                | (read(offs + 3) & 0xFF);
    }

    default void writeWord(long offs, int v) {
        write(offs, (byte) (v >> 24));
        write(offs + 1, (byte) (v >> 16));
        write(offs + 2, (byte) (v >> 8));
        write(offs + 3, (byte) v);
    }
}
