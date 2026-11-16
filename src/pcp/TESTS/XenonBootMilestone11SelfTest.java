package pcp.TESTS;

import java.nio.file.Path;
import pcp.xenon.XenonBootChainRuntime;
import pcp.xenon.XenonBootCredentials;

/** Ejecuta la cadena de arranque y valida la primera instrucción de xboxkrnl. */
public final class XenonBootMilestone11SelfTest {

    private XenonBootMilestone11SelfTest() {
    }

    public static void main(String[] args) throws Exception {
        args = new String[]{"./src/pcp/TESTS/nand.bin", "./src/pcp/fuses.txt","./src/pcp/fuses.txt","./src/pcp/TESTS/1bl.bin"};
        if (args.length != 4) {
            System.err.println("Uso: XenonBootMilestone11SelfTest <nand.bin> <fuses.txt> <boot-credentials.txt> <1bl.bin>");
            System.exit(2);
        }

        try (XenonBootCredentials credentials
                = XenonBootCredentials.load(Path.of(args[2]))) {
            XenonBootChainRuntime.KernelStageResult result
                    = XenonBootChainRuntime.bootToKernelText(
                            Path.of(args[0]), Path.of(args[1]), Path.of(args[3]), credentials);

            System.out.println("========================================");
            System.out.println(" PCP MILESTONE 11 - ORIGINAL XBOXKRNL");
            System.out.println("========================================");
            System.out.printf("CD -> CE     : verified 0x%X bytes%n", result.ceStage.ceSize);
            System.out.printf("CF/CG target : %04X%n", result.patchedKernel.targetVersion);
            System.out.printf("CF LDV       : %d%n", result.patchedKernel.cfLdv);
            System.out.printf("Kernel PC    : 0x%016X%n", result.kernelRuntime.firstKernelPc);
            System.out.printf("Kernel opcode: 0x%08X%n", result.kernelRuntime.firstKernelInstruction);
            System.out.printf("HV steps     : %d%n", result.kernelRuntime.instructionsToKernel);
            System.out.printf("Next PC      : 0x%016X%n", result.kernelRuntime.finalPc);
            System.out.println("MILESTONE 11: PASS");
        }
    }
}
