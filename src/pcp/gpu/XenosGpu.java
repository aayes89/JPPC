package pcp.gpu;

/**
 * Registros mínimos de la GPU Xenos (Xbox 360) en 0xEC800000, los que usan
 * libxenon/LK para descubrir el framebuffer (bloque D1GRPH):
 *   +0x6110 dirección física del framebuffer   +0x6120 pitch (bytes)
 *   +0x6134 X_END (ancho)                      +0x6138 Y_END (alto)
 * El resto de registros se comporta como RAM (lee lo último escrito).
 *
 * @author Slam
 */
public class XenosGpu implements Device {

    public static final long BASE = 0xEC800000L;
    public static final int SIZE = 0x100000;
    private final int[] regs = new int[SIZE / 4];

    private final boolean le;
    private java.util.function.LongConsumer onSurface;

    /** Se invoca con la dirección física cuando el firmware cambia D1GRPH_PRIMARY_SURFACE_ADDRESS (0x6110). */
    public void setOnSurfaceChange(java.util.function.LongConsumer c) {
        onSurface = c;
    }

    private int d(int v) {
        return le ? Integer.reverseBytes(v) : v;
    }

    /**
     * @param littleEndian true para drivers que usan read32n/write32n (XeLL/libxenon: lwbrx/stwbrx);
     *                     LK lee los registros con lwz normal (big-endian).
     */
    public XenosGpu(long fbPhysBase, int width, int height, boolean littleEndian) {
        this.le = littleEndian;
        regs[0x6110 / 4] = d((int) fbPhysBase);
        regs[0x6120 / 4] = d((width + 31 & ~31) * 4);
        regs[0x6134 / 4] = d(width);
        regs[0x6138 / 4] = d(height);
        regs[0x0244 / 4] = d(0x000C0011);   // xenos_init_phase1 espera este valor
        regs[0x0210 / 4] = d(9);            // gpu_clock == 9
        regs[0x281C / 4] = d(2);            // bit de estado que XeLL sondea (listo)
    }

    @Override
    public boolean containsAddress(long address) {
        return address >= 0 && address < SIZE;
    }

    @Override
    public int readWord(long offs) {
        int i = (int) (offs >> 2);
        return i >= 0 && i < regs.length ? regs[i] : 0;
    }

    @Override
    public void writeWord(long offs, int v) {
        int i = (int) (offs >> 2);
        if (i >= 0 && i < regs.length) {
            regs[i] = v;

            // El motor ANA confirma la escritura limpiando el registro de dirección.
            // libxenon espera que 0x7950 deje de contener la dirección enviada.
            if (offs == 0x7954) {
                regs[0x7950 / 4] = 0;
            }

            // DC_LUT_AUTOFILL: bit 1 indica que el llenado automático terminó.
            if (offs == 0x64A0 && (v & 1) != 0) {
                regs[i] = 2;
            }

            // D1GRPH_PRIMARY_SURFACE_ADDRESS: notifica el cambio de superficie.
            if (offs == 0x6110 && onSurface != null) {
                long address = d(v) & 0xFFFFFFFFL;
                if (address != 0) {
                    onSurface.accept(address);
                }
            }
        }
    }

    @Override
    public byte read(long offs) {
        return (byte) (readWord(offs & ~3L) >>> (24 - 8 * (int) (offs & 3)));
    }

    @Override
    public void write(long offs, byte v) {
        int sh = 24 - 8 * (int) (offs & 3);
        int w = readWord(offs & ~3L);
        writeWord(offs & ~3L, (w & ~(0xFF << sh)) | ((v & 0xFF) << sh));
    }
}
