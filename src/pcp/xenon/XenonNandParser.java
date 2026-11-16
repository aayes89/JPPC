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

    public static final long CB_A_OFFSET = 0x8000L; // Valor habitual; la NAND header es autoritativa.
    public static final long CF_OFFSET = 0x70000L;  // Valor habitual; la NAND header es autoritativa.

    private static final int NAND_HEADER_SIZE = 0x200;
    private static final int CB_HEADER_SIZE = 0x20;
    private static final int NAND_CB_OFFSET_FIELD = 0x08;
    private static final int NAND_CF_OFFSET_FIELD = 0x0C;
    private static final int NAND_KEYVAULT_OFFSET_FIELD = 0x6C;
    private static final int KEYVAULT_SIZE = 0x4000;
    private static final int CB_SIZE_FIELD = 0x0C;
    private static final int MAX_BOOTLOADER_SIZE = 0x100000;

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

    /** Offset lógico de CB_A declarado en la cabecera NAND. */
    public synchronized long getCBAOffset() throws IOException {
        byte[] header = readData(0, NAND_HEADER_SIZE);
        long offset = readU32BE(header, NAND_CB_OFFSET_FIELD);
        validateBootloaderRange(offset, CB_HEADER_SIZE, "CB_A");
        return offset;
    }

    /** Offset lógico de CF declarado en la cabecera NAND. */
    public synchronized long getCFOffset() throws IOException {
        byte[] header = readData(0, NAND_HEADER_SIZE);
        long offset = readU32BE(header, NAND_CF_OFFSET_FIELD);
        if (offset <= 0 || offset >= logicalSize) {
            throw new IOException(String.format("Offset CF fuera de NAND: 0x%X", offset));
        }
        return offset;
    }

    /** Versión declarada por la cabecera de CB_A. */
    public synchronized int getCBAVersion() throws IOException {
        byte[] header = readData(getCBAOffset(), CB_HEADER_SIZE);
        validateCBMagic(header, "CB_A");
        return readU16BE(header, 2);
    }

    /** Tamaño declarado por la cabecera de CB_A. */
    public synchronized int getCBASize() throws IOException {
        long offset = getCBAOffset();
        byte[] header = readData(offset, CB_HEADER_SIZE);
        validateCBMagic(header, "CB_A");
        long size = readU32BE(header, CB_SIZE_FIELD);
        if (size < CB_HEADER_SIZE || size > MAX_BOOTLOADER_SIZE || size > Integer.MAX_VALUE) {
            throw new IOException(String.format("Tamaño CB_A inválido: 0x%X", size));
        }
        validateBootloaderRange(offset, (int) size, "CB_A");
        long cfOffset = getCFOffset();
        if (cfOffset > offset && offset + size > cfOffset) {
            throw new IOException("CB_A invade el área CF declarada por la NAND");
        }
        return (int) size;
    }

    /** Extrae CB_A utilizando offset y tamaño declarados en la NAND. */
    public synchronized byte[] extractCBA() throws IOException {
        return extract(getCBAOffset(), getCBASize());
    }

    /**
     * CB_B se encuentra inmediatamente después de CB_A, alineado a 16 bytes.
     * La posición r31 que CB_A entrega al siguiente loader contiene este offset.
     */
    public synchronized long getCBBOffset() throws IOException {
        long offset = getCBAOffset() + align16(getCBASize());
        validateBootloaderRange(offset, CB_HEADER_SIZE, "CB_B");
        byte[] header = readData(offset, CB_HEADER_SIZE);
        validateMagic(header, 'C', 'B', "CB_B");
        return offset;
    }

    public synchronized int getCBBVersion() throws IOException {
        byte[] header = readData(getCBBOffset(), CB_HEADER_SIZE);
        return readU16BE(header, 2);
    }

    public synchronized int getCBBSize() throws IOException {
        long offset = getCBBOffset();
        byte[] header = readData(offset, CB_HEADER_SIZE);
        long size = readU32BE(header, CB_SIZE_FIELD);
        if (size < 0x140 || size > MAX_BOOTLOADER_SIZE || size > Integer.MAX_VALUE) {
            throw new IOException(String.format("Tamaño CB_B inválido: 0x%X", size));
        }
        int aligned = align16((int) size);
        validateBootloaderRange(offset, aligned, "CB_B");
        long cfOffset = getCFOffset();
        if (cfOffset > offset && offset + aligned > cfOffset) {
            throw new IOException("CB_B invade el área CF declarada por la NAND");
        }
        return (int) size;
    }

    public synchronized byte[] extractCBB() throws IOException {
        return extract(getCBBOffset(), getCBBSize());
    }

    /** Offset inmediatamente posterior a CB_B; debe comenzar CD. */
    public synchronized long getCDOffset() throws IOException {
        long offset = getCBBOffset() + align16(getCBBSize());
        validateBootloaderRange(offset, 0x20, "CD");
        byte[] header = readData(offset, 0x20);
        validateMagic(header, 'C', 'D', "CD");
        return offset;
    }

    public synchronized int getCDVersion() throws IOException {
        return readU16BE(readData(getCDOffset(), 0x20), 2);
    }

    public synchronized int getCDSize() throws IOException {
        long offset = getCDOffset();
        byte[] header = readData(offset, 0x20);
        long size = readU32BE(header, CB_SIZE_FIELD);
        if (size < 0x260 || size > MAX_BOOTLOADER_SIZE || size > Integer.MAX_VALUE) {
            throw new IOException(String.format("Tamaño CD inválido: 0x%X", size));
        }
        validateBootloaderRange(offset, align16((int) size), "CD");
        return (int) size;
    }

    public synchronized byte[] extractCD() throws IOException {
        return extract(getCDOffset(), getCDSize());
    }

    /** CE comienza inmediatamente después de CD, alineado a 16 bytes. */
    public synchronized long getCEOffset() throws IOException {
        long offset = getCDOffset() + align16(getCDSize());
        validateBootloaderRange(offset, 0x20, "CE");
        validateMagic(readData(offset, 0x20), 'C', 'E', "CE");
        return offset;
    }

    public synchronized int getCEVersion() throws IOException {
        return readU16BE(readData(getCEOffset(), 0x20), 2);
    }

    public synchronized int getCESize() throws IOException {
        long offset = getCEOffset();
        byte[] header = readData(offset, 0x20);
        long size = readU32BE(header, CB_SIZE_FIELD);
        if (size < 0x30 || size > MAX_BOOTLOADER_SIZE * 4L || size > Integer.MAX_VALUE) {
            throw new IOException(String.format("Tamaño CE inválido: 0x%X", size));
        }
        validateBootloaderRange(offset, align16((int) size), "CE");
        return (int) size;
    }

    /** Tamaño almacenado de CE, incluyendo el padding de alineación a 16 bytes. */
    public synchronized int getCEStoredSize() throws IOException {
        return align16(getCESize());
    }

    public synchronized byte[] extractCE() throws IOException {
        return extract(getCEOffset(), getCEStoredSize());
    }

    /** El KeyVault retail ocupa 0x4000 bytes y su offset está en NAND+0x6C. */
    public synchronized long getKeyVaultOffset() throws IOException {
        byte[] header = readData(0, NAND_HEADER_SIZE);
        long offset = readU32BE(header, NAND_KEYVAULT_OFFSET_FIELD);
        validateBootloaderRange(offset, KEYVAULT_SIZE, "KeyVault");
        if ((offset & 0x0F) != 0) {
            throw new IOException(String.format("Offset KeyVault no alineado: 0x%X", offset));
        }
        return offset;
    }

    public synchronized byte[] extractKeyVault() throws IOException {
        return extract(getKeyVaultOffset(), KEYVAULT_SIZE);
    }

    private static int align16(int value) throws IOException {
        if (value < 0 || value > Integer.MAX_VALUE - 15) {
            throw new IOException("Tamaño de bootloader fuera de rango");
        }
        return (value + 15) & ~15;
    }

    private void validateBootloaderRange(long offset, int length, String name) throws IOException {
        if (offset < 0 || offset > logicalSize || length < 0 || length > logicalSize - offset) {
            throw new IOException(String.format("Rango %s fuera del espacio lógico NAND: offset=0x%X length=0x%X", name, offset, length));
        }
    }

    private static void validateCBMagic(byte[] header, String name) throws IOException {
        validateMagic(header, 'C', 'B', name);
    }

    private static void validateMagic(byte[] header, char first, char second, String name) throws IOException {
        if (header == null || header.length < CB_HEADER_SIZE
                || (header[0] & 0xFF) != first || (header[1] & 0xFF) != second) {
            throw new IOException("Magic inválido para " + name);
        }
    }

    private static int readU16BE(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private static long readU32BE(byte[] data, int offset) {
        return ((long) (data[offset] & 0xFF) << 24)
                | ((long) (data[offset + 1] & 0xFF) << 16)
                | ((long) (data[offset + 2] & 0xFF) << 8)
                | (long) (data[offset + 3] & 0xFF);
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
