package pcp.TESTS;

import pcp.cpu.CPU;
import pcp.system.Bus;
import pcp.system.CacheController;
import pcp.system.Memory;

/** Pruebas deterministas de TBL/TBU y del underflow del Decrementer Xenon/PPC. */
public final class XenonTimebaseSelfTest {
    private XenonTimebaseSelfTest() { }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static int mfspr(int rt, int spr) {
        return (31 << 26) | (rt << 21) | ((spr & 31) << 16)
                | (((spr >>> 5) & 31) << 11) | (339 << 1);
    }

    public static void main(String[] args) {
        Memory memory = new Memory(1 << 20);
        Bus bus = new Bus(memory);
        CacheController cache = new CacheController(bus, 1 << 20, true);
        CPU cpu = new CPU(bus, cache);

        cpu.setTimeBase(0x1122334455667788L);
        cpu.execute(mfspr(3, 284), null);
        require(cpu.getGPRVal(3) == 0x55667788L, "mfspr TBL (284) debe devolver low32");
        cpu.execute(mfspr(4, 285), null);
        require(cpu.getGPRVal(4) == 0x11223344L, "mfspr TBU (285) debe devolver high32");
        cpu.execute(mfspr(5, 268), null);
        require(cpu.getGPRVal(5) == 0x55667788L, "mfspr TBL (268) debe devolver low32");
        cpu.execute(mfspr(6, 269), null);
        require(cpu.getGPRVal(6) == 0x11223344L, "mfspr TBU (269) debe devolver high32");

        cpu.setTimeBase(0x00000000FFFFFFFEL);
        cpu.setTbIncrement(1);
        cpu.tick();
        require(cpu.getTimeBase() == 0x00000000FFFFFFFFL, "Time Base debe incrementar low32");
        cpu.tick();
        require(cpu.getTimeBase() == 0x0000000100000000L, "Time Base debe propagar carry a TBU");

        cpu.setDec(1);
        cpu.tick();
        require(cpu.getDec() == 0, "DEC=1 debe decrementar a cero sin underflow");
        require(!cpu.isInterrupt(), "DEC no debe interrumpir al llegar a cero");
        cpu.tick();
        require(cpu.getDec() == -1, "DEC debe envolver 0 -> 0xFFFFFFFF");
        cpu.setMsr(cpu.getMsr() | CPU.MSR_EE);
        cpu.checkInterrupts();
        require(cpu.isInterrupt(), "DEC underflow debe solicitar excepción cuando MSR[EE]=1");

        cpu.setDec(0xFFFFFFFF);
        require(cpu.getDec() == -1, "mtspr DEC debe preservar patrones con bit 31 activo");
        System.out.println("Xenon Time Base/Decrementer: ALL OK");
    }
}
