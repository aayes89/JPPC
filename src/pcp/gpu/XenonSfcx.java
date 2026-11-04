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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Controlador SFCX small-block para Xenon en 0xEA00C000.
 * Las lecturas pueden proceder de una imagen NAND real (solo lectura).
 * ECC, remapeo de bloques defectuosos y escrituras físicas aún no están implementados.
 * 
 * @autor: Slam
 */
public final class XenonSfcx implements Device {
    public static final long BASE = 0xEA00C000L;
    public static final int SIZE = 0x100;

    private static final int CONFIG = 0x00, STATUS = 0x04, COMMAND = 0x08;
    private static final int ADDRESS = 0x0C, DATA = 0x10, PHISON = 0xFC;
    private static final int PAGE_BUF_TO_REG = 0, REG_TO_PAGE_BUF = 1;
    private static final int LOG_PAGE_TO_BUF = 2, PHY_PAGE_TO_BUF = 3;
    private static final int WRITE_PAGE_TO_PHY = 4, BLOCK_ERASE = 5;
    private static final int STATUS_READY = 0x200;

    private final byte[] pageBuffer = new byte[NandImage.RAW_PAGE_SIZE];
    private int config = 0x10;
    private int status = STATUS_READY;
    private int bufferAddress;
    private int flashAddress;
    private int dataRegister;
    private boolean pageLoaded;
    private NandImage nand;

    public XenonSfcx() { Arrays.fill(pageBuffer, (byte) 0xFF); }

    public synchronized void loadNand(Path path) throws IOException {
        nand = NandImage.load(path);
        Arrays.fill(pageBuffer, (byte) 0xFF);
    }

    public synchronized boolean hasNandImage() { return nand != null; }
    public synchronized NandImage getNandImage() { return nand; }

    @Override public boolean containsAddress(long address) { return address >= 0 && address < SIZE; }

    @Override
    public synchronized int readWord(long offset) {
        int value;
        switch ((int) (offset & ~3L)) {
            case CONFIG: value = config; break;
            case STATUS: value = status; break;
            case ADDRESS: value = bufferAddress; break;
            case DATA: value = dataRegister; break;
            case PHISON: value = 0; break;
            default: value = 0; break;
        }
        return Integer.reverseBytes(value);
    }

    @Override
    public synchronized void writeWord(long offset, int busValue) {
        int value = Integer.reverseBytes(busValue);
        switch ((int) (offset & ~3L)) {
            case CONFIG: config = value; break;
            case STATUS:
                status = STATUS_READY;
                pageLoaded = false;
                break;
            case ADDRESS:
                // La misma dirección selecciona NAND antes del comando y el
                // cursor del buffer después de completar PAGE_TO_BUF.
                if (pageLoaded) bufferAddress = value & 0x3FF;
                else flashAddress = value;
                break;
            case DATA: dataRegister = value; break;
            case COMMAND: executeCommand(value); break;
            default: break;
        }
    }

    private void executeCommand(int command) {
        switch (command) {
            case PAGE_BUF_TO_REG:
                dataRegister = readBufferWord();
                break;
            case REG_TO_PAGE_BUF:
                writeBufferWord(dataRegister);
                break;
            case LOG_PAGE_TO_BUF:
                loadPage(false);
                break;
            case PHY_PAGE_TO_BUF:
                loadPage(true);
                break;
            case WRITE_PAGE_TO_PHY:
            case BLOCK_ERASE:
                // Protección deliberada: la imagen NAND nunca se modifica.
                status = STATUS_READY;
                break;
            default:
                // Unlock y comandos no implementados no alteran la imagen.
                status = STATUS_READY;
                break;
        }
    }

    private void loadPage(boolean physical) {
        Arrays.fill(pageBuffer, (byte) 0xFF);
        if (nand == null) {
            status = STATUS_READY;
            return;
        }
        int page = flashAddress / NandImage.DATA_PAGE_SIZE;
        try {
            if (physical) nand.readPhysicalPage(page, pageBuffer);
            else nand.readLogicalPage(page, pageBuffer);
            status = STATUS_READY;
        } catch (IndexOutOfBoundsException ex) {
            status = STATUS_READY | 0x10; // error de dirección, sin bloquear XeLL
        }
        bufferAddress = 0;
        pageLoaded = true;
    }

    private int readBufferWord() {
        int a = bufferAddress & ~3;
        if (a < 0 || a + 4 > pageBuffer.length) a = 0;
        int v = ((pageBuffer[a] & 0xFF) << 24) | ((pageBuffer[a + 1] & 0xFF) << 16)
                | ((pageBuffer[a + 2] & 0xFF) << 8) | (pageBuffer[a + 3] & 0xFF);
        bufferAddress = a + 4;
        return v;
    }

    private void writeBufferWord(int value) {
        int a = bufferAddress & ~3;
        if (a < 0 || a + 4 > pageBuffer.length) return;
        pageBuffer[a] = (byte) (value >>> 24);
        pageBuffer[a + 1] = (byte) (value >>> 16);
        pageBuffer[a + 2] = (byte) (value >>> 8);
        pageBuffer[a + 3] = (byte) value;
        bufferAddress = a + 4;
    }

    @Override public byte read(long offset) {
        int word = readWord(offset & ~3L);
        return (byte) (word >>> (24 - 8 * (int) (offset & 3)));
    }

    @Override public void write(long offset, byte value) {
        if ((offset & 3) == 3) writeWord(offset & ~3L, value & 0xFF);
    }
}
