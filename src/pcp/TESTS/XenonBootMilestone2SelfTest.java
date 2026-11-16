package pcp.TESTS;

import java.nio.file.Paths;
import java.util.Arrays;
import pcp.xenon.XenonBootCrypto;
import pcp.xenon.XenonBootCredentials;
import pcp.xenon.XenonBootImage;
import pcp.xenon.XenonBootStrapper;
import pcp.xenon.XenonNandParser;

/** Pruebas del procesamiento CB_B, sin claves embebidas en el proyecto. */
public final class XenonBootMilestone2SelfTest {

    private XenonBootMilestone2SelfTest() { }

    public static void main(String[] args) throws Exception {
        testRotSumShaVector();
        testCBBDecryptRoundTrip();
        testNandCBBLayout();
        if (args.length == 2) {
            testRealNand(args[0], args[1]);
        }
        System.out.println("[M2] PASS: RotSumSha, CB_B v2 derivation, integrity and NAND layout");
    }

    private static void testRotSumShaVector() {
        byte[] data = new byte[0x100];
        for (int i = 0; i < data.length; i++) data[i] = (byte) (i * 7 + 3);
        byte[] digest = XenonBootCrypto.rotSumSha1(data);
        assertHex("C4A71C4787B7E7F397CE67711108C94BAA7E86E5", digest, "XeCryptRotSumSha vector");
        Arrays.fill(data, (byte) 0);
        Arrays.fill(digest, (byte) 0);
    }

    private static void testCBBDecryptRoundTrip() {
        byte[] cpuKey = sequence(16, 0x31);
        byte[] cbA = new byte[0x400];
        byte[] cbAKey = sequence(16, 0x80);
        cbA[0] = 0x43;
        cbA[1] = 0x42;
        cbA[2] = 0x07;
        cbA[3] = (byte) 0x88;
        cbA[4] = 0x18;
        cbA[5] = 0x00;
        System.arraycopy(cbAKey, 0, cbA, 0x10, 16);

        byte[] plain = new byte[0x200];
        plain[0] = 0x43;
        plain[1] = 0x42;
        plain[2] = 0x07;
        plain[3] = (byte) 0x88;
        plain[8] = 0x03;
        plain[9] = (byte) 0xE0;
        plain[0x0E] = 0x02;
        plain[0x0F] = 0x00;
        byte[] salt = sequence(16, 0x12);
        System.arraycopy(salt, 0, plain, 0x10, 16);
        for (int i = 0x20; i < plain.length; i++) plain[i] = (byte) (i * 13 + 5);

        byte[] hash = XenonBootCrypto.rotSumSha1(
                Arrays.copyOfRange(plain, 0, 0x10),
                Arrays.copyOfRange(plain, 0x140, plain.length));
        System.arraycopy(hash, 0, cbA, 0x39C, 20);

        byte[] cbAHeader = Arrays.copyOfRange(cbA, 0, 0x10);
        Arrays.fill(cbAHeader, 4, 8, (byte) 0);
        byte[] digest = XenonBootCrypto.hmacSha1(cbAKey, salt, cpuKey, cbAHeader);
        byte[] rc4Key = Arrays.copyOf(digest, 16);
        byte[] encrypted = plain.clone();
        byte[] ciphertext = XenonBootCrypto.rc4(rc4Key,
                Arrays.copyOfRange(plain, 0x20, plain.length));
        System.arraycopy(ciphertext, 0, encrypted, 0x20, ciphertext.length);

        byte[] decrypted = XenonBootCrypto.decryptCBB(cbA, encrypted, cpuKey, 0x0788);
        byte[] expected = plain.clone();
        System.arraycopy(rc4Key, 0, expected, 0x10, 16);
        if (!Arrays.equals(expected, decrypted)
                || !XenonBootCrypto.verifyCBBIntegrity(cbA, decrypted)) {
            throw new AssertionError("CB_B v2 decrypt/integrity round-trip failed");
        }
        wipe(cpuKey, cbA, cbAKey, plain, salt, hash, cbAHeader, digest,
                rc4Key, encrypted, ciphertext, decrypted, expected);
    }

    private static void testNandCBBLayout() throws Exception {
        XenonNandParser nand = XenonNandParser.load(Paths.get(
                System.getProperty("pcp.nand", "./src/pcp/TESTS/nand.bin")));
        
        if (nand.getCBBOffset() != 0xA000L
                || nand.getCBBVersion() != 0x0788
                || nand.getCBBSize() != 0x9BC0
                || nand.getCDOffset() != 0x13BC0L) {
            throw new AssertionError("Unexpected CB_B/CD NAND layout");
        }
    }

    private static void testRealNand(String nandPath, String credentialsPath) throws Exception {
        XenonBootStrapper bootstrapper = XenonBootStrapper.load(Paths.get(nandPath));
        try (XenonBootCredentials credentials = XenonBootCredentials.load(Paths.get(credentialsPath))) {
            XenonBootImage cbB = bootstrapper.prepareCBB(credentials);
            if (cbB.getState() != XenonBootImage.State.INTEGRITY_VERIFIED
                    || !bootstrapper.isCBBIntegrityVerified()) {
                throw new AssertionError("Real CB_B did not pass integrity verification");
            }
            System.out.printf("[M2] REAL NAND PASS: CB_A=%04X CB_B=%04X size=%08X CD=0x%X%n",
                    bootstrapper.getCBA().getVersion(), cbB.getVersion(),
                    cbB.getImageSize(), bootstrapper.getNand().getCDOffset());
        }
    }

    private static byte[] sequence(int length, int start) {
        byte[] result = new byte[length];
        for (int i = 0; i < length; i++) result[i] = (byte) (start + i);
        return result;
    }

    private static void wipe(byte[]... values) {
        for (byte[] value : values) if (value != null) Arrays.fill(value, (byte) 0);
    }

    private static void assertHex(String expected, byte[] actual, String label) {
        String value = XenonBootCrypto.toHex(actual);
        if (!expected.equals(value)) throw new AssertionError(label + " failed: " + value);
    }
}
