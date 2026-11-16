/*
 * The MIT License
 *
 * Copyright 2026 Slam.
 */
package pcp.xenon;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import pcp.PPCSimulator;
import pcp.cpu.CPU;

/**
 * Ejecuta el 1BL real desde su ROM hasta la transferencia efectiva a CB_A.
 *
 * No carga CB_A mediante un loader host: el 1BL lee la NAND conectada al bus,
 * valida su cabecera, calcula HMAC, descifra RC4, verifica la firma y realiza
 * el salto PPC. El contenido de SRAM se compara después con la decodificación
 * independiente del contenedor para detectar discrepancias.
 */
public final class XenonBootChainRuntime {

    public static final long BOOT_SRAM_BASE = 0x02000000L;
    public static final long CD_LOAD_BASE = 0x04000000L;
    public static final int DEFAULT_VERIFY_BYTES = 0x100;
    public static final int DEFAULT_POST_HANDOFF_INSTRUCTIONS = 32;
    public static final long DEFAULT_MAX_BOOT_INSTRUCTIONS = 3_000_000L;

    public static final class CEStageResult {
        public final PPCSimulator simulator;
        public final long instructionsToCE;
        public final long ceBufferAddress;
        public final int ceSize;
        public final int cdGateOffset;
        public final long cdGatePc;
        public final int gateInstruction;
        public final List<Integer> postCodes;

        private CEStageResult(PPCSimulator simulator, long instructionsToCE,
                long ceBufferAddress, int ceSize, int cdGateOffset,
                long cdGatePc, int gateInstruction, List<Integer> postCodes) {
            this.simulator = simulator;
            this.instructionsToCE = instructionsToCE;
            this.ceBufferAddress = ceBufferAddress;
            this.ceSize = ceSize;
            this.cdGateOffset = cdGateOffset;
            this.cdGatePc = cdGatePc;
            this.gateInstruction = gateInstruction;
            this.postCodes = Collections.unmodifiableList(new ArrayList<>(postCodes));
        }
    }

    private XenonBootChainRuntime() {
    }

    public static final class Result {
        public final PPCSimulator simulator;
        public final long instructionsToEntry;
        public final long entryAddress;
        public final int entryPoint;
        public final int firstInstruction;
        public final int verifiedBytes;
        public final int executedEntryInstructions;
        public final long pcAfterEntryInstructions;
        /** Alias conservado para compatibilidad con el self-test M07. */
        @Deprecated public final long instructionsToCBA;
        @Deprecated public final long cbaEntryAddress;
        @Deprecated public final int cbaEntryPoint;
        @Deprecated public final int executedCBAInstructions;
        @Deprecated public final long pcAfterCBAInstructions;
        public final List<Integer> postCodes;

        private Result(PPCSimulator simulator, long instructionsToCBA,
                long cbaEntryAddress, int cbaEntryPoint, int firstInstruction,
                int verifiedBytes, int executedCBAInstructions,
                long pcAfterCBAInstructions, List<Integer> postCodes) {
            this.simulator = simulator;
            this.instructionsToEntry = instructionsToCBA;
            this.entryAddress = cbaEntryAddress;
            this.entryPoint = cbaEntryPoint;
            this.firstInstruction = firstInstruction;
            this.verifiedBytes = verifiedBytes;
            this.executedEntryInstructions = executedCBAInstructions;
            this.pcAfterEntryInstructions = pcAfterCBAInstructions;
            this.instructionsToCBA = instructionsToCBA;
            this.cbaEntryAddress = cbaEntryAddress;
            this.cbaEntryPoint = cbaEntryPoint;
            this.executedCBAInstructions = executedCBAInstructions;
            this.pcAfterCBAInstructions = pcAfterCBAInstructions;
            this.postCodes = Collections.unmodifiableList(new ArrayList<>(postCodes));
        }
    }

    public static Result bootToCBA(Path nandPath, Path fusesPath,
            Path oneBLPath, XenonBootCredentials credentials) throws IOException {
        return bootToCBA(nandPath, fusesPath, oneBLPath, credentials,
                DEFAULT_MAX_BOOT_INSTRUCTIONS, DEFAULT_VERIFY_BYTES,
                DEFAULT_POST_HANDOFF_INSTRUCTIONS);
    }

    public static Result bootToCBA(Path nandPath, Path fusesPath,
            Path oneBLPath, XenonBootCredentials credentials,
            long maxBootInstructions, int verifyBytes,
            int postHandoffInstructions) throws IOException {

        if (nandPath == null || fusesPath == null || oneBLPath == null
                || credentials == null) {
            throw new IOException("NAND, fuses, 1BL y credenciales son obligatorios");
        }
        if (maxBootInstructions <= 0 || verifyBytes < 4
                || (verifyBytes & 3) != 0 || postHandoffInstructions < 0) {
            throw new IOException("Límites de ejecución/verificación inválidos");
        }

        XenonBootStrapper bootstrapper = XenonBootStrapper.load(nandPath);
        XenonBootImage cbaImage = bootstrapper.prepareCBA(credentials);
        byte[] expected = cbaImage.getDecryptedData();
        try {
            if (expected == null || expected.length < 0x20) {
                throw new IOException("CB_A descifrado no contiene una cabecera válida");
            }

            int entryPoint = readU32BE(expected, 0x08);
            int declaredSize = readU32BE(expected, 0x0C);
            if (declaredSize != expected.length || entryPoint < 0x140
                    || (entryPoint & 3) != 0 || entryPoint > expected.length - verifyBytes) {
                throw new IOException(String.format(
                        "Cabecera CB_A inválida: entry=0x%X size=0x%X decoded=0x%X",
                        entryPoint, declaredSize, expected.length));
            }

            PPCSimulator simulator = new PPCSimulator(800, 600);
            simulator.loadNand(nandPath);
            simulator.loadFuses(fusesPath);
            simulator.setBootSequenceModel(true);
            simulator.getCpu().setHaltOnSelfBranch(false);
            simulator.load1BL(oneBLPath.toString());

            CPU cpu = simulator.getCpu();
            long target = BOOT_SRAM_BASE + Integer.toUnsignedLong(entryPoint);
            long executed = 0;
            while (cpu.getPc() != target && !cpu.isHalted()
                    && executed < maxBootInstructions) {
                cpu.step();
                executed++;
            }

            if (cpu.isHalted()) {
                throw new IOException(String.format(
                        "1BL se detuvo antes de CB_A: PC=0x%016X tras %d instrucciones",
                        cpu.getPc(), executed));
            }
            if (cpu.getPc() != target) {
                throw new IOException(String.format(
                        "Timeout esperando CB_A: PC=0x%016X esperado=0x%016X instrucciones=%d",
                        cpu.getPc(), target, executed));
            }

            List<XenonPostCodeTracker.Entry> history
                    = simulator.getBootController().getPostTracker().history();
            int[] expectedPosts = {
                0x10, 0x11, 0x12, 0x13, 0x14,
                0x15, 0x16, 0x17, 0x18, 0x19,
                0x1A, 0x1B, 0x1C, 0x1D, 0x1E
            };
            if (history.size() < expectedPosts.length) {
                throw new IOException("El 1BL no completó la secuencia POST 0x10–0x1E");
            }
            List<Integer> posts = new ArrayList<>();
            for (int i = 0; i < expectedPosts.length; i++) {
                int actual = history.get(i).code;
                posts.add(actual);
                if (actual != expectedPosts[i]) {
                    throw new IOException(String.format(
                            "Secuencia POST inválida en posición %d: 0x%02X esperado 0x%02X",
                            i, actual, expectedPosts[i]));
                }
            }

            for (int offset = 0; offset < verifyBytes; offset += 4) {
                int actual = simulator.getBus().readWord(target + offset);
                int expectedWord = readU32BE(expected, entryPoint + offset);
                if (actual != expectedWord) {
                    throw new IOException(String.format(
                            "CB_A SRAM mismatch +0x%X: actual=%08X esperado=%08X",
                            offset, actual, expectedWord));
                }
            }

            int firstInstruction = simulator.getBus().readWord(target);
            for (int i = 0; i < postHandoffInstructions; i++) {
                if (cpu.isHalted()) {
                    throw new IOException("CPU detenida durante la ejecución inicial de CB_A");
                }
                cpu.step();
            }
            if (cpu.isHalted()) {
                throw new IOException("CB_A provocó una excepción durante su bloque inicial");
            }
            long pcAfter = cpu.getPc();
            if (pcAfter < BOOT_SRAM_BASE || pcAfter >= BOOT_SRAM_BASE + 0x10000L) {
                throw new IOException(String.format(
                        "CB_A abandonó SRAM durante el bloque inicial: PC=0x%016X", pcAfter));
            }

            return new Result(simulator, executed, target, entryPoint,
                    firstInstruction, verifyBytes, postHandoffInstructions,
                    pcAfter, posts);
        } finally {
            if (expected != null) {
                java.util.Arrays.fill(expected, (byte) 0);
            }
        }
    }

    /**
     * Ejecuta el 1BL, CB_A y CB_B reales hasta la entrada de CD.
     * La imagen CD debe coincidir byte a byte con la versión descifrada y
     * validada independientemente mediante XenonBootStrapper.
     */
    public static Result bootToCD(Path nandPath, Path fusesPath,
            Path oneBLPath, XenonBootCredentials credentials) throws IOException {
        return bootToCD(nandPath, fusesPath, oneBLPath, credentials,
                20_000_000L, DEFAULT_VERIFY_BYTES,
                DEFAULT_POST_HANDOFF_INSTRUCTIONS);
    }

    public static Result bootToCD(Path nandPath, Path fusesPath,
            Path oneBLPath, XenonBootCredentials credentials,
            long maxBootInstructions, int verifyBytes,
            int postHandoffInstructions) throws IOException {

        if (nandPath == null || fusesPath == null || oneBLPath == null
                || credentials == null) {
            throw new IOException("NAND, fuses, 1BL y credenciales son obligatorios");
        }
        if (maxBootInstructions <= 0 || verifyBytes < 4
                || (verifyBytes & 3) != 0 || postHandoffInstructions < 0) {
            throw new IOException("Límites de ejecución/verificación inválidos");
        }

        XenonBootStrapper bootstrapper = XenonBootStrapper.load(nandPath);
        XenonBootImage cdImage = bootstrapper.prepareCD(credentials);
        byte[] expected = cdImage.getDecryptedData();
        try {
            if (expected == null || expected.length < 0x20) {
                throw new IOException("CD descifrado no contiene una cabecera válida");
            }

            int entryPoint = readU32BE(expected, 0x08);
            int declaredSize = readU32BE(expected, 0x0C);
            if (declaredSize != expected.length || entryPoint < 0x260
                    || (entryPoint & 3) != 0 || entryPoint > expected.length - verifyBytes) {
                throw new IOException(String.format(
                        "Cabecera CD inválida: entry=0x%X size=0x%X decoded=0x%X",
                        entryPoint, declaredSize, expected.length));
            }

            PPCSimulator simulator = new PPCSimulator(800, 600);
            simulator.loadNand(nandPath);
            simulator.loadFuses(fusesPath);
            simulator.setBootSequenceModel(true);
            simulator.getCpu().setHaltOnSelfBranch(false);
            simulator.load1BL(oneBLPath.toString());

            CPU cpu = simulator.getCpu();
            long target = CD_LOAD_BASE + Integer.toUnsignedLong(entryPoint);
            long executed = 0;
            while (cpu.getPc() != target && !cpu.isHalted()
                    && executed < maxBootInstructions) {
                cpu.step();
                executed++;
            }

            if (cpu.isHalted()) {
                throw new IOException(String.format(
                        "La cadena de arranque se detuvo antes de CD: PC=0x%016X tras %d instrucciones",
                        cpu.getPc(), executed));
            }
            if (cpu.getPc() != target) {
                throw new IOException(String.format(
                        "Timeout esperando CD: PC=0x%016X esperado=0x%016X instrucciones=%d",
                        cpu.getPc(), target, executed));
            }

            List<XenonPostCodeTracker.Entry> history
                    = simulator.getBootController().getPostTracker().history();
            int[] expectedPosts = {
                0x10, 0x11, 0x12, 0x13, 0x14,
                0x15, 0x16, 0x17, 0x18, 0x19,
                0x1A, 0x1B, 0x1C, 0x1D, 0x1E
            };
            if (history.size() < expectedPosts.length) {
                throw new IOException("El 1BL no completó la secuencia POST 0x10–0x1E");
            }
            List<Integer> posts = new ArrayList<>();
            for (int i = 0; i < expectedPosts.length; i++) {
                int actual = history.get(i).code;
                posts.add(actual);
                if (actual != expectedPosts[i]) {
                    throw new IOException(String.format(
                            "Secuencia POST inválida en posición %d: 0x%02X esperado 0x%02X",
                            i, actual, expectedPosts[i]));
                }
            }

            for (int offset = 0; offset < verifyBytes; offset += 4) {
                int actual = simulator.getBus().readWord(target + offset);
                int expectedWord = readU32BE(expected, entryPoint + offset);
                if (actual != expectedWord) {
                    throw new IOException(String.format(
                            "CD RAM mismatch +0x%X: actual=%08X esperado=%08X",
                            offset, actual, expectedWord));
                }
            }

            int firstInstruction = simulator.getBus().readWord(target);
            for (int i = 0; i < postHandoffInstructions; i++) {
                if (cpu.isHalted()) {
                    throw new IOException("CPU detenida durante la ejecución inicial de CD");
                }
                cpu.step();
            }
            if (cpu.isHalted()) {
                throw new IOException("CD provocó una excepción durante su bloque inicial");
            }
            long pcAfter = cpu.getPc();
            if (pcAfter < CD_LOAD_BASE || pcAfter >= CD_LOAD_BASE + expected.length) {
                throw new IOException(String.format(
                        "CD abandonó su imagen durante el bloque inicial: PC=0x%016X", pcAfter));
            }

            return new Result(simulator, executed, target, entryPoint,
                    firstInstruction, verifyBytes, postHandoffInstructions,
                    pcAfter, posts);
        } finally {
            if (expected != null) {
                java.util.Arrays.fill(expected, (byte) 0);
            }
        }
    }

    /**
     * Ejecuta 1BL -> CB_A -> CB_B -> CD hasta que CD termina de descifrar CE.
     * Se detiene justo antes de la compuerta de validación posterior, compara
     * el buffer completo contra el descifrado software independiente y no
     * fuerza la continuación cuando la compuerta reporta una discrepancia.
     */
    public static CEStageResult bootToCE(Path nandPath, Path fusesPath,
            Path oneBLPath, XenonBootCredentials credentials) throws IOException {
        return bootToCE(nandPath, fusesPath, oneBLPath, credentials,
                80_000_000L);
    }

    public static CEStageResult bootToCE(Path nandPath, Path fusesPath,
            Path oneBLPath, XenonBootCredentials credentials,
            long maxBootInstructions) throws IOException {

        if (nandPath == null || fusesPath == null || oneBLPath == null
                || credentials == null) {
            throw new IOException("NAND, fuses, 1BL y credenciales son obligatorios");
        }
        if (maxBootInstructions <= 0) {
            throw new IOException("Límite de instrucciones inválido");
        }

        XenonBootStrapper bootstrapper = XenonBootStrapper.load(nandPath);
        XenonBootImage cdImage = bootstrapper.prepareCD(credentials);
        XenonBootImage ceImage = bootstrapper.prepareCE(credentials);
        byte[] cdExpected = cdImage.getDecryptedData();
        byte[] ceExpected = ceImage.getDecryptedData();
        try {
            if (cdExpected == null || ceExpected == null
                    || cdExpected.length < 0x20 || ceExpected.length < 0x20) {
                throw new IOException("CD/CE descifrado incompleto");
            }

            byte[] gateSignature = {
                (byte) 0x81, 0x7B, 0x00, 0x20,
                0x7F, 0x0B, (byte) 0xC0, 0x40,
                0x41, (byte) 0x9A, 0x00, 0x08,
                0x00, 0x00, 0x00, 0x00
            };
            int gateOffset = findUnique(cdExpected, gateSignature);
            if (gateOffset < 0 || (gateOffset & 3) != 0) {
                throw new IOException("No se encontró una compuerta CE única en CD");
            }

            PPCSimulator simulator = new PPCSimulator(800, 600);
            simulator.loadNand(nandPath);
            simulator.loadFuses(fusesPath);
            simulator.setBootSequenceModel(true);
            simulator.getCpu().setHaltOnSelfBranch(false);
            simulator.load1BL(oneBLPath.toString());

            CPU cpu = simulator.getCpu();
            long gatePc = CD_LOAD_BASE + Integer.toUnsignedLong(gateOffset);
            long executed = 0;
            while (cpu.getPc() != gatePc && !cpu.isHalted()
                    && executed < maxBootInstructions) {
                cpu.step();
                executed++;
            }

            if (cpu.isHalted()) {
                throw new IOException(String.format(
                        "La cadena se detuvo antes de la compuerta CE: PC=0x%016X tras %d instrucciones",
                        cpu.getPc(), executed));
            }
            if (cpu.getPc() != gatePc) {
                throw new IOException(String.format(
                        "Timeout esperando CE: PC=0x%016X esperado=0x%016X instrucciones=%d",
                        cpu.getPc(), gatePc, executed));
            }

            long ceBuffer = cpu.getGPRVal(29);
            if (ceBuffer < 0 || ceBuffer > PPCSimulator.MEMORY_SIZE - ceExpected.length) {
                throw new IOException(String.format("Buffer CE fuera de RAM: 0x%016X", ceBuffer));
            }
            int verified = 0;
            for (int offset = 0; offset < ceExpected.length; offset += 4) {
                int actual = simulator.getBus().readWord(ceBuffer + offset);
                int expectedWord = readU32BE(ceExpected, offset);
                if (actual != expectedWord) {
                    throw new IOException(String.format(
                            "CE descifrado no coincide en +0x%X: actual=%08X esperado=%08X",
                            offset, actual, expectedWord));
                }
                verified += 4;
            }

            List<XenonPostCodeTracker.Entry> history
                    = simulator.getBootController().getPostTracker().history();
            int[] expectedPosts = {
                0x10, 0x11, 0x12, 0x13, 0x14,
                0x15, 0x16, 0x17, 0x18, 0x19,
                0x1A, 0x1B, 0x1C, 0x1D, 0x1E
            };
            if (history.size() < expectedPosts.length) {
                throw new IOException("El 1BL no completó la secuencia POST 0x10–0x1E");
            }
            List<Integer> posts = new ArrayList<>();
            for (int i = 0; i < expectedPosts.length; i++) {
                int actual = history.get(i).code;
                posts.add(actual);
                if (actual != expectedPosts[i]) {
                    throw new IOException(String.format(
                            "Secuencia POST inválida en posición %d: 0x%02X esperado 0x%02X",
                            i, actual, expectedPosts[i]));
                }
            }

            return new CEStageResult(simulator, executed, ceBuffer,
                    ceExpected.length, gateOffset, gatePc,
                    simulator.getBus().readWord(gatePc), posts);
        } finally {
            if (cdExpected != null) java.util.Arrays.fill(cdExpected, (byte) 0);
            if (ceExpected != null) java.util.Arrays.fill(ceExpected, (byte) 0);
        }
    }

    public static final class KernelStageResult {
        public final CEStageResult ceStage;
        public final XenonKernelPatchLoader.Result patchedKernel;
        public final XenonKernelRuntime.Result kernelRuntime;

        private KernelStageResult(CEStageResult ceStage,
                XenonKernelPatchLoader.Result patchedKernel,
                XenonKernelRuntime.Result kernelRuntime) {
            this.ceStage = ceStage;
            this.patchedKernel = patchedKernel;
            this.kernelRuntime = kernelRuntime;
        }
    }

    /**
     * Cadena integrada hasta ejecutar la primera instrucción de xboxkrnl.exe.
     * 1BL/CB_A/CB_B/CD se ejecutan en PPC; CE se toma del buffer real de RAM,
     * se descomprime, CF/CG se reconstruyen y el HV transfiere control al kernel.
     */
    public static KernelStageResult bootToKernelText(Path nandPath,
            Path fusesPath, Path oneBLPath,
            XenonBootCredentials credentials) throws IOException {

        CEStageResult ceStage = bootToCE(nandPath, fusesPath, oneBLPath, credentials);
        byte[] guestCe = new byte[ceStage.ceSize];
        byte[] baseImage = null;
        byte[] oneBlKey = null;
        try {
            for (int offset = 0; offset < guestCe.length; offset += 4) {
                int word = ceStage.simulator.getBus().readWord(ceStage.ceBufferAddress + offset);
                guestCe[offset] = (byte) (word >>> 24);
                guestCe[offset + 1] = (byte) (word >>> 16);
                guestCe[offset + 2] = (byte) (word >>> 8);
                guestCe[offset + 3] = (byte) word;
            }

            XenonCeDecompressor.Result expanded = XenonCeDecompressor.decompress(guestCe);
            baseImage = expanded.getImage();
            XenonBaseImageInspector.Result baseInfo = XenonBaseImageInspector.inspect(baseImage);

            XenonNandParser nand = XenonNandParser.load(nandPath);
            XenonFuses fuses = new XenonFuses();
            fuses.load(fusesPath);
            oneBlKey = credentials.getOneBlKey();
            XenonBootLockdownValidator.Result lockdown
                    = XenonBootLockdownValidator.validate(nand, oneBlKey, fuses);
            if (!lockdown.matches) {
                throw new IOException(String.format(
                        "CF LDV incompatible: NAND=%d eFuse07=%d",
                        lockdown.cfLdv, lockdown.fuseLdv));
            }

            XenonKernelPatchLoader.Result patched
                    = XenonKernelPatchLoader.apply(nand, oneBlKey, baseImage);
            if (patched.cfLdv != lockdown.cfLdv) {
                throw new IOException("LDV CF inconsistente entre validador y reconstrucción CF/CG");
            }

            System.out.printf("[M11] CE guest buffer decompressed: target=0x%016X size=0x%X HV=%04X kernel=%08X%n",
                    expanded.targetAddress, expanded.uncompressedSize,
                    baseInfo.hypervisorVersion, baseInfo.kernelImageBase);
            System.out.printf("[M11] CF/CG applied: LDV=%d patches=%d SHA1=%s%n",
                    patched.cfLdv, patched.patchCount, patched.newSha1);

            XenonKernelRuntime.Result runtime = XenonKernelRuntime.bootToKernelText(
                    ceStage.simulator, patched, fusesPath);
            return new KernelStageResult(ceStage, patched, runtime);
        } finally {
            wipe(guestCe);
            wipe(baseImage);
            wipe(oneBlKey);
        }
    }

    private static int findUnique(byte[] data, byte[] pattern) throws IOException {
        int found = -1;
        for (int i = 0; i <= data.length - pattern.length; i++) {
            boolean match = true;
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                if (found >= 0) {
                    throw new IOException("La firma de compuerta CE aparece más de una vez en CD");
                }
                found = i;
            }
        }
        return found;
    }

    private static int readU32BE(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    private static void wipe(byte[] data) {
        if (data != null) java.util.Arrays.fill(data, (byte) 0);
    }

}
