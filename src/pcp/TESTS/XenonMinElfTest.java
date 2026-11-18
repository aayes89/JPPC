package pcp.TESTS;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import pcp.PPCSimulator;
import pcp.cpu.CPU;
import pcp.gpu.FrameBuffer;
import pcp.utils.ELFFile;

public final class XenonMinElfTest {
    private XenonMinElfTest() {}

    public static void main(String[] args) throws Exception {
        String elfPath = args.length > 0 ? args[0] : "TESTS/xenon_min.elf";
        long maxInstructions = args.length > 1 ? Long.parseLong(args[1]) : 5_000_000L;

        System.out.println("=== XenonMinElfTest ===");
        System.out.println("ELF: " + elfPath);
        System.out.println("Max instrucciones: " + maxInstructions);
        System.out.println();

        analyzeELF(elfPath);

        PPCSimulator simulator = new PPCSimulator(1280, 720);
        simulator.setFbBase(PPCSimulator.FB_BASE_ELF);

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
        System.out.println("Resolución: " + simulator.getFrameBuffer().getWidth() + "x" + simulator.getFrameBuffer().getHeight());

        System.out.println("\nEjecutando...");
        long start = System.currentTimeMillis();
        long executed = simulator.runBlocking(maxInstructions);
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("Instrucciones ejecutadas: " + executed);
        System.out.println("Tiempo real: " + elapsed + " ms");
        System.out.println("PC final: 0x" + Long.toHexString(cpu.getPc()));
        System.out.println("CPU halted: " + cpu.isHalted());
        System.out.println("Instrucciones totales: " + cpu.getInstructionCount());

        FrameBuffer fb = simulator.getFrameBuffer();
        System.out.println("\n=== Framebuffer State ===");
        System.out.println("Dirty: " + fb.isDirty());
        System.out.println("Tile mode: " + fb.getTileMode());
        System.out.println("Pitch bytes: " + fb.getPitchBytes());

        // Leer el buffer interno del FrameBuffer (VRAM)
        System.out.println("\n=== Analizando VRAM del FrameBuffer ===");
        analyzeVRAM(fb, "xenon_min_vram.png");

        // Capturar framebuffer actual
        System.out.println("\n=== Capturando framebuffer actual ===");
        fb.present();
        fb.savePNG(new File("xenon_min_current_fb.png"));
        System.out.println("PNG guardado: xenon_min_current_fb.png");

        System.out.println("\n=== RESUMEN ===");
        System.out.println("ELF: " + elfPath);
        System.out.println("Entry: 0x" + Long.toHexString(entryPoint));
        System.out.println("Executed: " + executed + " instructions in " + elapsed + "ms");
        System.out.println("Final PC: 0x" + Long.toHexString(cpu.getPc()));
        System.out.println("Halted: " + cpu.isHalted());
        System.out.println("Output VRAM: xenon_min_vram.png");
        System.out.println("Output current: xenon_min_current_fb.png");
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

        System.out.println("\nXeLL markers:");
        String[] markers = {"Xenon Linux Loader", "XeLL - Xenon linux loader", "XeLL Reloaded", "Free60.org XeLL", "XeLL Main Menu", "Xenos FB with", "0x9e000000", "0x9E000000"};
        for (String marker : markers) {
            int pos = indexOf(data, marker);
            System.out.printf("  %s: %s%n", marker, pos >= 0 ? "FOUND @ 0x" + Integer.toHexString(pos) : "NOT FOUND");
        }
    }

    private static void analyzeVRAM(FrameBuffer fb, String outputPng) {
        int[] gpuBuffer = fb.getGpuTiledBuffer();
        int w = fb.getWidth();
        int h = fb.getHeight();
        int totalPixels = w * h;

        int nonZero = 0;
        int nonBlack = 0;
        int maxR = 0, maxG = 0, maxB = 0;

        for (int i = 0; i < gpuBuffer.length; i++) {
            int pixel = gpuBuffer[i];
            if (pixel != 0) {
                nonZero++;
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;
                if (r > 0 || g > 0 || b > 0) {
                    nonBlack++;
                    if (r > maxR) maxR = r;
                    if (g > maxG) maxG = g;
                    if (b > maxB) maxB = b;
                }
            }
        }

        System.out.println("VRAM del FrameBuffer (" + w + "x" + h + " = " + totalPixels + " pixels):");
        System.out.println("  Non-zero pixels: " + nonZero + " (" + String.format("%.2f", 100.0 * nonZero / totalPixels) + "%)");
        System.out.println("  Non-black pixels: " + nonBlack + " (" + String.format("%.2f", 100.0 * nonBlack / totalPixels) + "%)");
        System.out.println("  Max RGB: R=" + maxR + " G=" + maxG + " B=" + maxB);

        // Mostrar algunos píxeles de ejemplo
        System.out.println("\n  Sample pixels (first 10 non-zero):");
        int count = 0;
        for (int i = 0; i < gpuBuffer.length && count < 10; i++) {
            if (gpuBuffer[i] != 0) {
                int row = i / w;
                int col = i % w;
                System.out.printf("    [%d,%d] = 0x%08X%n", col, row, gpuBuffer[i]);
                count++;
            }
        }

        // Guardar como PNG (modo lineal)
        System.out.println("\nGuardando VRAM como PNG (lineal)...");
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                int pixel = gpuBuffer[idx];
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        try {
            ImageIO.write(image, "png", new File(outputPng));
            System.out.println("PNG guardado: " + Path.of(outputPng).toAbsolutePath());
        } catch (Exception e) {
            System.err.println("Error guardando PNG: " + e.getMessage());
        }
    }

    private static int indexOf(byte[] data, String value) {
        byte[] needle = value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        outer: for (int i = 0; i <= data.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
