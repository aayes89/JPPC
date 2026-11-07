package pcp.xenon;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sistema de archivos flash Xenon sobre datos NAND lógicos.
 *
 * La NAND puede contener varias generaciones de la raíz. No se selecciona
 * simplemente la que tenga más entradas: se puntúan integridad de cadenas,
 * tamaños y cabeceras XEX2 reales al inicio de los archivos ejecutables.
 */
public final class XenonFlashFs {

    public static final int BLOCK_SIZE = 0x4000;
    private static final int PAGE = 0x200;
    private static final int FAT_PAGES = 4;
    private static final int END_OF_CHAIN = 0x1FF0;

    public static final class Entry {
        public final String name;
        public final int block;
        public final long size;
        public final int timestamp;

        Entry(String name, int block, long size, int timestamp) {
            this.name = name;
            this.block = block;
            this.size = size;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return String.format("%-24s bloque=0x%03x tamaño=0x%08x", name, block, size);
        }
    }

    public static final class RootInfo {
        public final int offset;
        public final int entryCount;
        public final int validChains;
        public final int exactChains;
        public final int xex2Files;
        public final int score;

        RootInfo(int offset, int entryCount, int validChains,
                 int exactChains, int xex2Files, int score) {
            this.offset = offset;
            this.entryCount = entryCount;
            this.validChains = validChains;
            this.exactChains = exactChains;
            this.xex2Files = xex2Files;
            this.score = score;
        }

        @Override
        public String toString() {
            return String.format("root=0x%06X entries=%d chains=%d exact=%d xex2=%d score=%d",
                    offset, entryCount, validChains, exactChains, xex2Files, score);
        }
    }

    private final byte[] logical;
    private final int[] fat;
    private final List<Entry> entries;
    private final int rootOffset;
    private final RootInfo rootInfo;

    private XenonFlashFs(byte[] logical, int rootOffset, int[] fat,
                         List<Entry> entries, RootInfo rootInfo) {
        this.logical = logical;
        this.rootOffset = rootOffset;
        this.fat = fat;
        this.entries = Collections.unmodifiableList(entries);
        this.rootInfo = rootInfo;
    }

    /** Selecciona la generación con mayor evidencia de ejecutables XEX2 válidos. */
    public static XenonFlashFs open(XenonNandParser nand) throws IOException {
        byte[] logical = nand.readData(0, nand.getLogicalSize());
        XenonFlashFs best = null;

        for (int off = 0; off + BLOCK_SIZE <= logical.length; off += BLOCK_SIZE) {
            int[] fat = readFat(logical, off);
            if (fat == null) {
                continue;
            }

            List<Entry> ents = readDirectory(logical, off, fat.length);
            if (ents.isEmpty()) {
                continue;
            }

            RootInfo info = scoreRoot(logical, off, fat, ents);
            if (best == null || info.score > best.rootInfo.score) {
                best = new XenonFlashFs(logical, off, fat, ents, info);
            }
        }

        if (best == null) {
            throw new IOException("No se encontró una raíz válida del sistema de archivos flash");
        }
        return best;
    }

    /** Permite fijar una raíz concreta para comparar generaciones de NAND. */
    public static XenonFlashFs openAt(XenonNandParser nand, int rootOffset) throws IOException {
        byte[] logical = nand.readData(0, nand.getLogicalSize());
        if (rootOffset < 0 || (rootOffset % BLOCK_SIZE) != 0
                || rootOffset + BLOCK_SIZE > logical.length) {
            throw new IOException("Offset de raíz inválido: 0x" + Integer.toHexString(rootOffset));
        }
        int[] fat = readFat(logical, rootOffset);
        if (fat == null) {
            throw new IOException("FAT inválida en raíz 0x" + Integer.toHexString(rootOffset));
        }
        List<Entry> ents = readDirectory(logical, rootOffset, fat.length);
        if (ents.isEmpty()) {
            throw new IOException("La raíz no contiene entradas válidas");
        }
        RootInfo info = scoreRoot(logical, rootOffset, fat, ents);
        return new XenonFlashFs(logical, rootOffset, fat, ents, info);
    }

    private static RootInfo scoreRoot(byte[] logical, int root, int[] fat,
                                       List<Entry> entries) {
        int validChains = 0;
        int exactChains = 0;
        int xex2Files = 0;
        int score = entries.size();

        for (Entry e : entries) {
            List<Integer> chain = buildChain(fat, e);
            long need = (e.size + BLOCK_SIZE - 1) / BLOCK_SIZE;
            if (chain.size() >= need) {
                validChains++;
                score += 8;
                if (chain.size() == need) {
                    exactChains++;
                    score += 5;
                } else {
                    score -= 2;
                }
            } else {
                score -= 12;
                continue;
            }

            String lower = e.name.toLowerCase(java.util.Locale.ROOT);
            if (lower.endsWith(".xex") || lower.endsWith(".xexp1")
                    || lower.endsWith(".xexp2")) {
                long firstOffset = (long) e.block * BLOCK_SIZE;
                if (firstOffset + 0x24 <= logical.length
                        && isValidXex2(logical, (int) firstOffset, (int) Math.min(e.size, Integer.MAX_VALUE))) {
                    xex2Files++;
                    score += 100;
                } else if (lower.endsWith(".xex")) {
                    score -= 3;
                }
            }
        }

        return new RootInfo(root, entries.size(), validChains, exactChains, xex2Files, score);
    }

    private static boolean isValidXex2(byte[] data, int off, int fileLength) {
        if (off < 0 || fileLength < 0 || off + 0x18 > data.length
                || fileLength < 0x18 || data[off] != 'X' || data[off + 1] != 'E'
                || data[off + 2] != 'X' || data[off + 3] != '2') {
            return false;
        }

        int peOffset = be32(data, off + 8);
        int securityOffset = be32(data, off + 16);
        int optionCount = be32(data, off + 20);
        if (optionCount < 0 || optionCount > 0x400
                || 0x18L + (long) optionCount * 8 > fileLength
                || peOffset < 0 || peOffset >= fileLength
                || securityOffset < 0 || securityOffset + 0x114L > fileLength) {
            return false;
        }

        int fileFormat = -1;
        for (int i = 0; i < optionCount; i++) {
            int key = be32(data, off + 0x18 + i * 8);
            int value = be32(data, off + 0x1C + i * 8);
            if (key == XexFile.OPT_FILE_FORMAT) {
                fileFormat = value;
                break;
            }
        }
        if (fileFormat < 0 || fileFormat + 8L > fileLength) {
            return false;
        }

        int encryption = be16(data, off + fileFormat + 4);
        int compression = be16(data, off + fileFormat + 6);
        long imageSize = be32(data, off + securityOffset + 4) & 0xFFFFFFFFL;
        return encryption <= 1 && compression <= 3 && imageSize > 0
                && imageSize <= 0x20000000L;
    }

    private static int[] readFat(byte[] logical, int root) {
        if (root < 0 || root + 0x1000 > logical.length) {
            return null;
        }
        int[] fat = new int[FAT_PAGES * 256];
        ByteBuffer bb = ByteBuffer.wrap(logical).order(ByteOrder.BIG_ENDIAN);
        int sequential = 0;
        for (int k = 0; k < FAT_PAGES; k++) {
            int p = root + k * 2 * PAGE;
            for (int i = 0; i < 256; i++) {
                fat[k * 256 + i] = bb.getShort(p + i * 2) & 0xFFFF;
            }
        }
        for (int i = 0; i < 255; i++) {
            if (fat[i + 1] == fat[i] + 1) {
                sequential++;
            }
        }
        return sequential >= 100 ? fat : null;
    }

    private static List<Entry> readDirectory(byte[] logical, int root, int blocks) {
        List<Entry> out = new ArrayList<>();
        ByteBuffer bb = ByteBuffer.wrap(logical).order(ByteOrder.BIG_ENDIAN);
        for (int k = 0; k < FAT_PAGES * 4; k++) {
            int page = root + PAGE + k * 2 * PAGE;
            if (page + PAGE > logical.length) {
                break;
            }
            for (int o = page; o < page + PAGE; o += 0x20) {
                int n = 0;
                while (n < 0x16 && logical[o + n] != 0) {
                    n++;
                }
                if (n == 0) {
                    continue;
                }
                boolean printable = true;
                for (int i = 0; i < n; i++) {
                    int c = logical[o + i] & 0xFF;
                    if (c < 32 || c >= 127) {
                        printable = false;
                        break;
                    }
                }
                if (!printable) {
                    continue;
                }
                int block = bb.getShort(o + 0x16) & 0xFFFF;
                long size = bb.getInt(o + 0x18) & 0xFFFFFFFFL;
                int time = bb.getInt(o + 0x1C);
                if (block <= 0 || block >= blocks || size <= 0 || size > logical.length) {
                    continue;
                }
                out.add(new Entry(new String(logical, o, n, StandardCharsets.US_ASCII),
                        block, size, time));
            }
        }
        return out;
    }

    private static List<Integer> buildChain(int[] fat, Entry e) {
        List<Integer> out = new ArrayList<>();
        boolean[] seen = new boolean[fat.length];
        int b = e.block;
        while (b > 0 && b < fat.length && !seen[b]) {
            seen[b] = true;
            out.add(b);
            int next = fat[b];
            if (next >= END_OF_CHAIN || next == 0) {
                break;
            }
            b = next;
        }
        return out;
    }

    public List<Entry> entries() {
        return entries;
    }

    public int rootOffset() {
        return rootOffset;
    }

    public RootInfo rootInfo() {
        return rootInfo;
    }

    public Entry find(String name) {
        for (Entry e : entries) {
            if (e.name.equalsIgnoreCase(name)) {
                return e;
            }
        }
        return null;
    }

    public List<Integer> chain(Entry e) throws IOException {
        List<Integer> out = buildChain(fat, e);
        long need = (e.size + BLOCK_SIZE - 1) / BLOCK_SIZE;
        if (out.size() < need) {
            throw new IOException("Cadena FAT corta para " + e.name + ": "
                    + out.size() + " < " + need);
        }
        return out;
    }

    public byte[] read(Entry e) throws IOException {
        List<Integer> chain = chain(e);
        if (e.size > Integer.MAX_VALUE) {
            throw new IOException("Archivo demasiado grande: " + e.name);
        }
        byte[] out = new byte[(int) e.size];
        int pos = 0;
        for (int b : chain) {
            if (pos >= out.length) {
                break;
            }
            int n = Math.min(BLOCK_SIZE, out.length - pos);
            long off = (long) b * BLOCK_SIZE;
            if (off + n > logical.length) {
                throw new IOException("Bloque fuera de la NAND en " + e.name);
            }
            System.arraycopy(logical, (int) off, out, pos, n);
            pos += n;
        }
        if (pos != out.length) {
            throw new IOException("Lectura incompleta para " + e.name + ": " + pos + "/" + out.length);
        }
        return out;
    }

    private static int be16(byte[] d, int p) {
        return ((d[p] & 0xFF) << 8) | (d[p + 1] & 0xFF);
    }

    private static int be32(byte[] d, int p) {
        return ((d[p] & 0xFF) << 24) | ((d[p + 1] & 0xFF) << 16)
                | ((d[p + 2] & 0xFF) << 8) | (d[p + 3] & 0xFF);
    }
}
