package pcp.gpu;

/**
 * Modelo mínimo del task-file ATA expuesto por Xenon en 0xEA001300.
 * El canal arranca sin HDD ATA y con una unidad ATAPI virtual sin medio.
 * El estado BSY=0, DRDY=1 (0x40) permite que el firmware complete el sondeo
 * inicial. No se presenta capacidad de disco duro ni se inventan sectores.
 */
public final class XenonSata implements Device {
    public static final long BASE = 0xEA001300L;
    public static final int SIZE = 0x100;

    private static final int REG_DATA = 0;
    private static final int REG_ERROR_FEATURES = 1;
    private static final int REG_SECTOR_COUNT = 2;
    private static final int REG_LBA_LOW = 3;
    private static final int REG_LBA_MID = 4;
    private static final int REG_LBA_HIGH = 5;
    private static final int REG_DEVICE = 6;
    private static final int REG_STATUS_COMMAND = 7;

    private static final int STATUS_DRDY = 0x40;
    private static final int STATUS_DRQ = 0x08;
    private static final int STATUS_ERR = 0x01;

    private final int[] taskFile = new int[8];
    private int lastCommand;
    private int status = STATUS_DRDY;
    private int error;
    private int dataPhase;
    private int dataCursor;
    private int traceCount;
    private int senseKey = 0x02;
    private int senseAsc = 0x3A;
    private final byte[] data = new byte[512];

    public XenonSata() {
        taskFile[REG_DEVICE] = 0xE0;
    }

    @Override public boolean containsAddress(long address) {
        return address >= 0 && address < SIZE;
    }

    @Override public synchronized byte read(long offset) {
        int reg = (int) (offset & 7);
        trace("R8", offset, reg == REG_STATUS_COMMAND ? status : (reg == REG_ERROR_FEATURES ? error : (reg == REG_DATA ? 0 : taskFile[reg])));
        if (reg == REG_STATUS_COMMAND) return (byte) status;
        if (reg == REG_ERROR_FEATURES) return (byte) error;
        if (reg == REG_DATA) return (byte) readDataByte();
        return (byte) taskFile[reg];
    }

    @Override public synchronized void write(long offset, byte value) {
        int reg = (int) (offset & 7);
        int v = value & 0xFF;
        trace("W8", offset, v);
        if (reg == REG_STATUS_COMMAND) {
            lastCommand = v;
            executeCommand(v);
        } else if (reg == REG_ERROR_FEATURES) {
            // Features se conserva para trazabilidad, sin habilitar extensiones.
            taskFile[reg] = v;
        } else if (reg != REG_DATA) {
            taskFile[reg] = v;
        } else {
            writeDataByte(v);
        }
    }

    @Override public synchronized int readWord(long offset) {
        if ((offset & 7) == REG_DATA) {
            int lo = readDataByte() & 0xFF;
            int hi = readDataByte() & 0xFF;
            return lo | (hi << 8);
        }
        return Device.super.readWord(offset);
    }

    @Override public synchronized void writeWord(long offset, int value) {
        if ((offset & 7) == REG_DATA) {
            writeDataByte((value >>> 8) & 0xFF);
            writeDataByte(value & 0xFF);
        } else {
            Device.super.writeWord(offset, value);
        }
    }

    private void trace(String operation, long offset, int value) {
        if (Boolean.getBoolean("jppc.sata.trace") && traceCount < 64) {
            System.out.printf("[SATA] %s +0x%02X %s 0x%02X%n", operation, offset,
                    operation.equals("R8") ? "->" : "<-", value & 0xFF);
            traceCount++;
        }
    }

    private void executeCommand(int command) {
        dataCursor = 0;
        dataPhase = 0;
        error = 0;
        status = STATUS_DRDY;
        switch (command) {
            case 0xEC: // IDENTIFY DEVICE: no existe HDD ATA
                status = STATUS_DRDY;
                break;
            case 0xA1: // IDENTIFY PACKET DEVICE: ATAPI virtual
                buildIdentifyPacket();
                dataPhase = 1;
                status = STATUS_DRDY | STATUS_DRQ;
                break;
            case 0xA0: // PACKET: espera CDB de 12 bytes
                dataPhase = 2;
                dataCursor = 0;
                status = STATUS_DRDY | STATUS_DRQ;
                break;
            case 0xE7: case 0xEA: case 0xEF: // comandos de control inocuos
                status = STATUS_DRDY;
                break;
            default:
                error = 0x04; // ABRT
                status = STATUS_DRDY | STATUS_ERR;
                break;
        }
    }

    private void buildIdentifyPacket() {
        java.util.Arrays.fill(data, (byte) 0);
        // ATA IDENTIFY PACKET DEVICE: word 0 = ATAPI, packet de 12 bytes.
        putWord(0, 0x8580);
        putAtaString(10, "JPPC Virtual DVD-ROM", 20);
        putAtaString(23, "1.000", 8);
        putAtaString(27, "JPPC Virtual DVD-ROM", 40);
        putWord(0x7E, 0x0000); // sin medio/capacidad reportada
    }

    private void putWord(int word, int value) {
        int p = word * 2;
        data[p] = (byte) value;
        data[p + 1] = (byte) (value >>> 8);
    }

    private void putAtaString(int firstWord, String value, int bytes) {
        byte[] src = value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        for (int i = 0; i < bytes; i += 2) {
            int a = i < src.length ? src[i] & 0xFF : 0x20;
            int b = i + 1 < src.length ? src[i + 1] & 0xFF : 0x20;
            int p = firstWord * 2 + i;
            data[p] = (byte) b;
            data[p + 1] = (byte) a;
        }
    }

    private int readDataByte() {
        if ((dataPhase != 1 && dataPhase != 3) || dataCursor >= data.length) return 0xFF;
        int v = data[dataCursor++] & 0xFF;
        if (dataCursor >= data.length) {
            dataPhase = 0;
            status = STATUS_DRDY;
        }
        return v;
    }

    private void writeDataByte(int value) {
        if (dataPhase != 2 || dataCursor >= 12) return;
        data[dataCursor++] = (byte) value;
        if (dataCursor == 12) executePacket();
    }

    private void executePacket() {
        int opcode = data[0] & 0xFF;
        java.util.Arrays.fill(data, (byte) 0);
        dataCursor = 0;
        dataPhase = 0;
        error = 0x20; // sense key 2 (NOT READY)
        status = STATUS_DRDY | STATUS_ERR;
        if (opcode == 0x12) { // INQUIRY: unidad identificable aunque no tenga medio
            data[0] = 0x05; // CD/DVD device
            data[1] = (byte) 0x80; // removable
            data[2] = 0x05;
            data[3] = 0x02;
            data[4] = 31;
            putScsiString(8, 8, "JPPC");
            putScsiString(16, 16, "Virtual DVD-ROM");
            putScsiString(32, 4, "1.0");
            dataPhase = 3;
            dataCursor = 0;
            status = STATUS_DRDY | STATUS_DRQ;
        } else if (opcode == 0x03) { // REQUEST SENSE
            data[0] = 0x70;
            data[2] = (byte) senseKey;
            data[7] = 10;
            data[12] = (byte) senseAsc;
            dataPhase = 3;
            status = STATUS_DRDY | STATUS_DRQ;
        } else if (opcode == 0x00 || opcode == 0x28 || opcode == 0xA8) {
            // TEST UNIT READY / READ(10) / READ(12): NOT READY, medium absent.
            senseKey = 0x02;
            senseAsc = 0x3A;
            error = (senseKey << 4);
        }
    }

    private void putScsiString(int offset, int length, String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, data, offset, Math.min(length, bytes.length));
    }

    public synchronized int getLastCommand() { return lastCommand; }
}
