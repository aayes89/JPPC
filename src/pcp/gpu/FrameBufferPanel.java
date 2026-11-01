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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.DirectColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import javax.swing.JPanel;

/**
 * Muestra un int[] lineal 0x??RRGGBB. El modelo de color ignora el canal alfa:
 * antes un píxel escrito como 0x00FFFFFF salía transparente (negro).
 *
 * @author Slam
 */
public class FrameBufferPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private final int width;
    private final int height;
    private final int[] framebuffer;
    private final transient BufferedImage image;

    public FrameBufferPanel(int width, int height, int[] framebuffer) {
        this.width = width;
        this.height = height;
        this.framebuffer = framebuffer;
        DataBufferInt db = new DataBufferInt(framebuffer, width * height);
        DirectColorModel cm = new DirectColorModel(24, 0x00FF0000, 0x0000FF00, 0x000000FF);
        WritableRaster raster = Raster.createPackedRaster(db, width, height, width,
                new int[]{0x00FF0000, 0x0000FF00, 0x000000FF}, null);
        this.image = new BufferedImage(cm, raster, false, null);
        setBackground(Color.BLACK);
        setPreferredSize(new Dimension(width, height));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int panelW = getWidth();
        int panelH = getHeight();
        float scale = Math.min((float) panelW / width, (float) panelH / height);
        int displayW = (int) (width * scale);
        int displayH = (int) (height * scale);
        g.drawImage(image, (panelW - displayW) / 2, (panelH - displayH) / 2, displayW, displayH, null);
    }

    public int[] getFb() {
        return framebuffer;
    }
}
