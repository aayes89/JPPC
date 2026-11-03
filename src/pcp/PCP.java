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
 *   --console-png salida.png captura UART como terminal de diagnóstico
 *   --nand archivo.bin   imagen NAND Xenon small-block (solo lectura)
 *   --max N          máximo de instrucciones (headless; por defecto 50 millones)
 *   --trace          traza de instrucciones
 *   --vram           abre el visor de VRAM
 *   --fb 0xADDR      dirección del framebuffer (por defecto 0x10000000)
 *   --threads N      hilos hardware (1..6; XeLL se detecta y usa 6)
 *   --bus            registra accesos a direcciones sin dispositivo
 *   --input "texto"  escribe texto por el UART (\\n = Enter), p. ej. para el shell de LK
 *
 * @author Slam
 */
public class PCP {

    public static void main(String[] args) throws Exception {
        String path = "./src/pcp/TESTS/fb_demo.bin";
        boolean elf = false, headless = false, trace = false, vram = false, mandel = false, busTrace = false;
        long fbBase = -1;
        String input = null;
        java.util.List<Long> watchList = new java.util.ArrayList<>();
        int threads = -1;
        long tb = -1;
        long load = 0, max = 50_000_000L;
        int w = 800, h = 600;
        String png = "fb.png";
        String consolePng = null;
        String nandPath = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--elf": elf = true; break;
                case "--headless": headless = true; break;
                case "--trace": trace = true; break;
                case "--vram": vram = true; break;
                case "--bus": busTrace = true; break;
                case "--tb": tb = Long.parseLong(args[++i]); break;
                case "--watch": watchList.add(Long.decode(args[++i])); break;
                case "--threads": threads = Integer.parseInt(args[++i]); break;
                case "--input": input = args[++i].replace("\\n", "\n"); break;
                case "--fb": fbBase = Long.decode(args[++i]); break;
                case "--mandelbrot": mandel = true; break;
                case "--load": load = Long.decode(args[++i]); break;
                case "--max": max = Long.parseLong(args[++i]); break;
                case "--png": png = args[++i]; break;
                case "--console-png": consolePng = args[++i]; break;
                case "--nand": nandPath = args[++i]; break;
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
        if (nandPath != null) ppcs.loadNand(java.nio.file.Paths.get(nandPath));
        ppcs.getBus().setTraceUnmapped(busTrace);
        ppcs.setSprTrace(busTrace);
        if (elf) {
            ppcs.loadELF(path);
        } else {
            ppcs.loadBinary(path, load);
        }
        if (threads > 0) {
            ppcs.setThreads(threads);
        }
        if (tb > 0) {
            ppcs.setTb(tb);
        }
        for (long wa : watchList) {
            ppcs.addWatch(wa);
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
            int nz = 0;
            for (int v : fb.getGpuTiledBuffer()) {
                if (v != 0) nz++;
            }
            System.out.println("[headless] VRAM palabras no nulas: " + nz + " (base FB 0x" + Long.toHexString(ppcs.getFbBase()) + ")");
            fb.savePNG(new File(png));
            if (consolePng != null) {
                pcp.gpu.ConsoleSnapshot.save(new File(consolePng), ppcs.getConsole().getOutput(), w, h);
                System.out.println("[headless] UART snapshot=" + consolePng);
            }
            System.out.printf("[headless] %d instrucciones, PC=0x%x, halted=%b, PNG=%s%n",
                    ppcs.getCpu().getInstructionCount(), ppcs.getCpu().getPc(), ppcs.getCpu().isHalted(), png);
            return;
        }

        final boolean fVram = vram, fMandel = mandel;
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
                new ExamplesFB(fb).FillMandelbrotColor(50);
            }
            if (fVram) {
                fb.showVRAMViewer();
            }
        });

        ppcs.start();
    }
}
