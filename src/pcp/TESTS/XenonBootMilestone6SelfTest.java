package pcp.TESTS;

import java.nio.file.Paths;
import pcp.PPCSimulator;
import pcp.xenon.XenonBootCredentials;
import pcp.xenon.XenonBootStrapper;
import pcp.xenon.XenonKernelPatchLoader;
import pcp.xenon.XenonKernelRuntime;
import pcp.xenon.XenonPostCodeTracker;

/** Verifica transferencia HV -> código original de xboxkrnl.exe. */
public final class XenonBootMilestone6SelfTest {

    private XenonBootMilestone6SelfTest() { }

    public static void main(String[] args) throws Exception {
        args = new String[]{"./src/pcp/TESTS/nand.bin","./src/pcp/fuses.txt"};
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Uso: XenonBootMilestone6SelfTest <nand.bin> <credentials.txt>");
        }

        XenonBootStrapper bootstrapper = XenonBootStrapper.load(Paths.get(args[0]));
        try (XenonBootCredentials credentials = XenonBootCredentials.load(Paths.get(args[1]))) {
            XenonKernelPatchLoader.Result kernel = bootstrapper.prepareKernel(credentials);
            PPCSimulator simulator = new PPCSimulator(800, 600);
            XenonKernelRuntime.Result result = XenonKernelRuntime.bootToKernelText(
                    simulator, kernel, Paths.get(args[1]));

            if (result.firstKernelPc < result.kernelTextStart
                    || result.firstKernelPc >= result.kernelTextEnd
                    || result.firstKernelInstruction == 0
                    || result.instructionsExecutedInKernel != 1
                    || result.instructionsToKernel <= 0
                    || result.postCodes.isEmpty()) {
                throw new AssertionError("HV/kernel execution handoff validation failed");
            }
            boolean post59 = false;
            boolean post5A = false;
            boolean post5B = false;
            for (XenonPostCodeTracker.Entry entry : result.postCodes) {
                if (entry.code == 0x59) post59 = true;
                if (entry.code == 0x5A) post5A = true;
                if (entry.code == 0x5B) post5B = true;
            }
            if (!post59 || !post5A || !post5B) {
                throw new AssertionError("Expected HV POST sequence 0x59-0x5B not observed");
            }

            System.out.printf("[M6] PASS: first xboxkrnl .text PC=%016X OP=%08X entry=%08X steps=%d POST=%d%n",
                    result.firstKernelPc, result.firstKernelInstruction,
                    result.kernelEntryPoint, result.instructionsToKernel,
                    result.postCodes.size());
        }
    }
}
