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

import java.awt.Point;
import java.util.Arrays;
import javax.swing.JFrame;

/**
 *
 * @author Slam
 */
public class FrameBuffer implements Device {

    /**
     * Framebuffer + untile para emulador Xbox 360 (PPC). Corrige: 1. índice
     * base de macro‑tile (32×32) → antes se desfasaba 2. setPixel / printText
     * ahora escriben en modo tiled 3. detile32 usa la fórmula oficial del Xenos
     *
     * @author Slam
     */
    private final int[] framebuffer;      // lineal, ARGB listos para Swing
    private final int[] gpuTiledBuffer;   // tal como los escribe la “GPU” emulada
    private final FrameBufferPanel fbPanel;
    private JFrame frame;
    private final int width;
    private final int height;
    private final byte[] buffer;
    private static final int BYTES_PER_PIXEL = 4;
    private PixelShader pixelShader = null;
    private VertexShader vertexShader = null;

    public void setPixelShader(PixelShader shader) {
        this.pixelShader = shader;
    }

    public void setVertexShader(VertexShader shader) {
        this.vertexShader = shader;
    }

    public FrameBuffer(int width, int height) {
        this.width = width;
        this.height = height;
        this.framebuffer = new int[width * height];
        this.gpuTiledBuffer = new int[width * height]; // corregido aquí
        this.buffer = new byte[width * height * BYTES_PER_PIXEL];
        this.fbPanel = new FrameBufferPanel(width, height, framebuffer);
    }

    @Override
    public byte read(long address) {
        int index = (int) address;
        if (index >= 0 && index < buffer.length) {
            return buffer[index];
        }
        return 0;
    }

    @Override
    public void write(long address, byte value) {
        int index = (int) address;
        if (index >= 0 && index < buffer.length) {
            buffer[index] = value;
        }
    }

    @Override
    public int readWord(long address) {
        int index = (int) address;
        if (index < 0 || index >= buffer.length - 3) {
            return 0;
        }
        return ((buffer[index] & 0xFF) << 24)
                | ((buffer[index + 1] & 0xFF) << 16)
                | ((buffer[index + 2] & 0xFF) << 8)
                | (buffer[index + 3] & 0xFF);
    }

    @Override
    public boolean containsAddress(long address) {
        return address >= 0 && address < buffer.length;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public byte[] getBuffer() {
        return buffer;
    }
    public int[] getFramebuffer(){
        return framebuffer;
    }

    public void clear() {
        Arrays.fill(buffer, (byte) 0);
    }

    /**
     * Actualiza el panel: destila y repinta
     */
    public void updateScreenTiled() {
        detile32(gpuTiledBuffer, framebuffer, width, height);
        
        fbPanel.repaint();
    }

    public void setPixel(int x, int y, int color) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return;
        }

        final int tileSize = 32;
        int macroTileX = x / tileSize;
        int macroTileY = y / tileSize;
        int tilesPerRow = (width + tileSize - 1) / tileSize;
        int macroIndex = (macroTileY * tilesPerRow + macroTileX) * (tileSize * tileSize);

        int microX = x % tileSize;
        int microY = y % tileSize;
        int morton = interleaveBits(microX, microY);

        int tiledIndex = macroIndex + morton;
        if (tiledIndex < width * height) {
            gpuTiledBuffer[tiledIndex] = color;
        }
    }

    /* ─── Detile 32×32 ─────────────────────────── */
    private void detile32(int[] src, int[] dst, int width, int height) {
        final int tileSize = 32;
        final int pixelsPerTile = tileSize * tileSize;
        final int tilesPerRow = (width + tileSize - 1) / tileSize;
        final int tilesPerCol = (height + tileSize - 1) / tileSize;
        final int maxTileIndex = tilesPerRow * tilesPerCol * pixelsPerTile;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int macroTileX = x / tileSize;
                int macroTileY = y / tileSize;

                int tileXOffset = x % tileSize;
                int tileYOffset = y % tileSize;
                int morton = interleaveBits(tileXOffset, tileYOffset);

                int macroIndex = (macroTileY * tilesPerRow + macroTileX) * pixelsPerTile;
                int srcIndex = macroIndex + morton;

                if (srcIndex < maxTileIndex) {
                    dst[y * width + x] = src[srcIndex];
                } else {
                    dst[y * width + x] = 0; // borde negro
                }
                //testTiledIndex(x, y);
            }
        }
    }

    /**
     * Morton Z‑order (Y primero, X segundo) para tiles de 32×32.
     */
    private int interleaveBits(int x, int y) {
        int m = 0;
        for (int i = 0; i < 5; i++) {
            m |= ((y >> i) & 1) << (2 * i);       // bit par  = Yi
            m |= ((x >> i) & 1) << (2 * i + 1);   // bit impar= Xi
        }
        return m;
    }

    public void testTiledIndex(int x, int y) {
        final int tileSize = 32;
        int macroTileX = x / tileSize;
        int macroTileY = y / tileSize;
        int tilesPerRow = (width + tileSize - 1) / tileSize;
        int macroIndex = (macroTileY * tilesPerRow + macroTileX) * (tileSize * tileSize);

        int microX = x % tileSize;
        int microY = y % tileSize;
        int morton = interleaveBits(microX, microY);

        int tiledIndex = macroIndex + morton;
        System.out.printf("Pixel (%d,%d) → macroTile (%d,%d), macroIndex %d, morton %d, tiledIndex %d\n",
                x, y, macroTileX, macroTileY, macroIndex, morton, tiledIndex);
    }

    public void renderTest() {
        clear(0xFF000000);

        setVertexShader((x, y, z) -> new Point(
                (int) ((x * 0.5f + 0.5f) * getWidth()),
                (int) ((-y * 0.5f + 0.5f) * getHeight())
        ));

        float[] triVerts = {
            -0.5f, -0.5f, 0f,
            0.5f, -0.5f, 0f,
            0f, 0.5f, 0f
        };

        drawTriangle(triVerts, 0xFFFF0000);
        updateScreenTiled();
    }

    public void showVRAMViewer() {
        // refresca la vista destilada antes de mostrar
        detile32(gpuTiledBuffer, framebuffer, width, height);
        VRAMViewerFrame viewer = new VRAMViewerFrame(gpuTiledBuffer, framebuffer, width, height);
        viewer.setVisible(true);
    }

    /* ---------- API público para la CPU ---------- */
    public int[] getGpuTiledBuffer() {              // RAM → aquí
        return gpuTiledBuffer;
    }

    public void clear(int colorARGB) {
        Arrays.fill(gpuTiledBuffer, colorARGB);
    }

    /* ---------- primitivas ---------- */
    public void printChar(int x, int y, char c, int color) {
        int idx = c - 0x20;
        if (idx < 0 || idx >= BitmapFont.font8x8_basic.length) {
            return;
        }
        byte[] bits = BitmapFont.font8x8_basic[idx];
        for (int row = 0; row < 8; row++) {
            byte line = bits[row];
            for (int col = 0; col < 8; col++) {
                if ((line & (1 << col)) != 0) {
                    setPixel(x + col, y + row, color);
                }
            }
        }
    }

    public void printText(int x, int y, String txt, int color) {
        int dx = 0;
        for (char c : txt.toCharArray()) {
            printChar(x + dx, y, c, color);
            dx += 8;
        }
    }

    // === Primitivas ===
    public void drawLine(int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;

        while (true) {
            setPixel(x0, y0, color);
            if (x0 == x1 && y0 == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    public void drawRect(int x, int y, int w, int h, int color) {
        drawLine(x, y, x + w, y, color);
        drawLine(x + w, y, x + w, y + h, color);
        drawLine(x + w, y + h, x, y + h, color);
        drawLine(x, y + h, x, y, color);
    }

    public void fillRect(int x, int y, int w, int h, int color) {
        for (int i = 0; i < h; i++) {
            for (int j = 0; j < w; j++) {
                setPixel(x + j, y + i, color);
            }
        }
    }

    public void fillSquare(int x, int y, int size, int color) {
        fillRect(x, y, size, size, color);
    }

    public void drawCircle(int cx, int cy, int radius, int color) {
        int x = radius, y = 0, err = 0;
        while (x >= y) {
            plotCirclePoints(cx, cy, x, y, color);
            y++;
            err += 2 * y + 1;
            if (err > 2 * x) {
                x--;
                err -= 2 * x + 1;
            }
        }
    }

    private void plotCirclePoints(int cx, int cy, int x, int y, int color) {
        setPixel(cx + x, cy + y, color);
        setPixel(cx + y, cy + x, color);
        setPixel(cx - y, cy + x, color);
        setPixel(cx - x, cy + y, color);
        setPixel(cx - x, cy - y, color);
        setPixel(cx - y, cy - x, color);
        setPixel(cx + y, cy - x, color);
        setPixel(cx + x, cy - y, color);
    }

    public void fillCircle(int cx, int cy, int radius, int color) {
        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                if (x * x + y * y <= radius * radius) {
                    setPixel(cx + x, cy + y, color);
                }
            }
        }
    }

    public void fillTriangle(int x0, int y0, int x1, int y1, int x2, int y2, int color) {
        int minX = Math.min(x0, Math.min(x1, x2));
        int maxX = Math.max(x0, Math.max(x1, x2));
        int minY = Math.min(y0, Math.min(y1, y2));
        int maxY = Math.max(y0, Math.max(y1, y2));

        int dx01 = x1 - x0, dy01 = y1 - y0;
        int dx12 = x2 - x1, dy12 = y2 - y1;
        int dx20 = x0 - x2, dy20 = y0 - y2;

        float area2 = dx01 * dy20 - dy01 * dx20;
        if (area2 == 0) {
            return; // Triángulo degenerado
        }
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                int dx0 = x - x0, dy0 = y - y0;
                int dx1 = x - x1, dy1 = y - y1;
                int dx2 = x - x2, dy2 = y - y2;

                float w0 = (dx1 * dy12 - dy1 * dx12) / area2;
                float w1 = (dx2 * dy20 - dy2 * dx20) / area2;
                float w2 = (dx0 * dy01 - dy0 * dx01) / area2;

                if (w0 >= 0 && w1 >= 0 && w2 >= 0) {
                    setPixel(x, y, color);
                }
            }
        }
    }

    // Ejemplo: Método para dibujar un triángulo con shader
    public void drawTriangle(float[] vertices, int baseColor) {
        Point[] pts = new Point[3];
        for (int i = 0; i < 3; i++) {
            float x = vertices[i * 3];
            float y = vertices[i * 3 + 1];
            float z = vertices[i * 3 + 2];
            if (vertexShader != null) {
                pts[i] = vertexShader.transform(x, y, z);
            } else {
                // Interpreta x,y como pixeles directamente
                pts[i] = new Point(
                        Math.round(x),
                        Math.round(y)
                );
            }
        }
        fillTriangleShader(pts[0], pts[1], pts[2], baseColor);
    }

    private void fillTriangleShader(Point p0, Point p1, Point p2, int baseColor) {
        int minX = Math.min(p0.x, Math.min(p1.x, p2.x));
        int maxX = Math.max(p0.x, Math.max(p1.x, p2.x));
        int minY = Math.min(p0.y, Math.min(p1.y, p2.y));
        int maxY = Math.max(p0.y, Math.max(p1.y, p2.y));

        int dx01 = p1.x - p0.x, dy01 = p1.y - p0.y;
        int dx12 = p2.x - p1.x, dy12 = p2.y - p1.y;
        int dx20 = p0.x - p2.x, dy20 = p0.y - p2.y;

        float area2 = dx01 * dy20 - dy01 * dx20;
        if (area2 == 0) {
            return; // Triángulo degenerado
        }
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                int dx0 = x - p0.x, dy0 = y - p0.y;
                int dx1 = x - p1.x, dy1 = y - p1.y;
                int dx2 = x - p2.x, dy2 = y - p2.y;

                float w0 = (dx1 * dy12 - dy1 * dx12) / area2;
                float w1 = (dx2 * dy20 - dy2 * dx20) / area2;
                float w2 = (dx0 * dy01 - dy0 * dx01) / area2;

                if (w0 >= 0 && w1 >= 0 && w2 >= 0) {
                    int color = baseColor;
                    if (pixelShader != null) {
                        color = pixelShader.shade(x, y, baseColor);
                    }
                    setPixel(x, y, color);
                }
            }
        }
    }

}
