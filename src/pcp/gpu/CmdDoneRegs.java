package pcp.gpu;

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

/**
 * Controlador serie/I2C del bus del sistema (0xEA001300). El firmware espera a
 * que el bit 7 del byte +7 esté libre, escribe un comando en ese byte y espera
 * a que los bits 7:6 valgan 01 ("comando terminado"). Modelo mínimo: al
 * escribir el byte de comando el estado pasa a 0x40 inmediatamente; el resto
 * son registros-RAM.
 *
 * @author Slam
 */
public class CmdDoneRegs implements Device {

    public static final long BASE = 0xEA001300L;
    private final byte[] r = new byte[0x20];

    public CmdDoneRegs() {
        r[7] = 0x48;
    }

    @Override
    public boolean containsAddress(long a) {
        return a >= 0 && a < r.length;
    }

    @Override
    public synchronized byte read(long offs) {
        return offs >= 0 && offs < r.length ? r[(int) offs] : 0;
    }

    @Override
    public synchronized void write(long offs, byte v) {
        if (offs < 0 || offs >= r.length) {
            return;
        }
        if (offs == 7) {
            r[7] = 0x48;          // estado: listo (bit 3), comando terminado (bits 7:6 = 01), sin dato RX pendiente
        } else {
            r[(int) offs] = v;
        }
    }

    @Override
    public synchronized int readWord(long offs) {
        int o = (int) (offs & ~3L);
        return ((read(o) & 0xFF) << 24) | ((read(o + 1) & 0xFF) << 16) | ((read(o + 2) & 0xFF) << 8) | (read(o + 3) & 0xFF);
    }

    @Override
    public synchronized void writeWord(long offs, int v) {
        int o = (int) (offs & ~3L);
        write(o, (byte) (v >>> 24));
        write(o + 1, (byte) (v >>> 16));
        write(o + 2, (byte) (v >>> 8));
        write(o + 3, (byte) v);
    }
}
