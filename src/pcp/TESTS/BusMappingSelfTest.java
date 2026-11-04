package pcp.TESTS;

import pcp.gpu.Device;
import pcp.system.Bus;
import pcp.system.Memory;

/** Regressiones de direccionamiento físico, alias real-mode y MMIO big-endian. */
public final class BusMappingSelfTest {
    private BusMappingSelfTest() {}

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static final class RegisterDevice implements Device {
        private final byte[] regs = new byte[0x100];
        @Override public boolean containsAddress(long address) {
            return address >= 0 && address < regs.length;
        }
        @Override public byte read(long offset) { return regs[(int) offset]; }
        @Override public void write(long offset, byte value) { regs[(int) offset] = value; }
    }

    public static void main(String[] args) {
        Memory ram = new Memory(1 << 20);
        Bus bus = new Bus(ram);

        bus.writeWord(0x00000100L, 0x12345678);
        require(bus.readWord(0x80000100L) == 0x12345678,
                "RAM alias 0x80000000 no coincide con dirección física");
        bus.write(0x80000104L, (byte) 0xA5);
        require((bus.read(0x00000104L) & 0xFF) == 0xA5,
                "escritura byte por alias no visible en RAM física");

        RegisterDevice device = new RegisterDevice();
        bus.attachDevice(device, 0xEA001300L, 0xEA0013FFL);
        bus.writeWord(0xEA001307L, 0x40C0A55A);
        require(bus.readWord(0xEA001307L) == 0x40C0A55A,
                "MMIO word no conserva orden big-endian");
        require((bus.read(0xEA001307L) & 0xFF) == 0x40,
                "MMIO byte read incorrecto");

        // Ventana física Xenon de 64 bits (bit 63 activo): no debe truncarse
        // ni confundirse con una dirección negativa de Java.
        RegisterDevice bootDevice = new RegisterDevice();
        long bootBase = 0x8000020000000000L;
        bus.attachDevice(bootDevice, bootBase, bootBase + 0xFF);
        bus.writeWord(bootBase + 0x10, 0xA1B2C3D4);
        require(bus.readWord(bootBase + 0x10) == 0xA1B2C3D4,
                "MMIO/ROM window Xenon 64-bit no conserva el acceso big-endian");
        require((bus.read(bootBase + 0x10) & 0xFF) == 0xA1,
                "MMIO/ROM window Xenon 64-bit byte read incorrecto");

        System.out.println("Bus mapping/alias/MMIO endian/64-bit: ALL OK");
    }
}
