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

import pcp.gpu.XenosGpu;

/**
 * Regresión de endianidad de registros Xenos para LK (BE) y XeLL
 * (lwbrx/stwbrx).
 */
public final class XenosGpuSelfTest {

    public static void main(String[] args) {
        testBigEndian();
        testLittleEndian();
        System.out.println("Xenos GPU endian/surface: ALL OK");
    }

    private static void testBigEndian() {
        final long[] surface = {-1};
        XenosGpu gpu = new XenosGpu(0x1E000000L, 1280, 720, false);
        gpu.setOnSurfaceChange(v -> surface[0] = v);
        require(gpu.readWord(0x6110) == 0x1E000000, "LK initial surface BE");
        gpu.writeWord(0x6110, 0x1E000000);
        require(surface[0] == 0x1E000000L, "LK surface callback BE");
        require(gpu.readWord(0x6120) == 5120, "LK pitch BE");
    }

    private static void testLittleEndian() {
        final long[] surface = {-1};
        XenosGpu gpu = new XenosGpu(0x1E000000L, 1280, 720, true);
        gpu.setOnSurfaceChange(v -> surface[0] = v);
        // lwbrx invierte el valor devuelto por el dispositivo little-endian.
        require(Integer.reverseBytes(gpu.readWord(0x6110)) == 0x1E000000,
                "XeLL initial surface LE/lwbrx");
        require(Integer.reverseBytes(gpu.readWord(0x6120)) == 5120,
                "XeLL pitch LE/lwbrx");
        // stwbrx invierte antes de escribir; el callback debe recuperar la dirección física.
        gpu.writeWord(0x6110, Integer.reverseBytes(0x1E000000));
        require(surface[0] == 0x1E000000L, "XeLL surface callback LE/stwbrx");
    }

    private static void require(boolean ok, String label) {
        if (!ok) {
            throw new AssertionError(label);
        }
    }
}
