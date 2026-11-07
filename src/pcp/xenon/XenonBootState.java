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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Estado de ejecución para el bootstrap Xenon.
 *
 * Mantiene: - Registros especiales PPE. - MSR y registros de retorno. - HRMOR.
 * - Configuración TLB observada. - Segmentos de memoria pendientes de carga. -
 * Punto de entrada del siguiente componente.
 *
 * Esta clase no modifica directamente CPU ni Memory. Proporciona un modelo
 * intermedio para que XenonBootStrapper prepare y transfiera el estado al
 * hardware emulado.
 *
 * Los valores de TLB y HRMOR deben interpretarse según la implementación real
 * de la CPU Xenon.
 *
 * @author Slam
 */
public final class XenonBootState {

    // ---------------------------------------------------------
    // Registros observados durante el análisis de CB_A
    // ---------------------------------------------------------
    public static final int SPR_SRR0 = 0x01A;
    public static final int SPR_SRR1 = 0x01B;

    public static final int SPR_HRMOR = 0x139;
    public static final int SPR_HID1 = 0x3F1;

    public static final int SPR_TLB_CFG_0 = 0x3B3;
    public static final int SPR_TLB_CFG_1 = 0x3B4;
    public static final int SPR_TLB_CFG_2 = 0x3B5;

    public static final long OBSERVED_TLB_CFG_0 = 0x208L;
    public static final long OBSERVED_TLB_CFG_1 = 0x205L;
    public static final long OBSERVED_TLB_CFG_2 = 0x00000200000001B3L;
    public static final long OBSERVED_CB_A_ENTRY = 0x030036C0L;

    // ---------------------------------------------------------
    // Estado PPE
    // ---------------------------------------------------------
    private final Map<Integer, Long> sprRegisters = new LinkedHashMap<>();

    private long msr;

    private long srr0;

    private long srr1;

    private long hrmor;

    private long hid1;

    private boolean msrKnown;
    private boolean srr0Known;
    private boolean srr1Known;
    private boolean hrmorKnown;
    private boolean hid1Known;

    // ---------------------------------------------------------
    // Configuración de ejecución
    // ---------------------------------------------------------
    private long entryPoint;

    private boolean entryPointKnown;

    private boolean prepared;

    // ---------------------------------------------------------
    // Memoria
    // ---------------------------------------------------------
    private final List<MemorySegment> memorySegments = new ArrayList<>();

    // ---------------------------------------------------------
    // Traducciones
    // ---------------------------------------------------------
    private final List<TLBMapping> tlbMappings = new ArrayList<>();

    // ---------------------------------------------------------
    // Clases internas
    // ---------------------------------------------------------
    /**
     * Segmento de memoria pendiente de instalación.
     */
    public static final class MemorySegment {

        private final long address;
        private final byte[] data;
        private final String description;

        public MemorySegment(long address, byte[] data, String description) {
            if (data == null || data.length == 0) {
                throw new IllegalArgumentException("Segmento de memoria vacío");
            }

            if (description == null) {
                throw new IllegalArgumentException("Descripción nula");
            }

            this.address = address;
            this.data = data.clone();
            this.description = description;
        }

        public long getAddress() {
            return address;
        }

        public byte[] getData() {
            return data.clone();
        }

        public int getSize() {
            return data.length;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Entrada de traducción TLB.
     *
     * Los campos rawEntry0, rawEntry1 y rawEntry2 conservan valores observados
     * sin atribuirles una semántica todavía no confirmada.
     */
    public static final class TLBMapping {

        private final long rawEntry0;
        private final long rawEntry1;
        private final long rawEntry2;

        private final boolean semanticsVerified;

        public TLBMapping(long rawEntry0, long rawEntry1, long rawEntry2, boolean semanticsVerified) {
            this.rawEntry0 = rawEntry0;
            this.rawEntry1 = rawEntry1;
            this.rawEntry2 = rawEntry2;

            this.semanticsVerified = semanticsVerified;
        }

        public long getRawEntry0() {
            return rawEntry0;
        }

        public long getRawEntry1() {
            return rawEntry1;
        }

        public long getRawEntry2() {
            return rawEntry2;
        }

        public boolean isSemanticsVerified() {
            return semanticsVerified;
        }
    }

    // ---------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------
    public XenonBootState() {
    }

    // ---------------------------------------------------------
    // Registros PPE
    // ---------------------------------------------------------
    public synchronized void setSPR(int register, long value) {
        sprRegisters.put(register, value);

        switch (register) {
            case SPR_SRR0:
                srr0 = value;
                srr0Known = true;
                break;

            case SPR_SRR1:
                srr1 = value;
                msr = value;
                srr1Known = true;
                msrKnown = true;
                break;

            case SPR_HRMOR:
                hrmor = value;
                hrmorKnown = true;
                break;

            case SPR_HID1:
                hid1 = value;
                hid1Known = true;
                break;

            default:
                break;
        }
    }

    public synchronized long getSPR(int register) {
        return sprRegisters.getOrDefault(register, 0L);
    }

    public synchronized boolean hasSPR(int register) {
        return sprRegisters.containsKey(register);
    }

    public synchronized Map<Integer, Long> getSPRRegisters() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(sprRegisters));
    }

    public synchronized void setMSR(long value) {
        msr = value;
        msrKnown = true;
    }

    public synchronized long getMSR() {
        return msr;
    }

    public synchronized boolean hasMSR() {
        return msrKnown;
    }

    public synchronized void setSRR0(long value) {
        setSPR(SPR_SRR0, value);
    }

    public synchronized long getSRR0() {
        return srr0;
    }

    public synchronized boolean hasSRR0() {
        return srr0Known;
    }

    public synchronized void setSRR1(long value) {
        setSPR(SPR_SRR1, value);
    }

    public synchronized long getSRR1() {
        return srr1;
    }

    public synchronized boolean hasSRR1() {
        return srr1Known;
    }

    public synchronized void setHRMOR(long value) {
        setSPR(SPR_HRMOR, value);
    }

    public synchronized long getHRMOR() {
        return hrmor;
    }

    public synchronized boolean hasHRMOR() {
        return hrmorKnown;
    }

    public synchronized void setHID1(long value) {
        setSPR(SPR_HID1, value);
    }

    public synchronized long getHID1() {
        return hid1;
    }

    public synchronized boolean hasHID1() {
        return hid1Known;
    }

    // ---------------------------------------------------------
    // Punto de entrada
    // ---------------------------------------------------------
    public synchronized void setEntryPoint(long address) {

        entryPoint = address;
        entryPointKnown = true;
    }

    public synchronized long getEntryPoint() {
        return entryPoint;
    }

    public synchronized boolean hasEntryPoint() {
        return entryPointKnown;
    }

    // ---------------------------------------------------------
    // Segmentos de memoria
    // ---------------------------------------------------------
    public synchronized void addMemorySegment(long address, byte[] data, String description) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Segmento inválido");
        }

        memorySegments.add(new MemorySegment(address, data, description));
    }

    public synchronized List<MemorySegment> getMemorySegments() {
        return Collections.unmodifiableList(new ArrayList<>(memorySegments));
    }

    // ---------------------------------------------------------
    // TLB
    // ---------------------------------------------------------
    public synchronized void addTLBMapping(long rawEntry0, long rawEntry1, long rawEntry2, boolean semanticsVerified) {
        tlbMappings.add(new TLBMapping(rawEntry0, rawEntry1, rawEntry2, semanticsVerified));
    }

    public synchronized List<TLBMapping> getTLBMappings() {
        return Collections.unmodifiableList(new ArrayList<>(tlbMappings));
    }

    // ---------------------------------------------------------
    // Estado de preparación
    // ---------------------------------------------------------
    public synchronized boolean isPrepared() {
        return prepared;
    }

    public synchronized void markPrepared() throws IOException {

        if (!entryPointKnown) {
            throw new IOException("No se ha definido el punto de entrada");
        }

        prepared = true;
    }

    public synchronized void reset() {

        sprRegisters.clear();

        memorySegments.clear();

        tlbMappings.clear();

        msr = 0;
        srr0 = 0;
        srr1 = 0;
        hrmor = 0;
        hid1 = 0;

        entryPoint = 0;

        msrKnown = false;
        srr0Known = false;
        srr1Known = false;
        hrmorKnown = false;
        hid1Known = false;

        entryPointKnown = false;
        prepared = false;
    }

    // ---------------------------------------------------------
    // Configuración de referencia CB_A
    // ---------------------------------------------------------
    /**
     * Conserva los tres valores observados durante la instalación de la
     * configuración TLB de CB_A.
     *
     * No instala traducciones reales.
     */
    public synchronized void addObservedCBATLBConfiguration() {
        addTLBMapping(OBSERVED_TLB_CFG_0, OBSERVED_TLB_CFG_1, OBSERVED_TLB_CFG_2, false);
    }
}
