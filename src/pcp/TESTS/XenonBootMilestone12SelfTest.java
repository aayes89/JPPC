package pcp.TESTS;

import java.nio.file.Path;
import pcp.PPCSimulator;
import pcp.xenon.XenonBootChainRuntime;
import pcp.xenon.XenonBootCredentials;
import pcp.xenon.XenonKernelRuntime;

/** M12: ejecución sostenida de xboxkrnl y diagnóstico de su estado PPC. */
public final class XenonBootMilestone12SelfTest {

    private static final long DEFAULT_KERNEL_BUDGET = 1_000_000L;

    private XenonBootMilestone12SelfTest() { }

    public static void main(String[] args) throws Exception {
        args = new String[]{"./src/pcp/TESTS/nand.bin", "./src/pcp/fuses.txt","./src/pcp/fuses.txt","./src/pcp/TESTS/1bl.bin"};
        
        if (args.length == 4) {
            runNand(args);
            return;
        }
        if (args.length != 0) {
            System.err.println("Uso: XenonBootMilestone12SelfTest [nand.bin fuses.txt boot-credentials.txt 1bl.bin]");
            System.exit(2);
        }
        runSynthetic();
    }

    private static void runNand(String[] args) throws Exception {
        try (XenonBootCredentials credentials
                = XenonBootCredentials.load(Path.of(args[2]))) {
            XenonBootChainRuntime.KernelStageResult boot
                    = XenonBootChainRuntime.bootToKernelText(
                            Path.of(args[0]), Path.of(args[1]), Path.of(args[3]), credentials);
            XenonKernelRuntime.ExecutionResult result
                    = XenonKernelRuntime.continueKernel(
                            boot.kernelRuntime, DEFAULT_KERNEL_BUDGET);

            System.out.println("========================================");
            System.out.println(" PCP MILESTONE 12 - XBOXKRNL EXECUTION");
            System.out.println("========================================");
            System.out.printf("Executed      : %d%n", result.instructionsExecuted);
            System.out.printf("Stop reason   : %s%n", result.stopReason);
            System.out.printf("Initial PC    : %016X%n", result.initialPc);
            System.out.printf("Final PC      : %016X%n", result.finalPc);
            System.out.printf("MSR           : %016X%n", result.finalMsr);
            System.out.printf("SRR0/SRR1     : %016X / %016X%n", result.srr0, result.srr1);
            System.out.printf("DAR/DSISR     : %016X / %08X%n", result.dar, result.dsisr);
            System.out.printf("Illegal opcode: %08X%n", result.lastIllegalInstruction);
            for (int i = 0; i < result.gpr0To31.length; i++) {
                System.out.printf("r%-2d           : %016X%n", i, result.gpr0To31[i]);
            }
            System.out.println("Last execution trace:");
            for (XenonKernelRuntime.StepSnapshot step : result.trace) {
                System.out.printf("  %016X  %08X -> %016X  MSR=%016X LR=%016X CTR=%016X CR=%08X%n",
                        step.pc, step.opcode, step.nextPc, step.msr,
                        step.lr, step.ctr, step.cr);
                System.out.printf("    r1=%016X r12=%016X r13=%016X inKernel=%b%n",
                        step.r1, step.r12, step.r13, step.inKernelImage);
            }
            System.out.println("MILESTONE 12: "
                    + (result.stopReason == XenonKernelRuntime.StopReason.INSTRUCTION_LIMIT
                    ? "PASS" : "BLOCKED - " + result.stopReason));
        }
    }

    private static void runSynthetic() throws Exception {
        PPCSimulator simulator = new PPCSimulator(320, 240);
        long base = 0x1000L;
        byte[] code = {
            0x38, 0x60, 0x00, 0x01, // addi r3,r0,1
            0x38, (byte) 0x83, 0x00, 0x02, // addi r4,r3,2
            0x38, (byte) 0xA4, 0x00, 0x04, // addi r5,r4,4
            0x60, (byte) 0xA6, 0x00, 0x08 // ori r6,r5,8
        };
        simulator.getMemory().writeBlock(base, code);
        simulator.getCpu().setHaltOnSelfBranch(false);
        simulator.getCpu().setPc(base);

        XenonKernelRuntime.ExecutionResult result
                = XenonKernelRuntime.executeKernelWindow(
                        simulator, base, base + code.length,
                        base, base + code.length, 4);

        if (result.instructionsExecuted != 4 || result.halted
                || result.stopReason != XenonKernelRuntime.StopReason.INSTRUCTION_LIMIT
                || result.gpr0To10[3] != 1
                || result.gpr0To10[4] != 3
                || result.gpr0To10[5] != 7
                || result.gpr0To10[6] != 15
                || result.trace.size() != 4
                || result.trace.get(3).opcode != 0x60A60008
                || result.finalPc != base + code.length) {
            throw new AssertionError("M12 execution/trace validation failed");
        }

        simulator.reset();
        PPCSimulator loopSimulator = simulator;
        long loopPc = 0x2000L;
        loopSimulator.getMemory().writeBlock(loopPc,
                new byte[]{0x48, 0x00, 0x00, 0x00}); // b .
        loopSimulator.getCpu().setHaltOnSelfBranch(false);
        loopSimulator.getCpu().setPc(loopPc);
        XenonKernelRuntime.ExecutionResult loop
                = XenonKernelRuntime.executeKernelWindow(
                        loopSimulator, 0x3000L, 0x3100L,
                        0x3000L, 0x3100L, 100);
        if (loop.stopReason != XenonKernelRuntime.StopReason.REPEATED_EXTERNAL_CONTROL_LOOP
                || loop.instructionsExecuted != 3) {
            throw new AssertionError("M12 repeated external loop detector failed");
        }

        System.out.printf("M12 PASS: executed=%d PC=%08X r3=%d r4=%d r5=%d r6=%d trace=%d externalLoop=%s%n",
                result.instructionsExecuted, result.finalPc,
                result.gpr0To10[3], result.gpr0To10[4],
                result.gpr0To10[5], result.gpr0To10[6], result.trace.size(),
                loop.stopReason);
    }
}
