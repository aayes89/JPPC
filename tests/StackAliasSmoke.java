package tests;

import pcp.cpu.CPU;
import pcp.system.Bus;
import pcp.system.CacheController;
import pcp.system.Memory;

/** Verifica el alias real-mode usado por la pila PPC32 de Xenon. */
public final class StackAliasSmoke {
    public static void main(String[] args) {
        final int ramSize = 0x01000000; // 16 MiB bastan para probar el espejo.
        Memory memory = new Memory(ramSize);
        Bus bus = new Bus(memory);
        CPU cpu = new CPU(bus, new CacheController(bus, ramSize, true));
        cpu.setMsr(0); // PPC32: SF=0, MMU desactivada.

        long stackTop = 0x80000000L | (ramSize - 16L);
        cpu.setGPR(1, stackTop);
        long saveAddress = stackTop - 8;
        long expected = 0x81696B20L;
        cpu.writeMemoryWord(saveAddress, expected);
        long actual = cpu.readMemoryWord(saveAddress);

        if (actual != expected) {
            throw new AssertionError(String.format(
                    "Stack mirror failed: EA=%08X expected=%08X actual=%08X",
                    saveAddress, expected, actual));
        }
        System.out.printf("STACK_ALIAS_OK EA=%08X PA=%08X VALUE=%08X%n",
                saveAddress, saveAddress & 0x1FFFFFFFL, actual);
    }
}
