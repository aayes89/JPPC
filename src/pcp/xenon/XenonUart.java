package pcp.xenon;

import java.util.ArrayDeque;
import java.util.Deque;
import pcp.gpu.Console;
import pcp.gpu.Device;

/**
 * UART del Xenon (Xbox 360) en 0xEA001000, tal como la usa libxenon/LK:
 *   +0x10 RX dato (byte en los bits altos)   +0x14 TX dato (byte en los bits altos)
 *   +0x18 estado: 0x02000000 = TX libre, 0x01000000 = hay dato RX   +0x1C configuración
 * Lo transmitido se reenvía a la Console del emulador.
 *
 * @author Slam
 */
public class XenonUart implements Device {

    public static final long BASE = 0xEA001000L;
    private final Console console;
    private final Deque<Integer> rx = new ArrayDeque<>();
    private int config;

    public XenonUart(Console console) {
        this.console = console;
    }

    /** Encola un carácter de entrada (teclado). */
    public synchronized void feed(int c) {
        rx.add(c & 0xFF);
    }

    @Override
    public boolean containsAddress(long address) {
        return address >= 0 && address < 0x20;
    }

    @Override
    public synchronized int readWord(long offs) {
        switch ((int) (offs & ~3L)) {
            case 0x10: {
                Integer c = rx.poll();
                return c == null ? 0 : c << 24;
            }
            case 0x18:
                return 0x02000000 | (rx.isEmpty() ? 0 : 0x01000000);
            case 0x1C:
                return config;
            default:
                return 0;
        }
    }

    @Override
    public void writeWord(long offs, int v) {
        switch ((int) (offs & ~3L)) {
            case 0x14:
                console.write(0, (byte) (v >>> 24));
                break;
            case 0x1C:
                config = v;
                break;
            default:
                break;
        }
    }

    @Override
    public byte read(long offs) {
        return (byte) (readWord(offs & ~3L) >>> (24 - 8 * (int) (offs & 3)));
    }

    @Override
    public void write(long offs, byte v) {
        if ((offs & 3) == 0 && (offs & ~3L) == 0x14) { // byte alto = carácter
            console.write(0, v);
        }
    }
}
