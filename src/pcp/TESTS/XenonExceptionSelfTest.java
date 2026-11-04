package pcp.TESTS;

import pcp.cpu.CPU;
import pcp.system.Bus;
import pcp.system.CacheController;
import pcp.system.Memory;

/** Verifica el estado arquitectónico mínimo de DSI/ISI. */
public final class XenonExceptionSelfTest {
    private XenonExceptionSelfTest() { }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Memory memory = new Memory(1 << 20);
        // Vectores presentes: takeException no debe detener la CPU por vector vacío.
        memory.writeMemoryWord(0x300, 0x48000300);
        memory.writeMemoryWord(0x400, 0x48000400);
        Bus bus = new Bus(memory);
        CPU cpu = new CPU(bus, new CacheController(bus, 1 << 20, true));

        cpu.setPc(0x81234560L);
        cpu.setMsr(CPU.MSR_EE | CPU.MSR_PR | CPU.MSR_DR | CPU.MSR_IR);
        cpu.raiseStorageException(false, 0xDEADBEEFL, true);
        require(cpu.getDar() == 0xDEADBEEFL, "DSI debe conservar la dirección efectiva en DAR");
        require(cpu.getDsisr() == 0x02000000L, "DSISR debe identificar escritura");
        require(cpu.getSrr0() == 0x81234560L, "DSI debe guardar PC en SRR0");
        require((cpu.getMsr() & (CPU.MSR_EE | CPU.MSR_PR | CPU.MSR_DR | CPU.MSR_IR)) == 0,
                "entrada DSI debe limpiar bits de ejecución/interrupción aplicables");

        cpu.setPc(0x80001000L);
        cpu.setMsr(CPU.MSR_EE | CPU.MSR_PR | CPU.MSR_IR);
        cpu.raiseStorageException(true, 0x80002000L, false);
        require(cpu.getSrr0() == 0x80001000L, "ISI debe guardar PC en SRR0");
        require(cpu.getDar() == 0xDEADBEEFL, "ISI no debe sobrescribir DAR");
        System.out.println("Xenon DSI/ISI exception state: ALL OK");
    }
}
