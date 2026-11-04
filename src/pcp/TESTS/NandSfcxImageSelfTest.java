package pcp.TESTS;

import java.nio.file.Paths;
import pcp.gpu.NandImage;
import pcp.gpu.XenonSfcx;

/** Verifica la ruta MMIO SFCX contra una imagen RAW suministrada localmente. */
public final class NandSfcxImageSelfTest {
    private static int readSfcxWord(XenonSfcx sfcx, long offset) {
        return Integer.reverseBytes(sfcx.readWord(offset));
    }
    private static void writeSfcxWord(XenonSfcx sfcx, long offset, int value) {
        sfcx.writeWord(offset, Integer.reverseBytes(value));
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Uso: NandSfcxImageSelfTest <nand.raw>");
        NandImage nand = NandImage.load(Paths.get(args[0]));
        final int offset = 0x4200;
        byte[] expected = new byte[4];
        byte[] page = new byte[NandImage.DATA_PAGE_SIZE];
        nand.readLogicalPage(offset / NandImage.DATA_PAGE_SIZE, page);
        System.arraycopy(page, offset % NandImage.DATA_PAGE_SIZE, expected, 0, 4);

        XenonSfcx sfcx = new XenonSfcx();
        sfcx.loadNand(Paths.get(args[0]));
        writeSfcxWord(sfcx, 0x04, 0);
        writeSfcxWord(sfcx, 0x0C, offset);
        writeSfcxWord(sfcx, 0x08, 2); // LOG_PAGE_TO_BUF
        writeSfcxWord(sfcx, 0x0C, 0);
        writeSfcxWord(sfcx, 0x08, 0); // PAGE_BUF_TO_REG
        int actual = readSfcxWord(sfcx, 0x10);
        int wanted = ((expected[0] & 255) << 24) | ((expected[1] & 255) << 16)
                | ((expected[2] & 255) << 8) | (expected[3] & 255);
        if (actual != wanted) throw new AssertionError("SFCX logical read mismatch at 0x4200");
        System.out.println("SFCX NAND image: read-only transfer at logical offset 0x4200 OK; page ECC="
                + nand.getEccStatus(offset / NandImage.DATA_PAGE_SIZE));
    }
}
