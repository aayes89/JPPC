package pcp.TESTS;
/*
 * The MIT License
 *
 * Copyright 2025 Slam.
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

import java.nio.file.Paths;
import pcp.gpu.NandImage;
import pcp.gpu.XenonSfcx;

/**
 * Verifica la ruta MMIO SFCX contra una imagen RAW suministrada localmente.
 */
public final class NandSfcxImageSelfTest {

    private static int readSfcxWord(XenonSfcx sfcx, long offset) {
        return Integer.reverseBytes(sfcx.readWord(offset));
    }

    private static void writeSfcxWord(XenonSfcx sfcx, long offset, int value) {
        sfcx.writeWord(offset, Integer.reverseBytes(value));
    }

    public static void main(String[] args) throws Exception {
        
        //NandImage nand = NandImage.load(Paths.get(args[0]));
        NandImage nand = NandImage.load(Paths.get("./src/pcp/TESTS/nand.bin"));
        final int offset = 0x4200;
        byte[] expected = new byte[4];
        byte[] page = new byte[NandImage.DATA_PAGE_SIZE];
        nand.readLogicalPage(offset / NandImage.DATA_PAGE_SIZE, page);
        System.arraycopy(page, offset % NandImage.DATA_PAGE_SIZE, expected, 0, 4);

        XenonSfcx sfcx = new XenonSfcx();
        sfcx.loadNand(Paths.get("./src/pcp/TESTS/nand.bin"));
        writeSfcxWord(sfcx, 0x04, 0);
        writeSfcxWord(sfcx, 0x0C, offset);
        writeSfcxWord(sfcx, 0x08, 2); // LOG_PAGE_TO_BUF
        writeSfcxWord(sfcx, 0x0C, 0);
        writeSfcxWord(sfcx, 0x08, 0); // PAGE_BUF_TO_REG
        int actual = readSfcxWord(sfcx, 0x10);
        int wanted = ((expected[0] & 255) << 24) | ((expected[1] & 255) << 16)
                | ((expected[2] & 255) << 8) | (expected[3] & 255);
        if (actual != wanted) {
            throw new AssertionError("SFCX logical read mismatch at 0x4200");
        }
        System.out.println("SFCX NAND image: read-only transfer at logical offset 0x4200 OK; page ECC="
                + nand.getEccStatus(offset / NandImage.DATA_PAGE_SIZE));
    }
}
