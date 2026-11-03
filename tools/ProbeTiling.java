import pcp.PPCSimulator;
public class ProbeTiling {
    public static void main(String[] a) throws Exception {
        int W = 1280, H = 720;
        PPCSimulator s = new PPCSimulator(W, H);
        s.loadELF("src/pcp/TESTS/lk.elf");
        for (char c : "gfx rgb_bars\n".toCharArray()) s.getUart().feed(c);
        s.runBlocking(400_000_000);
        var m = s.getMemory();
        long best = 0, bestStart = 0, run = 0, start = 0;
        for (long p = 0x10000000L; p < 0x1E000000L; p += 4) {
            if (m.readMemoryWord(p) != 0) { if (run == 0) start = p; run += 4; if (run > best) { best = run; bestStart = start; } }
            else run = 0;
        }
        System.out.printf("origen LK: 0x%x tamaño=%d (esperado %d)%n", bestStart, best, W*H*4);
        long S = bestStart;
        byte[] buf = new byte[W * H * 4];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int v = ((y << 11) | x) + 1; int o = (y * W + x) * 4;
            buf[o] = (byte)(v>>>24); buf[o+1] = (byte)(v>>>16); buf[o+2] = (byte)(v>>>8); buf[o+3] = (byte)v;
        }
        m.writeBlock(S, buf);
        s.getFrameBuffer().clear();
        for (char c : "gfx display_info\n".toCharArray()) s.getUart().feed(c);
        s.runBlocking(60_000_000);
        int[] vram = s.getFrameBuffer().getGpuTiledBuffer();
        int[] dstOf = new int[W * H]; java.util.Arrays.fill(dstOf, -1);
        int found = 0, bad = 0;
        for (int i = 0; i < vram.length; i++) {
            int v = Integer.reverseBytes(vram[i]); if (v == 0) continue; v -= 1;
            int px = v & 0x7FF, py = v >>> 11;
            long L = (long) py * W + px - 1;          // el origen real de LK empieza 4 bytes después de S
            if (px < W && py < H && L >= 0 && L < (long) W * H) { dstOf[(int) L] = i*4; found++; } else bad++;
        }
        System.out.println("encontrados=" + found + " invalidos=" + bad);
        int[][] probes = {{0,0},{1,0},{2,0},{4,0},{8,0},{16,0},{32,0},{64,0},{0,1},{0,2},{0,4},{0,8},{0,16},{0,32},{0,64},{1,1},{3,0},{5,0}};
        for (int[] p : probes) System.out.printf("(x=%d,y=%d) -> 0x%x%n", p[0], p[1], dstOf[p[1]*W+p[0]]);
        int mism = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int f = formula(x, y, W);
            if (dstOf[y*W+x] != f) { if (mism < 8) System.out.printf("DIFF (%d,%d): real=0x%x formula=0x%x%n", x, y, dstOf[y*W+x], f); mism++; }
        }
        System.out.println("diferencias con la fórmula: " + mism);
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
        return ((offset & ~0x1FF) << 3) + ((y & 16) << 7) + ((offset & 0x1C0) << 2)
                + (((((y & 8) >> 2) + (x >> 3)) & 3) << 6) + (offset & 0x3F);
    }
}
