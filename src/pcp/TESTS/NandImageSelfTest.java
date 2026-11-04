package pcp.TESTS;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import pcp.gpu.NandImage;
import pcp.gpu.XenonSfcx;

/** Prueba de geometría RAW Xenon sin distribuir una NAND real. */
public final class NandImageSelfTest {
    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempFile("jppc-nand-test", ".bin");
        try {
            try (RandomAccessFile f = new RandomAccessFile(temp.toFile(), "rw")) {
                f.setLength(NandImage.RAW_SIZE);
                f.seek(2L * NandImage.RAW_PAGE_SIZE);
                f.write(new byte[]{0x12, 0x34, 0x56, 0x78});
                f.seek(2L * NandImage.RAW_PAGE_SIZE + NandImage.DATA_PAGE_SIZE);
                f.write(new byte[]{(byte) 0xA5, 0x5A});
            }
            NandImage nand = NandImage.load(temp);
            if (nand.getFormat() != NandImage.Format.RAW_WITH_SPARE) throw new AssertionError("formato");
            if (nand.getPageCount() != 32768 || nand.getBlockCount() != 1024) throw new AssertionError("geometría");
            byte[] raw = new byte[NandImage.RAW_PAGE_SIZE];
            nand.readPhysicalPage(2, raw);
            if (!Arrays.equals(Arrays.copyOf(raw, 4), new byte[]{0x12, 0x34, 0x56, 0x78})) throw new AssertionError("datos");
            if (raw[512] != (byte) 0xA5 || raw[513] != 0x5A) throw new AssertionError("OOB");
            byte[] logical = new byte[NandImage.DATA_PAGE_SIZE];
            nand.readLogicalPage(2, logical);
            if (logical[0] != 0x12 || logical[3] != 0x78) throw new AssertionError("lectura lógica");

            // EDC Xbox 360: genera el valor de referencia y valida que un bit
            // alterado en datos u OOB metadata invalida la página.
            byte[] eccPage = new byte[NandImage.RAW_PAGE_SIZE];
            for (int i = 0; i < NandImage.DATA_PAGE_SIZE; i++)
                eccPage[i] = (byte) (i * 37 + 11);
            eccPage[512] = 0x34;
            eccPage[513] = 0x02; // BlockID 0x234
            eccPage[517] = (byte) 0xFF;
            int ecc = NandImage.calculateEcc(eccPage);
            eccPage[524] = (byte) ((ecc >>> 24) & 0xC0);
            eccPage[525] = (byte) (ecc >>> 16);
            eccPage[526] = (byte) (ecc >>> 8);
            eccPage[527] = (byte) ecc;
            if (!NandImage.isEccValid(eccPage)) throw new AssertionError("ECC calculado no valida");
            if (NandImage.getSmallBlockId(eccPage) != 0x234) throw new AssertionError("BlockID OOB");
            if (NandImage.getSmallBlockMarkerField(eccPage) != 0xFF) throw new AssertionError("marker OOB");
            eccPage[17] ^= 0x01;
            if (NandImage.isEccValid(eccPage)) throw new AssertionError("ECC no detectó bit alterado");

            XenonSfcx sfcx = new XenonSfcx();
            sfcx.loadNand(temp);
            // SFCX: clear status, flash address 0x400 (página 2), physical read,
            // reset buffer cursor, transfer first word to DATA.
            sfcx.writeWord(0x04, Integer.reverseBytes(0));
            sfcx.writeWord(0x0C, Integer.reverseBytes(0x400));
            sfcx.writeWord(0x08, Integer.reverseBytes(3));
            sfcx.writeWord(0x0C, Integer.reverseBytes(0));
            sfcx.writeWord(0x08, Integer.reverseBytes(0));
            int dataWord = Integer.reverseBytes(sfcx.readWord(0x10));
            if (dataWord != 0x12345678) throw new AssertionError(String.format("SFCX DATA=%08X", dataWord));
            // Escritura y borrado se aceptan como comandos, pero no alteran el dump.
            byte[] before = Files.readAllBytes(temp);
            sfcx.writeWord(0x08, Integer.reverseBytes(4));
            sfcx.writeWord(0x08, Integer.reverseBytes(5));
            if (!Arrays.equals(before, Files.readAllBytes(temp))) throw new AssertionError("NAND modificada");
            System.out.println("NAND RAW Xenon: ALL OK (geometry, OOB, 26-bit EDC, metadata, SFCX read-only)");
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
