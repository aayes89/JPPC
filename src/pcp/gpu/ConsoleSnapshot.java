package pcp.gpu;

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

import java.io.File;
import java.io.IOException;

/**
 * Captura visual del canal serie UART.
 *
 * No representa la salida del motor gráfico Xenos: es un monitor de diagnóstico
 * para firmware que informa su estado por consola serie. Usa el framebuffer y
 * la fuente bitmap del proyecto, por lo que también funciona en modo headless.
 *
 * @autor: Slam
 */
public final class ConsoleSnapshot {

    private ConsoleSnapshot() {
    }

    public static void save(File file, String output, int width, int height) throws IOException {
        int w = Math.max(640, width);
        int h = Math.max(400, height);
        FrameBuffer image = new FrameBuffer(w, h);
        image.fillRect(0, 0, w, h, 0x00080D12);
        image.fillRect(0, 0, w, 44, 0x0014232D);
        image.printText(16, 16, "JPPC / XENON SERIAL MONITOR", 0x0075D7A0);
        image.printText(16, 56, "UART CAPTURE - NOT A NATIVE XENOS FRAMEBUFFER", 0x0078909C);

        int lineHeight = 12;
        int top = 82;
        int maxLines = Math.max(1, (h - top - 16) / lineHeight);
        int columns = Math.max(20, (w - 32) / 8);
        String normalized = output == null ? "" : output
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\t', ' ');

        java.util.List<String> lines = new java.util.ArrayList<>();
        for (String sourceLine : normalized.split("\n", -1)) {
            String line = sourceLine;
            if (line.isEmpty()) {
                lines.add("");
            } else {
                while (line.length() > columns) {
                    lines.add(line.substring(0, columns));
                    line = line.substring(columns);
                }
                lines.add(line);
            }
        }

        int first = Math.max(0, lines.size() - maxLines);
        int y = top;
        for (int i = first; i < lines.size(); i++) {
            String line = lines.get(i);
            StringBuilder printable = new StringBuilder(line.length());
            for (int j = 0; j < line.length(); j++) {
                char c = line.charAt(j);
                printable.append(c >= 0x20 && c <= 0x7E ? c : ' ');
            }
            image.printText(16, y, printable.toString(), 0x00B7E4C7);
            y += lineHeight;
        }
        image.drawLine(16, h - 12, w - 16, h - 12, 0x0031434D);
        image.savePNG(file);
    }
}
