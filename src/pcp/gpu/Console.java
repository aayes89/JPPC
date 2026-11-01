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

    private final StringBuilder history = new StringBuilder();
    private volatile java.util.function.IntConsumer listener;
    private volatile boolean echo = true;

    /** Recibe cada carácter emitido (por ejemplo, para mostrarlo en una ventana). */
    public void setListener(java.util.function.IntConsumer l) {
        listener = l;
    }

    public void setEcho(boolean e) {
        echo = e;
    }

    @Override
    public byte read(long address) {
        return 0; // Solo dispositivo de salida
    }

    @Override
    public void write(long address, byte value) {
        if (address == 0) {
            char c = (char) (value & 0xFF);
            synchronized (history) {
                history.append(c);
            }
            if (echo) {
                System.out.print(c); // inmediato: el prompt "] " de LK no termina en '\n'
                System.out.flush();
            }
            java.util.function.IntConsumer l = listener;
            if (l != null) {
                l.accept(c);
            }
        }
    }

    @Override
    public boolean containsAddress(long address) {
        return address >= 0 && address < 4;
    }

    /** Todo lo emitido hasta ahora. */
    public String getOutput() {
        synchronized (history) {
            return history.toString();
        }
    }

    public void clear() {
        synchronized (history) {
            history.setLength(0);
        }
    }

    @Override
    public int readWord(long address) {
        return 0;
    }
}
