package pcp.gpu;

/**
 * SMC (System Management Controller) del Xenon, FIFO de mensajes en 0xEA001080 (según libxenon):
 *   +0x04 estado TX (bit 2 = listo para enviar)      +0x00..0x0C datos a enviar (4 palabras)
 *   +0x14 estado RX (bit 2 = hay mensaje)            +0x10 dato recibido
 * Modelo funcional mínimo: FIFO de cuatro palabras, respuesta inmediata y AVPACK VGA virtual.
 *
 * @author Slam
 */
public class XenonSmc implements Device {

    public static final long BASE = 0xEA001080L;
    private final int[] tx = new int[4];
    private final int[] rx = new int[4];
    private int txIdx = 0, rxIdx = 0;

    @Override
    public boolean containsAddress(long address) {
        return address >= 0 && address < 0x20;
    }

    @Override
    public synchronized int readWord(long offs) {
        return Integer.reverseBytes(readReg(offs)); // registros little-endian (libxenon usa lwbrx/stwbrx)
    }

    private int readReg(long offs) {
        switch ((int) (offs & ~3L)) {
            case 0x04: return 4;                       // listo para enviar
            case 0x14: return 4;                       // siempre hay respuesta disponible
            case 0x10: {
                int v = rx[rxIdx & 3];
                rxIdx++;
                return v;
            }
            default:
                return 0;
        }
    }

    @Override
    public synchronized void writeWord(long offs, int vBE) {
        int v = Integer.reverseBytes(vBE);
        switch ((int) (offs & ~3L)) {
            case 0x00: tx[txIdx++ & 3] = v; break;    // palabra del mensaje saliente
            case 0x04:
                if (v == 0 && txIdx >= 4) {            // mensaje completo: prepara respuesta
                    respond(tx[0] & 0xFF);
                    txIdx = 0;
                }
                break;
            case 0x14:
                if (v == 0) {
                    rxIdx = 0;
                }
                break;
            default: break;
        }
    }

    private void respond(int cmd) {
        java.util.Arrays.fill(rx, 0);

        // Las respuestas llegan como cuatro palabras de 32 bits que llenan
        // el buffer de 16 bytes del protocolo libxenon.
        if (cmd == 0x12) {                              // versión del SMC
            rx[0] = 0x00010000;
        } else if (cmd == 0x0F) {                       // xenon_smc_read_avpack()
            rx[0] = 0x00005B00;                        // byte 1 = AVPACK VGA virtual
        }
    }

    @Override
    public byte read(long offs) {
        return (byte) (readWord(offs & ~3L) >>> (24 - 8 * (int) (offs & 3)));
    }

    @Override
    public void write(long offs, byte v) {
        if ((offs & 3) == 3) {
            writeWord(offs & ~3L, v & 0xFF);
        }
    }
}
