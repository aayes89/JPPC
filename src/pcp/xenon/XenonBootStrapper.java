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
 * Fase implementada: cargar NAND RAW, extraer CB_A según la cabecera NAND,
 * derivar su clave RC4 mediante HMAC-SHA1 con la clave 1BL y conservar el
 * resultado para su posterior análisis. Esto no implica validar firma ni
 * integridad criptográfica.
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
    private XenonBootImage cbB;
    private XenonBootImage cd;
    private XenonBootImage ce;
    private XenonBootImage keyVault;
    private XenonBaseImageInspector.Result baseImageInfo;
    private XenonBootState bootState;

    private boolean cbAExtracted;
    private boolean cbADecrypted;
    private boolean cbBExtracted;
    private boolean cbBDecrypted;
    private boolean cbBIntegrityVerified;
    private boolean cdExtracted;
    private boolean cdDecrypted;
    private boolean cdIntegrityVerified;
    private boolean ceExtracted;
    private boolean ceDecrypted;
    private boolean ceIntegrityVerified;
    private boolean ceDecompressed;
    private boolean keyVaultExtracted;
    private boolean keyVaultDecrypted;
    private boolean keyVaultIntegrityVerified;

    public XenonBootStrapper(XenonNandParser nand) throws IOException {

        if (nand == null) {
            throw new IOException("NAND no especificada");
        }

        this.nand = nand;

        this.cbA = null;
        this.cbB = null;
        this.cd = null;
        this.ce = null;
        this.keyVault = null;
        this.baseImageInfo = null;
        this.bootState = new XenonBootState();

        this.cbAExtracted = false;
        this.cbADecrypted = false;
        this.cbBExtracted = false;
        this.cbBDecrypted = false;
        this.cbBIntegrityVerified = false;
        this.cdExtracted = false;
        this.cdDecrypted = false;
        this.cdIntegrityVerified = false;
        this.ceExtracted = false;
        this.ceDecrypted = false;
        this.ceIntegrityVerified = false;
        this.ceDecompressed = false;
        this.keyVaultExtracted = false;
        this.keyVaultDecrypted = false;
        this.keyVaultIntegrityVerified = false;
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
        int version = nand.getCBAVersion();
        int declaredSize = nand.getCBASize();
        if (data.length != declaredSize) {
            throw new IOException(String.format(
                    "Tamaño CB_A inesperado: extraído=0x%X declarado=0x%X",
                    data.length, declaredSize));
        }

        cbA = new XenonBootImage(XenonBootImage.Type.CB_A, data);
        cbA.setVersion(version);
        cbA.setDeclaredSize(declaredSize);
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
        if (oneBLKey == null || oneBLKey.length != 16) {
            throw new IOException("La clave 1BL debe tener exactamente 16 bytes");
        }

        if (cbADecrypted) {
            return cbA;
        }

        if (!cbAExtracted) {
            extractCBA();
        }

        System.out.println("[BOOTSTRAPPER] Descifrando CB_A (HMAC-SHA1 + RC4)...");
        byte[] encrypted = cbA.getOriginalData();
        byte[] decrypted;
        try {
            decrypted = XenonBootCrypto.decryptCBA(oneBLKey, encrypted);
        } catch (IllegalArgumentException ex) {
            throw new IOException("No se pudo descifrar CB_A: " + ex.getMessage(), ex);
        }

        cbA.setDecryptedData(decrypted);
        cbADecrypted = true;

        System.out.println("[BOOTSTRAPPER] CB_A procesado; firma e integridad aún no verificadas");
        System.out.printf("[BOOTSTRAPPER] SHA1: %s%n", XenonBootCrypto.toHex(XenonBootCrypto.sha1(decrypted)));
        java.util.Arrays.fill(decrypted, (byte) 0);

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

    /** Prepara CB_A usando credenciales proporcionadas por el usuario. */
    public synchronized XenonBootImage prepareCBA(XenonBootCredentials credentials) throws IOException {
        if (credentials == null) {
            throw new IOException("Credenciales de arranque no especificadas");
        }
        byte[] oneBlKey = credentials.getOneBlKey();
        try {
            return prepareCBA(oneBlKey);
        } finally {
            java.util.Arrays.fill(oneBlKey, (byte) 0);
        }
    }

    /** Extrae CB_B desde el offset contiguo calculado por CB_A. */
    public synchronized XenonBootImage extractCBB() throws IOException {
        if (cbBExtracted) return cbB;
        if (!cbADecrypted) {
            throw new IOException("CB_A debe estar descifrado antes de localizar CB_B");
        }
        System.out.println("[BOOTSTRAPPER] Localizando CB_B...");
        byte[] data = nand.extractCBB();
        int version = nand.getCBBVersion();
        int declaredSize = nand.getCBBSize();
        if (data.length != declaredSize) {
            throw new IOException(String.format(
                    "Tamaño CB_B inesperado: extraído=0x%X declarado=0x%X",
                    data.length, declaredSize));
        }
        cbB = new XenonBootImage(XenonBootImage.Type.CB_B, data);
        cbB.setVersion(version);
        cbB.setDeclaredSize(declaredSize);
        cbB.setEntryPoint(readU32BE(data, 8));
        cbBExtracted = true;
        java.util.Arrays.fill(data, (byte) 0);
        System.out.printf("[BOOTSTRAPPER] CB_B EXTRAÍDO: offset=0x%X version=%04X size=0x%X%n",
                nand.getCBBOffset(), version, declaredSize);
        return cbB;
    }

    /**
     * Descifra CB_B con la clave derivada de CB_A, CPU key y variante v1/v2.
     * El resultado sólo se acepta si coincide el XeCryptRotSumSha almacenado
     * en CB_A. Para CB_A 1928 (0x0788) se utiliza la derivación v2.
     */
    public synchronized XenonBootImage decryptCBB(byte[] cpuKey) throws IOException {
        if (cpuKey == null || cpuKey.length != 16) {
            throw new IOException("La CPU key debe tener exactamente 16 bytes");
        }
        if (cbBIntegrityVerified) return cbB;
        if (!cbADecrypted) {
            throw new IOException("CB_A debe estar descifrado antes de CB_B");
        }
        if (!cbBExtracted) extractCBB();

        byte[] cbAData = cbA.getDecryptedData();
        byte[] encrypted = cbB.getOriginalData();
        byte[] decrypted = null;
        try {
            System.out.printf("[BOOTSTRAPPER] Derivando clave CB_B (CB_A=%04X, esquema=%s)...%n",
                    cbA.getVersion(), XenonBootCrypto.usesCBBKeyV2(cbA.getVersion()) ? "v2" : "v1");
            decrypted = XenonBootCrypto.decryptCBB(cbAData, encrypted, cpuKey, cbA.getVersion());
            if (!XenonBootCrypto.verifyCBBIntegrity(cbAData, decrypted)) {
                throw new IOException("CB_B descifrado, pero XeCryptRotSumSha no coincide con el hash almacenado en CB_A");
            }
            cbB.setDecryptedData(decrypted);
            cbB.advanceState(XenonBootImage.State.INTEGRITY_VERIFIED);
            cbBDecrypted = true;
            cbBIntegrityVerified = true;
            System.out.println("[BOOTSTRAPPER] CB_B descifrado y XeCryptRotSumSha verificado");
            System.out.printf("[BOOTSTRAPPER] EntryPoint=0x%08X Next=CD@0x%X%n",
                    cbB.getEntryPoint(), nand.getCDOffset());
            return cbB;
        } catch (IllegalArgumentException ex) {
            throw new IOException("No se pudo descifrar CB_B: " + ex.getMessage(), ex);
        } finally {
            java.util.Arrays.fill(cbAData, (byte) 0);
            java.util.Arrays.fill(encrypted, (byte) 0);
            if (decrypted != null) java.util.Arrays.fill(decrypted, (byte) 0);
        }
    }

    /** Ejecuta CB_A y CB_B con credenciales externas del propietario. */
    public synchronized XenonBootImage prepareCBB(XenonBootCredentials credentials) throws IOException {
        if (credentials == null) throw new IOException("Credenciales de arranque no especificadas");
        prepareCBA(credentials);
        byte[] cpuKey = credentials.getCpuKey();
        try {
            return decryptCBB(cpuKey);
        } finally {
            java.util.Arrays.fill(cpuKey, (byte) 0);
        }
    }

    /** Extrae CD, etapa posterior a CB_B. */
    public synchronized XenonBootImage extractCD() throws IOException {
        if (cdExtracted) return cd;
        if (!cbBIntegrityVerified) {
            throw new IOException("CB_B debe superar RotSumSha antes de localizar CD");
        }
        System.out.println("[BOOTSTRAPPER] Localizando CD...");
        byte[] data = nand.extractCD();
        int version = nand.getCDVersion();
        int declaredSize = nand.getCDSize();
        if (data.length != declaredSize) {
            throw new IOException(String.format(
                    "Tamaño CD inesperado: extraído=0x%X declarado=0x%X",
                    data.length, declaredSize));
        }
        cd = new XenonBootImage(XenonBootImage.Type.CD, data);
        cd.setVersion(version);
        cd.setDeclaredSize(declaredSize);
        cd.setEntryPoint(readU32BE(data, 8));
        cdExtracted = true;
        java.util.Arrays.fill(data, (byte) 0);
        System.out.printf("[BOOTSTRAPPER] CD EXTRAÍDO: offset=0x%X version=%04X size=0x%X%n",
                nand.getCDOffset(), version, declaredSize);
        return cd;
    }

    /** Descifra CD y exige que su RotSumSha coincida con el valor de CB_B. */
    public synchronized XenonBootImage decryptCD() throws IOException {
        if (cdIntegrityVerified) return cd;
        if (!cbBIntegrityVerified) {
            throw new IOException("CB_B debe estar descifrado y verificado antes de CD");
        }
        if (!cdExtracted) extractCD();

        byte[] cbBData = cbB.getDecryptedData();
        byte[] encrypted = cd.getOriginalData();
        byte[] decrypted = null;
        try {
            System.out.println("[BOOTSTRAPPER] Descifrando CD (HMAC-SHA1 + RC4)...");
            decrypted = XenonBootCrypto.decryptCD(cbBData, encrypted);
            if (!XenonBootCrypto.verifyCDIntegrity(cbBData, decrypted)) {
                throw new IOException("CD descifrado, pero XeCryptRotSumSha no coincide con el hash almacenado en CB_B");
            }
            cd.setDecryptedData(decrypted);
            cd.advanceState(XenonBootImage.State.INTEGRITY_VERIFIED);
            cdDecrypted = true;
            cdIntegrityVerified = true;
            System.out.println("[BOOTSTRAPPER] CD descifrado y XeCryptRotSumSha verificado");
            System.out.printf("[BOOTSTRAPPER] EntryPoint=0x%08X CE@0x%X%n",
                    cd.getEntryPoint(), nand.getCEOffset());
            return cd;
        } catch (IllegalArgumentException ex) {
            throw new IOException("No se pudo descifrar CD: " + ex.getMessage(), ex);
        } finally {
            java.util.Arrays.fill(cbBData, (byte) 0);
            java.util.Arrays.fill(encrypted, (byte) 0);
            if (decrypted != null) java.util.Arrays.fill(decrypted, (byte) 0);
        }
    }

    /** Ejecuta CB_A, CB_B y CD con las credenciales del usuario. */
    public synchronized XenonBootImage prepareCD(XenonBootCredentials credentials) throws IOException {
        if (credentials == null) throw new IOException("Credenciales de arranque no especificadas");
        prepareCBB(credentials);
        return decryptCD();
    }

    /** Extrae CE incluyendo su padding físico de alineación a 16 bytes. */
    public synchronized XenonBootImage extractCE() throws IOException {
        if (ceExtracted) return ce;
        if (!cdIntegrityVerified) {
            throw new IOException("CD debe superar RotSumSha antes de localizar CE");
        }
        System.out.println("[BOOTSTRAPPER] Localizando CE...");
        byte[] data = nand.extractCE();
        int version = nand.getCEVersion();
        int declaredSize = nand.getCESize();
        if (data.length != nand.getCEStoredSize()) {
            throw new IOException(String.format(
                    "Tamaño CE almacenado inesperado: extraído=0x%X esperado=0x%X",
                    data.length, nand.getCEStoredSize()));
        }
        ce = new XenonBootImage(XenonBootImage.Type.CE, data);
        ce.setVersion(version);
        ce.setDeclaredSize(declaredSize);
        ceExtracted = true;
        java.util.Arrays.fill(data, (byte) 0);
        System.out.printf("[BOOTSTRAPPER] CE EXTRAÍDO: offset=0x%X version=%04X declared=0x%X stored=0x%X%n",
                nand.getCEOffset(), version, declaredSize, nand.getCEStoredSize());
        return ce;
    }

    /** Descifra CE y valida su RotSumSha contra el hash incluido en CD. */
    public synchronized XenonBootImage decryptCE() throws IOException {
        if (ceIntegrityVerified) return ce;
        if (!cdIntegrityVerified) {
            throw new IOException("CD debe estar descifrado y verificado antes de CE");
        }
        if (!ceExtracted) extractCE();
        byte[] cdData = cd.getDecryptedData();
        byte[] encrypted = ce.getOriginalData();
        byte[] decrypted = null;
        try {
            System.out.println("[BOOTSTRAPPER] Descifrando CE (HMAC-SHA1 + RC4)...");
            decrypted = XenonBootCrypto.decryptCE(cdData, encrypted);
            if (!XenonBootCrypto.verifyCEIntegrity(cdData, decrypted)) {
                throw new IOException("CE descifrado, pero XeCryptRotSumSha no coincide con el hash almacenado en CD");
            }
            long targetAddress = readU64BE(decrypted, 0x20);
            ce.setLoadAddress(targetAddress);
            ce.setDecryptedData(decrypted);
            ce.advanceState(XenonBootImage.State.INTEGRITY_VERIFIED);
            ceDecrypted = true;
            ceIntegrityVerified = true;
            System.out.printf("[BOOTSTRAPPER] CE descifrado y XeCryptRotSumSha verificado; target=0x%016X%n",
                    targetAddress);
            return ce;
        } catch (IllegalArgumentException ex) {
            throw new IOException("No se pudo descifrar CE: " + ex.getMessage(), ex);
        } finally {
            java.util.Arrays.fill(cdData, (byte) 0);
            java.util.Arrays.fill(encrypted, (byte) 0);
            if (decrypted != null) java.util.Arrays.fill(decrypted, (byte) 0);
        }
    }

    /** Reconstruye la imagen base de kernel/HV a partir de los bloques LZX de CE. */
    public synchronized XenonBootImage decompressCE() throws IOException {
        if (ceDecompressed) return ce;
        if (!ceIntegrityVerified) {
            throw new IOException("CE debe superar RotSumSha antes de descomprimirse");
        }
        byte[] decrypted = ce.getDecryptedData();
        try {
            XenonCeDecompressor.Result result = XenonCeDecompressor.decompress(decrypted);
            byte[] image = result.getImage();
            try {
                XenonBaseImageInspector.Result inspected = XenonBaseImageInspector.inspect(image);
                ce.setExpandedData(image);
                ce.advanceState(XenonBootImage.State.DECOMPRESSED);
                baseImageInfo = inspected;
                ceDecompressed = true;
                System.out.printf("[BOOTSTRAPPER] CE LZX descomprimido: target=0x%016X size=0x%X blocks=%d%n",
                        result.targetAddress, result.uncompressedSize, result.blockCount);
                System.out.printf("[BOOTSTRAPPER] HV NN version=%04X entry=0x%X; xboxkrnl.exe base=0x%08X entry=0x%08X size=0x%X sections=%d%n",
                        inspected.hypervisorVersion, inspected.hypervisorEntryOffset,
                        inspected.kernelImageBase, inspected.kernelEntryPoint,
                        inspected.kernelSizeOfImage, inspected.kernelSectionCount);
            } finally {
                java.util.Arrays.fill(image, (byte) 0);
            }
            return ce;
        } finally {
            java.util.Arrays.fill(decrypted, (byte) 0);
        }
    }

    public synchronized XenonBootImage prepareCE(XenonBootCredentials credentials) throws IOException {
        if (credentials == null) throw new IOException("Credenciales de arranque no especificadas");
        prepareCD(credentials);
        decryptCE();
        return decompressCE();
    }

    /** Extrae el KeyVault según el offset indicado en la cabecera NAND. */
    public synchronized XenonBootImage extractKeyVault() throws IOException {
        if (keyVaultExtracted) return keyVault;
        System.out.println("[BOOTSTRAPPER] Extrayendo KeyVault...");
        byte[] data = nand.extractKeyVault();
        keyVault = new XenonBootImage(XenonBootImage.Type.KEYVAULT, data);
        keyVault.setDeclaredSize(data.length);
        keyVaultExtracted = true;
        java.util.Arrays.fill(data, (byte) 0);
        System.out.printf("[BOOTSTRAPPER] KeyVault EXTRAÍDO: offset=0x%X size=0x4000%n",
                nand.getKeyVaultOffset());
        return keyVault;
    }

    /** Descifra KeyVault y valida su HMAC con la CPU key proporcionada. */
    public synchronized XenonBootImage decryptKeyVault(byte[] cpuKey) throws IOException {
        if (cpuKey == null || cpuKey.length != 16) {
            throw new IOException("La CPU key debe tener exactamente 16 bytes");
        }
        if (keyVaultIntegrityVerified) return keyVault;
        if (!keyVaultExtracted) extractKeyVault();
        byte[] encrypted = keyVault.getOriginalData();
        byte[] decrypted = null;
        try {
            System.out.println("[BOOTSTRAPPER] Descifrando KeyVault (HMAC-SHA1 + RC4)...");
            decrypted = XenonBootCrypto.decryptKeyVault(cpuKey, encrypted);
            keyVault.setDecryptedData(decrypted);
            keyVault.advanceState(XenonBootImage.State.INTEGRITY_VERIFIED);
            keyVaultDecrypted = true;
            keyVaultIntegrityVerified = true;
            System.out.println("[BOOTSTRAPPER] KeyVault descifrado y HMAC verificado");
            return keyVault;
        } catch (IllegalArgumentException ex) {
            throw new IOException("No se pudo descifrar KeyVault: " + ex.getMessage(), ex);
        } finally {
            java.util.Arrays.fill(encrypted, (byte) 0);
            if (decrypted != null) java.util.Arrays.fill(decrypted, (byte) 0);
        }
    }

    public synchronized XenonBootImage prepareKeyVault(XenonBootCredentials credentials) throws IOException {
        if (credentials == null) throw new IOException("Credenciales de arranque no especificadas");
        byte[] cpuKey = credentials.getCpuKey();
        try {
            return decryptKeyVault(cpuKey);
        } finally {
            java.util.Arrays.fill(cpuKey, (byte) 0);
        }
    }

    private static long readU64BE(byte[] data, int offset) {
        long value = 0;
        for (int i = 0; i < 8; i++) value = (value << 8) | (data[offset + i] & 0xFFL);
        return value;
    }

    private static long readU32BE(byte[] data, int offset) {
        return ((long) (data[offset] & 0xFF) << 24)
                | ((long) (data[offset + 1] & 0xFF) << 16)
                | ((long) (data[offset + 2] & 0xFF) << 8)
                | (long) (data[offset + 3] & 0xFF);
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

    public synchronized XenonBootImage getCBB() {
        return cbB;
    }

    public synchronized XenonBootImage getCD() {
        return cd;
    }

    public synchronized XenonBootImage getCE() {
        return ce;
    }

    public synchronized XenonBaseImageInspector.Result getBaseImageInfo() {
        return baseImageInfo;
    }

    public synchronized XenonBootImage getKeyVault() {
        return keyVault;
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


    public synchronized boolean isCBBExtracted() {
        return cbBExtracted;
    }

    public synchronized boolean isCBBDecrypted() {
        return cbBDecrypted;
    }

    public synchronized boolean isCBBIntegrityVerified() {
        return cbBIntegrityVerified;
    }


    public synchronized boolean isCDIntegrityVerified() {
        return cdIntegrityVerified;
    }


    public synchronized boolean isCEIntegrityVerified() {
        return ceIntegrityVerified;
    }

    public synchronized boolean isCEDecompressed() {
        return ceDecompressed;
    }

    public synchronized boolean isKeyVaultIntegrityVerified() {
        return keyVaultIntegrityVerified;
    }

    public synchronized boolean isReady() {
        return false;
    }
}
