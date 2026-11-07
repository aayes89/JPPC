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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Parser de imágenes NAND RAW de Xbox 360 Xenon.
 *
 * Formato físico: - 512 bytes de datos por página. - 16 bytes de Spare/OOB. -
 * 528 bytes por página física. - 32 páginas por bloque.
 *
 * Los offsets utilizados por los métodos readData() y extract() corresponden al
 * espacio lógico de datos, sin OOB.
 *
 * No realiza corrección ECC ni reconstrucción de bloques.
 *
 * @author Slam
 */
public final class XenonNandParser {

    public static final int DATA_SIZE = 512;
    public static final int SPARE_SIZE = 16;
    public static final int PAGE_SIZE = DATA_SIZE + SPARE_SIZE;

    public static final int PAGES_PER_BLOCK = 32;
    public static final int BLOCK_SIZE = PAGE_SIZE * PAGES_PER_BLOCK;

    public static final long CB_A_OFFSET = 0x8000L;
    public static final long CF_OFFSET = 0x70000L;

    public static final int CB_SIZE = 0x1800;

    private final byte[] raw;

    private final int physicalPages;
    private final int logicalSize;

    public XenonNandParser(byte[] raw) throws IOException {

        if (raw == null || raw.length == 0) {
            throw new IOException("Imagen NAND vacía");
        }

        if (raw.length % PAGE_SIZE != 0) {
            throw new IOException("Tamaño RAW inválido: no es múltiplo de 528 bytes");
        }

        this.raw = raw.clone();

        this.physicalPages = raw.length / PAGE_SIZE;
        this.logicalSize = physicalPages * DATA_SIZE;
    }

    public static XenonNandParser load(Path path) throws IOException {
        if (path == null) {
            throw new IOException("Ruta NAND no especificada");
        }

        return new XenonNandParser(Files.readAllBytes(path));
    }

    public int getPhysicalPages() {
        return physicalPages;
    }

    public int getLogicalSize() {
        return logicalSize;
    }

    public int getPhysicalSize() {
        return raw.length;
    }

    public int getBlockCount() {
        return (physicalPages + PAGES_PER_BLOCK - 1) / PAGES_PER_BLOCK;
    }

    /**
     * Lee una página física completa: 512 datos + 16 OOB.
     */
    public synchronized byte[] readRawPage(int pageIndex) throws IOException {
        if (pageIndex < 0 || pageIndex >= physicalPages) {
            throw new IOException("Página NAND fuera de rango: " + pageIndex);
        }

        int offset = pageIndex * PAGE_SIZE;
        return Arrays.copyOfRange(raw, offset, offset + PAGE_SIZE);
    }

    /**
     * Lee los 512 bytes de datos de una página.
     */
    public synchronized byte[] readPageData(int pageIndex) throws IOException {
        byte[] page = readRawPage(pageIndex);
        return Arrays.copyOfRange(page, 0, DATA_SIZE);
    }

    /**
     * Lee los 16 bytes Spare/OOB de una página.
     */
    public synchronized byte[] readPageSpare(int pageIndex) throws IOException {
        byte[] page = readRawPage(pageIndex);
        return Arrays.copyOfRange(page, DATA_SIZE, PAGE_SIZE);
    }

    /**
     * Traduce un offset lógico de datos a un offset físico RAW.
     */
    public static long logicalToPhysical(long logicalOffset) {

        if (logicalOffset < 0) {
            throw new IllegalArgumentException("Offset lógico negativo");
        }

        long page = logicalOffset / DATA_SIZE;
        long inPage = logicalOffset % DATA_SIZE;

        return page * PAGE_SIZE + inPage;
    }

    /**
     * Lee un rango continuo del espacio lógico NAND. Los bytes OOB son
     * omitidos.
     */
    public synchronized byte[] readData(long logicalOffset, int length) throws IOException {

        if (logicalOffset < 0 || length < 0) {
            throw new IOException("Rango NAND inválido");
        }

        if (logicalOffset > logicalSize || length > logicalSize - logicalOffset) {
            throw new IOException("Lectura fuera del espacio lógico NAND");
        }

        byte[] result = new byte[length];

        int remaining = length;
        int destination = 0;
        long position = logicalOffset;

        while (remaining > 0) {

            int pageIndex = (int) (position / DATA_SIZE);
            int pageOffset = (int) (position % DATA_SIZE);

            int available = DATA_SIZE - pageOffset;
            int count = Math.min(remaining, available);

            int physicalOffset = pageIndex * PAGE_SIZE + pageOffset;
            System.arraycopy(raw, physicalOffset, result, destination, count);

            position += count;
            destination += count;
            remaining -= count;
        }

        return result;
    }

    /**
     * Extrae un componente desde el espacio lógico NAND.
     */
    public synchronized byte[] extract(long logicalOffset, int length) throws IOException {
        return readData(logicalOffset, length);
    }

    /**
     * Extrae CB_A usando el tamaño conocido de la imagen analizada.
     */
    public synchronized byte[] extractCBA() throws IOException {
        return extract(CB_A_OFFSET, CB_SIZE);
    }

    /**
     * Devuelve los datos de un bloque lógico completo.
     *
     * El resultado contiene 32 * 512 bytes.
     */
    public synchronized byte[] readBlockData(int blockIndex) throws IOException {
        if (blockIndex < 0 || blockIndex >= getBlockCount()) {
            throw new IOException("Bloque NAND fuera de rango: " + blockIndex);
        }

        long offset = (long) blockIndex * PAGES_PER_BLOCK * DATA_SIZE;
        int length = Math.min(PAGES_PER_BLOCK * DATA_SIZE, logicalSize - (int) offset);
        return readData(offset, length);
    }

    /**
     * Devuelve los OOB de todas las páginas de un bloque.
     */
    public synchronized byte[] readBlockSpare(int blockIndex) throws IOException {
        if (blockIndex < 0 || blockIndex >= getBlockCount()) {
            throw new IOException("Bloque NAND fuera de rango: " + blockIndex);
        }

        int firstPage = blockIndex * PAGES_PER_BLOCK;

        int pageCount = Math.min(PAGES_PER_BLOCK, physicalPages - firstPage);
        byte[] result = new byte[pageCount * SPARE_SIZE];

        for (int i = 0; i < pageCount; i++) {
            byte[] spare = readPageSpare(firstPage + i);
            System.arraycopy(spare, 0, result, i * SPARE_SIZE, SPARE_SIZE);
        }

        return result;
    }

    /**
     * Comprueba si una página física está completamente borrada.
     */
    public synchronized boolean isErasedPage(int pageIndex) throws IOException {
        byte[] page = readRawPage(pageIndex);

        for (byte value : page) {
            if ((value & 0xFF) != 0xFF) {
                return false;
            }
        }

        return true;
    }

    /**
     * Cuenta páginas completamente borradas.
     */
    public synchronized int countErasedPages() throws IOException {
        int count = 0;
        for (int i = 0; i < physicalPages; i++) {
            if (isErasedPage(i)) {
                count++;
            }
        }

        return count;
    }

    /**
     * Acceso a la imagen RAW original.
     */
    public synchronized byte[] getRawImage() {
        return raw.clone();
    }
}
