package pcp.TESTS;

import java.nio.file.Path;
import pcp.xenon.XenonBootChainRuntime;
import pcp.xenon.XenonBootCredentials;

/** Prueba de integración: 1BL -> CB_A -> CB_B -> CD. */
public final class XenonBootMilestone8SelfTest {

    private XenonBootMilestone8SelfTest() {
    }

    public static void main(String[] args) throws Exception {
        args = new String[]{"./src/pcp/TESTS/nand.bin", "./src/pcp/fuses.txt","./src/pcp/fuses.txt","./src/pcp/TESTS/1bl.bin"};
        if (args.length != 4) {
            System.err.println("Uso: XenonBootMilestone8SelfTest <nand.bin> <fuses.txt> <boot-credentials.txt> <1bl.bin>");
            System.exit(2);
        }

        try (XenonBootCredentials credentials
                = XenonBootCredentials.load(Path.of(args[2]))) {
            XenonBootChainRuntime.Result result = XenonBootChainRuntime.bootToCD(
                    Path.of(args[0]), Path.of(args[1]), Path.of(args[3]), credentials);

            System.out.println("========================================");
            System.out.println(" PCP MILESTONE 08 - 1BL -> CB_A -> CB_B -> CD");
            System.out.println("========================================");
            System.out.printf("POST sequence : %s%n", result.postCodes);
            System.out.printf("Boot instrs   : %d%n", result.instructionsToEntry);
            System.out.printf("CD entry      : 0x%08X%n", result.entryAddress);
            System.out.printf("First PPC op  : 0x%08X%n", result.firstInstruction);
            System.out.printf("CD verified   : 0x%X bytes%n", result.verifiedBytes);
            System.out.printf("CD executed   : %d instructions%n", result.executedEntryInstructions);
            System.out.printf("PC after      : 0x%016X%n", result.pcAfterEntryInstructions);
            System.out.println("MILESTONE 08: PASS");
        }
    }
}
