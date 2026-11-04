package pcp.TESTS;

import pcp.cpu.CPU;
import pcp.system.Bus;
import pcp.system.CacheController;
import pcp.system.Memory;
import pcp.system.MMU;

/** Regresión de lookup e invalidación TLB por EA/VSID. */
public final class XenonTlbSelfTest {
    private XenonTlbSelfTest() { }
    private static void require(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }
    public static void main(String[] args) {
        Memory memory = new Memory(1 << 20);
        Bus bus = new Bus(memory);
        CPU cpu = new CPU(bus, new CacheController(bus, 1 << 20, true));
        MMU mmu = cpu.getMmu();
        long ea = 0x81234567L;
        int vsid = 0x123456;
        mmu.setSegmentRegister(8, vsid);
        mmu.setTLBEntry(3, (int)((ea >>> 12) & 0xFFFFF), 0x00042, vsid);
        cpu.setMsr(cpu.getMsr() | CPU.MSR_DR);
        long pa = mmu.translateAddress(ea, false, false);
        require(pa == 0x00042567L, "TLB debe conservar los 20 bits de VPN y el offset");
        mmu.invalidateTLBEntry(ea);
        boolean fault = false;
        try { mmu.translateAddress(ea, false, false); }
        catch (MMU.TranslationFault expected) { fault = expected.getEffectiveAddress() == ea; }
        require(fault, "tlbie debe invalidar únicamente la traducción EA/VSID correspondiente");
        cpu.setMsr(cpu.getMsr() | CPU.MSR_DR);
        mmu.setTLBEntry(4, (int)((ea >>> 12) & 0xFFFFF), 0x00042, vsid);
        mmu.invalidateTLB();
        fault = false;
        try { mmu.translateAddress(ea, false, false); }
        catch (MMU.TranslationFault expected) { fault = true; }
        require(fault, "invalidación global debe limpiar entradas TLB");
        System.out.println("Xenon TLB: ALL OK");
    }
}
