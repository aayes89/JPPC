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
package pcp;

/**
 *
 * @author Slam
 */
public class ProbeTiling {

    public static void main(String[] a) throws Exception {
        PPCSimulator s = new PPCSimulator(1280, 720);
        s.loadELF("src/pcp/TESTS/lk.elf");
        for (char c : "gfx display_info\n".toCharArray()) {
            s.getUart().feed(c);
        }
        s.runBlocking(20_000_000);
        String out = s.getConsole().getOutput();
        System.out.println("OUT: " + out.replace("\n", "|"));
        // dirección de la superficie de LK = 0x10035d70 (la imprime LK)
        long addr = 0x10035d70L;
        int W = 1280, H = 720;
        byte[] buf = new byte[W * H * 4];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int v = ((y << 11) | x) + 1;
                int o = (y * W + x) * 4;
                buf[o] = (byte) (v >>> 24);
                buf[o + 1] = (byte) (v >>> 16);
                buf[o + 2] = (byte) (v >>> 8);
                buf[o + 3] = (byte) v;
            }
        }
        s.getMemory().writeBlock(addr, buf);
        s.getFrameBuffer().clear();
        for (char c : "gfx display_info\n".toCharArray()) {
            s.getUart().feed(c);
        }
        s.runBlocking(60_000_000);
        int[] vram = s.getFrameBuffer().getGpuTiledBuffer();
        int[][] pos = new int[W * H + 1][];
        int found = 0, bad = 0;
        // dst offset (bytes) para cada pixel origen
        int[] dstOf = new int[W * H];
        java.util.Arrays.fill(dstOf, -1);
        for (int i = 0; i < vram.length; i++) {
            int v = vram[i];
            if (v == 0) {
                continue;
            }
            v -= 1;
            int x = v & 0x7FF, y = v >>> 11;
            if (x < W && y < H) {
                dstOf[y * W + x] = i * 4;
                found++;
            } else {
                bad++;
            }
        }
        System.out.println("encontrados=" + found + " invalidos=" + bad);
        int[][] probes = {{0, 0}, {1, 0}, {2, 0}, {4, 0}, {8, 0}, {16, 0}, {32, 0}, {64, 0}, {0, 1}, {0, 2}, {0, 4}, {0, 8}, {0, 16}, {0, 32}, {0, 64}, {1, 1}, {3, 0}, {5, 0}};
        for (int[] p : probes) {
            System.out.printf("(x=%d,y=%d) -> 0x%x%n", p[0], p[1], dstOf[p[1] * W + p[0]]);
        }
        // comparar con la fórmula de mi implementación
        int mism = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int f = formula(x, y, W);
                if (dstOf[y * W + x] != f) {
                    if (mism < 10) {
                        System.out.printf("DIFF (%d,%d): real=0x%x formula=0x%x%n", x, y, dstOf[y * W + x], f);
                    }
                    mism++;
                }
            }
        }
        System.out.println("diferencias con la fórmula: " + mism);
        s.getFrameBuffer().savePNG(new java.io.File("probe.png"));
    }

    static int formula(int x, int y, int pitchPixels) {
        int tile = (x >> 5) + (y >> 5) * ((pitchPixels + 31) >> 5);
        int o = ((x & 1) << 2) | (((x >> 1) & 1) << 3) | ((y & 1) << 4) | (((x >> 2) & 1) << 5)
                | (((x >> 3) & 1) << 6) | ((((x >> 4) & 1) ^ ((y >> 3) & 1)) << 7) | (((y >> 1) & 1) << 8)
                | (((y >> 2) & 1) << 9) | (((y >> 3) & 1) << 10) | (((y >> 4) & 1) << 11);
        return (tile << 12) + o;
    }

    static int formulaOld(int x, int y, int pitchPixels) {
        final int log2bpp = 2;
        int pitch = (pitchPixels + 31) & ~31;
        int macro = ((x >> 5) + (y >> 5) * (pitch >> 5)) << (log2bpp + 7);
        int micro = ((x & 7) + ((y & 0xE) << 2)) << log2bpp;
        int offset = macro + ((micro & ~0xF) << 1) + (micro & 0xF) + ((micro & 0x10) << 1);
        return ((offset & ~0x1FF) << 3) + ((y & 16) << 7) + ((offset & 0x1C0) << 2) + (((((y & 8) >> 2) + (x >> 3)) & 3) << 6) + (offset & 0x3F);
    }
}
