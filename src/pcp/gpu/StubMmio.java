package pcp.gpu;

/**
 * Ventana MMIO "genérica" para periféricos que el firmware sondea pero que no hace falta modelar
 * (USB EHCI/OHCI, ethernet, audio, ATA...): los registros se comportan como RAM (lo escrito se lee de
 * vuelta) y, si el firmware lee 1000 veces seguidas la misma palabra sin que cambie, se da por completada
 * la operación de hardware (se limpia a 0 si estaba activa, o se pone a 0xFFFFFFFF si esperaba un "listo").
 * Es el enfoque habitual de los emuladores para hardware no imprescindible.
 *
 * @author Slam
 */
public class StubMmio implements Device {

    private final String name;
    private final int[] regs;
    private final boolean trace;
    private long lastRead = -1;
    private int same = 0;
    private int logged = 0;

    public StubMmio(String name, int sizeBytes, boolean trace) {
        this.name = name;
        this.regs = new int[sizeBytes / 4];
        this.trace = trace;
    }

    @Override
    public boolean containsAddress(long address) {
        return address >= 0 && address < regs.length * 4L;
    }

    private void log(String op, long offs, int v) {
        if (trace && logged < 300) {
            logged++;
            System.out.printf("[%s] %s +0x%04x = 0x%08x%n", name, op, offs, v);
        }
    }

    @Override
    public synchronized int readWord(long offs) {
        int i = (int) (offs >> 2);
        if (i < 0 || i >= regs.length) {
            return 0;
        }
        if (offs == lastRead) {
            if (++same >= 1000) {
                regs[i] = regs[i] != 0 ? 0 : 0xFFFFFFFF;
                same = 0;
            }
        } else {
            lastRead = offs;
            same = 0;
        }
        log("R", offs, regs[i]);
        return regs[i];
    }

    @Override
    public synchronized void writeWord(long offs, int v) {
        int i = (int) (offs >> 2);
        if (i >= 0 && i < regs.length) {
            log("W", offs, v);
            regs[i] = v;
        }
    }

    @Override
    public synchronized byte read(long offs) {
        return (byte) (readWord(offs & ~3L) >>> (24 - 8 * (int) (offs & 3)));
    }

    @Override
    public synchronized void write(long offs, byte v) {
        int i = (int) (offs >> 2);
        if (i < 0 || i >= regs.length) {
            return;
        }
        int sh = 24 - 8 * (int) (offs & 3);
        regs[i] = (regs[i] & ~(0xFF << sh)) | ((v & 0xFF) << sh);
    }
}
