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
}
