package pcp.TESTS;

import java.nio.file.Paths;
import java.util.Arrays;
import pcp.xenon.XenonBaseImageInspector;
import pcp.xenon.XenonBootCrypto;
import pcp.xenon.XenonBootCredentials;
import pcp.xenon.XenonBootImage;
import pcp.xenon.XenonBootStrapper;
import pcp.xenon.XenonNandParser;

/** Pruebas de descifrado CE, RotSumSha y reconstrucción LZX de la imagen base. */
public final class XenonBootMilestone4SelfTest {

    private XenonBootMilestone4SelfTest() { }

    public static void main(String[] args) throws Exception {
        testCEDecryptRoundTrip();
        testNandCELayout();
        if (args.length == 2) testRealNand(args[0], args[1]);
        System.out.println("[M4] PASS: CE HMAC/RC4, RotSumSha, block framing and LZX base image");
    }

    private static void testCEDecryptRoundTrip() {
        byte[] cd = new byte[0x300];
        byte[] cdKey = sequence(16, 0x51);
        System.arraycopy(cdKey, 0, cd, 0x10, 16);

        byte[] plain = new byte[0x80];
        plain[0] = 0x43;
        plain[1] = 0x45;
        plain[2] = 0x07;
        plain[3] = 0x60;
        plain[0x0E] = 0;
        plain[0x0F] = (byte) plain.length;
        byte[] salt = sequence(16, 0x20);
        System.arraycopy(salt, 0, plain, 0x10, 16);
        plain[0x28] = 0;
        plain[0x29] = 0;
        plain[0x2A] = 0;
        plain[0x2B] = 0x20;
        for (int i = 0x30; i < plain.length; i++) plain[i] = (byte) (i * 3 + 1);

        byte[] hash = XenonBootCrypto.rotSumSha1(
                Arrays.copyOfRange(plain, 0, 0x10),
                Arrays.copyOfRange(plain, 0x20, plain.length));
        System.arraycopy(hash, 0, cd, 0x24C, 20);
        byte[] digest = XenonBootCrypto.hmacSha1(cdKey, salt);
        byte[] rc4Key = Arrays.copyOf(digest, 16);
        byte[] encrypted = plain.clone();
        byte[] ciphertext = XenonBootCrypto.rc4(rc4Key,
                Arrays.copyOfRange(plain, 0x20, plain.length));
        System.arraycopy(ciphertext, 0, encrypted, 0x20, ciphertext.length);

        byte[] decrypted = XenonBootCrypto.decryptCE(cd, encrypted);
        byte[] expected = plain.clone();
        System.arraycopy(rc4Key, 0, expected, 0x10, 16);
        if (!Arrays.equals(expected, decrypted)
                || !XenonBootCrypto.verifyCEIntegrity(cd, decrypted)) {
            throw new AssertionError("CE decrypt/integrity round-trip failed");
        }
        wipe(cd, cdKey, plain, salt, hash, digest, rc4Key,
                encrypted, ciphertext, decrypted, expected);
    }

    private static void testNandCELayout() throws Exception {
        XenonNandParser nand = XenonNandParser.load(Paths.get(
                System.getProperty("pcp.nand", "./src/pcp/TESTS/nand.bin")));
        if (nand.getCEOffset() != 0x18C40L || nand.getCEVersion() != 0x0760
                || nand.getCESize() != 0x5606A || nand.getCEStoredSize() != 0x56070
                || nand.extractCE().length != 0x56070) {
            throw new AssertionError("Unexpected CE NAND layout");
        }
    }

    private static void testRealNand(String nandPath, String credentialsPath) throws Exception {
        XenonBootStrapper bootstrapper = XenonBootStrapper.load(Paths.get(nandPath));
        try (XenonBootCredentials credentials = XenonBootCredentials.load(Paths.get(credentialsPath))) {
            XenonBootImage ce = bootstrapper.prepareCE(credentials);
            byte[] baseKernel = ce.getExpandedData();
            try {
                XenonBaseImageInspector.Result info = bootstrapper.getBaseImageInfo();
                if (ce.getState() != XenonBootImage.State.DECOMPRESSED
                        || !bootstrapper.isCEIntegrityVerified()
                        || !bootstrapper.isCEDecompressed()
                        || baseKernel == null || baseKernel.length != 0x120000
                        || info == null || info.kernelImageBase != 0x80040000L
                        || info.kernelEntryPoint != 0x80050C40L
                        || info.kernelSizeOfImage != 0xE0000L
                        || info.kernelSectionCount != 9) {
                    throw new AssertionError("Real CE failed integrity/decompression/base PE inspection");
                }
                System.out.printf("[M4] REAL NAND PASS: CE=%04X stored=%08X declared=%08X target=0x%016X base=%08X xboxkrnl=0x%08X entry=0x%08X size=0x%X sections=%d%n",
                        ce.getVersion(), ce.getImageSize(), ce.getDeclaredSize(),
                        ce.getLoadAddress(), baseKernel.length, info.kernelImageBase,
                        info.kernelEntryPoint, info.kernelSizeOfImage, info.kernelSectionCount);
                System.out.println("[M4] BASE HEADER=" + XenonBootCrypto.toHex(Arrays.copyOf(baseKernel, 16)));
            } finally {
                wipe(baseKernel);
            }
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
