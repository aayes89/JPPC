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
public class Console implements Device {

    private final StringBuilder buffer = new StringBuilder();

    @Override
    public byte read(long address) {
        return 0; // Solo dispositivo de salida
    }

    @Override
    public void write(long address, byte value) {
        if (address == 0) {
            char c = (char) (value & 0xFF);
            buffer.append(c);
            if (c == '\n') {
                System.out.print(buffer);
                buffer.setLength(0);
            }
        }
    }

    @Override
    public boolean containsAddress(long address) {
        return address >= 0 && address < 4;
    }

    public String getOutput() {
        return buffer.toString();
    }

    public void clear() {
        buffer.setLength(0);
    }

    @Override
    public int readWord(long address) {
        int index = (int) address;
        if (index < 0 || index >= buffer.length() - 3) {
            return 0;
        }

        return ((buffer.charAt(index) & 0xFF) << 24)
                | ((buffer.charAt(index + 1) & 0xFF) << 16)
                | ((buffer.charAt(index + 2) & 0xFF) << 8)
                | (buffer.charAt(index + 3) & 0xFF);
    }
}
