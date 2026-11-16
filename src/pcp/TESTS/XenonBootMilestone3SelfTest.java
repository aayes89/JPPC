package pcp.TESTS;

import java.nio.file.Paths;
import java.util.Arrays;
import pcp.xenon.XenonBootCrypto;
import pcp.xenon.XenonBootCredentials;
import pcp.xenon.XenonBootImage;
import pcp.xenon.XenonBootStrapper;
import pcp.xenon.XenonNandParser;

/** Pruebas de CD y KeyVault; no contiene claves reales. */
public final class XenonBootMilestone3SelfTest {

    private XenonBootMilestone3SelfTest() { }

    public static void main(String[] args) throws Exception {
        testCDDecryptRoundTrip();
        testKeyVaultDecryptRoundTrip();
        testNandStageLayout();
        if (args.length == 2) testRealNand(args[0], args[1]);
        System.out.println("[M3] PASS: CD HMAC/RC4 + RotSumSha, KeyVault HMAC/RC4 and NAND layout");
    }

    private static void testCDDecryptRoundTrip() {
        byte[] cbb = new byte[0x400];
        byte[] cbbKey = sequence(16, 0x61);
        System.arraycopy(cbbKey, 0, cbb, 0x10, 16);

        byte[] plain = new byte[0x300];
        plain[0] = 0x43;
        plain[1] = 0x44;
        plain[2] = 0x07;
        plain[3] = (byte) 0x88;
        plain[8] = 0x03;
        plain[9] = 0x10;
        plain[0x0E] = 0x03;
        plain[0x0F] = 0x00;
        byte[] salt = sequence(16, 0x22);
        System.arraycopy(salt, 0, plain, 0x10, 16);
        plain[0x120] = 0;
        for (int i = 0x121; i < plain.length; i++) plain[i] = (byte) (i * 11 + 9);

        byte[] hash = XenonBootCrypto.rotSumSha1(
                Arrays.copyOfRange(plain, 0, 0x10),
                Arrays.copyOfRange(plain, 0x120, plain.length));
        System.arraycopy(hash, 0, cbb, 0x39C, 20);

        byte[] digest = XenonBootCrypto.hmacSha1(cbbKey, salt);
        byte[] rc4Key = Arrays.copyOf(digest, 16);
        byte[] encrypted = plain.clone();
        byte[] ciphertext = XenonBootCrypto.rc4(rc4Key,
                Arrays.copyOfRange(plain, 0x20, plain.length));
        System.arraycopy(ciphertext, 0, encrypted, 0x20, ciphertext.length);

        byte[] decrypted = XenonBootCrypto.decryptCD(cbb, encrypted);
        byte[] expected = plain.clone();
        System.arraycopy(rc4Key, 0, expected, 0x10, 16);
        if (!Arrays.equals(expected, decrypted)
                || !XenonBootCrypto.verifyCDIntegrity(cbb, decrypted)) {
            throw new AssertionError("CD decrypt/integrity round-trip failed");
        }
        wipe(cbb, cbbKey, plain, salt, hash, digest, rc4Key,
                encrypted, ciphertext, decrypted, expected);
    }

    private static void testKeyVaultDecryptRoundTrip() {
        byte[] cpuKey = sequence(16, 0x40);
        byte[] plain = new byte[0x4000];
        for (int i = 0x10; i < plain.length; i++) plain[i] = (byte) (i * 5 + 7);
        byte[] version = new byte[]{0x07, 0x12};
        byte[] hash = XenonBootCrypto.hmacSha1(cpuKey,
                Arrays.copyOfRange(plain, 0x10, plain.length), version);
        System.arraycopy(hash, 0, plain, 0, 16);
        byte[] rc4Digest = XenonBootCrypto.hmacSha1(cpuKey,
                Arrays.copyOfRange(plain, 0, 16));
        byte[] rc4Key = Arrays.copyOf(rc4Digest, 16);
        byte[] encrypted = plain.clone();
        byte[] encryptedBody = XenonBootCrypto.rc4(rc4Key,
                Arrays.copyOfRange(plain, 0x10, plain.length));
        System.arraycopy(encryptedBody, 0, encrypted, 0x10, encryptedBody.length);

        byte[] decrypted = XenonBootCrypto.decryptKeyVault(cpuKey, encrypted);
        if (!Arrays.equals(plain, decrypted)) {
            throw new AssertionError("KeyVault decrypt round-trip failed");
        }
        wipe(cpuKey, plain, version, hash, rc4Digest, rc4Key,
                encrypted, encryptedBody, decrypted);
    }

    private static void testNandStageLayout() throws Exception {
        XenonNandParser nand = XenonNandParser.load(Paths.get(
                System.getProperty("pcp.nand", "./src/pcp/TESTS/nand.bin")));
        if (nand.getCDOffset() != 0x13BC0L || nand.getCDVersion() != 0x0788
                || nand.getCDSize() != 0x5080 || nand.getCEOffset() != 0x18C40L
                || nand.getKeyVaultOffset() != 0x4000L
                || nand.extractKeyVault().length != 0x4000) {
            throw new AssertionError("Unexpected CD/CE/KeyVault NAND layout");
        }
    }

    private static void testRealNand(String nandPath, String credentialsPath) throws Exception {
        XenonBootStrapper bootstrapper = XenonBootStrapper.load(Paths.get(nandPath));
        try (XenonBootCredentials credentials = XenonBootCredentials.load(Paths.get(credentialsPath))) {
            XenonBootImage cd = bootstrapper.prepareCD(credentials);
            XenonBootImage kv = bootstrapper.prepareKeyVault(credentials);
            if (cd.getState() != XenonBootImage.State.INTEGRITY_VERIFIED
                    || kv.getState() != XenonBootImage.State.INTEGRITY_VERIFIED
                    || !bootstrapper.isCDIntegrityVerified()
                    || !bootstrapper.isKeyVaultIntegrityVerified()) {
                throw new AssertionError("Real CD/KeyVault integrity verification failed");
            }
            System.out.printf("[M3] REAL NAND PASS: CD=%04X size=%08X CE=0x%X KeyVault=%08X%n",
                    cd.getVersion(), cd.getImageSize(), bootstrapper.getNand().getCEOffset(),
                    kv.getImageSize());
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
}
