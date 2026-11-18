package pcp.TESTS;

import pcp.PPCSimulator;
import pcp.cpu.CPU;

/** Verifica el alias virtual retail de Xenos y las escrituras guest a la superficie. */
public final class XenonFramebufferVirtualMmioSelfTest {
    private XenonFramebufferVirtualMmioSelfTest() { }

    public static void main(String[] args) {
        PPCSimulator simulator = new PPCSimulator(1280, 720);
        simulator.setFbBase(PPCSimulator.FB_BASE_ELF);
        simulator.initializeXenonFramebuffer();

        CPU cpu = simulator.getCpu();
        cpu.setMsr(0); // real mode: valida el mapeo de ventana virtual sin alterar MMU.
        long xenos = 0x7FC80000L;
        cpu.writeMemoryWord(xenos + 0x6110, PPCSimulator.FB_BASE_ELF);
        require(cpu.readMemoryWord(xenos + 0x6110) == PPCSimulator.FB_BASE_ELF,
                "0x7FC80000 must alias physical Xenos at 0xEC800000");

        long pixel = PPCSimulator.FB_BASE_ELF;
        cpu.writeMemoryWord(pixel, 0x11223344L);
        require(simulator.getFrameBuffer().getGpuTiledBuffer()[0] == 0x11223344,
                "guest write must reach Xenos primary surface");
        require(simulator.getFrameBuffer().isDirty(),
                "guest framebuffer write must mark surface dirty");

        System.out.println("Xenon framebuffer virtual MMIO: ALL OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
