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
            System.out.println("NAND RAW Xenon: ALL OK (528-byte page, OOB, 32768 pages, 1024 blocks, SFCX read-only)");
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
