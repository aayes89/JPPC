package pcp.xenon;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Parser XEX2, descifrado AES y expansión de compresión basic/LZX. */
public final class XexFile {

    public static final int OPT_RESOURCES = 0x000002FF;
    public static final int OPT_FILE_FORMAT = 0x000003FF;
    public static final int OPT_ENTRY_POINT = 0x00010100;
    public static final int OPT_IMAGE_BASE = 0x00010201;
    public static final int OPT_IMPORT_LIBS = 0x000103FF;
    public static final int OPT_ORIGINAL_NAME = 0x000080FF;
    public static final int OPT_ORIGINAL_PE_NAME = 0x000183FF;
    public static final int OPT_STATIC_LIBRARIES = 0x000200FF;
    public static final int OPT_TLS = 0x00020104;
    public static final int OPT_STACK = 0x00020200;
    public static final int OPT_EXECUTION_INFO = 0x00040006;

    private final byte[] data;
    private final Map<Integer, Integer> options = new LinkedHashMap<>();
    private final int moduleFlags;
    private final int peOffset;
    private final int securityOffset;
    private final int encryption;
    private final int compression;
    private final long imageSize;
    private final long loadAddress;
    private final int fileFormatOffset;

    private XexFile(byte[] data) throws IOException {
        if (data == null || data.length < 0x18
                || data[0] != 'X' || data[1] != 'E' || data[2] != 'X' || data[3] != '2') {
            throw new IOException("No es un XEX2");
        }
        this.data = data;
        moduleFlags = readInt(data, 4);
        peOffset = readInt(data, 8);
        securityOffset = readInt(data, 16);
        int count = readInt(data, 20);
        if (count < 0 || count > 0x400 || 0x18L + (long) count * 8 > data.length) {
            throw new IOException("Tabla de opciones XEX2 fuera de rango");
        }
        for (int i = 0; i < count; i++) {
            options.put(readInt(data, 0x18 + i * 8), readInt(data, 0x1C + i * 8));
        }

        Integer ff = options.get(OPT_FILE_FORMAT);
        if (ff == null || ff < 0 || ff + 8L > data.length) {
            throw new IOException("XEX2 sin información de formato válida");
        }
        fileFormatOffset = ff;
        encryption = readU16(data, ff + 4);
        compression = readU16(data, ff + 6);

        if (securityOffset < 0 || securityOffset + 0x114L > data.length) {
            throw new IOException("SecurityInfo XEX2 fuera de rango");
        }
        imageSize = readInt(data, securityOffset + 4) & 0xFFFFFFFFL;
        loadAddress = readInt(data, securityOffset + 0x110) & 0xFFFFFFFFL;
        if (imageSize <= 0 || imageSize > Integer.MAX_VALUE) {
            throw new IOException("Tamaño de imagen XEX2 inválido: " + imageSize);
        }
        if (peOffset < 0 || peOffset > data.length) {
            throw new IOException("PE offset XEX2 fuera de rango");
        }
        if (compression == 2 && ff + 36L > data.length) {
            throw new IOException("FILE_FORMAT_INFO LZX incompleto");
        }
    }

    public static XexFile parse(byte[] xex) throws IOException {
        return new XexFile(xex);
    }

    public int moduleFlags() { return moduleFlags; }
    public int peOffset() { return peOffset; }
    public int securityOffset() { return securityOffset; }
    public int encryption() { return encryption; }
    public int compression() { return compression; }
    public long imageSize() { return imageSize; }
    public long loadAddress() { return loadAddress; }

    public long imageBase() {
        Integer v = options.get(OPT_IMAGE_BASE);
        return v == null ? loadAddress : v & 0xFFFFFFFFL;
    }

    public long entryPoint() {
        Integer v = options.get(OPT_ENTRY_POINT);
        return v == null ? 0 : v & 0xFFFFFFFFL;
    }

    public boolean hasEntryPoint() {
        return options.containsKey(OPT_ENTRY_POINT);
    }

    /** Ruta XEX, normalmente \Device\Flash\nombre.xex. */
    public String originalName() {
        return readStringOption(OPT_ORIGINAL_NAME);
    }

    /** Nombre PE original, por ejemplo dash.exe o xamc.dll. */
    public String originalPeName() {
        return readStringOption(OPT_ORIGINAL_PE_NAME);
    }

    private String readStringOption(int key) {
        Integer off = options.get(key);
        if (off == null || off < 0 || off + 4L > data.length) return null;
        int size = readInt(data, off);
        if (size < 4 || off + (long) size > data.length) return null;
        int n = 0;
        int max = size - 4;
        while (n < max && data[off + 4 + n] != 0) n++;
        return new String(data, off + 4, n, StandardCharsets.US_ASCII);
    }

    public Map<Integer, Integer> options() {
        return java.util.Collections.unmodifiableMap(options);
    }

    public byte[] raw() { return data; }

    /** Recursos XEX: nombre de 8 bytes, dirección virtual y tamaño. */
    public List<Resource> resources() throws IOException {
        Integer off = options.get(OPT_RESOURCES);
        if (off == null) return java.util.Collections.emptyList();
        if (off < 0 || off + 4L > data.length) throw new IOException("ResourceInfo fuera de rango");
        int size = readInt(data, off);
        if (size < 4 || off + (long) size > data.length || ((size - 4) % 16) != 0) {
            throw new IOException("Tabla de recursos XEX inválida");
        }
        List<Resource> out = new ArrayList<>();
        for (int p = off + 4; p < off + size; p += 16) {
            int n = 0;
            while (n < 8 && data[p + n] != 0) n++;
            String name = new String(data, p, n, StandardCharsets.US_ASCII);
            long address = readInt(data, p + 8) & 0xFFFFFFFFL;
            long length = readInt(data, p + 12) & 0xFFFFFFFFL;
            out.add(new Resource(name, address, length));
        }
        return out;
    }

    public static final class Resource {
        public final String name;
        public final long address;
        public final long size;
        Resource(String name, long address, long size) {
            this.name = name; this.address = address; this.size = size;
        }
    }

    /** Tabla de importación XEX2, incluidos los thunk RVAs por biblioteca. */
    public List<ImportLibrary> importLibraries() throws IOException {
        Integer off = options.get(OPT_IMPORT_LIBS);
        if (off == null) return java.util.Collections.emptyList();
        if (off < 0 || off + 12L > data.length) throw new IOException("ImportLibraries fuera de rango");
        int size = readInt(data, off);
        int stringSize = readInt(data, off + 4);
        int libraryCount = readInt(data, off + 8);
        if (size < 12 || stringSize < 0 || libraryCount < 0 || libraryCount > 0x1000
                || off + (long) size > data.length
                || off + 12L + stringSize > off + (long) size) {
            throw new IOException("Cabecera ImportLibraries inválida");
        }

        byte[] stringTable = Arrays.copyOfRange(data, off + 12, off + 12 + stringSize);
        List<String> names = new ArrayList<>();
        int s = 0;
        while (s < stringTable.length) {
            int e = s;
            while (e < stringTable.length && stringTable[e] != 0) e++;
            names.add(new String(stringTable, s, e - s, StandardCharsets.US_ASCII));
            s = e + 1;
        }

        int p = off + 12 + stringSize;
        List<ImportLibrary> out = new ArrayList<>();
        for (int i = 0; i < libraryCount; i++) {
            if (p + 40L > off + (long) size) throw new IOException("Registro import library truncado");
            int librarySize = readInt(data, p);
            if (librarySize < 40 || p + (long) librarySize > off + (long) size) {
                throw new IOException("Tamaño de import library inválido");
            }
            byte[] digest = Arrays.copyOfRange(data, p + 4, p + 24);
            int libraryId = readInt(data, p + 24);
            int version = readInt(data, p + 28);
            int minVersion = readInt(data, p + 32);
            int nameIndex = readU16(data, p + 36);
            int importCount = readU16(data, p + 38);
            if (40L + (long) importCount * 4 > librarySize) {
                throw new IOException("Import table excede el registro de biblioteca");
            }
            String name = nameIndex < names.size() ? names.get(nameIndex) : "#" + nameIndex;
            int[] thunks = new int[importCount];
            for (int j = 0; j < importCount; j++) thunks[j] = readInt(data, p + 40 + j * 4);
            out.add(new ImportLibrary(name, libraryId, version, minVersion, digest, thunks));
            p += librarySize;
        }
        return out;
    }

    public static final class ImportLibrary {
        public final String name;
        public final int libraryId;
        public final int version;
        public final int minVersion;
        public final byte[] digest;
        public final int[] thunkAddresses;
        ImportLibrary(String name, int libraryId, int version, int minVersion,
                      byte[] digest, int[] thunkAddresses) {
            this.name = name; this.libraryId = libraryId; this.version = version;
            this.minVersion = minVersion; this.digest = digest;
            this.thunkAddresses = thunkAddresses;
        }
    }

    /** Import individual resuelto a su ordinal y byte de tipo desde la imagen. */
    public List<ImportEntry> importEntries(byte[] image) throws IOException {
        if (image == null) throw new IOException("Imagen nula para resolver imports");
        long base = imageBase() & 0xFFFFFFFFL;
        List<ImportEntry> out = new ArrayList<>();
        for (ImportLibrary lib : importLibraries()) {
            for (int address : lib.thunkAddresses) {
                long thunkVa = address & 0xFFFFFFFFL;
                long rva = thunkVa - base;
                if (rva < 0 || rva + 4 > image.length) {
                    throw new IOException("Import thunk fuera de imagen: " + lib.name
                            + " VA=0x" + Long.toHexString(thunkVa));
                }
                int raw = readInt(image, (int) rva);
                int typeFlag = (raw >>> 24) & 0xFF;
                int ordinal = raw & 0x00FFFFFF;
                out.add(new ImportEntry(lib.name, lib.libraryId, thunkVa,
                        rva, ordinal, typeFlag, raw));
            }
        }
        return out;
    }

    public static final class ImportEntry {
        public final String library;
        public final int libraryId;
        public final long thunkAddress;
        public final long thunkRva;
        public final int ordinal;
        public final int typeFlag;
        public final int rawWord;
        ImportEntry(String library, int libraryId, long thunkAddress, long thunkRva,
                    int ordinal, int typeFlag, int rawWord) {
            this.library = library; this.libraryId = libraryId;
            this.thunkAddress = thunkAddress; this.thunkRva = thunkRva;
            this.ordinal = ordinal; this.typeFlag = typeFlag; this.rawWord = rawWord;
        }
    }

    /**
     * Devuelve la imagen descomprimida. No exige firma MZ: XEX también puede
     * contener recursos XUIZ/XUIB u otros formatos no PE.
     */
    public byte[] image(List<byte[]> keys) throws IOException {
        if (compression == 3) {
            throw new IOException("Compresión delta (XEXP): requiere aplicar el parche sobre su imagen base");
        }
        byte[] payload = Arrays.copyOfRange(data, peOffset, data.length);
        if (encryption == 0) {
            return expand(payload);
        }
        if (encryption != 1) {
            throw new IOException("Tipo de cifrado XEX desconocido: " + encryption);
        }
        if (keys == null || keys.isEmpty()) {
            throw new IOException("XEX cifrado y sin claves");
        }

        if (securityOffset + 0x160L > data.length) {
            throw new IOException("ImageKey XEX fuera de rango");
        }
        byte[] encryptedFileKey = Arrays.copyOfRange(data, securityOffset + 0x150,
                securityOffset + 0x160);
        int alignedLength = payload.length & ~15;
        if (alignedLength == 0) throw new IOException("Payload cifrado vacío");
        byte[] cipherText = Arrays.copyOf(payload, alignedLength);
        IOException last = null;

        for (byte[] key : keys) {
            if (key == null || key.length != 16) continue;
            try {
                byte[] fileKey = aes("AES/ECB/NoPadding", key, null, encryptedFileKey);
                byte[] decrypted = aes("AES/CBC/NoPadding", fileKey, new byte[16], cipherText);
                return expand(decrypted);
            } catch (IOException ex) {
                last = ex;
            }
        }
        throw new IOException("Ninguna clave valida el XEX" + (last != null ? ": " + last.getMessage() : ""));
    }

    private byte[] expand(byte[] payload) throws IOException {
        int outLen = (int) imageSize;
        if (compression == 0) {
            if (payload.length < outLen) throw new IOException("Payload sin compresión más corto que imageSize");
            return Arrays.copyOf(payload, outLen);
        }
        if (compression == 1) {
            int size = readInt(data, fileFormatOffset);
            byte[] out = new byte[outLen];
            int pos = 0, src = 0;
            for (int i = 8; i + 8 <= size; i += 8) {
                int dsz = readInt(data, fileFormatOffset + i);
                int zsz = readInt(data, fileFormatOffset + i + 4);
                if (dsz < 0 || zsz < 0 || src + (long) dsz > payload.length
                        || pos + (long) dsz + zsz > out.length) {
                    throw new IOException("Bloques de compresión básica inválidos");
                }
                System.arraycopy(payload, src, out, pos, dsz);
                src += dsz;
                pos += dsz + zsz;
            }
            return out;
        }
        if (compression != 2) throw new IOException("Compresión desconocida: " + compression);

        int window = readInt(data, fileFormatOffset + 8);
        int firstBlockSize = readInt(data, fileFormatOffset + 12);
        byte[] firstBlockHash = Arrays.copyOfRange(data, fileFormatOffset + 16,
                fileFormatOffset + 36);
        byte[] stream = deblockLzx(payload, firstBlockSize, firstBlockHash);
        return new XexLzx(window).decompress(stream, outLen);
    }

    /** Reconstruye el flujo LZX verificando el SHA-1 de cada bloque XEX. */
    private static byte[] deblockLzx(byte[] payload, int firstBlockSize,
                                     byte[] firstBlockHash) throws IOException {
        if (firstBlockSize < 24 || firstBlockHash == null || firstBlockHash.length != 20) {
            throw new IOException("Primer bloque LZX inválido");
        }
        ByteArrayOutputStream stream = new ByteArrayOutputStream(payload.length);
        int p = 0;
        int currentSize = firstBlockSize;
        byte[] expectedHash = firstBlockHash;
        int blockCount = 0;

        while (currentSize != 0) {
            long blockEndLong = (long) p + currentSize;
            if (currentSize < 24 || blockEndLong > payload.length) {
                throw new IOException("Bloque LZX fuera de rango: p=0x"
                        + Integer.toHexString(p) + " size=0x" + Integer.toHexString(currentSize));
            }
            int blockEnd = (int) blockEndLong;
            byte[] actualHash = sha1(payload, p, currentSize);
            if (!MessageDigest.isEqual(actualHash, expectedHash)) {
                throw new IOException("SHA1 XEX inválido en bloque " + blockCount
                        + ": esperado=" + hex(expectedHash) + " real=" + hex(actualHash));
            }

            int nextSize = readInt(payload, p);
            byte[] nextHash = Arrays.copyOfRange(payload, p + 4, p + 24);
            int q = p + 24;

            while (q + 2 <= blockEnd) {
                int chunkSize = readU16(payload, q);
                q += 2;
                if (chunkSize == 0) break;
                int available = blockEnd - q;
                int take = Math.min(chunkSize, available);
                if (take < 0) throw new IOException("Chunk LZX inválido");
                stream.write(payload, q, take);
                q += take;
                if (take != chunkSize) break;
            }

            p = blockEnd;
            currentSize = nextSize;
            expectedHash = nextHash;
            if (++blockCount > 0x10000) throw new IOException("Cadena de bloques LZX cíclica");
        }
        return stream.toByteArray();
    }

    private static byte[] sha1(byte[] data, int off, int len) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(data, off, len);
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 no disponible", e);
        }
    }

    private static byte[] aes(String mode, byte[] key, byte[] iv, byte[] input) throws IOException {
        try {
            Cipher c = Cipher.getInstance(mode);
            SecretKeySpec ks = new SecretKeySpec(key, "AES");
            if (iv == null) c.init(Cipher.DECRYPT_MODE, ks);
            else c.init(Cipher.DECRYPT_MODE, ks, new IvParameterSpec(iv));
            return c.doFinal(input);
        } catch (java.security.GeneralSecurityException e) {
            throw new IOException("AES: " + e.getMessage(), e);
        }
    }

    public static byte[] hexKey(String hex) {
        String h = hex.replaceAll("[^0-9a-fA-F]", "");
        if (h.length() != 32) throw new IllegalArgumentException("Se esperaban 32 dígitos hexadecimales");
        byte[] k = new byte[16];
        for (int i = 0; i < 16; i++) k[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
        return k;
    }

    public static List<byte[]> loadKeys(Path file) throws IOException {
        List<byte[]> keys = new ArrayList<>();
        for (String line : Files.readAllLines(file)) {
            int c = line.indexOf(':');
            if (c > 0 && line.substring(0, c).trim().toLowerCase(java.util.Locale.ROOT).startsWith("xex")) {
                keys.add(hexKey(line.substring(c + 1)));
            }
        }
        return keys;
    }

    private static int readInt(byte[] d, int p) {
        return ByteBuffer.wrap(d, p, 4).order(ByteOrder.BIG_ENDIAN).getInt();
    }
    private static int readU16(byte[] d, int p) {
        return ByteBuffer.wrap(d, p, 2).order(ByteOrder.BIG_ENDIAN).getShort() & 0xFFFF;
    }
    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte v : b) s.append(String.format("%02X", v & 0xFF));
        return s.toString();
    }
}
