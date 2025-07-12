package pcp;

import javax.swing.JFrame;
import pcp.gpu.FrameBuffer;
import pcp.gpu.FrameBufferPanel;

/**
 *
 * @author Slam
 */
import javax.swing.*;
import java.nio.file.Paths;
import pcp.gpu.ExamplesFB;

public class PCP {

    public static void main(String[] args) {
        boolean debug = false;
        boolean useELF = false; // Cambiá esto a true para probar un ELF

        String path = useELF
                ? "./src/pcp/TESTS/lk.elf"
                : "./src/pcp/TESTS/fb_800x600_hola.bin"; //triangle.bin";

        PPCSimulator ppcs = new PPCSimulator();

        if (useELF) {
            ppcs.loadELF(Paths.get(path).toString());
        } else {
            ppcs.loadBinary(Paths.get(path).toString());
        }

        FrameBuffer fb = ppcs.getFrameBuffer();

        // GUI para visualizar framebuffer
        JFrame frame = new JFrame("PowerPC FrameBuffer Viewer");
        FrameBufferPanel panel = new FrameBufferPanel(fb.getWidth(), fb.getHeight(), fb.getGpuTiledBuffer());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(panel);
        frame.pack();
        frame.setVisible(true);

        // Timer para repintado cada 33ms (aprox 30 FPS)
        new Timer(33, e -> panel.repaint()).start();
        // prueba        
        //fb.renderTest();
        //ExamplesFB efb = new ExamplesFB(fb);
        //efb.FillTestPattern();
        //efb.FillMandelbrotColor(50);
        //fb.showVRAMViewer(); // ← lanzará el visor VRAM

        // Hilo de ejecución del simulador
        new Thread(() -> {
            if (debug) {
                ppcs.setDebugMode(true);
                for (int i = 0; i < 1000; i++) {
                    ppcs.step();
                    try {
                        Thread.sleep(10); // Delay opcional para ver el framebuffer paso a paso
                    } catch (InterruptedException ex) {
                        break;
                    }
                }
            } else {
                ppcs.start();
            }

            // Mostrar consola al final
            System.out.println("\n--- Console Output ---");
            System.out.println(ppcs.getConsole().getOutput());
        }).start();
    }
}
