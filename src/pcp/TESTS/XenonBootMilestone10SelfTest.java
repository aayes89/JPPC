package pcp.TESTS;

import java.nio.file.Path;
import pcp.xenon.XenonBootCredentials;
import pcp.xenon.XenonBootStrapper;
import pcp.xenon.XenonFuses;
import pcp.xenon.XenonKernelPatchLoader;

/** Valida CF LDV/eFuse07 y reconstruye el kernel actualizado CF/CG. */
public final class XenonBootMilestone10SelfTest {

    private XenonBootMilestone10SelfTest() {
    }

    public static void main(String[] args) throws Exception {
        args = new String[]{"./src/pcp/TESTS/nand.bin", "./src/pcp/fuses.txt","./src/pcp/fuses.txt"};
        if (args.length != 3) {
            System.err.println("Uso: XenonBootMilestone10SelfTest <nand.bin> <fuses.txt> <boot-credentials.txt>");
            System.exit(2);
        }

        XenonFuses fuses = new XenonFuses();
        fuses.load(Path.of(args[1]));
        try (XenonBootCredentials credentials
                = XenonBootCredentials.load(Path.of(args[2]))) {
            XenonBootStrapper bootstrapper = XenonBootStrapper.load(Path.of(args[0]));
            XenonKernelPatchLoader.Result kernel
                    = bootstrapper.prepareKernel(credentials, fuses);

            int fuseLdv = pcp.xenon.XenonBootLockdownValidator.calculateFuseLdv(
                    fuses.getFuseSet(7));
            System.out.println("========================================");
            System.out.println(" PCP MILESTONE 10 - CF LDV / CF-CG");
            System.out.println("========================================");
            System.out.printf("CF version    : %04X%n", kernel.cfVersion);
            System.out.printf("Base version  : %04X%n", kernel.baseVersion);
            System.out.printf("Target version: %04X%n", kernel.targetVersion);
            System.out.printf("CF LDV        : %d%n", kernel.cfLdv);
            System.out.printf("eFuse07 LDV   : %d%n", fuseLdv);
            System.out.printf("Delta patches : %d%n", kernel.patchCount);
            System.out.printf("Kernel SHA1   : %s%n", kernel.newSha1);
            System.out.println("MILESTONE 10: PASS");
        }
    }
}
