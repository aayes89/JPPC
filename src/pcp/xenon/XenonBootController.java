/*
 * The MIT License
 *
 * Copyright 2026 Slam.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package pcp.xenon;

import java.util.HashMap;
import java.util.Map;
import pcp.gpu.Device;
import pcp.system.Bus;

/**
 * Controlador provisional de registros de arranque Xenon.
 *
 * Mantiene el contenido de los registros MMIO observados durante la ejecución
 * del 1BL. No inventa respuestas ni transiciones de hardware.
 *
 * Todos los accesos son big-endian.
 *
 * @author Slam
 */
public final class XenonBootController {

    public static final long XENON_BASE = 0x8000020000000000L;

    // Ventana de inicialización y secuencia de arranque.
    public static final long INIT_BASE = XENON_BASE + 0x38000L;
    public static final long INIT_END = XENON_BASE + 0x48027L;

    public static final long INIT_STATUS = XENON_BASE + 0x38000L;
    public static final long SEQUENCE_STATUS = XENON_BASE + 0x47100L;
    public static final long SEQUENCE_COMMAND = XENON_BASE + 0x47120L;
    public static final long AUX_CONTROL = XENON_BASE + 0x48020L;

    // Ventana de salida POST. 0x61010 recibe el código en el byte más
    // significativo del word escrito por el 1BL (0x10, 0x11, ...).
    // No es un command/config ring: es exclusivamente diagnóstico.
    public static final long POST_BASE = XENON_BASE + 0x61008L;
    public static final long POST_END = XENON_BASE + 0x61017L;
    public static final long SOC_WINDOW_BASE = XENON_BASE + 0x60000L;
    public static final long SOC_WINDOW_END = XENON_BASE + 0x61FFFL;
    public static final long BOOT_STATUS_REGISTER = XENON_BASE + 0x61000L;
    public static final long SOC_ID_REGISTER = XENON_BASE + 0xE1040000L;
    public static final long BOOT_STATUS_SEQUENCE_READY = 0x2000000000000000L;
    public static final long SOC_ID_XENON_VALUE = 0x0000002000000000L;
    public static final long POST_STATUS = XENON_BASE + 0x61008L;
    public static final long POST_OUTPUT = XENON_BASE + 0x61010L;

    /**
     * Alias de compatibilidad con código de diagnóstico anterior.
     * CONFIG_COMMAND no representa un comando de hardware.
     */
    @Deprecated
    public static final long CONFIG_BASE = POST_BASE;
    @Deprecated
    public static final long CONFIG_END = POST_END;
    @Deprecated
    public static final long CONFIG_STATUS = POST_STATUS;
    @Deprecated
    public static final long CONFIG_COMMAND = POST_OUTPUT;

    private final Map<Long, Byte> registers = new HashMap<>();
    private final XenonPostCodeTracker postTracker = new XenonPostCodeTracker();

    private final DeviceWindow initWindow = new DeviceWindow(INIT_BASE, INIT_END);

    private final DeviceWindow postWindow = new DeviceWindow(POST_BASE, POST_END);
    private final DeviceWindow socWindow = new DeviceWindow(SOC_WINDOW_BASE, SOC_WINDOW_END);
    private final DeviceWindow socIdWindow = new DeviceWindow(SOC_ID_REGISTER, SOC_ID_REGISTER + 7L);

    private boolean trace;

    /**
     * Modelo OPCIONAL del secuenciador (apagado por defecto): el 1BL espera en
     * 0x34D0 a que SEQUENCE_STATUS alcance 0xE. Con el modelo activo, cada
     * lectura avanza un estado. También se activa el bit 61 de SoC+0x61000,
     * consultado por CB_A durante su inicialización. Ambos comportamientos
     * son un perfil experimental inferido del código de arranque, no una
     * descripción verificada de todos los stepping Xenon.
     */
    private boolean sequenceModel;
    private int sequenceState;

    public synchronized void setSequenceModel(boolean enabled) {
        sequenceModel = enabled;
        sequenceState = 0;
        if (enabled) {
            // Perfil Xenon experimental: CB_A consulta el bit 61 de +0x61000
            // y CB_B valida el identificador leído desde +0xE1040000.
            writeLong(BOOT_STATUS_REGISTER, BOOT_STATUS_SEQUENCE_READY);
            writeLong(SOC_ID_REGISTER, SOC_ID_XENON_VALUE);
        } else {
            clearRegister64(BOOT_STATUS_REGISTER);
            clearRegister64(SOC_ID_REGISTER);
        }
    }

    public XenonBootController() {
    }

    /**
     * Registra las ventanas MMIO en el bus.
     */
    public void attachTo(Bus bus) {
        if (bus == null) {
            throw new IllegalArgumentException("Bus nulo");
        }

        bus.attachDevice(initWindow, INIT_BASE, INIT_END);
        bus.attachDevice(postWindow, POST_BASE, POST_END);
        bus.attachDevice(socWindow, SOC_WINDOW_BASE, SOC_WINDOW_END);
        bus.attachDevice(socIdWindow, SOC_ID_REGISTER, SOC_ID_REGISTER + 7L);

        System.out.println("[XENON-BOOT] Controlador MMIO conectado");
        System.out.printf("[XENON-BOOT] INIT 0x%016X - 0x%016X%n", INIT_BASE, INIT_END);
        System.out.printf("[XENON-BOOT] POST 0x%016X - 0x%016X (output=0x%016X)%n",
                POST_BASE, POST_END, POST_OUTPUT);
    }

    public void setTrace(boolean enabled) {
        trace = enabled;
    }

    /** Historial real de códigos escritos por el firmware en el puerto POST. */
    public XenonPostCodeTracker getPostTracker() {
        return postTracker;
    }

    /**
     * Lectura de un registro de 64 bits.
     *
     * El primer byte leído corresponde al byte más significativo.
     */
    public synchronized long readLong(long address) {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (readByte(address + i) & 0xFFL);
        }

        return value;
    }

    /**
     * Escritura de un registro de 64 bits.
     *
     * No produce efectos secundarios sobre otros registros.
     */
    public synchronized void writeLong(long address, long value) {
        for (int i = 0; i < 8; i++) {
            int shift = 56 - (i * 8);
            writeByte(address + i, (byte) (value >>> shift));
        }

        if (trace) {
            System.out.printf("[XENON-BOOT] WRITE64 EA=0x%016X VALUE=0x%016X%n", address, value);
        }
    }

    public synchronized int readRegister32(long address) {
        return ((readByte(address) & 0xFF) << 24)
                | ((readByte(address + 1) & 0xFF) << 16)
                | ((readByte(address + 2) & 0xFF) << 8)
                | (readByte(address + 3) & 0xFF);
    }

    public synchronized void writeRegister32(long address, int value) {
        writeByte(address, (byte) (value >>> 24));
        writeByte(address + 1, (byte) (value >>> 16));
        writeByte(address + 2, (byte) (value >>> 8));
        writeByte(address + 3, (byte) value);

        if (address == POST_OUTPUT) {
            int code = (value >>> 24) & 0xFF;
            XenonPostCodeTracker.Entry entry = postTracker.record(code);
            System.out.printf("[POST] #%03d 0x%02X %s%n",
                    entry.sequence, entry.code, entry.description);
            if (entry.hasViolation()) {
                System.out.printf("[POST-WARN] %s%n", entry.orderViolation);
            }
        }

        if (trace) {
            System.out.printf("[XENON-BOOT] WRITE32 EA=0x%016X VALUE=0x%08X%n", address, value);
        }
    }

    /**
     * Inicializa el almacenamiento del controlador. No altera la RAM, CPU ni
     * otros dispositivos.
     */
    public synchronized void reset() {
        registers.clear();
        sequenceState = 0;
        postTracker.reset();
        if (sequenceModel) {
            // reset() limpia el estado transitorio, no el perfil de hardware.
            writeLong(BOOT_STATUS_REGISTER, BOOT_STATUS_SEQUENCE_READY);
            writeLong(SOC_ID_REGISTER, SOC_ID_XENON_VALUE);
        }
    }

    /** Indica si una dirección pertenece a una ventana gestionada por este controlador. */
    public synchronized boolean ownsAddress(long address, int length) {
        if (length <= 0) return false;
        long end = address + length - 1L;
        return withinUnsigned(address, end, INIT_BASE, INIT_END)
                || withinUnsigned(address, end, POST_BASE, POST_END)
                || withinUnsigned(address, end, SOC_WINDOW_BASE, SOC_WINDOW_END)
                || withinUnsigned(address, end, SOC_ID_REGISTER, SOC_ID_REGISTER + 7L);
    }

    private static boolean withinUnsigned(long start, long end, long rangeStart, long rangeEnd) {
        return Long.compareUnsigned(start, rangeStart) >= 0
                && Long.compareUnsigned(end, rangeEnd) <= 0;
    }

    private void clearRegister64(long address) {
        for (int i = 0; i < 8; i++) registers.remove(address + i);
    }

    private byte readByte(long address) {
        return registers.getOrDefault(address, (byte) 0);
    }

    private void writeByte(long address, byte value) {
        if (value == 0) {
            registers.remove(address);
        } else {
            registers.put(address, value);
        }
    }

    private boolean isStatusRegister(long address) {
        return address == INIT_STATUS || address == SEQUENCE_STATUS || address == CONFIG_STATUS;
    }

    /**
     * Ventana Device que traduce offsets locales a direcciones físicas.
     */
    private final class DeviceWindow implements Device {

        private final long start;
        private final long end;

        DeviceWindow(long start, long end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public boolean containsAddress(long address) {
            return Long.compareUnsigned(address, 0) >= 0
                    && Long.compareUnsigned(address, end - start) <= 0;
        }

        @Override
        public byte read(long offset) {
            synchronized (XenonBootController.this) {
                return readByte(start + offset);
            }
        }

        @Override
        public void write(long offset, byte value) {
            synchronized (XenonBootController.this) {
                writeByte(start + offset, value);
            }
        }

        @Override
        public int readWord(long offset) {
            synchronized (XenonBootController.this) {
                long address = start + offset;
                int value = readRegister32(address);

                if (sequenceModel && address == SEQUENCE_STATUS) {
                    // palabra alta: bits 3:0 = estado (etapa 1 del 1BL espera 0xE) y bits 5:4 = 0b10
                    // cuando termina (etapa 2 del 1BL espera (estado >> 36) & 3 == 2).
                    value = sequenceState | (sequenceState >= 0xE ? 0x20 : 0);
                    if (sequenceState < 0xE) {
                        sequenceState++;
                    }
                }

                if (trace && isStatusRegister(address)) {
                    System.out.printf("[XENON-BOOT] STATUS READ32 EA=0x%016X VALUE=0x%08X%n", address, value);
                }

                return value;
            }
        }

        @Override
        public void writeWord(long offset, int value) {
            synchronized (XenonBootController.this) {
                long address = start + offset;
                writeRegister32(address, value);
            }
        }
    }

}
