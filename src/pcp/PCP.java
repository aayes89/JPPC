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
import pcp.xenon.XenonBootCredentials;
import pcp.xenon.XenonBootImage;
import pcp.xenon.XenonBootStrapper;
import pcp.xenon.XenonNandParser;
import pcp.xenon.XenonFlashFs;
import pcp.xenon.XenonPeLoader;
import pcp.xenon.XenonFlashModuleLoader;
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
 * --nand archivo.bin imagen NAND Xenon small-block (solo lectura) --fuses archivo.txt
 * fusesets del usuario --boot-cba procesa CB_A con credenciales propias --boot-credentials
 * archivo.txt carga 1BL/CPU keys desde un archivo externo (si se omite, solicita en terminal) --max N
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
     * Presenta un recurso gráfico ORIGINAL extraído de dash.xex cuando el guest
     * todavía no consigue emitir comandos Xenos/XUI. No simula el shell:
     * muestra literalmente un AppTile de la NAND para verificar el camino FB.
     */
    private static void showNandTileFallback(FrameBuffer fb, Path resource) throws Exception {
        if (!Files.isRegularFile(resource)) {
            System.out.println("[NAND-TILE] recurso ausente: " + resource);
            return;
        }
        BufferedImage src = ImageIO.read(resource.toFile());
        if (src == null) {
            throw new IllegalStateException("Formato gráfico no soportado: " + resource);
        }
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
                if ((argb >>> 24) != 0) {
                    fb.setPixel(ox + x, oy + y, argb & 0x00FFFFFF);
                }
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
        String fusesPath = null;
        String bootCredentialsPath = null;
        String nandPath = "./src/pcp/TESTS/nand.bin";
        String oneBLPath = "./src/pcp/TESTS/1bl.bin";
        java.util.List<long[]> socRegs = new java.util.ArrayList<>();
        String dumpSram = null;
        boolean bootSeq = false;
        boolean processCBA = false;
        boolean elf = false, headless = false, trace = false, vram = false, mandel = false, busTrace = false, oneBl = true, ppc32 = false, xamMode = true, nandDashboardMode = true, dashboardCompat = true;
        long fbBase = -1;
        String input = null;
        java.util.List<Long> watchList = new java.util.ArrayList<>();
        int threads = -1;
        long tb = -1;
        long load = 0, max = 50_000_000L;
        int w = 800, h = 600;
        long[] busRange = null;

        // Las claves de arranque nunca se incluyen en el código fuente.
        // Se solicitan de forma interactiva o desde un archivo externo elegido
        // explícitamente por el usuario mediante --boot-credentials.

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
                case "--nand-dashboard-compat":
                    nandDashboardMode = true;
                    dashboardCompat = true;
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
                case "--boot-cba":
                    processCBA = true;
                    break;
                case "--boot-credentials":
                    bootCredentialsPath = args[++i];
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
            if (nandPath == null || "./src/pcp/TESTS/nand.bin".equals(nandPath)) {
                nandPath = "./src/pcp/TESTS/nand.bin";
            }
            if (fbBase < 0) {
                fbBase = PPCSimulator.FB_BASE_ELF;
            }
        }
        if (processCBA) {
            XenonBootStrapper bootstrapper = XenonBootStrapper.load(Paths.get(nandPath));
            XenonBootImage cbA;
            if (bootCredentialsPath == null) {
                try (XenonBootCredentials credentials = XenonBootCredentials.readFromUser()) {
                    cbA = bootstrapper.prepareCBA(credentials);
                }
            } else {
                try (XenonBootCredentials credentials = XenonBootCredentials.load(Paths.get(bootCredentialsPath))) {
                    cbA = bootstrapper.prepareCBA(credentials);
                }
            }
            System.out.printf("[BOOTSTRAP] CB_A version=%04X size=%08X state=%s%n",
                    cbA.getVersion(), cbA.getImageSize(), cbA.getState());
            System.out.println("[BOOTSTRAP] CB_A descifrado; firma/integridad y CB_B pendientes.");
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
            flashFs.dumpDirectory();

            System.exit(0);
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
            if (dashboardCompat) {
                // Fallback opt-in: ejecuta el dashboard retail extraído de la
                // propia FlashFS. XAM y bootanim se cargan primero; sólo los
                // imports XAM sin export disponible reciben stubs de éxito.
                XenonFlashModuleLoader flashLoader = new XenonFlashModuleLoader(
                        flashFs, ppcs.getMemory(), java.util.Collections.emptyList());
                XenonFlashModuleLoader.Module dash = flashLoader.load("dash.xex");
                byte[] dashPe = dash.getXex().image(java.util.Collections.emptyList());
                Path bootApis = Paths.get("./nandDump/extracted/manifests/boot_api_imports.csv");
                runtimeKernel.installXamImportsFromXex(
                        dash.getXex(), dashPe, "dash.xex", bootApis, true);
                dash.getImage().start(ppcs.getCpu(), PPCSimulator.MEMORY_SIZE);
                long threadContext = 0x9FFFD000L;
                long threadExit = 0x9FFF0010L;
                ppcs.getCpu().setGPR(31, threadContext);
                ppcs.getCpu().setGPR(29, threadContext);
                ppcs.getCpu().setGPR(27, threadContext);
                ppcs.getCpu().writeMemoryWord(threadContext + 0x138, threadExit);
                ppcs.getCpu().writeMemoryWord(threadContext + 0x164, 0L);
                // Callback de inicialización de módulo que el stub de entrada
                // obtiene desde KTHREAD+0x1C. Se modela como retorno STATUS_SUCCESS.
                long moduleInitStub = 0x9FFF0100L;
                ppcs.getCpu().writeMemoryWord(threadContext + 0x1C, moduleInitStub);
                ppcs.getCpu().writeMemoryWord(threadContext + 0x20, 0L);
                ppcs.getMemory().writeMemoryWord(moduleInitStub, 0x38600000); // li r3,0
                ppcs.getMemory().writeMemoryWord(moduleInitStub + 4, 0x4E800020); // blr
                // Los stubs de arranque de esta imagen contienen una llamada
                // absoluta a 0x3A00 (dispatcher de runtime no presente). Se
                // instala un retorno seguro en esa página baja, sólo en compat.
                ppcs.getMemory().writeMemoryWord(0x3A00L, 0x38600000); // li r3,0
                ppcs.getMemory().writeMemoryWord(0x3A04L, 0x4E800020); // blr
                // La imagen depende de varios entrypoints de kernel en la
                // región baja que este runtime aún no mapea. Stubs de retorno
                // (sólo 0x1000..0x10000, sólo en compatibilidad).
                for (long low = 0x100L; low < 0x10000L; low += 4L) {
                    ppcs.getMemory().writeMemoryWord(low, 0x4E800020);
                }
                // Stubs kernel en el espejo alto y dos huecos de código
                // observados en esta imagen PE; sólo evitan vectores vacíos.
                runtimeKernel.installCompatibilityKernelMirror();
                ppcs.getMemory().writeMemoryWord(0x9217D7D8L, 0x4E800020);
                ppcs.getMemory().writeMemoryWord(0x9217DEE8L, 0x4E800020);
                ppcs.getMemory().writeMemoryWord(0x3A00L, 0x38600000);
                ppcs.getMemory().writeMemoryWord(0x3A04L, 0x4E800020);
                // El entrypoint de esta revisión comienza con bctrl y espera
                // un callback CTR que sólo instala el loader retail. En este
                // modo aislado se omite ese salto indirecto y se continúa en
                // el stub de inicialización del dashboard.
                int dashFirst = ppcs.getMemory().readMemoryWord(dash.getEntryPoint());
                if (dashFirst == 0x4E800421) {
                    ppcs.getMemory().writeMemoryWord(dash.getEntryPoint(), 0x60000000);
                    System.out.printf("[NAND-COMPAT] ENTRY PATCH bctrl->nop at %08X%n", dash.getEntryPoint());
                } else {
                    throw new IllegalStateException(String.format(
                            "Entry dashboard inesperado: %08X", dashFirst));
                }
                System.out.printf("[NAND-COMPAT] DASHBOARD ENTRY PC=%08X base=%08X size=%08X%n",
                        ppcs.getCpu().getPc(), dash.getImage().getImageBase(),
                        dash.getImage().getSizeOfImage());
                xamMode = false;
            }
            if (!bootanim.hasEntryPoint()) {
                System.out.println("[NAND-BOOT] bootanim es módulo kernel-managed; no se fuerza un PC ficticio.");
            }
            ppcs.setFbBase(fbBase);
            ppcs.initializeXenonFramebuffer();
            File fbInit = new File("fb-init.png");
            ppcs.getFrameBuffer().savePNG(fbInit);
            System.out.println("[NAND-BOOT] Captura de inicialización FB: " + fbInit.getAbsolutePath());
            // El modo normal permanece en XAM. En compatibilidad se conserva
            // el PC del dashboard ya preparado y no se reactiva el overlay XAM.
            if (!dashboardCompat) {
                path = xamPath.toString();
                xamMode = true;
            }
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
            // XAM o el entrypoint retail de compatibilidad ya están preparados.
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
                if (fb.isDirty()) {
                    fb.present();
                }
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
