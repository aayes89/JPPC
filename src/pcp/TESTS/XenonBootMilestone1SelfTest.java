package pcp.TESTS;

import java.nio.file.Paths;
import java.util.Arrays;
import pcp.xenon.XenonBootCrypto;
import pcp.xenon.XenonBootCredentials;
import pcp.xenon.XenonNandParser;

/** Pruebas sin claves reales para el milestone 01. */
public final class XenonBootMilestone1SelfTest {

    private XenonBootMilestone1SelfTest() { }

    public static void main(String[] args) throws Exception {
        testCryptoVectors();
        testCBADecryption();
        testUserCredentials();
        testNandBootHeader();
        System.out.println("[M1] PASS: crypto primitives, external credentials and NAND CB_A extraction");
    }

    private static void testCryptoVectors() {
        byte[] rc4 = XenonBootCrypto.rc4(
                "Key".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                "Plaintext".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertHex("BBF316E8D940AF0AD3", rc4, "RC4 RFC vector");

        byte[] hmac = XenonBootCrypto.hmacSha1(
                repeat((byte) 0x0B, 20),
                "Hi There".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertHex("B617318655057264E28BC0B6FB378C8EF146BE00", hmac, "HMAC-SHA1 RFC 2202 vector");
    }

    private static void testCBADecryption() {
        byte[] oneBl = new byte[16];
        for (int i = 0; i < oneBl.length; i++) oneBl[i] = (byte) (i + 1);

        byte[] plain = new byte[0x40];
        plain[0] = 0x43;
        plain[1] = 0x42;
        plain[2] = 0x07;
        plain[3] = (byte) 0x88;
        plain[0x0C] = 0;
        plain[0x0D] = 0;
        plain[0x0E] = 0;
        plain[0x0F] = (byte) plain.length;
        for (int i = 0x10; i < 0x20; i++) plain[i] = (byte) (0xA0 + i);
        for (int i = 0x20; i < plain.length; i++) plain[i] = (byte) (i * 3);

        byte[] salt = Arrays.copyOfRange(plain, 0x10, 0x20);
        byte[] digest = XenonBootCrypto.hmacSha1(oneBl, salt);
        byte[] rc4Key = Arrays.copyOf(digest, 16);
        byte[] encrypted = plain.clone();
        byte[] expected = plain.clone();
        System.arraycopy(rc4Key, 0, expected, 0x10, 16);
        byte[] encryptedPayload = XenonBootCrypto.rc4(rc4Key,
                Arrays.copyOfRange(plain, 0x20, plain.length));
        System.arraycopy(encryptedPayload, 0, encrypted, 0x20, encryptedPayload.length);

        byte[] decrypted = XenonBootCrypto.decryptCBA(oneBl, encrypted);
        if (!Arrays.equals(expected, decrypted)) {
            throw new AssertionError("CB_A HMAC-SHA1 + RC4 round-trip failed");
        }
        Arrays.fill(oneBl, (byte) 0);
        Arrays.fill(plain, (byte) 0);
        Arrays.fill(expected, (byte) 0);
        Arrays.fill(salt, (byte) 0);
        Arrays.fill(digest, (byte) 0);
        Arrays.fill(rc4Key, (byte) 0);
        Arrays.fill(encrypted, (byte) 0);
        Arrays.fill(encryptedPayload, (byte) 0);
        Arrays.fill(decrypted, (byte) 0);
    }

    private static void testUserCredentials() throws Exception {
        java.nio.file.Path file = java.nio.file.Files.createTempFile("xenon-credentials-test", ".txt");
        try {
            java.nio.file.Files.write(file, Arrays.asList(
                    "fuseset 00: 0000000000000000",
                    "Your CPU key : 00112233445566778899AABBCCDDEEFF",
                    "1BL: FFEEDDCCBBAA99887766554433221100",
                    "CPU: 00112233445566778899AABBCCDDEEFF"));
            try (XenonBootCredentials credentials = XenonBootCredentials.load(file)) {
                byte[] oneBl = credentials.getOneBlKey();
                byte[] cpu = credentials.getCpuKey();
                if (oneBl.length != 16 || cpu.length != 16
                        || (oneBl[0] & 0xFF) != 0xFF || (cpu[0] & 0xFF) != 0x00) {
                    throw new AssertionError("External credentials parsed incorrectly");
                }
                Arrays.fill(oneBl, (byte) 0);
                Arrays.fill(cpu, (byte) 0);
            }
        } finally {
            java.nio.file.Files.deleteIfExists(file);
        }
    }

    private static void testNandBootHeader() throws Exception {
        XenonNandParser nand = XenonNandParser.load(
                Paths.get(argsPath("./src/pcp/TESTS/nand.bin")));
        long cbOffset = nand.getCBAOffset();
        long cfOffset = nand.getCFOffset();
        int cbSize = nand.getCBASize();
        int cbVersion = nand.getCBAVersion();
        byte[] cb = nand.extractCBA();

        if (cbOffset != 0x8000L || cfOffset != 0x70000L
                || cbSize != 0x2000 || cbVersion != 0x0788
                || cb.length != cbSize || cb[0] != 0x43 || cb[1] != 0x42) {
            throw new AssertionError(String.format(
                    "Unexpected NAND boot header: CB=0x%X size=0x%X version=0x%X CF=0x%X",
                    cbOffset, cbSize, cbVersion, cfOffset));
        }
        System.out.printf("[M1] NAND header OK: CB_A=0x%X size=0x%X version=0x%04X CF=0x%X%n",
                cbOffset, cbSize, cbVersion, cfOffset);
    }

    private static String argsPath(String fallback) {
        return System.getProperty("pcp.nand", fallback);
    }

    private static byte[] repeat(byte value, int count) {
        byte[] result = new byte[count];
        Arrays.fill(result, value);
        return result;
    }

    private static void assertHex(String expected, byte[] actual, String label) {
        String value = XenonBootCrypto.toHex(actual);
        if (!expected.equals(value)) {
            throw new AssertionError(label + " failed: " + value);
        }
    }
}
