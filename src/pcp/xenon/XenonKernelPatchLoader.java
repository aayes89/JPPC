/*
 * The MIT License
 *
 * Copyright 2026 Slam.
 */
package pcp.xenon;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Reconstruye el par CF/CG almacenado en NAND y aplica el delta sobre la imagen
 * HV+kernel expandida desde CE.
 *
 * El CF proporciona el HMAC de CG y la lista de bloques físicos que completan
 * el contenedor CG. El CG contiene una secuencia de descriptores delta que se
 * aplica en orden sobre una copia de la imagen base.
 */
public final class XenonKernelPatchLoader {

    private static final int LOGICAL_BLOCK_SIZE = 0x4000;
    private static final int CF_HEADER_SIZE = 0x354;
    private static final int CF_KEY_OFFSET = 0x20;
    private static final int CF_PAIRING_OFFSET = 0x30;
    private static final int CF_PAIRING_SIZE = 0x200;
    private static final int CF_CG_HMAC_OFFSET = 0x330;
    private static final int CF_CG_HASH_OFFSET = 0x340;
    private static final int CG_HEADER_SIZE = 0x50;
    private static final int CG_KEY_OFFSET = 0x10;
    private static final int CG_ENCRYPTED_OFFSET = 0x20;
    private static final int CG_DELTA_OFFSET = 0x50;
    private static final int DELTA_DESCRIPTOR_SIZE = 12;
    private static final int LZX_WINDOW_SIZE = 0x8000;

    private XenonKernelPatchLoader() {
    }

    public static final class Result {

        public final long cfOffset;
        public final int cfVersion;
        public final int baseVersion;
        public final int targetVersion;
        public final int cgVersion;
        public final int cfLdv;
        public final int patchCount;
        public final long originalSize;
        public final long newSize;
        public final String originalSha1;
        public final String newSha1;
        public final String cgRotSumSha1;
        public final boolean cfSignatureVerified;
        public final XenonBaseImageInspector.Result imageInfo;
        private final byte[] image;

        private Result(long cfOffset, int cfVersion, int baseVersion,
                int targetVersion, int cgVersion, int cfLdv, int patchCount,
                long originalSize, long newSize, byte[] originalSha1,
                byte[] newSha1, byte[] cgRotSumSha1, byte[] image,
                XenonBaseImageInspector.Result imageInfo) {
            this.cfOffset = cfOffset;
            this.cfVersion = cfVersion;
            this.baseVersion = baseVersion;
            this.targetVersion = targetVersion;
            this.cgVersion = cgVersion;
            this.cfLdv = cfLdv;
            this.patchCount = patchCount;
            this.originalSize = originalSize;
            this.newSize = newSize;
            this.originalSha1 = XenonBootCrypto.toHex(originalSha1);
            this.newSha1 = XenonBootCrypto.toHex(newSha1);
            this.cgRotSumSha1 = XenonBootCrypto.toHex(cgRotSumSha1);
            this.image = image.clone();
            this.imageInfo = imageInfo;
            this.cfSignatureVerified = false;
        }

        public byte[] getImage() {
            return image.clone();
        }
    }

    /**
     * Aplica el CF activo indicado por la cabecera NAND.
     *
     * La clave 1BL se recibe desde las credenciales del propietario; nunca se
     * almacena en esta clase ni se incorpora al resultado.
     */
    public static Result apply(XenonNandParser nand, byte[] oneBlKey,
            byte[] baseImage) throws IOException {
        if (nand == null) {
            throw new IOException("NAND no especificada");
        }
        requireKey(oneBlKey, "1BL");
        if (baseImage == null || baseImage.length == 0) {
            throw new IOException("Imagen base CE vacía");
        }

        long cfOffset = nand.getCFOffset();
        byte[] cfHeader = null;
        byte[] cg = null;
        byte[] cfKey = null;
        byte[] cgKey = null;
        byte[] cgRotSum = null;
        byte[] baseHash = null;
        byte[] targetHash = null;
        byte[] patched = null;
        try {
            byte[] cfPrefix = nand.readData(cfOffset, 0x30);
            validateMagic(cfPrefix, 0x4346, "CF");
            int cfVersion = readU16BE(cfPrefix, 2);
            long cfDeclaredSize = readU32BE(cfPrefix, 0x0C);
            int cfAlignedSize = align16(checkedInt(cfDeclaredSize, "CF size"));
            long cgStoredSizeLong = readU32BE(cfPrefix, 0x1C);
            int cgStoredSize = checkedInt(cgStoredSizeLong, "CG stored size");
            if (cfAlignedSize < CF_HEADER_SIZE || cfAlignedSize > 0x100000
                    || cgStoredSize < CG_HEADER_SIZE || cgStoredSize > 0x1000000) {
                throw new IOException(String.format(
                        "Tamaños CF/CG fuera de rango: CF=0x%X CG=0x%X",
                        cfAlignedSize, cgStoredSize));
            }

            cfHeader = nand.readData(cfOffset, cfAlignedSize);
            cfKey = XenonBootCrypto.hmacSha1(oneBlKey,
                    slice(cfHeader, CF_KEY_OFFSET, 0x10));
            byte[] decryptedCf = XenonBootCrypto.rc4(slice(cfKey, 0, 0x10),
                    slice(cfHeader, CF_PAIRING_OFFSET, cfAlignedSize - CF_PAIRING_OFFSET));
            System.arraycopy(decryptedCf, 0, cfHeader, CF_PAIRING_OFFSET, decryptedCf.length);
            Arrays.fill(decryptedCf, (byte) 0);

            int baseVersion = readU16BE(cfHeader, 0x10);
            int targetVersion = readU16BE(cfHeader, 0x14);
            int cfLdv = cfHeader[0x21F] & 0xFF;
            int pairCount = readU16BE(cfHeader, CF_PAIRING_OFFSET);
            if (pairCount < 0 || pairCount > (CF_PAIRING_SIZE - 2) / 2) {
                throw new IOException("Cantidad de bloques CG inválida en pairing CF");
            }
            if (baseVersion == 0 || targetVersion == 0) {
                throw new IOException("Versiones base/objetivo CF inválidas");
            }

            System.out.printf("[CF/CG] CF activo @0x%X version=%04X base=%04X target=%04X cgSize=0x%X%n",
                    cfOffset, cfVersion, baseVersion, targetVersion, cgStoredSize);
            System.out.printf("[CF/CG] Pairing NAND: %d bloques adicionales%n", pairCount);

            cg = extractCgFromNand(nand, cfOffset + cfAlignedSize,
                    cgStoredSize, cfHeader, pairCount);
            validateMagic(cg, 0x4347, "CG");
            int cgVersion = readU16BE(cg, 2);
            long cgDeclaredSizeLong = readU32BE(cg, 0x0C);
            int cgDeclaredSize = checkedInt(cgDeclaredSizeLong, "CG declared size");
            int cgAlignedSize = align16(cgDeclaredSize);
            if (cgVersion != targetVersion) {
                throw new IOException(String.format(
                        "Versión CG %04X no coincide con target CF %04X",
                        cgVersion, targetVersion));
            }
            if (cgDeclaredSize < CG_HEADER_SIZE || cgAlignedSize > cg.length
                    || cgDeclaredSize > cg.length) {
                throw new IOException("Tamaño declarado CG inconsistente");
            }

            cgKey = XenonBootCrypto.hmacSha1(
                    slice(cfHeader, CF_CG_HMAC_OFFSET, 0x10),
                    slice(cg, CG_KEY_OFFSET, 0x10));
            byte[] decryptedCg = XenonBootCrypto.rc4(slice(cgKey, 0, 0x10),
                    slice(cg, CG_ENCRYPTED_OFFSET, cgAlignedSize - CG_ENCRYPTED_OFFSET));
            System.arraycopy(decryptedCg, 0, cg, CG_ENCRYPTED_OFFSET, decryptedCg.length);
            Arrays.fill(decryptedCg, (byte) 0);

            cgRotSum = XenonBootCrypto.rotSumSha1(
                    slice(cg, 0, 0x10),
                    slice(cg, CG_ENCRYPTED_OFFSET, cgAlignedSize - CG_ENCRYPTED_OFFSET));
            byte[] expectedCgRotSum = slice(cfHeader, CF_CG_HASH_OFFSET, 0x14);
            if (!XenonBootCrypto.constantTimeEquals(cgRotSum, expectedCgRotSum)) {
                throw new IOException("XeCryptRotSumSha de CG no coincide con CF.cg_hash");
            }
            System.out.println("[CF/CG] CG descifrado; XeCryptRotSumSha verificado contra CF");

            long originalSizeLong = readU32BE(cg, 0x20);
            long newSizeLong = readU32BE(cg, 0x38);
            int originalSize = checkedInt(originalSizeLong, "CG original_size");
            int newSize = checkedInt(newSizeLong, "CG new_size");
            if (originalSize != baseImage.length) {
                throw new IOException(String.format(
                        "Tamaño base CE 0x%X no coincide con CG.original_size 0x%X",
                        baseImage.length, originalSize));
            }
            if (newSize < originalSize || newSize > 0x1000000) {
                throw new IOException(String.format("CG.new_size fuera de rango: 0x%X", newSize));
            }

            baseHash = XenonBootCrypto.sha1(baseImage);
            byte[] expectedBaseHash = slice(cg, 0x24, 0x14);
            if (!XenonBootCrypto.constantTimeEquals(baseHash, expectedBaseHash)) {
                throw new IOException("SHA1 de imagen base no coincide con CG.original_hash");
            }
            if (baseVersion != XenonBaseImageInspector.inspect(baseImage).hypervisorVersion) {
                throw new IOException(String.format(
                        "CF espera base %04X pero CE contiene HV %04X",
                        baseVersion, XenonBaseImageInspector.inspect(baseImage).hypervisorVersion));
            }
            System.out.printf("[CF/CG] Base CE verificada: size=0x%X SHA1=%s%n",
                    originalSize, XenonBootCrypto.toHex(baseHash));

            patched = new byte[newSize];
            System.arraycopy(baseImage, 0, patched, 0, baseImage.length);
            int patchCount = applyDelta(cg, cgDeclaredSize, patched);

            targetHash = XenonBootCrypto.sha1(patched);
            byte[] expectedTargetHash = slice(cg, 0x3C, 0x14);
            if (!XenonBootCrypto.constantTimeEquals(targetHash, expectedTargetHash)) {
                throw new IOException(String.format(
                        "SHA1 de kernel actualizado no coincide: calculado=%s esperado=%s",
                        XenonBootCrypto.toHex(targetHash), XenonBootCrypto.toHex(expectedTargetHash)));
            }
            XenonBaseImageInspector.Result imageInfo = XenonBaseImageInspector.inspect(patched);
            if (imageInfo.hypervisorVersion != targetVersion) {
                throw new IOException(String.format(
                        "HV reconstruido %04X no coincide con target CF %04X",
                        imageInfo.hypervisorVersion, targetVersion));
            }

            System.out.printf("[CF/CG] Delta aplicado: %d bloques; imagen=0x%X%n", patchCount, newSize);
            System.out.printf("[CF/CG] SHA1 objetivo verificado: %s%n", XenonBootCrypto.toHex(targetHash));
            System.out.printf("[CF/CG] HV=%04X entry=0x%X; xboxkrnl base=0x%08X entry=0x%08X size=0x%X sections=%d%n",
                    imageInfo.hypervisorVersion, imageInfo.hypervisorEntryOffset,
                    imageInfo.kernelImageBase, imageInfo.kernelEntryPoint,
                    imageInfo.kernelSizeOfImage, imageInfo.kernelSectionCount);
            System.out.println("[CF/CG] CF RSA signature: NOT VERIFIED (public-key verifier not implemented)");

            return new Result(cfOffset, cfVersion, baseVersion, targetVersion,
                    cgVersion, cfLdv, patchCount, originalSize, newSize, baseHash,
                    targetHash, cgRotSum, patched, imageInfo);
        } finally {
            wipe(cfHeader);
            wipe(cg);
            wipe(cfKey);
            wipe(cgKey);
            wipe(cgRotSum);
            wipe(baseHash);
            wipe(targetHash);
            wipe(patched);
        }
    }

    private static byte[] extractCgFromNand(XenonNandParser nand,
            long cgOffset, int cgSize, byte[] decryptedCf, int pairCount)
            throws IOException {
        long blockCountLong = nand.getLogicalSize() / LOGICAL_BLOCK_SIZE;
        if (blockCountLong <= 0 || blockCountLong > Integer.MAX_VALUE) {
            throw new IOException("Cantidad de bloques lógicos NAND inválida");
        }
        int blockCount = (int) blockCountLong;
        int firstBlock = checkedInt(cgOffset / LOGICAL_BLOCK_SIZE, "CG first block");
        int firstBlockOffset = (int) (cgOffset % LOGICAL_BLOCK_SIZE);
        int firstLength = Math.min(cgSize, LOGICAL_BLOCK_SIZE - firstBlockOffset);
        int remaining = cgSize - firstLength;
        int blocksNeeded = (remaining + LOGICAL_BLOCK_SIZE - 1) / LOGICAL_BLOCK_SIZE;
        int contiguousCount = blocksNeeded - pairCount;
        if (contiguousCount < 0 || contiguousCount > blockCount) {
            throw new IOException(String.format(
                    "Pairing CF incompatible: se requieren %d bloques, pairing declara %d",
                    blocksNeeded, pairCount));
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream(cgSize + LOGICAL_BLOCK_SIZE);
        output.write(nand.readData(cgOffset, firstLength));
        Set<Integer> used = new HashSet<>();
        used.add(firstBlock);

        for (int i = 1; i <= contiguousCount; i++) {
            int block = firstBlock + i;
            validateBlock(block, blockCount, used);
            output.write(nand.readData((long) block * LOGICAL_BLOCK_SIZE, LOGICAL_BLOCK_SIZE));
        }

        for (int i = 0; i < pairCount; i++) {
            int block = readU16BE(decryptedCf, CF_PAIRING_OFFSET + 2 + i * 2);
            validateBlock(block, blockCount, used);
            output.write(nand.readData((long) block * LOGICAL_BLOCK_SIZE, LOGICAL_BLOCK_SIZE));
        }

        byte[] assembled = output.toByteArray();
        if (assembled.length < cgSize) {
            wipe(assembled);
            throw new IOException(String.format(
                    "CG incompleto: reconstruido=0x%X requerido=0x%X",
                    assembled.length, cgSize));
        }
        byte[] result = Arrays.copyOf(assembled, cgSize);
        wipe(assembled);
        return result;
    }

    private static void validateBlock(int block, int blockCount, Set<Integer> used)
            throws IOException {
        if (block < 0 || block >= blockCount) {
            throw new IOException(String.format("Bloque NAND CF/CG fuera de rango: 0x%X", block));
        }
        if (!used.add(block)) {
            throw new IOException(String.format("Bloque NAND CF/CG duplicado: 0x%X", block));
        }
    }

    private static int applyDelta(byte[] cg, int cgDeclaredSize, byte[] output)
            throws IOException {
        int pointer = CG_DELTA_OFFSET;
        int patchEnd = cgDeclaredSize;
        int patchCount = 0;
        while (pointer < patchEnd) {
            if (patchEnd - pointer < DELTA_DESCRIPTOR_SIZE) {
                throw new IOException("Descriptor delta CG truncado");
            }
            long oldAddressLong = readU32BE(cg, pointer);
            long newAddressLong = readU32BE(cg, pointer + 4);
            int decompressedSize = readU16BE(cg, pointer + 8);
            int compressedSize = readU16BE(cg, pointer + 10);
            if (oldAddressLong == 0 && newAddressLong == 0
                    && decompressedSize == 0 && compressedSize == 0) {
                break;
            }
            int oldAddress = checkedInt(oldAddressLong, "delta old_addr");
            int newAddress = checkedInt(newAddressLong, "delta new_addr");
            if (decompressedSize <= 0 || newAddress > output.length
                    || decompressedSize > output.length - newAddress) {
                throw new IOException(String.format(
                        "Destino delta fuera de rango: dst=0x%X size=0x%X",
                        newAddress, decompressedSize));
            }

            int dataOffset = pointer + DELTA_DESCRIPTOR_SIZE;
            switch (compressedSize) {
                case 0:
                    Arrays.fill(output, newAddress, newAddress + decompressedSize, (byte) 0);
                    break;
                case 1:
                    if (oldAddress > output.length
                            || decompressedSize > output.length - oldAddress) {
                        throw new IOException("Origen de copia delta fuera de rango");
                    }
                    System.arraycopy(output, oldAddress, output, newAddress, decompressedSize);
                    break;
                default:
                    if (dataOffset > patchEnd || compressedSize > patchEnd - dataOffset) {
                        throw new IOException("Datos comprimidos delta truncados");
                    }
                    if (oldAddress > output.length
                            || decompressedSize > output.length - oldAddress) {
                        throw new IOException("Diccionario delta fuera de rango");
                    }
                    byte[] dictionary = slice(output, oldAddress, decompressedSize);
                    byte[] compressed = slice(cg, dataOffset, compressedSize);
                    byte[] expanded;
                    try {
                        expanded = new XexLzx(LZX_WINDOW_SIZE)
                                .decompress(compressed, decompressedSize, dictionary);
                    } catch (IOException ex) {
                        throw new IOException(String.format(
                                "Falló LZX delta en bloque %d (src=0x%X dst=0x%X)",
                                patchCount, oldAddress, newAddress), ex);
                    } finally {
                        wipe(dictionary);
                        wipe(compressed);
                    }
                    System.arraycopy(expanded, 0, output, newAddress, expanded.length);
                    wipe(expanded);
                    break;
            }

            pointer = dataOffset + (compressedSize > 1 ? compressedSize : 0);
            patchCount++;
            if (patchCount > 100000) {
                throw new IOException("Cantidad de bloques delta excedida");
            }
        }
        if (pointer != patchEnd) {
            // Un terminador cero es válido; cualquier residuo no descrito no lo es.
            for (int i = pointer; i < patchEnd; i++) {
                if (cg[i] != 0) {
                    throw new IOException(String.format(
                            "Datos delta residuales no interpretados en 0x%X", pointer));
                }
            }
        }
        if (patchCount == 0) {
            throw new IOException("CG no contiene bloques delta");
        }
        return patchCount;
    }

    private static void validateMagic(byte[] data, int expected, String name)
            throws IOException {
        if (data == null || data.length < 4 || readU16BE(data, 0) != expected) {
            throw new IOException("Magic " + name + " inválido");
        }
    }

    private static void requireKey(byte[] key, String name) throws IOException {
        if (key == null || key.length != 16) {
            throw new IOException("La clave " + name + " debe tener exactamente 16 bytes");
        }
    }

    private static int align16(int value) throws IOException {
        if (value < 0 || value > Integer.MAX_VALUE - 15) {
            throw new IOException("Tamaño inválido para alineación");
        }
        return (value + 15) & ~15;
    }

    private static int checkedInt(long value, String field) throws IOException {
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new IOException("Campo fuera de rango: " + field);
        }
        return (int) value;
    }

    private static int readU16BE(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private static long readU32BE(byte[] data, int offset) {
        return ((long) (data[offset] & 0xFF) << 24)
                | ((long) (data[offset + 1] & 0xFF) << 16)
                | ((long) (data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFFL);
    }

    private static byte[] slice(byte[] data, int offset, int length) throws IOException {
        if (data == null || offset < 0 || length < 0
                || offset > data.length || length > data.length - offset) {
            throw new IOException("Rango de datos fuera de límites");
        }
        return Arrays.copyOfRange(data, offset, offset + length);
    }

    private static void wipe(byte[] data) {
        if (data != null) {
            Arrays.fill(data, (byte) 0);
        }
    }
}
