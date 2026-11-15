package pcp;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.Arrays;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import pcp.cpu.CPU;
import pcp.gpu.ExamplesFB;
import pcp.gpu.FrameBuffer;
import pcp.gpu.FrameBufferPanel;
import pcp.xenon.XenonBootCrypto;
import pcp.xenon.XenonBootImage;
import pcp.xenon.XenonBootState;
import pcp.xenon.XenonBootStrapper;
import pcp.xenon.XenonNandParser;
import pcp.xenon.XenonFlashFs;
import pcp.xenon.XenonPeLoader;
import pcp.xenon.TKernel;
import pcp.xenon.BootAnimationOverlay;

/**
 * Uso: java pcp.PCP [archivo] [opciones] --1bl carga y ejecuta el Boot ROM 1BL
 * Xenon --elf el archivo es un ELF de PowerPC (por defecto: binario plano en
 * 0x0) --ppc32 ejecuta un binario plano con MSR[SF]=0 (modo PPC de 32 bits)
 * --load 0xADDR dirección de carga del binario plano --size WxH resolución del
 * framebuffer (por defecto 1024x768) --headless sin ventana: ejecuta, guarda
 * PNG y termina --png salida.png archivo PNG en modo headless (por defecto
 * fb.png) --console-png salida.png captura UART como terminal de diagnóstico
 * --nand archivo.bin imagen NAND Xenon small-block (solo lectura) --max N
 * máximo de instrucciones (headless; por defecto 50 millones) --trace traza de
 * instrucciones --vram abre el visor de VRAM --fb 0xADDR dirección del
 * framebuffer (por defecto 0x10000000) --threads N hilos hardware (1..6; XeLL
 * se detecta y usa 6) --bus registra accesos a direcciones sin dispositivo
 * --bus-range A:B limita --bus al rango hexadecimal inclusivo --input "texto"
 * --xam carga ./nandDump/image/xam.xex.pe e inicia su contexto PPC32 --input
 * escribe texto por el UART (\\n = Enter), p. ej. para el shell de LK
 *
 * @author Slam
 */
public class PCP {

    /**
     * Parse signed decimal or full-width unsigned hexadecimal addresses.
     */
    public static long parseAddress(String value) {
        String s = value.trim().replace("_", "");
        if (s.startsWith("0x") || s.startsWith("0X")) {
            return Long.parseUnsignedLong(s.substring(2), 16);
        }
        if (s.startsWith("-") && (s.startsWith("-0x") || s.startsWith("-0X"))) {
            return -Long.parseUnsignedLong(s.substring(3), 16);
        }
        return Long.parseLong(s);
    }

    public static void testXenonBootCrypto() {

        // RC4: RFC 6229, clave 01 02 03 04 05.
        byte[] rc4Key = XenonBootCrypto.fromHex("0102030405");
        byte[] rc4Input = new byte[16];
        byte[] rc4Result = XenonBootCrypto.rc4(rc4Key, rc4Input);
        String expectedRC4 = "B2396305F03DC027CCC3524A0A1118A8";
        if (!expectedRC4.equals(XenonBootCrypto.toHex(rc4Result))) {
            throw new AssertionError("RC4 incorrecto");
        }

        // SHA-1: RFC 3174.
        byte[] shaInput = "abc".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        String expectedSHA1 = "A9993E364706816ABA3E25717850C26C9CD0D89D";
        String actualSHA1 = XenonBootCrypto.toHex(XenonBootCrypto.sha1(shaInput));
        if (!expectedSHA1.equals(actualSHA1)) {
            throw new AssertionError("SHA-1 incorrecto");
        }

        // HMAC-SHA1: RFC 2202, test case 1.
        byte[] hmacKey = new byte[20];
        Arrays.fill(hmacKey, (byte) 0x0B);

        byte[] hmacMessage = "Hi There".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        String expectedHMAC = "B617318655057264E28BC0B6FB378C8EF146BE00";
        String actualHMAC = XenonBootCrypto.toHex(XenonBootCrypto.hmacSha1(hmacKey, hmacMessage));
        if (!expectedHMAC.equals(actualHMAC)) {
            throw new AssertionError("HMAC-SHA1 incorrecto");
        }

        System.out.println("RC4: OK");
        System.out.println("SHA-1: OK");
        System.out.println("HMAC-SHA1: OK");
    }

    /**
     * Presenta un recurso gráfico ORIGINAL extraído de dash.xex cuando el
     * guest todavía no consigue emitir comandos Xenos/XUI. No simula el shell:
     * muestra literalmente un AppTile de la NAND para verificar el camino FB.
     */
    private static void showNandTileFallback(FrameBuffer fb, Path resource) throws Exception {
        if (!Files.isRegularFile(resource)) {
            System.out.println("[NAND-TILE] recurso ausente: " + resource);
            return;
        }
        BufferedImage src = ImageIO.read(resource.toFile());
        if (src == null) throw new IllegalStateException("Formato gráfico no soportado: " + resource);
        int tileW = Math.min(320, fb.getWidth() - 32);
        int tileH = Math.min(320, fb.getHeight() - 32);
        BufferedImage scaled = new BufferedImage(tileW, tileH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, tileW, tileH, null);
        g.dispose();
        int ox = 48, oy = 112;
        for (int y = 0; y < tileH && oy + y < fb.getHeight(); y++) {
            for (int x = 0; x < tileW && ox + x < fb.getWidth(); x++) {
                int argb = scaled.getRGB(x, y);
                if ((argb >>> 24) != 0) fb.setPixel(ox + x, oy + y, argb & 0x00FFFFFF);
            }
        }
        fb.present();
        System.out.printf("[NAND-TILE] recurso original visible: %s (%dx%d) en FB %dx%d%n",
                resource, src.getWidth(), src.getHeight(), fb.getWidth(), fb.getHeight());
    }

    public static void main(String[] args) throws Exception {
        String path = "./src/pcp/TESTS/lk.elf";
        String png = "fb.png";
        String consolePng = null;
        String fusesPath = "./fuses.txt";
        String nandPath = "./src/pcp/TESTS/nand.bin";
        String oneBLPath = "./src/pcp/TESTS/1bl.bin";
        java.util.List<long[]> socRegs = new java.util.ArrayList<>();
        String dumpSram = null;
        boolean bootSeq = false;
        boolean elf = false, headless = false, trace = false, vram = false, mandel = false, busTrace = false, oneBl = true, ppc32 = false, xamMode = false, nandDashboardMode = false;
        long fbBase = -1;
        String input = null;
        java.util.List<Long> watchList = new java.util.ArrayList<>();
        int threads = -1;
        long tb = -1;
        long load = 0, max = 50_000_000L;
        int w = 800, h = 600;
        long[] busRange = null;

        //Testing
        /*
        XenonNandParser nand = XenonNandParser.load(Path.of(nandPath));
        System.out.printf("RAW: 0x%X%n", nand.getPhysicalSize());
        System.out.printf("PAGES: %d%n", nand.getPhysicalPages());
        System.out.printf("LOGICAL: 0x%X%n", nand.getLogicalSize());
        System.out.printf("BLOCKS: %d%n", nand.getBlockCount());

        byte[] cbAData = nand.extractCBA();
        XenonBootImage cbA = new XenonBootImage(XenonBootImage.Type.CB_A, cbAData);
        cbA.setVersion(0x0788);

        System.out.println("TYPE: " + cbA.getType());
        System.out.printf("VERSION: 0x%04X%n", cbA.getVersion());
        System.out.printf("SIZE: 0x%X%n", cbA.getImageSize());
        System.out.println("STATE: " + cbA.getState());

        byte[] cbAA = nand.extractCBA();

        System.out.printf("CB_A: 0x%X bytes%n", cbAA.length);
        //Pruebas crypto
        testXenonBootCrypto();
        
        //BootStrapper
        XenonBootStrapper bootstrapper = XenonBootStrapper.load(Path.of(nandPath));
        XenonBootImage cbAa = bootstrapper.prepareCBA("DD88AD0C9ED669E7B56794FB68563EFA".getBytes());
        XenonBootState state = bootstrapper.buildBootState();
        System.out.println("TYPE: " + cbAa.getType());
        System.out.printf("VERSION: 0x%04X%n", cbAa.getVersion());
        System.out.printf("SIZE: 0x%X%n", cbAa.getImageSize());
        System.out.println("STATE: " + cbAa.getState());
        System.out.printf("ENTRY POINT: 0x%016X%n", state.getEntryPoint());
         */
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--elf":
                    elf = true;
                    break;
                case "--1bl":
                    oneBl = true;
                    break;
                case "--xam":
                    xamMode = true;
                    elf = false;
                    break;
                case "--nand-dashboard":
                    nandDashboardMode = true;
                    elf = false;
                    break;
                case "--boot-seq-model":
                    bootSeq = true;
                    break;
                case "--ppc32":
                    ppc32 = true;
                    break;
                case "--headless":
                    headless = true;
                    break;
                case "--trace":
                    trace = true;
                    break;
                case "--vram":
                    vram = true;
                    break;
                case "--soc-reg": {
                    String[] kv = args[++i].split("=", 2);
                    if (kv.length != 2) {
                        throw new IllegalArgumentException("--soc-reg requiere DIR=VALOR");
                    }
                    socRegs.add(new long[]{Long.parseUnsignedLong(kv[0].replaceFirst("^0[xX]", ""), 16), Long.parseUnsignedLong(kv[1].replaceFirst("^0[xX]", ""), 16)});
                    break;
                }
                case "--dump-sram":
                    dumpSram = args[++i];
                    break;

                case "--fuses":
                    fusesPath = args[++i];
                    break;
                case "--bus":
                    busTrace = true;
                    break;
                case "--bus-range": {
                    String[] r = args[++i].split(":", 2);
                    if (r.length != 2) {
                        throw new IllegalArgumentException("--bus-range requiere INICIO:FIN");
                    }
                    busRange = new long[]{parseAddress(r[0]), parseAddress(r[1])};
                    busTrace = true;
                    break;
                }
                case "--tb":
                    tb = Long.parseLong(args[++i]);
                    break;
                case "--watch":
                    watchList.add(parseAddress(args[++i]));
                    break;
                case "--threads":
                    threads = Integer.parseInt(args[++i]);
                    break;
                case "--input":
                    input = args[++i].replace("\\n", "\n");
                    break;
                case "--fb":
                    fbBase = parseAddress(args[++i]);
                    break;
                case "--mandelbrot":
                    mandel = true;
                    break;
                case "--load":
                    load = parseAddress(args[++i]);
                    break;
                case "--max":
                    max = Long.parseLong(args[++i]);
                    break;
                case "--png":
                    png = args[++i];
                    break;
                case "--console-png":
                    consolePng = args[++i];
                    break;
                case "--nand":
                    nandPath = args[++i];
                    break;
                case "--size": {
                    String[] p = args[++i].toLowerCase().split("x");
                    w = Integer.parseInt(p[0]);
                    h = Integer.parseInt(p[1]);
                    break;
                }
                default:
                    path = args[i];
            }
        }

        if (nandDashboardMode) {
            w = 1280;
            h = 720;
            if (nandPath == null) nandPath = "./nandDump/original/nand.bin";
            if (fbBase < 0) fbBase = PPCSimulator.FB_BASE_ELF;
        }
        PPCSimulator ppcs = new PPCSimulator(w, h);
        XenonPeLoader.LoadedImage xam = null;
        TKernel runtimeKernel = null;
        XenonPeLoader.LoadedImage bootanimImage = null;
        if (nandDashboardMode) {
            XenonPeLoader loader = new XenonPeLoader();
            Path root = Paths.get("./nandDump/image");
            Path xamPath = root.resolve("xam.xex.pe");
            Path animPath = root.resolve("bootanim.xex.pe");
            for (Path required : new Path[]{xamPath, animPath}) {
                if (!Files.isRegularFile(required)) {
                    throw new IllegalStateException("Falta imagen NAND extraída: " + required.toAbsolutePath());
                }
            }
            xam = loader.load(xamPath, ppcs.getMemory());
            XenonPeLoader.LoadedImage bootanim = loader.load(animPath, ppcs.getMemory());
            runtimeKernel = new TKernel(ppcs);
            XenonNandParser retailNand = XenonNandParser.load(Paths.get(nandPath));
            XenonFlashFs flashFs = XenonFlashFs.open(retailNand);
            runtimeKernel.attachFlashFileSystem(flashFs);
            Path imports = Paths.get("./nandDump/extracted/manifests/imports.csv");
            int xamImports = runtimeKernel.installImports(imports, "xam.xex");
            int animImports = runtimeKernel.installImports(imports, "bootanim.xex");
            runtimeKernel.registerBootAnimationModule(bootanim,
                    Paths.get("./nandDump/fs/bootanim.xex"),
                    Paths.get("./nandDump/extracted/resources/bootanim.xex"));
            System.out.printf("[NAND-BOOT] XAM=%08X BOOTANIM=%08X%n",
                    xam.getEntryPoint(), bootanim.getEntryPoint());
            System.out.printf("[NAND-BOOT] IMPORTS XAM=%d BOOTANIM=%d; FlashFS entries=%d%n",
                    xamImports, animImports, flashFs.entries().size());
            if (!bootanim.hasEntryPoint()) {
                System.out.println("[NAND-BOOT] bootanim es módulo kernel-managed; no se fuerza un PC ficticio.");
            }
            ppcs.setFbBase(fbBase);
            ppcs.initializeXenonFramebuffer();
            File fbInit = new File("fb-init.png");
            ppcs.getFrameBuffer().savePNG(fbInit);
            System.out.println("[NAND-BOOT] Captura de inicialización FB: " + fbInit.getAbsolutePath());
            // El control permanece en XAM; el dashboard se abrirá mediante sus
            // solicitudes de archivos al kernel, nunca mediante un salto host.
            path = xamPath.toString();
            xamMode = true;
        } else if (xamMode) {
            XenonPeLoader loader = new XenonPeLoader();
            Path xamPath = Paths.get("./nandDump/image/xam.xex.pe");
            if (!Files.isRegularFile(xamPath)) {
                throw new IllegalStateException("No existe la imagen PE de XAM: " + xamPath.toAbsolutePath());
            }
            xam = loader.load(xamPath, ppcs.getMemory());
            Path bootanimPath = Paths.get("./nandDump/image/bootanim.xex.pe");
            if (!Files.isRegularFile(bootanimPath)) {
                throw new IllegalStateException("No existe bootanim.xex.pe: " + bootanimPath.toAbsolutePath());
            }
            bootanimImage = loader.load(bootanimPath, ppcs.getMemory());
            runtimeKernel = new TKernel(ppcs);
            Path imports = Paths.get("./nandDump/extracted/manifests/imports.csv");
            int xamImports = runtimeKernel.installImports(imports, "xam.xex");
            int animImports = runtimeKernel.installImports(imports, "bootanim.xex");
            int bootanimResources = runtimeKernel.registerBootAnimationModule(bootanimImage,
                    Paths.get("./nandDump/fs/bootanim.xex"),
                    Paths.get("./nandDump/extracted/resources/bootanim.xex"));
            System.out.printf("[XAM-BOOT] BOOTANIM resources mapped=%d%n", bootanimResources);
            System.out.printf("[XAM-BOOT] BASE=%08X ENTRY=%08X SIZE=%08X%n",
                    xam.getImageBase(), xam.getEntryPoint(), xam.getSizeOfImage());
            System.out.printf("[XAM-BOOT] BOOTANIM BASE=%08X ENTRY=%08X SIZE=%08X HAS_ENTRY=%b%n",
                    bootanimImage.getImageBase(), bootanimImage.getEntryPoint(), bootanimImage.getSizeOfImage(), bootanimImage.hasEntryPoint());
            System.out.printf("[XAM-BOOT] IMPORTS XAM=%d BOOTANIM=%d; PPC32 sc dispatch enabled%n",
                    xamImports, animImports);
        }
        if (fusesPath != null) {
            ppcs.loadFuses(Path.of(fusesPath));
        }
        if (fbBase >= 0) {
            ppcs.setFbBase(fbBase);
        }
        if (nandPath != null) {
            Path nandFile = Path.of(nandPath);

            if (Files.isRegularFile(nandFile)) {
                ppcs.loadNand(nandFile);
            } else {
                System.err.printf("[NAND] Imagen no encontrada: %s%n", nandFile.toAbsolutePath());
                System.err.println("[NAND] Se continuará sin imagen; las lecturas devolverán 0xFF.");
            }
        }
        ppcs.getBus().setTraceUnmapped(busTrace);
        if (busRange != null) {
            ppcs.getBus().setTraceRange(busRange[0], busRange[1]);
        }
        ppcs.setSprTrace(busTrace);
        ppcs.setBootControllerTrace(true);
        ppcs.setBootSequenceModel(bootSeq);
        for (long[] r : socRegs) {
            ppcs.addSocRegister(r[0], r[1]);
        }

        if (nandDashboardMode) {
            // La imagen dash.xex.pe y su contexto ya están cargados arriba.
        } else if (oneBl) {
            if (oneBLPath == null) {
                throw new IllegalArgumentException("--1bl requiere configurar la ruta de 1BL");
            }
            ppcs.load1BL(oneBLPath);
        } else if (xamMode || nandDashboardMode) {
            long xamThreadExit = 0x9FFC0010L;
            long aniStartThunk = runtimeKernel.getImportThunk("xam.xex", "xboxkrnl.exe", 678);
            runtimeKernel.installBootAnimationStartupHook(ppcs.getCpu(), 0x9FF00000L,
                    aniStartThunk, xam.getEntryPoint(), xamThreadExit);
            System.out.printf("[XAM-BOOT] Guest shim ejecutará import ordinal 678 antes de entrar en XAM.%n");
        } else if (elf) {
            ppcs.loadELF(path);
        } else {
            ppcs.loadBinary(path, load);
            if (ppc32) {
                ppcs.getCpu().setMsr(ppcs.getCpu().getMsr() & 0x7FFFFFFFFFFFFFFFL);
            }
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
        BootAnimationOverlay bootAnimationOverlay = null;
        if (xamMode) {
            Path logoPath = Paths.get("./nandDump/extracted/resources/xam.xex/xam/xenonLogo.png");
            if (Files.isRegularFile(logoPath)) {
                try {
                    bootAnimationOverlay = new BootAnimationOverlay(logoPath);
                    System.out.println("[BOOTANIM-SCREEN] Recurso gráfico original XAM cargado.");
                } catch (Exception e) {
                    System.err.println("[BOOTANIM-SCREEN] Error cargando recurso XAM: " + e.getMessage());
                }
            } else {
                System.err.println("[BOOTANIM-SCREEN] No existe el logo XAM: " + logoPath);
            }
        }
        // El modo headless no simula ni sustituye la salida guest con gráficos host.

        if (headless) {
            long n = ppcs.runBlocking(max);
            System.out.println();
            int nz = 0;
            for (int v : fb.getGpuTiledBuffer()) {
                if (v != 0) {
                    nz++;
                }
            }
            System.out.println("[headless] VRAM palabras no nulas: " + nz + " (base FB 0x" + Long.toHexString(ppcs.getFbBase()) + ")");
            fb.savePNG(new File(png));
            if (xamMode) {
                File tracePng = new File("xam-execution-trace.png");
                pcp.gpu.ExecutionTraceSnapshot.save(tracePng, ppcs.getCpu());
                System.out.println("[headless] NAND guest execution trace=" + tracePng.getAbsolutePath());
            }
            if (consolePng != null) {
                pcp.gpu.ConsoleSnapshot.save(new File(consolePng), ppcs.getConsole().getOutput(), w, h);
                System.out.println("[headless] UART snapshot=" + consolePng);
            }
            System.out.printf("[headless] %d instrucciones, PC=0x%x, halted=%b, PNG=%s%n",
                    ppcs.getCpu().getInstructionCount(), ppcs.getCpu().getPc(), ppcs.getCpu().isHalted(), png);
            return;
        }

        final boolean fVram = vram, fMandel = mandel;
        final TKernel fKernel = runtimeKernel;
        final BootAnimationOverlay fBootOverlay = bootAnimationOverlay;
        final pcp.xenon.BootAnimationAudio bootAudio = pcp.xenon.BootAnimationAudio.fromDefaultNand();
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
            // Monitor visible de instrucciones ejecutadas desde la imagen PE extraída de NAND.
            javax.swing.JTextArea execArea = new javax.swing.JTextArea(28, 34);
            execArea.setEditable(false);
            execArea.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
            execArea.setBackground(new java.awt.Color(0x101820));
            execArea.setForeground(new java.awt.Color(0x8FE3A0));
            javax.swing.JPanel execPanel = new javax.swing.JPanel(new java.awt.BorderLayout());
            execPanel.add(new javax.swing.JLabel("XENON GUEST EXECUTION — NAND CODE"), java.awt.BorderLayout.NORTH);
            execPanel.add(new javax.swing.JScrollPane(execArea), java.awt.BorderLayout.CENTER);
            javax.swing.JSplitPane center = new javax.swing.JSplitPane(
                    javax.swing.JSplitPane.HORIZONTAL_SPLIT, panel, execPanel);
            center.setResizeWeight(0.78);
            frame.add(center, java.awt.BorderLayout.CENTER);
            frame.add(south, java.awt.BorderLayout.SOUTH);
            frame.pack();
            frame.setVisible(true);
            cmdField.requestFocusInWindow();
            // ~30 FPS: VRAM (lo que escribe la CPU) -> imagen lineal -> pantalla
            new Timer(33, e -> {
                if (fb.isDirty()) fb.present();
                if (fKernel != null && fKernel.isBootAnimationRequested()) {
                    bootAudio.startOnce();
                    if (fBootOverlay != null) {
                        panel.setOverlayFrame(fBootOverlay.render(fb.getWidth(), fb.getHeight()));
                    }
                } else {
                    panel.setOverlayFrame(null);
                }
                panel.repaint();
                long[][] history = ppcs.getCpu().getExecutionTrace();
                StringBuilder traceText = new StringBuilder(8192);
                traceText.append(String.format("PC=%08X  INSTRUCTIONS=%d  HALTED=%s%n%n",
                        ppcs.getCpu().getPc(), ppcs.getCpu().getInstructionCount(),
                        ppcs.getCpu().isHalted()));
                traceText.append("   ADDRESS    PPC WORD\n");
                traceText.append("------------------------\n");
                for (long[] item : history) {
                    traceText.append(String.format("   %08X   %08X%n", item[0], item[1]));
                }
                execArea.setText(traceText.toString());
                execArea.setCaretPosition(execArea.getDocument().getLength());
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
