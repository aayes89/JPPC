package pcp.TESTS;

import java.nio.file.Path;
import pcp.xenon.XenonBootChainRuntime;
import pcp.xenon.XenonBootCredentials;

/** Prueba de integración: CD carga y descifra CE dentro de la RAM emulada. */
public final class XenonBootMilestone9SelfTest {

    private XenonBootMilestone9SelfTest() {
    }

    public static void main(String[] args) throws Exception {
        args = new String[]{"./src/pcp/TESTS/nand.bin", "./src/pcp/fuses.txt","./src/pcp/fuses.txt","./src/pcp/TESTS/1bl.bin"};
        if (args.length != 4) {
            System.err.println("Uso: XenonBootMilestone9SelfTest <nand.bin> <fuses.txt> <boot-credentials.txt> <1bl.bin>");
            System.exit(2);
        }

        try (XenonBootCredentials credentials
                = XenonBootCredentials.load(Path.of(args[2]))) {
            XenonBootChainRuntime.CEStageResult result = XenonBootChainRuntime.bootToCE(
                    Path.of(args[0]), Path.of(args[1]), Path.of(args[3]), credentials);

            System.out.println("========================================");
            System.out.println(" PCP MILESTONE 09 - CD -> CE");
            System.out.println("========================================");
            System.out.printf("POST sequence : %s%n", result.postCodes);
            System.out.printf("Boot instrs   : %d%n", result.instructionsToCE);
            System.out.printf("CE buffer     : 0x%08X%n", result.ceBufferAddress);
            System.out.printf("CE size       : 0x%X%n", result.ceSize);
            System.out.printf("CD gate       : 0x%08X%n", result.cdGatePc);
            System.out.printf("Gate opcode   : 0x%08X%n", result.gateInstruction);
            System.out.printf("CE verified   : 0x%X bytes%n", result.ceSize);
            System.out.println("MILESTONE 09: PASS");
        }
    }
}
