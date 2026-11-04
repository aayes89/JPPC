package pcp.TESTS;

import pcp.cpu.CPU;
import pcp.system.Bus;
import pcp.system.CacheController;
import pcp.system.Memory;

/** Pruebas deterministas de BATU/BATL y selección independiente I/D. */
public final class XenonMmuSelfTest {
    private XenonMmuSelfTest() { }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Memory memory = new Memory(1 << 20);
        Bus bus = new Bus(memory);
        CacheController cache = new CacheController(bus, 1 << 20, true);
        CPU cpu = new CPU(bus, cache);

        // BAT de 16 MiB: BL=0x7F; válido en supervisor; EA 0x80000000 -> PA 0.
        cpu.getMmu().setBAT(0, 0x80000000 | (0x7F << 2) | 0x2);
        cpu.getMmu().setBAT(1, 0x00000000);
        cpu.setMsr(cpu.getMsr() | CPU.MSR_IR);
        long instructionPA = cpu.getMmu().translateAddress(0x80123456L, false, true);
        require(instructionPA == 0x00123456L,
                "IBAT0 debe traducir por BEPI/BL/BRPN independientemente del segmento");

        // La ruta de datos usa DBAT, no IBAT.
        cpu.getMmu().setBAT(8, 0x90000000 | (0x7F << 2) | 0x2);
        cpu.getMmu().setBAT(9, 0x10000000);
        cpu.setMsr(cpu.getMsr() | CPU.MSR_DR);
        long dataPA = cpu.getMmu().translateAddress(0x901ABCDE, false, false);
        require(dataPA == 0x101ABCDEL, "DBAT0 debe traducir datos con su propio par");

        // Dirección fuera del bloque debe abortar, nunca convertirse en PA=0.
        boolean faulted = false;
        try {
            cpu.getMmu().translateAddress(0x81000000L, false, true);
        } catch (pcp.system.MMU.TranslationFault expected) {
            faulted = expected.getEffectiveAddress() == 0x81000000L && expected.isInstruction();
        }
        require(faulted, "fallo BAT debe abortar la traducción y conservar la EA");

        // Los registros BAT y SR se mantienen direccionables por índice.
        require(cpu.getMmu().getBAT(0) == (0x80000000 | (0x7F << 2) | 0x2), "lectura IBAT0U");
        cpu.getMmu().setSegmentRegister(3, 0x00123456);
        require(cpu.getMmu().getSegmentRegister(3) == 0x00123456, "lectura/escritura SR3");

        System.out.println("Xenon MMU BAT: ALL OK");
    }
}
