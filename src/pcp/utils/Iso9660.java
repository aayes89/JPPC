package pcp.utils;

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
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Lector de imágenes ISO9660 (solo lectura): ISO9660 plano (8.3), Rock Ridge
 * (nombres largos, "NM") y Joliet (UCS-2), más detección de El Torito (imagen
 * de arranque y plataforma). Pensado para extraer kernels/bootloaders de ISOs
 * de sistemas PPC (Linux PPC, Mac, etc.).
 *
 * @author Slam
 */
public class Iso9660 implements AutoCloseable {

    public static final int SECTOR = 2048;

    public static class Entry {

        public final String name;
        public final long lba;
        public final long size;
        public final boolean dir;

        Entry(String name, long lba, long size, boolean dir) {
            this.name = name;
            this.lba = lba;
            this.size = size;
            this.dir = dir;
        }

        @Override
        public String toString() {
            return (dir ? "<DIR> " : "      ") + String.format("%10d  ", size) + name;
        }
    }

    /**
     * Imagen de arranque El Torito.
     */
    public static class BootImage {

        public int platformId;      // 0 = x86, 1 = PowerPC, 2 = Mac, 0xEF = EFI
        public boolean bootable;
        public int loadSegment;
        public int sectors512;
        public long lba;
    }

    private final RandomAccessFile f;
    private long rootLba, rootSize;
    private boolean joliet = false;
    private String volumeId = "";

    public Iso9660(String path) throws IOException {
        f = new RandomAccessFile(path, "r");
        byte[] s = sector(16);
        if (!(s[0] == 1 && s[1] == 'C' && s[2] == 'D' && s[3] == '0' && s[4] == '0' && s[5] == '1')) {
            throw new IOException("No es una imagen ISO9660 (falta CD001 en el sector 16)");
        }
        volumeId = new String(s, 40, 32, StandardCharsets.US_ASCII).trim();
        rootLba = le32(s, 156 + 2);
        rootSize = le32(s, 156 + 10);
        for (int lba = 17; lba < 40; lba++) { // buscar el descriptor suplementario Joliet
            byte[] d = sector(lba);
            if (d[0] == (byte) 255) {
                break;
            }
            if (d[0] == 2 && d[1] == 'C' && d[88] == '%' && d[89] == '/' && (d[90] == '@' || d[90] == 'C' || d[90] == 'E')) {
                rootLba = le32(d, 156 + 2);
                rootSize = le32(d, 156 + 10);
                joliet = true;
                break;
            }
        }
    }

    public String getVolumeId() {
        return volumeId;
    }

    public boolean isJoliet() {
        return joliet;
    }

    public long totalSectors() throws IOException {
        return f.length() / SECTOR;
    }

    private static long le32(byte[] b, int o) {
        return (b[o] & 0xFFL) | ((b[o + 1] & 0xFFL) << 8) | ((b[o + 2] & 0xFFL) << 16) | ((b[o + 3] & 0xFFL) << 24);
    }

    public byte[] sector(long lba) throws IOException {
        byte[] b = new byte[SECTOR];
        f.seek(lba * SECTOR);
        f.readFully(b);
        return b;
    }

    public byte[] readExtent(long lba, long size) throws IOException {
        byte[] b = new byte[(int) size];
        f.seek(lba * SECTOR);
        f.readFully(b);
        return b;
    }

    private List<Entry> readDir(long lba, long size) throws IOException {
        List<Entry> out = new ArrayList<>();
        byte[] data = readExtent(lba, size);
        int p = 0;
        while (p < data.length) {
            int len = data[p] & 0xFF;
            if (len == 0) {                       // relleno hasta el siguiente sector
                p = (p / SECTOR + 1) * SECTOR;
                continue;
            }
            int nameLen = data[p + 32] & 0xFF;
            boolean isDir = (data[p + 25] & 2) != 0;
            long elba = le32(data, p + 2), esize = le32(data, p + 10);
            String name;
            if (nameLen == 1 && (data[p + 33] == 0 || data[p + 33] == 1)) {
                p += len;
                continue;                         // "." y ".."
            }
            if (joliet) {
                name = new String(data, p + 33, nameLen, StandardCharsets.UTF_16BE);
            } else {
                name = new String(data, p + 33, nameLen, StandardCharsets.US_ASCII);
                String rr = rockRidgeName(data, p + 33 + nameLen + ((nameLen % 2 == 0) ? 1 : 0), p + len);
                if (rr != null) {
                    name = rr;
                }
            }
            int semi = name.indexOf(';');
            if (semi >= 0) {
                name = name.substring(0, semi);
            }
            if (name.endsWith(".") && name.length() > 1) {
                name = name.substring(0, name.length() - 1);
            }
            out.add(new Entry(name, elba, esize, isDir));
            p += len;
        }
        return out;
    }

    /**
     * Nombre largo Rock Ridge (entrada "NM") del área de uso del sistema, o
     * null.
     */
    private static String rockRidgeName(byte[] d, int p, int end) {
        StringBuilder sb = null;
        while (p + 4 <= end) {
            int len = d[p + 2] & 0xFF;
            if (len < 4 || p + len > end) {
                break;
            }
            if (d[p] == 'N' && d[p + 1] == 'M') {
                if (sb == null) {
                    sb = new StringBuilder();
                }
                sb.append(new String(d, p + 5, len - 5, StandardCharsets.UTF_8));
            }
            p += len;
        }
        return sb == null ? null : sb.toString();
    }

    /**
     * Lista un directorio ("/" = raíz).
     */
    public List<Entry> list(String path) throws IOException {
        Entry e = find(path);
        if (e == null || !e.dir) {
            throw new IOException("No es un directorio: " + path);
        }
        return readDir(e.lba, e.size);
    }

    /**
     * Busca una ruta (sin distinguir mayúsculas).
     */
    public Entry find(String path) throws IOException {
        Entry cur = new Entry("/", rootLba, rootSize, true);
        for (String part : path.split("/")) {
            if (part.isEmpty()) {
                continue;
            }
            Entry next = null;
            for (Entry c : readDir(cur.lba, cur.size)) {
                if (c.name.equalsIgnoreCase(part)) {
                    next = c;
                    break;
                }
            }
            if (next == null) {
                return null;
            }
            cur = next;
        }
        return cur;
    }

    public byte[] readFile(String path) throws IOException {
        Entry e = find(path);
        if (e == null || e.dir) {
            throw new IOException("Archivo no encontrado en la ISO: " + path);
        }
        return readExtent(e.lba, e.size);
    }

    /**
     * Primera imagen de arranque El Torito, o null si el disco no es
     * arrancable.
     */
    public BootImage elTorito() throws IOException {
        for (int lba = 17; lba < 24; lba++) {
            byte[] d = sector(lba);
            String id = new String(d, 7, 23, StandardCharsets.US_ASCII);
            if (d[0] == 0 && id.startsWith("EL TORITO SPECIFICATION")) {
                byte[] cat = sector(le32(d, 71));
                BootImage b = new BootImage();
                b.platformId = cat[1] & 0xFF;               // entrada de validación
                int o = 32;                                 // entrada por defecto
                b.bootable = (cat[o] & 0xFF) == 0x88;
                b.loadSegment = (cat[o + 2] & 0xFF) | ((cat[o + 3] & 0xFF) << 8);
                b.sectors512 = (cat[o + 6] & 0xFF) | ((cat[o + 7] & 0xFF) << 8);
                b.lba = le32(cat, o + 8);
                return b;
            }
        }
        return null;
    }

    @Override
    public void close() throws IOException {
        f.close();
    }
}
