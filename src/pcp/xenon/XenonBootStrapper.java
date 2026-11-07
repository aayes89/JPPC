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
import java.nio.file.Path;

/**
 * Coordinador funcional de la cadena de arranque Xenon.
 *
 * Fase implementada: 1. Cargar NAND RAW. 2. Extraer CB_A. 3. Construir
 * XenonBootImage. 4. Descifrar CB_A mediante RC4. 5. Conservar el resultado
 * para su posterior análisis.
 *
 * Las fases de CB_B, KeyVault, RSA, HMAC específico de la cadena y
 * transferencia a CPU se incorporarán cuando estén identificados sus formatos y
 * parámetros.
 *
 * @author Slam
 */
public final class XenonBootStrapper {

    public static final int CB_VERSION_0788 = 0x0788;
    public static final long CB_A_SRAM_BASE = 0x02000000L;
    public static final long CB_A_FINAL_ENTRY = 0x030036C0L;

    private final XenonNandParser nand;
    private XenonBootImage cbA;
    private XenonBootState bootState;

    private boolean cbAExtracted;
    private boolean cbADecrypted;

    public XenonBootStrapper(XenonNandParser nand) throws IOException {

        if (nand == null) {
            throw new IOException("NAND no especificada");
        }

        this.nand = nand;

        this.cbA = null;
        this.bootState = new XenonBootState();

        this.cbAExtracted = false;
        this.cbADecrypted = false;
    }

    /**
     * Construye el bootstrapper directamente desde una NAND RAW.
     */
    public static XenonBootStrapper load(Path nandPath) throws IOException {
        XenonNandParser parser = XenonNandParser.load(nandPath);
        return new XenonBootStrapper(parser);
    }

    /**
     * Extrae CB_A de NAND y crea su representación.
     */
    public synchronized XenonBootImage extractCBA() throws IOException {
        if (cbAExtracted) {
            return cbA;
        }

        System.out.println("[BOOTSTRAPPER] Extrayendo CB_A...");

        byte[] data = nand.extractCBA();
        if (data.length != XenonNandParser.CB_SIZE) {
            throw new IOException("Tamaño CB_A inesperado: 0x" + Integer.toHexString(data.length));
        }

        cbA = new XenonBootImage(XenonBootImage.Type.CB_A, data);
        cbA.setVersion(CB_VERSION_0788);
        cbAExtracted = true;
        System.out.printf("[BOOTSTRAPPER] CB_A EXTRAÍDO: 0x%X bytes%n", data.length);
        return cbA;
    }

    /**
     * Descifra CB_A utilizando la clave RC4 proporcionada.
     *
     * La clave debe estar en el formato binario exacto esperado por RC4.
     */
    public synchronized XenonBootImage decryptCBA(byte[] oneBLKey) throws IOException {
        if (oneBLKey == null || oneBLKey.length == 0) {
            throw new IOException("Clave 1BL RC4 inválida");
        }

        if (cbADecrypted) {
            return cbA;
        }

        if (!cbAExtracted) {
            extractCBA();
        }

        System.out.println("[BOOTSTRAPPER] Descifrando CB_A...");
        byte[] encrypted = cbA.getOriginalData();
        byte[] decrypted = XenonBootCrypto.rc4(oneBLKey, encrypted);

        if (decrypted.length != encrypted.length) {
            throw new IOException("Longitud CB_A alterada durante descifrado");
        }

        cbA.setDecryptedData(decrypted);
        cbADecrypted = true;

        System.out.println("[BOOTSTRAPPER] CB_A DESCIFRADO");
        System.out.printf("[BOOTSTRAPPER] SHA1: %s%n", XenonBootCrypto.toHex(XenonBootCrypto.sha1(decrypted)));

        return cbA;
    }

    /**
     * Ejecuta la fase de preparación disponible.
     */
    public synchronized XenonBootImage prepareCBA(byte[] oneBLKey) throws IOException {
        extractCBA();
        decryptCBA(oneBLKey);
        return cbA;
    }

    /**
     * Construye el estado conocido del punto de transferencia de CB_A.
     *
     * No marca el estado como READY: faltan los valores reales de HRMOR, HID1,
     * MSR y la instalación efectiva de memoria y traducciones.
     */
    public synchronized XenonBootState buildBootState() throws IOException {

        if (!cbADecrypted) {
            throw new IOException("CB_A todavía no está descifrado");
        }

        bootState.reset();

        /*
         * SRR0 observado al final del procesamiento de CB_A.
         * Este valor procede del análisis del código CB_A.
         */
        bootState.setSRR0(CB_A_FINAL_ENTRY);

        /*
         * Punto de transferencia observado.
         */
        bootState.setEntryPoint(CB_A_FINAL_ENTRY);

        /*
         * Valores TLB observados en el código CB_A.
         * Se conservan como registros independientes.
         */
        bootState.setSPR(XenonBootState.SPR_TLB_CFG_0, XenonBootState.OBSERVED_TLB_CFG_0);
        bootState.setSPR(XenonBootState.SPR_TLB_CFG_1, XenonBootState.OBSERVED_TLB_CFG_1);
        bootState.setSPR(XenonBootState.SPR_TLB_CFG_2, XenonBootState.OBSERVED_TLB_CFG_2);

        bootState.addObservedCBATLBConfiguration();

        return bootState;
    }

    public XenonNandParser getNand() {
        return nand;
    }

    public synchronized XenonBootImage getCBA() {
        return cbA;
    }

    public synchronized XenonBootState getBootState() {
        return bootState;
    }

    public synchronized boolean isCBAExtracted() {
        return cbAExtracted;
    }

    public synchronized boolean isCBADecrypted() {
        return cbADecrypted;
    }

    public synchronized boolean isReady() {
        return false;
    }
}
