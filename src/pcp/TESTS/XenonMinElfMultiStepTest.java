package pcp.TESTS;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import pcp.PPCSimulator;
import pcp.cpu.CPU;
import pcp.gpu.FrameBuffer;
import pcp.utils.ELFFile;

public final class XenonMinElfMultiStepTest {
    private XenonMinElfMultiStepTest() {}

    public static void main(String[] args) throws Exception {
        String elfPath = args.length > 0 ? args[0] : "TESTS/xenon_min.elf";
        long maxInstructions = args.length > 1 ? Long.parseLong(args[1]) : 20_000_000L;

        System.out.println("=== XenonMinElfMultiStepTest ===");
        System.out.println("ELF: " + elfPath);
        System.out.println("Max instrucciones: " + maxInstructions);
        System.out.println();

        analyzeELF(elfPath);

        // Crear framebuffer con pitch de 4096 píxeles (128 tiles × 32)
        // para que coincida con lo que el firmware espera
        int visibleW = 1024;
        int visibleH = 768;
        int vramPitch = 4096;  // 128 tiles × 32 píxeles
        int vramH = 1536;      // 48 tiles × 32 píxeles
        
        PPCSimulator simulator = new PPCSimulator(visibleW, visibleH);
        simulator.setFbBase(PPCSimulator.FB_BASE_ELF);
        
        // Sobrescribir el framebuffer con uno de pitch correcto
        FrameBuffer originalFB = simulator.getFrameBuffer();
        FrameBuffer newFB = new FrameBuffer(vramPitch, vramH);
        newFB.setTileMode(2);
        
        // Reemplazar el framebuffer en el simulador
        java.lang.reflect.Field fbField = PPCSimulator.class.getDeclaredField("frameBuffer");
        fbField.setAccessible(true);
        fbField.set(simulator, newFB);
        
        // Re-attach el framebuffer al bus
        long fbBase = simulator.getFbBase();
        simulator.getBus().detachDevice(originalFB);
        simulator.getBus().attachDevice(newFB, fbBase, fbBase + (long) newFB.sizeBytes() - 1);
        
        // Re-attach los registros del framebuffer
        simulator.getBus().detachDevice(originalFB.getRegisters());
        simulator.getBus().attachDevice(newFB.getRegisters(), 0xF0000000L, 0xF000000FL);

        System.out.println("\nCargando ELF...");
        simulator.loadELF(elfPath);

        CPU cpu = simulator.getCpu();
        cpu.setMsr(cpu.getMsr() | 0x8000000000000000L);

        ELFFile elf = new ELFFile(elfPath);
        long entryPoint = elf.getEntryPoint();
        cpu.setPc(entryPoint);

        System.out.println("Entry point ELF: 0x" + Long.toHexString(entryPoint));
        System.out.println("PC inicial: 0x" + Long.toHexString(cpu.getPc()));
        System.out.println("Framebuffer base inicial: 0x" + Long.toHexString(simulator.getFbBase()));
        System.out.println("Resolución visible: " + visibleW + "x" + visibleH);
        System.out.println("VRAM pitch: " + vramPitch + " píxeles (" + vramPitch + " × " + vramH + ")");

        // Ejecutar en pasos
        long[] milestones = {1000000L, 5000000L, 10000000L, 20000000L};
        for (long milestone : milestones) {
            if (milestone <= maxInstructions) {
                System.out.println("\n--- Ejecutando hasta " + milestone + " instrucciones ---");
                long start = System.currentTimeMillis();
                long executed = simulator.runBlocking(milestone);
                long elapsed = System.currentTimeMillis() - start;
                
                FrameBuffer fb = simulator.getFrameBuffer();
                System.out.println("PC: 0x" + Long.toHexString(cpu.getPc()));
                System.out.println("Halted: " + cpu.isHalted());
                System.out.println("Tile mode: " + fb.getTileMode());
                System.out.println("Pitch bytes: " + fb.getPitchBytes());
                
                // Llamar present() para detile, luego guardar PNG
                fb.present();
                fb.savePNG(new File("xenon_min_step" + (milestone / 1000000) + "M.png"));
                System.out.println("PNG guardado: xenon_min_step" + (milestone / 1000000) + "M.png");
                
                // Analizar framebuffer lineal (después de detile)
                analyzeLinearFB(fb, visibleW, visibleH, "xenon_min_step" + (milestone / 1000000) + "M_detail.png");
            }
        }

        System.out.println("\n=== RESUMEN FINAL ===");
        System.out.println("ELF: " + elfPath);
        System.out.println("Entry: 0x" + Long.toHexString(entryPoint));
        System.out.println("Final PC: 0x" + Long.toHexString(cpu.getPc()));
        System.out.println("Halted: " + cpu.isHalted());
    }

    private static void analyzeELF(String path) throws Exception {
        ELFFile elf = new ELFFile(path);
        byte[] data = elf.elfData;

        System.out.println("=== ELF Analysis ===");
        System.out.println("Size: " + data.length + " bytes");
        System.out.println("Class: " + (elf.is64Bit() ? "64-bit" : "32-bit"));
        System.out.println("Endian: " + (elf.isBigEndian() ? "Big" : "Little"));
        System.out.println("Machine: " + (elf.getMachine() == ELFFile.EM_PPC64 ? "PPC64" : elf.getMachine() == ELFFile.EM_PPC ? "PPC" : "Unknown"));
        System.out.println("Entry point: 0x" + Long.toHexString(elf.getEntryPoint()));
        System.out.println("Segments:");

        for (int i = 0; i < elf.getSegments().size(); i++) {
            var seg = elf.getSegments().get(i);
            System.out.printf("  [%d] phys=0x%08X virt=0x%08X file=0x%08X size=%d mem=%d%n",
                    i, seg.physicalAddress, seg.virtualAddress, seg.fileOffset, seg.fileSize, seg.memorySize);
        }
    }

    private static void analyzeLinearFB(FrameBuffer fb, int visibleW, int visibleH, String outputPng) {
        int[] linearBuffer = fb.getFramebuffer();
        int totalPixels = visibleW * visibleH;

        int nonZero = 0;
        int nonBlack = 0;
        int maxR = 0, maxG = 0, maxB = 0;
        int maxA = 0;

        for (int i = 0; i < totalPixels; i++) {
            int pixel = linearBuffer[i];
            if (pixel != 0) {
                nonZero++;
                int a = (pixel >> 24) & 0xFF;
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;
                if (r > 0 || g > 0 || b > 0) {
                    nonBlack++;
                    if (r > maxR) maxR = r;
                    if (g > maxG) maxG = g;
                    if (b > maxB) maxB = b;
                    if (a > maxA) maxA = a;
                }
            }
        }

        System.out.println("Framebuffer lineal (después de detile) (" + visibleW + "x" + visibleH + " = " + totalPixels + " pixels):");
        System.out.println("  Non-zero pixels: " + nonZero + " (" + String.format("%.2f", 100.0 * nonZero / totalPixels) + "%)");
        System.out.println("  Non-black pixels: " + nonBlack + " (" + String.format("%.2f", 100.0 * nonBlack / totalPixels) + "%)");
        System.out.println("  Max RGBA: R=" + maxR + " G=" + maxG + " B=" + maxB + " A=" + maxA);

        // Mostrar algunos píxeles de ejemplo
        System.out.println("\n  Sample pixels (first 15 non-zero):");
        int count = 0;
        for (int i = 0; i < totalPixels && count < 15; i++) {
            if (linearBuffer[i] != 0) {
                int row = i / visibleW;
                int col = i % visibleW;
                System.out.printf("    [%d,%d] = 0x%08X%n", col, row, linearBuffer[i]);
                count++;
            }
        }
    }
}
