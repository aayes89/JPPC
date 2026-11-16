package pcp.TESTS;

import java.nio.file.Path;
import pcp.xenon.XenonBootChainRuntime;
import pcp.xenon.XenonBootCredentials;

/** Prueba de integración: ROM 1BL real -> NAND -> CB_A real. */
public final class XenonBootMilestone7SelfTest {

    private XenonBootMilestone7SelfTest() {
    }

    public static void main(String[] args) throws Exception {
        args = new String[]{"./src/pcp/TESTS/nand.bin", "./src/pcp/fuses.txt","./src/pcp/fuses.txt","./src/pcp/TESTS/1bl.bin"};
        if (args.length != 4) {
            System.err.println("Uso: XenonBootMilestone7SelfTest <nand.bin> <fuses.txt> <boot-credentials.txt> <1bl.bin>");
            System.exit(2);
        }

        try (XenonBootCredentials credentials
                = XenonBootCredentials.load(Path.of(args[2]))) {
            XenonBootChainRuntime.Result result = XenonBootChainRuntime.bootToCBA(
                    Path.of(args[0]), Path.of(args[1]), Path.of(args[3]), credentials);

            System.out.println("========================================");
            System.out.println(" PCP MILESTONE 07 - 1BL -> CB_A");
            System.out.println("========================================");
            System.out.printf("POST sequence : %s%n", result.postCodes);
            System.out.printf("1BL instrs    : %d%n", result.instructionsToCBA);
            System.out.printf("CB_A entry    : 0x%08X%n", result.cbaEntryAddress);
            System.out.printf("First PPC op  : 0x%08X%n", result.firstInstruction);
            System.out.printf("SRAM verified : 0x%X bytes%n", result.verifiedBytes);
            System.out.printf("CB_A executed : %d instructions%n", result.executedCBAInstructions);
            System.out.printf("PC after      : 0x%016X%n", result.pcAfterCBAInstructions);
            System.out.println("MILESTONE 07: PASS");
        }
    }
}
