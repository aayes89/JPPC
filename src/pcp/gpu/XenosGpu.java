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
 * Registros mínimos de la GPU Xenos (Xbox 360) en 0xEC800000, los que usan
 * libxenon/LK para descubrir el framebuffer (bloque D1GRPH):
 *   +0x6110 dirección física del framebuffer   +0x6120 pitch (bytes)
 *   +0x6134 X_END (ancho)                      +0x6138 Y_END (alto)
 * El resto de registros se comporta como RAM (lee lo último escrito).
 *
 * @author Slam
 */
public class XenosGpu implements Device {

    public static final long BASE = 0xEC800000L;
    public static final int SIZE = 0x100000;
    private final int[] regs = new int[SIZE / 4];

    public XenosGpu(long fbPhysBase, int width, int height) {
        regs[0x6110 / 4] = (int) fbPhysBase;
        regs[0x6120 / 4] = width * 4;
        regs[0x6134 / 4] = width;
        regs[0x6138 / 4] = height;
    }

    @Override
    public boolean containsAddress(long address) {
        return address >= 0 && address < SIZE;
    }

    @Override
    public int readWord(long offs) {
        int i = (int) (offs >> 2);
        return i >= 0 && i < regs.length ? regs[i] : 0;
    }

    @Override
    public void writeWord(long offs, int v) {
        int i = (int) (offs >> 2);
        if (i >= 0 && i < regs.length) {
            regs[i] = v;
        }
    }

    @Override
    public byte read(long offs) {
        return (byte) (readWord(offs & ~3L) >>> (24 - 8 * (int) (offs & 3)));
    }

    @Override
    public void write(long offs, byte v) {
        int sh = 24 - 8 * (int) (offs & 3);
        int w = readWord(offs & ~3L);
        writeWord(offs & ~3L, (w & ~(0xFF << sh)) | ((v & 0xFF) << sh));
    }
}
