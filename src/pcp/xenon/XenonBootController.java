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

    // Ventana de intercambio de configuración.
    public static final long CONFIG_BASE = XENON_BASE + 0x61008L;
    public static final long CONFIG_END = XENON_BASE + 0x61017L;

    public static final long CONFIG_STATUS = XENON_BASE + 0x61008L;
    public static final long CONFIG_COMMAND = XENON_BASE + 0x61010L;

    private final Map<Long, Byte> registers = new HashMap<>();

    private final DeviceWindow initWindow = new DeviceWindow(INIT_BASE, INIT_END);

    private final DeviceWindow configWindow = new DeviceWindow(CONFIG_BASE, CONFIG_END);

    private boolean trace;

    /**
     * Modelo OPCIONAL del secuenciador (apagado por defecto): el 1BL espera en
     * 0x34D0 a que (SEQUENCE_STATUS >> 32) & 0xF valga 0xE y se da por vencido
     * tras 64 lecturas de INIT_STATUS, lo que indica que el chip avanza ese
     * estado por sí solo. Con el modelo activo, cada lectura de SEQUENCE_STATUS
     * sube un estado hasta 0xE. Es una inferencia del código del 1BL, NO un
     * comportamiento de hardware verificado.
     */
    private boolean sequenceModel;
    private int sequenceState;

    public synchronized void setSequenceModel(boolean enabled) {
        sequenceModel = enabled;
        sequenceState = 0;
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
        bus.attachDevice(configWindow, CONFIG_BASE, CONFIG_END);

        System.out.println("[XENON-BOOT] Controlador MMIO conectado");
        System.out.printf("[XENON-BOOT] INIT   0x%016X - 0x%016X%n", INIT_BASE, INIT_END);
        System.out.printf("[XENON-BOOT] CONFIG 0x%016X - 0x%016X%n", CONFIG_BASE, CONFIG_END);
    }

    public void setTrace(boolean enabled) {
        trace = enabled;
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
