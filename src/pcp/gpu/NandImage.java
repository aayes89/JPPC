package pcp.gpu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Imagen NAND small-block Xenon. La imagen se carga en memoria y se trata
 * estrictamente como solo lectura. Admite RAW 512+16 y datos sin OOB.
 */
public final class NandImage {
    public static final int DATA_PAGE_SIZE = 0x200;
    public static final int SPARE_SIZE = 0x10;
    public static final int RAW_PAGE_SIZE = 0x210;
    public static final int BLOCK_SIZE = 0x4000;
    public static final int PAGES_PER_BLOCK = BLOCK_SIZE / DATA_PAGE_SIZE;
    public static final int DATA_SIZE = 0x1000000;
    public static final int RAW_SIZE = 0x1080000;

    public enum Format { RAW_WITH_SPARE, DATA_ONLY }

    /** Resultado de comprobar el EDC de una página RAW. */
    public enum EccStatus { VALID, INVALID, ERASED, UNAVAILABLE }

    private final byte[] bytes;
    private final Format format;
    private final Path source;

    private NandImage(Path source, byte[] bytes, Format format) {
        this.source = source;
        this.bytes = bytes;
        this.format = format;
    }

    public static NandImage load(Path path) throws IOException {
        if (path == null) throw new IllegalArgumentException("Ruta NAND nula");
        byte[] data = Files.readAllBytes(path);
        Format f;
        if (data.length == RAW_SIZE) f = Format.RAW_WITH_SPARE;
        else if (data.length == DATA_SIZE) f = Format.DATA_ONLY;
        else throw new IOException(String.format(
                "Tamaño NAND no soportado: %d (esperado RAW=%d o DATA=%d)",
                data.length, RAW_SIZE, DATA_SIZE));
        return new NandImage(path.toAbsolutePath(), data, f);
    }

    public int getSize() { return bytes.length; }
    public Format getFormat() { return format; }
    public Path getSource() { return source; }
    public int getPageCount() { return DATA_SIZE / DATA_PAGE_SIZE; }
    public int getBlockCount() { return DATA_SIZE / BLOCK_SIZE; }

    /** Lee una página física completa (datos+OOB). */
    public void readPhysicalPage(int pageIndex, byte[] destination) {
        if (pageIndex < 0 || pageIndex >= getPageCount())
            throw new IndexOutOfBoundsException("Página NAND fuera de rango: " + pageIndex);
        if (destination == null || destination.length < RAW_PAGE_SIZE)
            throw new IllegalArgumentException("Buffer destino debe tener al menos 528 bytes");
        if (format == Format.RAW_WITH_SPARE) {
            System.arraycopy(bytes, pageIndex * RAW_PAGE_SIZE, destination, 0, RAW_PAGE_SIZE);
        } else {
            System.arraycopy(bytes, pageIndex * DATA_PAGE_SIZE, destination, 0, DATA_PAGE_SIZE);
            Arrays.fill(destination, DATA_PAGE_SIZE, RAW_PAGE_SIZE, (byte) 0xFF);
        }
    }

    /** Lee solo los 512 bytes de datos de una página. */
    public void readLogicalPage(int pageIndex, byte[] destination) {
        if (pageIndex < 0 || pageIndex >= getPageCount())
            throw new IndexOutOfBoundsException("Página NAND fuera de rango: " + pageIndex);
        if (destination == null || destination.length < DATA_PAGE_SIZE)
            throw new IllegalArgumentException("Buffer destino debe tener al menos 512 bytes");
        int stride = format == Format.RAW_WITH_SPARE ? RAW_PAGE_SIZE : DATA_PAGE_SIZE;
        System.arraycopy(bytes, pageIndex * stride, destination, 0, DATA_PAGE_SIZE);
    }

    /**
     * Calcula el EDC Xbox 360 de 26 bits. El algoritmo consume los 512 bytes
     * de datos, los 12 primeros bytes de metadata OOB y los seis bits bajos
     * de OOB[12]. Los 26 bits calculados se almacenan en OOB[12..15].
     *
     * El buffer debe contener exactamente una página física de 528 bytes.
     */
    public static int calculateEcc(byte[] rawPage) {
        requireRawPage(rawPage);
        int value = 0;
        int word = 0;
        for (int bit = 0; bit < 0x1066; bit++) {
            if ((bit & 31) == 0) {
                int offset;
                if (bit < 0x1000) {
                    offset = (bit >>> 5) << 2;
                } else {
                    offset = DATA_PAGE_SIZE + (((bit - 0x1000) >>> 5) << 2);
                }
                // El SFCX de referencia ejecuta bswap32() en Xenon (PPC BE),
                // equivalente a reconstruir aquí el DWORD en orden little-endian.
                word = ~readLittleEndian32(rawPage, offset);
            }
            value ^= word & 1;
            word >>>= 1;
            if ((value & 1) != 0) value ^= 0x06954559;
            value >>>= 1;
        }
        value = ~value;
        return (((value << 6) & 0xC0) << 24)
                | (((value >>> 2) & 0xFF) << 16)
                | (((value >>> 10) & 0xFF) << 8)
                | ((value >>> 18) & 0xFF);
    }

    /** Compara el EDC calculado con los 26 bits almacenados en el OOB. */
    public static boolean isEccValid(byte[] rawPage) {
        requireRawPage(rawPage);
        int calculated = calculateEcc(rawPage);
        int stored = ((rawPage[DATA_PAGE_SIZE + 0x0C] & 0xC0) << 24)
                | ((rawPage[DATA_PAGE_SIZE + 0x0D] & 0xFF) << 16)
                | ((rawPage[DATA_PAGE_SIZE + 0x0E] & 0xFF) << 8)
                | (rawPage[DATA_PAGE_SIZE + 0x0F] & 0xFF);
        return calculated == stored;
    }

    /** Estado EDC; una imagen sin OOB no permite validar ECC. */
    public EccStatus getEccStatus(int pageIndex) {
        if (format != Format.RAW_WITH_SPARE) return EccStatus.UNAVAILABLE;
        byte[] raw = new byte[RAW_PAGE_SIZE];
        readPhysicalPage(pageIndex, raw);
        if (isErased(raw)) return EccStatus.ERASED;
        return isEccValid(raw) ? EccStatus.VALID : EccStatus.INVALID;
    }

    /** Small-block metadata: BlockID = ((OOB[1] & 0x0F) << 8) | OOB[0]. */
    public static int getSmallBlockId(byte[] rawPage) {
        requireRawPage(rawPage);
        int oob = DATA_PAGE_SIZE;
        return ((rawPage[oob + 1] & 0x0F) << 8) | (rawPage[oob] & 0xFF);
    }

    /** Devuelve el byte OOB[5] sin asignarle semántica de bad-block todavía. */
    public static int getSmallBlockMarkerField(byte[] rawPage) {
        requireRawPage(rawPage);
        return rawPage[DATA_PAGE_SIZE + 5] & 0xFF;
    }

    private static int readLittleEndian32(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }

    private static boolean isErased(byte[] page) {
        for (byte b : page) if ((b & 0xFF) != 0xFF) return false;
        return true;
    }

    private static void requireRawPage(byte[] rawPage) {
        if (rawPage == null || rawPage.length != RAW_PAGE_SIZE)
            throw new IllegalArgumentException("Se requiere una página RAW exacta de 528 bytes");
    }
}
