package pcp.gpu;

/**
 * Controlador serie/I2C del bus del sistema (0xEA001300). El firmware espera a que el bit 7 del byte +7
 * esté libre, escribe un comando en ese byte y espera a que los bits 7:6 valgan 01 ("comando terminado").
 * Modelo mínimo: al escribir el byte de comando el estado pasa a 0x40 inmediatamente; el resto son registros-RAM.
 *
 * @author Slam
 */
public class CmdDoneRegs implements Device {

    public static final long BASE = 0xEA001300L;
    private final byte[] r = new byte[0x20];

    public CmdDoneRegs() {
        r[7] = 0x48;
    }

    @Override
    public boolean containsAddress(long a) {
        return a >= 0 && a < r.length;
    }

    @Override
    public synchronized byte read(long offs) {
        return offs >= 0 && offs < r.length ? r[(int) offs] : 0;
    }

    @Override
    public synchronized void write(long offs, byte v) {
        if (offs < 0 || offs >= r.length) {
            return;
        }
        if (offs == 7) {
            r[7] = 0x48;          // estado: listo (bit 3), comando terminado (bits 7:6 = 01), sin dato RX pendiente
        } else {
            r[(int) offs] = v;
        }
    }

    @Override
    public synchronized int readWord(long offs) {
        int o = (int) (offs & ~3L);
        return ((read(o) & 0xFF) << 24) | ((read(o + 1) & 0xFF) << 16) | ((read(o + 2) & 0xFF) << 8) | (read(o + 3) & 0xFF);
    }

    @Override
    public synchronized void writeWord(long offs, int v) {
        int o = (int) (offs & ~3L);
        write(o, (byte) (v >>> 24));
        write(o + 1, (byte) (v >>> 16));
        write(o + 2, (byte) (v >>> 8));
        write(o + 3, (byte) v);
    }
}
