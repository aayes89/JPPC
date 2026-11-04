package pcp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import pcp.gpu.NandImage;

/**
 * Inspector read-only de NAND Xenon small-block y descifrador CB_A. No modifica
 * la NAND de origen. Los secretos se reciben por argumento y nunca se escriben
 * en el informe.
 */
public final class XenonNandBootInspector {

    private static final int NAND_HEADER_SIZE = 0x200;
    private static final int CB_HEADER_SIZE = 0x20;
    private static final int CB_OFFSET_FIELD = 0x08;
    private static final int CF_OFFSET_FIELD = 0x0C;
    private static final byte[] CB_MAGIC = {0x43, 0x42};

    private XenonNandBootInspector() {
    }

    public static final class Report {

        public final long validEcc, invalidEcc, erasedPages;
        public final int cbOffset, cfOffset, cbSize, cbVersion;
        public final Path decryptedCbA;

        private Report(long valid, long invalid, long erased, int cbOffset,
                int cfOffset, int cbSize, int cbVersion, Path output) {
            this.validEcc = valid;
            this.invalidEcc = invalid;
            this.erasedPages = erased;
            this.cbOffset = cbOffset;
            this.cfOffset = cfOffset;
            this.cbSize = cbSize;
            this.cbVersion = cbVersion;
            this.decryptedCbA = output;
        }
    }

    public static Report inspect(Path nandPath, byte[] oneBlKey, byte[] cpuKey,
            Path outputDirectory) throws IOException, GeneralSecurityException {
        requireKey(oneBlKey, "1BL");
        requireKey(cpuKey, "CPU");
        NandImage nand = NandImage.load(nandPath);
        if (nand.getFormat() != NandImage.Format.RAW_WITH_SPARE) {
            throw new IOException("La inspección EDC requiere NAND RAW 512+16 con OOB");
        }

        long valid = 0, invalid = 0, erased = 0;
        byte[] raw = new byte[NandImage.RAW_PAGE_SIZE];
        for (int page = 0; page < nand.getPageCount(); page++) {
            nand.readPhysicalPage(page, raw);
            if (allFF(raw)) {
                erased++;
            } else if (NandImage.isEccValid(raw)) {
                valid++;
            } else {
                invalid++;
            }
        }

        byte[] header = readLogical(nand, 0, NAND_HEADER_SIZE);
        int cbOffset = checkedOffset(be32(header, CB_OFFSET_FIELD), nand.getPageCount() * NandImage.DATA_PAGE_SIZE, "CB");
        int cfOffset = checkedOffset(be32(header, CF_OFFSET_FIELD), nand.getPageCount() * NandImage.DATA_PAGE_SIZE, "CF");
        if (cbOffset + CB_HEADER_SIZE > nand.getPageCount() * NandImage.DATA_PAGE_SIZE) {
            throw new IOException("CB fuera de NAND");
        }

        byte[] cbHeader = readLogical(nand, cbOffset, CB_HEADER_SIZE);
        if (cbHeader[0] != CB_MAGIC[0] || cbHeader[1] != CB_MAGIC[1]) {
            throw new IOException(String.format("Magic CB inválido en 0x%X", cbOffset));
        }
        int cbVersion = be16(cbHeader, 2);
        int cbSize = checkedOffset(be32(cbHeader, 4), 0x100000, "tamaño CB");
        if (cbSize < CB_HEADER_SIZE || cbSize > (cfOffset > cbOffset ? cfOffset - cbOffset : 0x10000)) {
            throw new IOException(String.format("Tamaño CB fuera de rango: 0x%X", cbSize));
        }

        byte[] encrypted = readLogical(nand, cbOffset, cbSize);
        byte[] derived = hmacSha1(oneBlKey, Arrays.copyOfRange(encrypted, 0x10, 0x20));
        byte[] rc4Key = Arrays.copyOf(derived, 16);
        rc4(encrypted, CB_HEADER_SIZE, cbSize - CB_HEADER_SIZE, rc4Key);

        Files.createDirectories(outputDirectory);
        Path cbOut = outputDirectory.resolve("CB_A.decrypted.bin");
        Files.write(cbOut, encrypted);
        Arrays.fill(derived, (byte) 0);
        Arrays.fill(rc4Key, (byte) 0);
        // cpuKey is validated here and intentionally not emitted; it is required
        // by subsequent CB_B / KeyVault stages, not by CB_A's 1BL derivation.
        return new Report(valid, invalid, erased, cbOffset, cfOffset, cbSize, cbVersion, cbOut);
    }

    private static byte[] readLogical(NandImage nand, int offset, int length) {
        if (offset < 0 || length < 0 || (long) offset + length > nand.getPageCount() * NandImage.DATA_PAGE_SIZE) {
            throw new IndexOutOfBoundsException("Rango lógico NAND fuera de límites");
        }
        byte[] out = new byte[length];
        byte[] page = new byte[NandImage.DATA_PAGE_SIZE];
        int done = 0;
        while (done < length) {
            int absolute = offset + done;
            int pageIndex = absolute / NandImage.DATA_PAGE_SIZE;
            int inPage = absolute % NandImage.DATA_PAGE_SIZE;
            nand.readLogicalPage(pageIndex, page);
            int count = Math.min(length - done, NandImage.DATA_PAGE_SIZE - inPage);
            System.arraycopy(page, inPage, out, done, count);
            done += count;
        }
        return out;
    }

    private static byte[] hmacSha1(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key, "HmacSHA1"));
        return mac.doFinal(data);
    }

    /**
     * RC4 PRGA, compatible con el flujo ExCryptRc4Ecb usado por CB_A.
     */
    private static void rc4(byte[] data, int offset, int length, byte[] key) {
        int[] s = new int[256];
        for (int i = 0; i < 256; i++) {
            s[i] = i;
        }
        int j = 0;
        for (int i = 0; i < 256; i++) {
            j = (j + s[i] + (key[i % key.length] & 0xFF)) & 0xFF;
            int t = s[i];
            s[i] = s[j];
            s[j] = t;
        }
        int i = 0;
        j = 0;
        for (int n = 0; n < length; n++) {
            i = (i + 1) & 0xFF;
            j = (j + s[i]) & 0xFF;
            int t = s[i];
            s[i] = s[j];
            s[j] = t;
            int k = s[(s[i] + s[j]) & 0xFF];
            data[offset + n] ^= (byte) k;
        }
        Arrays.fill(s, 0);
    }

    private static int checkedOffset(long value, int limit, String name) throws IOException {
        if (value < 0 || value > limit) {
            throw new IOException(name + " fuera de rango: 0x" + Long.toHexString(value));
        }
        return (int) value;
    }

    private static int be16(byte[] b, int o) {
        return ((b[o] & 255) << 8) | (b[o + 1] & 255);
    }

    private static long be32(byte[] b, int o) {
        return ((long) (b[o] & 255) << 24) | ((long) (b[o + 1] & 255) << 16)
                | ((long) (b[o + 2] & 255) << 8) | (b[o + 3] & 255L);
    }

    private static boolean allFF(byte[] b) {
        for (byte v : b) {
            if ((v & 255) != 255) {
                return false;
            }
        }
        return true;
    }

    private static void requireKey(byte[] key, String name) {
        if (key == null || key.length != 16) {
            throw new IllegalArgumentException(name + " key debe ser de 16 bytes");
        }
    }

    public static byte[] parseHexKey(String hex) {
        if (hex == null) {
            throw new IllegalArgumentException("Clave nula");
        }
        return parseHexKey(hex.toCharArray());
    }

    private static byte[] parseHexKey(char[] hex) {
        if (hex == null || hex.length != 32) {
            throw new IllegalArgumentException("La clave debe contener 32 dígitos hexadecimales");
        }
        byte[] out = new byte[16];
        for (int i = 0; i < 16; i++) {
            int hi = Character.digit(hex[i * 2], 16);
            int lo = Character.digit(hex[i * 2 + 1], 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("Clave hexadecimal inválida");
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    public static void main(String[] args) throws Exception {
        char[] oneBlChars = "DD88AD0C9ED669E7B56794FB68563EFA".toCharArray();
        char[] cpuChars = "01F53DB42708CB8FC91395EB8E7DE908".toCharArray();
        String nandPath = "./src/pcp/TESTS/nand.bin";
        if (args.length != 2) {
            System.err.println("Uso: XenonNandBootInspector <nand.raw> <output-dir>");
            System.err.println("Las claves 1BL y CPU se solicitan de forma interactiva y no se guardan.");
            //System.exit(2);
        }
        java.io.Console console = System.console();
        if (console == null) {
            throw new IOException("Se requiere una consola interactiva para introducir las claves sin exponerlas en argv");
        }
        //oneBlChars = console.readPassword("1BL key (32 hex): ");
        //cpuChars = console.readPassword("CPU key (32 hex): ");
        byte[] oneBl = null, cpu = null;
        try {
            oneBl = parseHexKey(oneBlChars);
            cpu = parseHexKey(cpuChars);
            //Report r = inspect(java.nio.file.Paths.get(args[0]), oneBl, cpu, java.nio.file.Paths.get(args[1]));
            Report r = inspect(java.nio.file.Paths.get(nandPath), oneBl, cpu, java.nio.file.Paths.get("./"));
            System.out.printf("NAND: EDC valid=%d invalid=%d erased=%d%n", r.validEcc, r.invalidEcc, r.erasedPages);
            System.out.printf("HEADER: CB offset=0x%08X CF offset=0x%08X%n", r.cbOffset, r.cfOffset);
            System.out.printf("CB_A: version=0x%04X size=0x%X decrypted=%s%n", r.cbVersion, r.cbSize, r.decryptedCbA);
            System.out.println("CPU key recibida para CB_B/KeyVault; todavía no se usa ni se imprime.");
        } finally {
            Arrays.fill(oneBlChars, '\0');
            Arrays.fill(cpuChars, '\0');
            if (oneBl != null) {
                Arrays.fill(oneBl, (byte) 0);
            }
            if (cpu != null) {
                Arrays.fill(cpu, (byte) 0);
            }
        }
    }
}
