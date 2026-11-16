package pcp.TESTS;

import java.nio.file.Path;
import pcp.xenon.XenonBootChainRuntime;
import pcp.xenon.XenonBootCredentials;
import pcp.xenon.XenonKernelRuntime;
import pcp.xenon.XenonPostCodeTracker;

/** Verifica el contexto CD->HV preservado y el handoff al .text de xboxkrnl. */
public final class XenonBootMilestone13SelfTest {

    private XenonBootMilestone13SelfTest() { }

    public static void main(String[] args) throws Exception {
        args = new String[]{"./src/pcp/TESTS/nand.bin", "./src/pcp/fuses.txt", "./src/pcp/fuses.txt", "./src/pcp/TESTS/1bl.bin"};

        if (args.length != 4) {
            throw new IllegalArgumentException(
                    "Uso: XenonBootMilestone13SelfTest <nand.bin> <fuses.txt> <boot-credentials.txt> <1bl.bin>");
        }

        try (XenonBootCredentials credentials
                = XenonBootCredentials.load(Path.of(args[2]))) {
            XenonBootChainRuntime.KernelStageResult boot
                    = XenonBootChainRuntime.bootToKernelText(
                            Path.of(args[0]), Path.of(args[1]), Path.of(args[3]), credentials);
            XenonKernelRuntime.Result kernel = boot.kernelRuntime;

            if (kernel.handoffHrmor == 0 || kernel.handoffR1 == 0
                    || (kernel.handoffMsr & 0x8000000000000000L) == 0) {
                throw new AssertionError("CD handoff context was not preserved");
            }
            if (kernel.firstKernelPc < kernel.kernelTextStart
                    || kernel.firstKernelPc >= kernel.kernelTextEnd
                    || kernel.firstKernelInstruction == 0
                    || kernel.instructionsExecutedInKernel != 1) {
                throw new AssertionError("HV did not transfer control to original xboxkrnl .text");
            }

            boolean post59 = false, post5A = false, post5B = false;
            boolean post5C = false, post5E = false, post5F = false;
            for (XenonPostCodeTracker.Entry entry : kernel.postCodes) {
                switch (entry.code) {
                    case 0x59: post59 = true; break;
                    case 0x5A: post5A = true; break;
                    case 0x5B: post5B = true; break;
                    case 0x5C: post5C = true; break;
                    case 0x5E: post5E = true; break;
                    case 0x5F: post5F = true; break;
                    default: break;
                }
            }
            if (!(post59 && post5A && post5B && post5C && post5E && post5F)) {
                throw new AssertionError("HV POST sequence 0x59-0x5F incomplete");
            }

            long physicalKernelEntry = kernel.handoffHrmor + kernel.firstKernelPc;
            int aliasOpcode = boot.kernelRuntime.simulator.getMemory()
                    .readMemoryWord(physicalKernelEntry);
            if (aliasOpcode != kernel.firstKernelInstruction) {
                throw new AssertionError(String.format(
                        "HRMOR kernel alias mismatch: %08X != %08X",
                        aliasOpcode, kernel.firstKernelInstruction));
            }

            System.out.printf("MILESTONE 13: PASS CD->HV->xboxkrnl PC=%016X OP=%08X "
                    + "HRMOR=%016X r1=%016X HVsteps=%d POST=%d%n",
                    kernel.firstKernelPc, kernel.firstKernelInstruction,
                    kernel.handoffHrmor, kernel.handoffR1,
                    kernel.instructionsToKernel, kernel.postCodes.size());
        }
    }
}
