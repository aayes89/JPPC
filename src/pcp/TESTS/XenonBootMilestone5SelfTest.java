package pcp.TESTS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import pcp.xenon.XenonBootCrypto;
import pcp.xenon.XenonBootCredentials;
import pcp.xenon.XenonBootStrapper;
import pcp.xenon.XenonKernelPatchLoader;

/** Prueba de reconstrucción NAND CF/CG y validación SHA1 del kernel actualizado. */
public final class XenonBootMilestone5SelfTest {

    private XenonBootMilestone5SelfTest() { }

    public static void main(String[] args) throws Exception {
        args = new String[]{"./src/pcp/TESTS/nand.bin","./src/pcp/fuses.txt","patched.bin"};
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException(
                    "Uso: XenonBootMilestone5SelfTest <nand.bin> <credentials.txt> [patched-image.bin]");
        }

        XenonBootStrapper bootstrapper = XenonBootStrapper.load(Paths.get(args[0]));
        try (XenonBootCredentials credentials = XenonBootCredentials.load(Paths.get(args[1]))) {
            XenonKernelPatchLoader.Result result = bootstrapper.prepareKernel(credentials);
            byte[] image = result.getImage();
            try {
                if (result.cfVersion != 0x4497 || result.baseVersion != 0x0760
                        || result.targetVersion != 0x4497 || result.cgVersion != 0x4497
                        || result.originalSize != 0x120000 || result.newSize != 0x1F0000
                        || result.patchCount != 57 || image.length != 0x1F0000
                        || result.imageInfo.hypervisorVersion != result.targetVersion
                        || result.imageInfo.kernelImageBase != 0x80040000L
                        || result.imageInfo.kernelSizeOfImage != image.length - 0x40000L
                        || result.cfSignatureVerified) {
                    throw new AssertionError("CF/CG result metadata or image validation mismatch");
                }
                byte[] actualHash = XenonBootCrypto.sha1(image);
                if (!result.newSha1.equals(XenonBootCrypto.toHex(actualHash))) {
                    throw new AssertionError("Patched image SHA1 mismatch");
                }
                if (args.length == 3) Files.write(Path.of(args[2]), image);
                System.out.printf("[M5] PASS: CF=%04X CG=%04X target=%04X patches=%d size=0x%X SHA1=%s%n",
                        result.cfVersion, result.cgVersion, result.targetVersion,
                        result.patchCount, image.length, result.newSha1);
                System.out.printf("[M5] xboxkrnl.exe base=0x%08X entry=0x%08X size=0x%X sections=%d%n",
                        result.imageInfo.kernelImageBase, result.imageInfo.kernelEntryPoint,
                        result.imageInfo.kernelSizeOfImage, result.imageInfo.kernelSectionCount);
                System.out.println("[M5] CF RSA signature verification remains pending.");
            } finally {
                Arrays.fill(image, (byte) 0);
            }
        }
    }
}
