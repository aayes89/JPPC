package pcp.xenon;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/** Reconstruye el stream LZX por bloques almacenado dentro de CE. */
public final class XenonCeDecompressor {

    private static final int HEADER_SIZE = 0x30;
    private static final int LZX_WINDOW_SIZE = 0x20000;

    private XenonCeDecompressor() { }

    public static final class Result {
        public final long targetAddress;
        public final int uncompressedSize;
        public final int blockCount;
        private final byte[] image;

        private Result(long targetAddress, int uncompressedSize, int blockCount, byte[] image) {
            this.targetAddress = targetAddress;
            this.uncompressedSize = uncompressedSize;
            this.blockCount = blockCount;
            this.image = image;
        }

        public byte[] getImage() {
            return image.clone();
        }
    }

    public static Result decompress(byte[] ceDecrypted) throws IOException {
        if (ceDecrypted == null || ceDecrypted.length < HEADER_SIZE
                || ceDecrypted[0] != 0x43 || ceDecrypted[1] != 0x45) {
            throw new IOException("CE descifrado inválido");
        }

        int declaredSize = readU32(ceDecrypted, 0x0C);
        if (declaredSize < HEADER_SIZE || declaredSize > ceDecrypted.length
                || ((declaredSize + 15) & ~15) != ceDecrypted.length) {
            throw new IOException(String.format(
                    "Tamaño CE inválido: declarado=0x%X buffer=0x%X",
                    declaredSize, ceDecrypted.length));
        }

        long targetAddress = readU64(ceDecrypted, 0x20);
        int outputSize = readU32(ceDecrypted, 0x28);
        if (outputSize <= 0 || outputSize > 0x4000000) {
            throw new IOException(String.format("Tamaño descomprimido CE inválido: 0x%X", outputSize));
        }

        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        int inputOffset = HEADER_SIZE;
        int decompressedTotal = 0;
        int blocks = 0;

        while (decompressedTotal < outputSize) {
            if (inputOffset > declaredSize - 4) {
                throw new IOException("CE truncado mientras se leían los bloques comprimidos");
            }
            int compressedSize = readU16(ceDecrypted, inputOffset);
            int decompressedSize = readU16(ceDecrypted, inputOffset + 2);
            inputOffset += 4;

            if (compressedSize <= 0 || decompressedSize <= 0
                    || compressedSize > declaredSize - inputOffset
                    || decompressedSize > outputSize - decompressedTotal) {
                throw new IOException(String.format(
                        "Bloque CE inválido #%d: compressed=0x%X decompressed=0x%X offset=0x%X",
                        blocks, compressedSize, decompressedSize, inputOffset - 4));
            }

            compressed.write(ceDecrypted, inputOffset, compressedSize);
            inputOffset += compressedSize;
            decompressedTotal += decompressedSize;
            blocks++;
        }

        if (decompressedTotal != outputSize) {
            throw new IOException(String.format(
                    "Bloques CE producen 0x%X bytes; cabecera declara 0x%X",
                    decompressedTotal, outputSize));
        }
        if (inputOffset != declaredSize) {
            throw new IOException(String.format(
                    "CE contiene datos no consumidos: cursor=0x%X declared=0x%X",
                    inputOffset, declaredSize));
        }

        byte[] stream = compressed.toByteArray();
        try {
            byte[] image = new XexLzx(LZX_WINDOW_SIZE).decompress(stream, outputSize);
            if (image.length != outputSize) {
                Arrays.fill(image, (byte) 0);
                throw new IOException("LZX devolvió un tamaño distinto al declarado por CE");
            }
            return new Result(targetAddress, outputSize, blocks, image);
        } finally {
            Arrays.fill(stream, (byte) 0);
        }
    }

    private static int readU16(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private static int readU32(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    private static long readU64(byte[] data, int offset) {
        long value = 0;
        for (int i = 0; i < 8; i++) value = (value << 8) | (data[offset + i] & 0xFFL);
        return value;
    }
}
