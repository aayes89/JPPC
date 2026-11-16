/*
 * The MIT License
 *
 * Copyright 2026 Slam.
 */
package pcp.xenon;

import java.io.IOException;
import java.util.Arrays;

/** Valida el CF LDV contra el contador físico de fuseset 07. */
public final class XenonBootLockdownValidator {

    private static final int CF_PAIRING_OFFSET = 0x30;
    private static final int CF_KEY_OFFSET = 0x20;
    private static final int CF_LDV_OFFSET = 0x21F;
    private static final int CF_MIN_SIZE = 0x354;

    private XenonBootLockdownValidator() {
    }

    public static final class Result {
        public final long cfOffset;
        public final int cfVersion;
        public final int baseVersion;
        public final int targetVersion;
        public final int cfLdv;
        public final int fuseLdv;
        public final long fuseSet07;
        public final boolean matches;

        private Result(long cfOffset, int cfVersion, int baseVersion,
                int targetVersion, int cfLdv, int fuseLdv, long fuseSet07) {
            this.cfOffset = cfOffset;
            this.cfVersion = cfVersion;
            this.baseVersion = baseVersion;
            this.targetVersion = targetVersion;
            this.cfLdv = cfLdv;
            this.fuseLdv = fuseLdv;
            this.fuseSet07 = fuseSet07;
            this.matches = cfLdv == fuseLdv;
        }
    }

    public static Result validate(XenonNandParser nand, byte[] oneBlKey,
            XenonFuses fuses) throws IOException {
        if (nand == null || fuses == null) {
            throw new IOException("NAND y fusesets son obligatorios para validar CF LDV");
        }
        if (oneBlKey == null || oneBlKey.length != 16) {
            throw new IOException("La clave 1BL debe tener 16 bytes");
        }

        long cfOffset = nand.getCFOffset();
        byte[] prefix = null;
        byte[] cfHeader = null;
        byte[] key = null;
        byte[] encrypted = null;
        byte[] decrypted = null;
        try {
            prefix = nand.readData(cfOffset, CF_PAIRING_OFFSET);
            if (readU16BE(prefix, 0) != 0x4346) {
                throw new IOException("Magic CF inválido en la NAND");
            }
            int cfVersion = readU16BE(prefix, 2);
            int declaredSize = checkedInt(readU32BE(prefix, 0x0C), "CF size");
            int alignedSize = (declaredSize + 15) & ~15;
            if (alignedSize < CF_MIN_SIZE || alignedSize > 0x100000) {
                throw new IOException(String.format("Tamaño CF inválido: 0x%X", alignedSize));
            }

            cfHeader = nand.readData(cfOffset, alignedSize);
            key = XenonBootCrypto.hmacSha1(oneBlKey,
                    slice(cfHeader, CF_KEY_OFFSET, 0x10));
            encrypted = slice(cfHeader, CF_PAIRING_OFFSET,
                    alignedSize - CF_PAIRING_OFFSET);
            decrypted = XenonBootCrypto.rc4(slice(key, 0, 0x10), encrypted);
            System.arraycopy(decrypted, 0, cfHeader, CF_PAIRING_OFFSET, decrypted.length);

            int baseVersion = readU16BE(cfHeader, 0x10);
            int targetVersion = readU16BE(cfHeader, 0x14);
            int cfLdv = cfHeader[CF_LDV_OFFSET] & 0xFF;
            long fuseSet07 = fuses.getFuseSet(7);
            int fuseLdv = calculateFuseLdv(fuseSet07);

            return new Result(cfOffset, cfVersion, baseVersion,
                    targetVersion, cfLdv, fuseLdv, fuseSet07);
        } finally {
            wipe(prefix);
            wipe(cfHeader);
            wipe(key);
            wipe(encrypted);
            wipe(decrypted);
        }
    }

    /** Cuenta los nibbles de eFuse aún no quemados desde el extremo bajo. */
    public static int calculateFuseLdv(long fuseSet07) {
        int zeroNibbles = 0;
        long value = fuseSet07;
        while ((value & 0x0FL) == 0 && zeroNibbles < 16) {
            zeroNibbles++;
            value >>>= 4;
        }
        return zeroNibbles == 16 ? 0 : 16 - zeroNibbles;
    }

    private static byte[] slice(byte[] source, int offset, int length) throws IOException {
        if (source == null || offset < 0 || length < 0 || offset > source.length - length) {
            throw new IOException("Campo CF fuera de rango");
        }
        return Arrays.copyOfRange(source, offset, offset + length);
    }

    private static int readU16BE(byte[] data, int offset) throws IOException {
        if (offset < 0 || offset > data.length - 2) throw new IOException("Campo CF truncado");
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private static long readU32BE(byte[] data, int offset) throws IOException {
        if (offset < 0 || offset > data.length - 4) throw new IOException("Campo CF truncado");
        return ((long) (data[offset] & 0xFF) << 24)
                | ((long) (data[offset + 1] & 0xFF) << 16)
                | ((long) (data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFFL);
    }

    private static int checkedInt(long value, String field) throws IOException {
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new IOException(field + " fuera de rango");
        }
        return (int) value;
    }

    private static void wipe(byte[] value) {
        if (value != null) Arrays.fill(value, (byte) 0);
    }
}
