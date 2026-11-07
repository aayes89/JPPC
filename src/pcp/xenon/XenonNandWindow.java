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

import java.util.Arrays;
import pcp.gpu.Device;
import pcp.gpu.NandImage;

/**
 * Ventana de datos lógica de la NAND en el mapa del SoC (0x80000200C8000000).
 *
 * El 1BL no usa comandos SFCX para leer la cabecera y el CB: los lee
 * directamente por esta ventana (en TOC+0x130 del 1BL está 0x80000200C8000000;
 * el campo "entry" de la cabecera NAND, en +8, es el offset del CB). La ventana
 * expone los 512 bytes de datos de cada página, sin OOB, de forma contigua:
 * offset = página * 0x200 + byte.
 *
 * Solo lectura. Sin imagen NAND devuelve 0xFF (flash borrada), así que el 1BL
 * falla de forma natural en la validación del offset del CB en lugar de leer
 * ceros.
 *
 * @autor: Slam
 */
public final class XenonNandWindow implements Device {

    public static final long BASE = 0x80000200C8000000L;
    public static final int SIZE = NandImage.DATA_SIZE;

    private final XenonSfcx sfcx;
    private final byte[] page = new byte[NandImage.DATA_PAGE_SIZE];
    private int cachedPage = -1;
    private NandImage cachedImage;

    public XenonNandWindow(XenonSfcx sfcx) {
        if (sfcx == null) {
            throw new IllegalArgumentException("XenonSfcx no puede ser null");
        }
        this.sfcx = sfcx;
    }

    @Override
    public boolean containsAddress(long address) {
        return address >= 0 && address < SIZE;
    }

    @Override
    public synchronized byte read(long offset) {
        NandImage nand = sfcx.getNandImage();
        if (nand == null || offset < 0 || offset >= SIZE) {
            return (byte) 0xFF;
        }
        int p = (int) (offset / NandImage.DATA_PAGE_SIZE);
        if (p != cachedPage || nand != cachedImage) {
            Arrays.fill(page, (byte) 0xFF);
            nand.readLogicalPage(p, page);
            cachedPage = p;
            cachedImage = nand;
        }
        return page[(int) (offset % NandImage.DATA_PAGE_SIZE)];
    }

    @Override
    public void write(long offset, byte value) {
        // La NAND nunca se modifica desde el emulador.
    }
}
