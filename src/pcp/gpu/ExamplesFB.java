package pcp.gpu;

/**
 *
 * @author sistemas
 */
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

import pcp.gpu.FrameBuffer;

/**
 * @author Slam
 */
public class ExamplesFB {

    private final FrameBuffer fb;
    private final int width, height;

    public ExamplesFB(FrameBuffer fb) {
        this.fb = fb;
        this.width = fb.getWidth();
        this.height = fb.getHeight();
    }

    // ======== Llenar framebuffer con patrón de prueba
    public void FillTestPattern() {
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                int r = x * 255 / width;
                int g = y * 255 / height;
                int b = 0x80;
                int color = (0xFF << 24) | (r << 16) | (g << 8) | b; // ARGB
                fb.setPixel(x, y, color); // Write to gpuTiledBuffer
            }
        }
        fb.updateScreenTiled(); // Detile to framebuffer and repaint
        System.out.println("[INFO] Test pattern OK");
        // Debug: Log first pixel
        System.out.println("[DEBUG] gpuTiledBuffer[0]=0x" + Integer.toHexString(fb.getGpuTiledBuffer()[0]));
        System.out.println("[DEBUG] framebuffer[0]=0x" + Integer.toHexString(fb.getFramebuffer()[0]));
    }

    // ======== Comprobar si un punto pertenece al conjunto de Mandelbrot
    private int Mandelbrot(double real, double imag, int max_iter) {
        double z_real = 0.0;
        double z_imag = 0.0;
        for (int i = 0; i < max_iter; ++i) {
            double z_real_sq = z_real * z_real;
            double z_imag_sq = z_imag * z_imag;
            if (z_real_sq + z_imag_sq > 4.0) {
                return i;
            }
            double temp_real = z_real_sq - z_imag_sq + real;
            z_imag = 2.0 * z_real * z_imag + imag;
            z_real = temp_real;
        }
        return max_iter;
    }

    // ======== Llena el framebuffer con una imagen del conjunto de Mandelbrot (escala de grises)
    public void FillMandelbrotBW(int max_iter) {
        double scale_real = 3.0 / width;
        double scale_imag = 2.0 / height;
        double offset_real = -2.0;
        double offset_imag = -1.0;

        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                double c_real = offset_real + x * scale_real;
                double c_imag = offset_imag + y * scale_imag;
                int iter = Mandelbrot(c_real, c_imag, max_iter);
                int color;
                if (iter == max_iter) {
                    color = 0; // Negro
                } else {
                    color = (int) Math.sqrt((double) iter / max_iter * 255); // Escala de grises
                }
                int argb = (0xFF << 24) | (color << 16) | (color << 8) | color; // ARGB
                fb.setPixel(x, y, argb); // Write to gpuTiledBuffer
            }
        }
        fb.updateScreenTiled(); // Detile to framebuffer and repaint
        System.out.println("[INFO] Mandelbrot BW pattern OK");
        // Debug: Log first pixel
        System.out.println("[DEBUG] gpuTiledBuffer[0]=0x" + Integer.toHexString(fb.getGpuTiledBuffer()[0]));
        System.out.println("[DEBUG] framebuffer[0]=0x" + Integer.toHexString(fb.getFramebuffer()[0]));
    }

    // ======== Llena el framebuffer con una imagen del conjunto de Mandelbrot (color)
    public void FillMandelbrotColor(int max_iter) {
        double scale_real = 3.0 / width;
        double scale_imag = 2.0 / height;
        double offset_real = -2.0;
        double offset_imag = -1.0;

        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                double c_real = offset_real + x * scale_real;
                double c_imag = offset_imag + y * scale_imag;
                int iter = Mandelbrot(c_real, c_imag, max_iter);
                int r = 0, g = 0, b = 0;
                if (iter != max_iter) {
                    double t = (double) iter / max_iter;
                    r = (int) (9 * (1 - t) * t * t * t * 255);
                    g = (int) (15 * (1 - t) * (1 - t) * t * t * 255);
                    b = (int) (8 * (1 - t) * (1 - t) * (1 - t) * t * 255);
                }
                int argb = (0xFF << 24) | (r << 16) | (g << 8) | b; // ARGB
                fb.setPixel(x, y, argb); // Write to gpuTiledBuffer
            }
        }
        fb.updateScreenTiled(); // Detile to framebuffer and repaint
        System.out.println("[INFO] Mandelbrot color pattern OK");
        // Debug: Log first pixel
        System.out.println("[DEBUG] gpuTiledBuffer[0]=0x" + Integer.toHexString(fb.getGpuTiledBuffer()[0]));
        System.out.println("[DEBUG] framebuffer[0]=0x" + Integer.toHexString(fb.getFramebuffer()[0]));
    }
}
