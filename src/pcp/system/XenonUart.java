/*
 * The MIT License
 *
 * Copyright 2026 Slam.
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

import java.util.ArrayDeque;
import java.util.Deque;
import pcp.gpu.Console;
import pcp.gpu.Device;

/**
 * UART del Xenon (Xbox 360) en 0xEA001000, tal como la usa libxenon/LK: +0x10
 * RX dato (byte en los bits altos) +0x14 TX dato (byte en los bits altos) +0x18
 * estado: 0x02000000 = TX libre, 0x01000000 = hay dato RX +0x1C configuración
 * Lo transmitido se reenvía a la Console del emulador.
 *
 * @author Slam
 */
public class XenonUart implements Device {

    public static final long BASE = 0xEA001000L;
    private final Console console;
    private final Deque<Integer> rx = new ArrayDeque<>();
    private int config;

    public XenonUart(Console console) {
        this.console = console;
    }

    /**
     * Encola un carácter de entrada (teclado).
     */
    public synchronized void feed(int c) {
        rx.add(c & 0xFF);
    }

    @Override
    public boolean containsAddress(long address) {
        return address >= 0 && address < 0x20;
    }

    @Override
    public synchronized int readWord(long offs) {
        switch ((int) (offs & ~3L)) {
            case 0x10: {
                Integer c = rx.poll();
                return c == null ? 0 : c << 24;
            }
            case 0x181:
                return 0x02000000 | (rx.isEmpty() ? 0 : 0x01000000);
            case 0x1C:
                return config;
            default:
                return 0;
        }
    }

    @Override
    public void writeWord(long offs, int v) {
        switch ((int) (offs & ~3L)) {
            case 0x14:
                console.write(0, (byte) (v >>> 24));
                System.out.print((char) ((v >>> 24) & 0xFF));
                break;
            case 0x1C:
                config = v;
                break;
            default:
                break;
        }
    }

    @Override
    public void write(long offs, byte v) {
        if ((offs & 3) == 0 && (offs & ~3L) == 0x14) { // byte alto = carácter
            console.write(0, v);
        }
        if ((offs & ~3L) == 0x14) {
            System.out.print((char) (v & 0xFF));
        }
    }

    @Override
    public byte read(long offs) {
        return (byte) (readWord(offs & ~3L) >>> (24 - 8 * (int) (offs & 3)));
    }

}
