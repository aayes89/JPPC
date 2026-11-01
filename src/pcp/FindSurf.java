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
public class FindSurf {

    public static void main(String[] a) throws Exception {
        PPCSimulator s = new PPCSimulator(1280, 720);
        s.loadELF("src/pcp/TESTS/lk.elf");
        for (char c : "gfx display_info\n".toCharArray()) {
            s.getUart().feed(c);
        }
        s.runBlocking(20_000_000);
        var m = s.getMemory();
        long best = 0, bestStart = 0, run = 0, start = 0;
        for (long p = 0x10000000L; p < 0x14000000L; p += 4) {
            if (m.readMemoryWord(p) != 0) {
                if (run == 0) {
                    start = p;
                }
                run += 4;
                if (run > best) {
                    best = run;
                    bestStart = start;
                }
            } else {
                run = 0;
            }
        }
        System.out.printf("bloque no-cero mayor: 0x%x tamaño=%d%n", bestStart, best);
        for (int i = 0; i < 4; i++) {
            System.out.printf("  [%d] = 0x%08x%n", i, m.readMemoryWord(bestStart + 4L * i));
        }
        int[] vram = s.getFrameBuffer().getGpuTiledBuffer();
        int nz = 0;
        for (int v : vram) {
            if (v != 0) {
                nz++;
            }
        }
        System.out.printf("vram no-cero=%d primeros=%08x %08x%n", nz, vram[0], vram[1]);
    }
}
