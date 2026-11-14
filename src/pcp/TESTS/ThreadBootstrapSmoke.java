package tests;

import pcp.PPCSimulator;
import pcp.cpu.CPU;
import pcp.xenon.TKernel;

/** Structural smoke test for the Xenon PPC thread trampoline. */
public final class ThreadBootstrapSmoke {
    public static void main(String[] args) {
        PPCSimulator sim = new PPCSimulator(16, 16);
        TKernel kernel = new TKernel(sim);
        CPU cpu = sim.getCpu();
        kernel.startThread(cpu, 0x81234560L, 0xCAFEBABEL,
                0x9FFF0000L, 0x9FFE0000L, 0x9FFD0000L);
        int[] expected = {0x3D808123, 0x618C4560, 0x7D8903A6, 0x4E800421, 0x4E800020};
        for (int i = 0; i < expected.length; i++) {
            int actual = (int) cpu.readMemoryWord(0x9FFD0000L + i * 4L);
            if (actual != expected[i]) {
                throw new AssertionError(String.format("stub[%d] expected=%08X actual=%08X", i, expected[i], actual));
            }
        }
        if (cpu.getPc() != 0x9FFD0000L || cpu.getLr() != 0x9FFE0000L
                || cpu.getGPRVal(1) != 0x9FFEFF00L
                || cpu.getGPRVal(3) != 0xCAFEBABEL) {
            throw new AssertionError("CPU thread context mismatch");
        }
        System.out.println("THREAD_BOOTSTRAP_OK");
    }
}
