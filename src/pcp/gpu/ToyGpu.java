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
package pcp.gpu;

import pcp.system.Bus;

/**
 * GPU de juguete controlada por registros en 0xC8001000 (la usan los programas triangle.*).
 * Registros (offset desde 0xC8000000):
 *   1000 base del render target (0 = framebuffer de pantalla)   1004 ancho   1008 alto
 *   100C formato   1010 tiling   1014 pitch   1020 color de clear (0xAARRGGBB)   1024 clear (cmd)
 *   1080 base del vertex buffer   1084 stride   1088 nº de vértices
 *   1100/1104 vertex shader (dir/tamaño, se ignora: identidad)
 *   1200/1204 pixel shader (dir/tamaño): si mide >= 24 bytes, el color constante son 4 floats en +8
 *   1300 tipo de primitiva (3 = TRIANGLE_LIST)   1304 draw (nº de primitivas)   1400 flush
 * Los vértices son floats big-endian (x,y,z) en coordenadas normalizadas [-1,1].
 *
 * @author Slam
 */
public class ToyGpu implements Device {

    public static final long BASE = 0xC8001000L;
    public static final int SIZE = 0x1000;
    private static final int REG_BASE = 0xC8000000 >>> 0;
    private final int[] regs = new int[SIZE / 4];
    private final Bus bus;
    private final FrameBuffer fb;

    public ToyGpu(Bus bus, FrameBuffer fb) {
        this.bus = bus;
        this.fb = fb;
    }

    private int reg(int absOffset) { // absOffset relativo a 0xC8000000
        return regs[(absOffset - 0x1000) >> 2];
    }

    @Override
    public boolean containsAddress(long address) {
        return address >= 0 && address < SIZE;
    }

    @Override
    public int readWord(long offs) {
        int i = (int) (offs >> 2);
        return i >= 0 && i < regs.length ? regs[i] : 0;
    }

    @Override
    public void writeWord(long offs, int v) {
        int i = (int) (offs >> 2);
        if (i < 0 || i >= regs.length) {
            return;
        }
        regs[i] = v;
        switch (0x1000 + (int) (offs & ~3L)) {
            case 0x1024: clear(); break;
            case 0x1304: draw(v); break;
            case 0x1400: fb.present(); break;
            default: break;
        }
    }

    @Override
    public byte read(long offs) {
        return (byte) (readWord(offs & ~3L) >>> (24 - 8 * (int) (offs & 3)));
    }

    @Override
    public void write(long offs, byte v) {
        int sh = 24 - 8 * (int) (offs & 3);
        writeWord(offs & ~3L, (readWord(offs & ~3L) & ~(0xFF << sh)) | ((v & 0xFF) << sh));
    }

    private int width() {
        int w = reg(0x1004);
        return w > 0 ? Math.min(w, fb.getWidth()) : fb.getWidth();
    }

    private int height() {
        int h = reg(0x1008);
        return h > 0 ? Math.min(h, fb.getHeight()) : fb.getHeight();
    }

    private void clear() {
        int c = reg(0x1020) & 0x00FFFFFF;
        for (int y = 0; y < height(); y++) {
            for (int x = 0; x < width(); x++) {
                fb.setPixel(x, y, c);
            }
        }
    }

    private float f(long addr) {
        return Float.intBitsToFloat(bus.readWord(addr));
    }

    private int pixelShaderColor() {
        long ps = reg(0x1200) & 0xFFFFFFFFL;
        float r = 1f, g = 0f, b = 0f;
        if (reg(0x1204) >= 24) {
            r = f(ps + 8);
            g = f(ps + 12);
            b = f(ps + 16);
        }
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255f)));
    }

    private void draw(int primitives) {
        long vb = reg(0x1080) & 0xFFFFFFFFL;
        int stride = reg(0x1084) > 0 ? reg(0x1084) : 12;
        int count = reg(0x1088);
        int w = width(), h = height();
        int color = pixelShaderColor();
        boolean empty = true;
        for (int p = 0; p < primitives && (p + 1) * 3 <= count; p++) {
            float[] sx = new float[3], sy = new float[3];
            for (int k = 0; k < 3; k++) {
                long a = vb + (long) (p * 3 + k) * stride;
                float x = f(a), y = f(a + 4);
                if (x != 0 || y != 0 || f(a + 8) != 0) {
                    empty = false;
                }
                sx[k] = (x * 0.5f + 0.5f) * w;
                sy[k] = (1f - (y * 0.5f + 0.5f)) * h; // y NDC hacia arriba
            }
            fillTriangle(sx, sy, w, h, color);
        }
        if (empty) {
            System.err.printf("[ToyGpu] aviso: el vertex buffer en 0x%x está vacío (¿dirección incorrecta?)%n", vb);
        }
    }

    private void fillTriangle(float[] sx, float[] sy, int w, int h, int color) {
        float area = (sx[1] - sx[0]) * (sy[2] - sy[0]) - (sx[2] - sx[0]) * (sy[1] - sy[0]);
        if (area == 0) {
            return;
        }
        int minX = Math.max(0, (int) Math.floor(Math.min(sx[0], Math.min(sx[1], sx[2]))));
        int maxX = Math.min(w - 1, (int) Math.ceil(Math.max(sx[0], Math.max(sx[1], sx[2]))));
        int minY = Math.max(0, (int) Math.floor(Math.min(sy[0], Math.min(sy[1], sy[2]))));
        int maxY = Math.min(h - 1, (int) Math.ceil(Math.max(sy[0], Math.max(sy[1], sy[2]))));
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                float px = x + 0.5f, py = y + 0.5f;
                float w0 = ((sx[1] - sx[0]) * (py - sy[0]) - (sy[1] - sy[0]) * (px - sx[0])) / area;
                float w1 = ((sx[2] - sx[1]) * (py - sy[1]) - (sy[2] - sy[1]) * (px - sx[1])) / area;
                float w2 = ((sx[0] - sx[2]) * (py - sy[2]) - (sy[0] - sy[2]) * (px - sx[2])) / area;
                if (w0 >= 0 && w1 >= 0 && w2 >= 0) {
                    fb.setPixel(x, y, color);
                }
            }
        }
    }
}
