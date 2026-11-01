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
package pcp;

import java.io.File;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import pcp.gpu.ExamplesFB;
import pcp.gpu.FrameBuffer;
import pcp.gpu.FrameBufferPanel;

/**
 * Uso: java pcp.PCP [archivo] [opciones]
 *   --elf            el archivo es un ELF de PowerPC (por defecto: binario plano en 0x0)
 *   --load 0xADDR    dirección de carga del binario plano
 *   --size WxH       resolución del framebuffer (por defecto 800x600)
 *   --headless       sin ventana: ejecuta, guarda PNG y termina
 *   --png salida.png archivo PNG en modo headless (por defecto fb.png)
 *   --max N          máximo de instrucciones (headless; por defecto 50 millones)
 *   --trace          traza de instrucciones
 *   --vram           abre el visor de VRAM
 *   --fb 0xADDR      dirección del framebuffer (por defecto 0x10000000)
 *   --bus            registra accesos a direcciones sin dispositivo
 *   --input "texto"  escribe texto por el UART (\\n = Enter), p. ej. para el shell de LK
 *
 * @author Slam
 */
public class PCP {

    public static void main(String[] args) throws Exception {
        String path = "./src/pcp/TESTS/lk.elf";//fb_demo.bin|lk.elf|triangle_fixed.bin
        boolean elf = true, headless = false, trace = false, vram = false, mandel = true, pattern = false, busTrace = false;
        int mandelbw = 0; // 0-color | 1-blanco y negro
        long fbBase = -1;
        String input = null;
        long load = 0, max = 50_000_000L;
        int w = 800, h = 600;
        String png = "fb.png";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--elf": elf = true; break;
                case "--headless": headless = true; break;
                case "--trace": trace = true; break;
                case "--vram": vram = true; break;
                case "--bus": busTrace = true; break;
                case "--input": input = args[++i].replace("\\n", "\n"); break;
                case "--fb": fbBase = Long.decode(args[++i]); break;
                case "--mandelbrot": mandel = true; break;
                case "--pattern": pattern = true; break;
                case "--load": load = Long.decode(args[++i]); break;
                case "--max": max = Long.parseLong(args[++i]); break;
                case "--png": png = args[++i]; break;
                case "--size": {
                    String[] p = args[++i].toLowerCase().split("x");
                    w = Integer.parseInt(p[0]);
                    h = Integer.parseInt(p[1]);
                    break;
                }
                default: path = args[i];
            }
        }

        PPCSimulator ppcs = new PPCSimulator(w, h);
        if (fbBase >= 0) {
            ppcs.setFbBase(fbBase);
        }
        ppcs.getBus().setTraceUnmapped(busTrace);
        if (elf) {
            ppcs.loadELF(path);
        } else {
            ppcs.loadBinary(path, load);
        }
        ppcs.setTrace(trace);
        if (input != null) {
            for (char ch : input.toCharArray()) {
                ppcs.getUart().feed(ch);
            }
        }
        FrameBuffer fb = ppcs.getFrameBuffer();

        if (headless) {
            long n = ppcs.runBlocking(max);
            System.out.println();
            fb.savePNG(new File(png));
            System.out.printf("[headless] %d instrucciones, PC=0x%x, halted=%b, PNG=%s%n",
                    ppcs.getCpu().getInstructionCount(), ppcs.getCpu().getPc(), ppcs.getCpu().isHalted(), png);
            return;
        }

        final boolean fVram = vram, fMandel = mandel, fPattern = pattern;
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("JPPC - PowerPC / framebuffer + consola UART");
            FrameBufferPanel panel = new FrameBufferPanel(fb.getWidth(), fb.getHeight(), fb.getFramebuffer());
            double sc = fb.getWidth() > 960 ? 960.0 / fb.getWidth() : 1.0;
            panel.setPreferredSize(new java.awt.Dimension((int) (fb.getWidth() * sc), (int) (fb.getHeight() * sc)));
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            // Consola serie (UART del Xenon y consola 0x0FFF0000): salida arriba, entrada abajo
            javax.swing.JTextArea area = new javax.swing.JTextArea(8, 80);
            area.setEditable(false);
            area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
            area.setBackground(java.awt.Color.BLACK);
            area.setForeground(new java.awt.Color(0xC0FFC0));
            ppcs.getConsole().setListener(ch -> SwingUtilities.invokeLater(() -> {
                area.append(String.valueOf((char) ch));
                area.setCaretPosition(area.getDocument().getLength());
            }));
            // lo ya emitido antes de abrir la ventana
            area.setText(ppcs.getConsole().getOutput());
            javax.swing.JTextField cmdField = new javax.swing.JTextField();
            cmdField.setToolTipText("Escribe un comando (p. ej. para el shell de LK) y pulsa Enter");
            cmdField.addActionListener(e -> {
                for (char ch : (cmdField.getText() + "\n").toCharArray()) {
                    ppcs.getUart().feed(ch);
                }
                cmdField.setText("");
            });
            javax.swing.JPanel south = new javax.swing.JPanel(new java.awt.BorderLayout());
            south.add(new javax.swing.JScrollPane(area), java.awt.BorderLayout.CENTER);
            south.add(cmdField, java.awt.BorderLayout.SOUTH);
            frame.add(panel, java.awt.BorderLayout.CENTER);
            frame.add(south, java.awt.BorderLayout.SOUTH);
            frame.pack();
            frame.setVisible(true);
            cmdField.requestFocusInWindow();
            // ~30 FPS: VRAM (lo que escribe la CPU) -> imagen lineal -> pantalla
            new Timer(33, e -> {
                if (fb.isDirty()) {
                    fb.present();
                }
                panel.repaint();
            }).start();
            if (fMandel) {
                if(mandelbw == 0)                
                    new ExamplesFB(fb).FillMandelbrotColor(50);
                else
                    new ExamplesFB(fb).FillMandelbrotBW(50);                
            }
            if(fPattern){
                new ExamplesFB(fb).FillTestPattern();
            }
            if (fVram) {
                fb.showVRAMViewer();
            }
        });

        ppcs.start();
    }
}
