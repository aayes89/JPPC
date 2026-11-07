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
package pcp.xenon;

import java.util.HashMap;
import java.util.Map;
import pcp.gpu.Device;

/**
 * Interfaz MMIO provisional del motor criptográfico Xenon.
 *
 * En esta etapa: - Conserva las escrituras MMIO. - Devuelve los valores
 * almacenados. - Registra accesos para reconstruir el mapa de registros. - No
 * ejecuta operaciones criptográficas no verificadas.
 *
 * @author Slam
 */
public final class XenonCrypto implements Device {

    public static final long BASE = 0xEA004000L;
    public static final long SIZE = 0x10000L;

    private final Map<Long, Integer> registers = new HashMap<>();

    private boolean trace = true;

    public XenonCrypto() {
    }

    public void setTrace(boolean enabled) {
        this.trace = enabled;
    }

    @Override
    public boolean containsAddress(long address) {
        return Long.compareUnsigned(address, SIZE - 1) <= 0;
    }

    @Override
    public synchronized byte read(long offset) {
        long aligned = offset & ~3L;
        int value = readWord(aligned);

        int shift = 24 - (int) ((offset & 3L) * 8);

        return (byte) (value >>> shift);
    }

    @Override
    public synchronized void write(long offset, byte value) {
        long aligned = offset & ~3L;

        int oldValue = registers.getOrDefault(aligned, 0);

        int shift = 24 - (int) ((offset & 3L) * 8);
        int mask = 0xFF << shift;

        int newValue = (oldValue & ~mask) | ((value & 0xFF) << shift);

        writeWord(aligned, newValue);
    }

    @Override
    public synchronized int readWord(long offset) {
        long aligned = offset & ~3L;

        int value = registers.getOrDefault(aligned, 0);

        if (trace) {
            System.out.printf(
                    "[XENON-CRYPTO] READ32 OFFSET=0x%08X VALUE=0x%08X%n",
                    aligned, value
            );
        }

        return value;
    }

    @Override
    public synchronized void writeWord(long offset, int value) {
        long aligned = offset & ~3L;

        registers.put(aligned, value);

        if (trace) {
            System.out.printf(
                    "[XENON-CRYPTO] WRITE32 OFFSET=0x%08X VALUE=0x%08X%n",
                    aligned, value
            );
        }
    }

    public synchronized void reset() {
        registers.clear();
    }

    public synchronized int getRegister(long offset) {
        return registers.getOrDefault(offset, 0);
    }
}
