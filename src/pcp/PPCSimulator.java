package pcp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import pcp.cpu.CPU;
import pcp.gpu.Console;
import pcp.gpu.FrameBuffer;
import pcp.system.Bus;
import pcp.system.CacheController;
import pcp.system.Debugger;
import pcp.system.ELFException;
import pcp.system.Memory;
import pcp.utils.ELFFile;
import pcp.utils.ELFSection;
import pcp.utils.ELFSegment;

/**
 * Sistema completo: RAM + bus + CPU + consola + framebuffer.
 *
 * Mapa de memoria (modo real):
 *   0x00000000 RAM (512 MB, espejada: 0x8xxxxxxx == 0x0xxxxxxx)
 *   0x0FFF0000 Consola serie (stb al offset 0 imprime un carácter)
 *   0x10000000 Framebuffer lineal, píxeles 32 bits 0x00RRGGBB (big-endian)
 *   0xC8000000 Registros del framebuffer (modo, ancho, alto, present)
 *
 * @author Slam
 */
public class PPCSimulator {

    public static final int MEMORY_SIZE = 1024 * 1024 * 512;
    public static final long CONSOLE_BASE = 0x0FFF0000L;
    public static final long FB_BASE = 0x10000000L;   // por defecto (binarios planos)
    public static final long FB_REGS = 0xC8000000L;

    private final Memory memory = new Memory(MEMORY_SIZE);
    private final Bus bus = new Bus(memory);
    private final CacheController cache = new CacheController(bus, MEMORY_SIZE, true);
    private final CPU cpu = new CPU(bus, cache);
    private final java.util.List<CPU> threads = new java.util.ArrayList<>();
    private int threadCount = 1;
    private boolean threadsPrepared = false;
    private boolean threadsUserSet = false;
    private long tb = -1;
    private final Debugger debugger = new Debugger(cpu);
    private final Console console = new Console();
    private final pcp.gpu.XenonUart uart = new pcp.gpu.XenonUart(console);
    private final pcp.gpu.XenonSfcx sfcx = new pcp.gpu.XenonSfcx();
    private final pcp.gpu.XenonSata sata = new pcp.gpu.XenonSata();
    private final FrameBuffer frameBuffer;
    private final int fbW, fbH;
    private long fbBase = FB_BASE;
    private boolean fbAttached = false;
    private boolean fbBaseUserSet = false;
    private boolean bootRomMode = false;
    private boolean elfMode = false;
    private boolean xell = false;
    public static final long FB_BASE_ELF = 0x1E000000L; // superficie Xenos usada por XeLL Xenon (1280x720)

    private volatile boolean running = false;
    private boolean debugMode = false;
    private Thread thread;

    public PPCSimulator() {
        this(800, 600);
    }

    public PPCSimulator(int fbWidth, int fbHeight) {
        threads.add(cpu);
        frameBuffer = new FrameBuffer(fbWidth, fbHeight);
        fbW = fbWidth;
        fbH = fbHeight;
        bus.attachDevice(console, CONSOLE_BASE, CONSOLE_BASE + 0xF);
        bus.attachDevice(frameBuffer.getRegisters(), FB_REGS, FB_REGS + 0xF);
        bus.attachDevice(uart, pcp.gpu.XenonUart.BASE, pcp.gpu.XenonUart.BASE + 0x1F);
        bus.attachDevice(new pcp.gpu.XenonSmc(), pcp.gpu.XenonSmc.BASE, pcp.gpu.XenonSmc.BASE + 0x1F);
        bus.attachDevice(sfcx, pcp.gpu.XenonSfcx.BASE,
                pcp.gpu.XenonSfcx.BASE + pcp.gpu.XenonSfcx.SIZE - 1);
        bus.attachDevice(sata, pcp.gpu.XenonSata.BASE,
                pcp.gpu.XenonSata.BASE + pcp.gpu.XenonSata.SIZE - 1);
    }

    /** Elige la dirección del framebuffer (debe llamarse antes de start/run). */
    public void setFbBase(long base) {
        fbBase = base;
        fbBaseUserSet = true;
    }

    public long getFbBase() {
        return fbBase;
    }

    /** Conecta el framebuffer al bus en fbBase (una sola vez). */
    private void attachFb() {
        if (!fbAttached && !bootRomMode) {
            bus.attachDevice(frameBuffer, fbBase, fbBase + frameBuffer.sizeBytes() - 1);
            bus.attachDevice(new pcp.gpu.ToyGpu(bus, frameBuffer), pcp.gpu.ToyGpu.BASE,
                    pcp.gpu.ToyGpu.BASE + pcp.gpu.ToyGpu.SIZE - 1);
            // GPU Xenos (registros D1GRPH, etc.): la usan tanto LK (lwz) como XeLL (read32n)
            // Los registros Xenos expuestos por este backend usan el contrato big-endian observado en XeLL y LK.
            pcp.gpu.XenosGpu gpu = new pcp.gpu.XenosGpu(fbBase, fbW, fbH, false);
            gpu.setOnSurfaceChange(addr -> {
                if (addr != 0 && addr != fbBase) {
                    System.out.printf("[gpu] superficie primaria -> 0x%x%n", addr);
                    fbBase = addr;
                    bus.moveDevice(frameBuffer, addr, addr + frameBuffer.sizeBytes() - 1);
                }
            });
            bus.attachDevice(gpu, pcp.gpu.XenosGpu.BASE, pcp.gpu.XenosGpu.BASE + pcp.gpu.XenosGpu.SIZE - 1);
            if (elfMode) {
                frameBuffer.setTileMode(2); // tiling real del Xenos (libxenon)
            }
            fbAttached = true;
        }
    }

    public Bus getBus() {
        return bus;
    }


    /** ROM de arranque 1BL: ventana física Xenon, de solo lectura. */
    private static final class BootRom implements pcp.gpu.Device {
        private final byte[] image;
        BootRom(byte[] image) { this.image = image.clone(); }
        @Override public boolean containsAddress(long address) {
            return address >= 0 && address < image.length;
        }
        @Override public byte read(long offset) {
            return offset >= 0 && offset < image.length ? image[(int) offset] : 0;
        }
        @Override public void write(long offset, byte value) { /* ROM */ }
    }

    /** SRAM interna del XCPU (64 KiB), usada por 1BL antes de inicializar la DDR. */
    private static final class BootSram implements pcp.gpu.Device {
        private final byte[] bytes;
        BootSram(int size) { bytes = new byte[size]; }
        @Override public boolean containsAddress(long address) {
            return address >= 0 && address < bytes.length;
        }
        @Override public byte read(long offset) {
            return offset >= 0 && offset < bytes.length ? bytes[(int) offset] : 0;
        }
        @Override public void write(long offset, byte value) {
            if (offset >= 0 && offset < bytes.length) bytes[(int) offset] = value;
        }
    }

    /**
     * Carga el Boot ROM 1BL sin modificar ni copiar la NAND. El archivo completo se presenta en 0x8000020000000000; el entry 0x100
     * de la cabecera determina el PC inicial.
     */
    public void load1BL(String filename) {
        try {
            byte[] image = Files.readAllBytes(Paths.get(filename));
            if (image.length != 0x8000 || ((image[0] & 0xFF) << 8 | (image[1] & 0xFF)) != 0x0341) {
                throw new IllegalArgumentException("No es un 1BL Xenon de 32 KiB (magic 0x0341)");
            }
            int declared = ((image[12] & 0xFF) << 24) | ((image[13] & 0xFF) << 16)
                    | ((image[14] & 0xFF) << 8) | (image[15] & 0xFF);
            if (declared <= 0 || 0x100L + declared > image.length) {
                throw new IllegalArgumentException("Tamaño 1BL inválido: 0x" + Integer.toHexString(declared));
            }
            byte[] mapped = Arrays.copyOf(image, 0x100 + declared);
            final long romBase = 0x8000020000000000L;
            BootRom rom = new BootRom(mapped);
            bootRomMode = true;
            // El config-ring de Xenon ocupa una ventana de 4 KiB en el mapa de boot.
            // Debe tener prioridad sobre el contenido ROM: el 1BL lo limpia y lo consulta.
            BootSram configRing = new BootSram(0x1000);
            bus.attachDevice(configRing, 0x200L, 0x11FFL);
            bus.attachDevice(configRing, romBase + 0x200L, romBase + 0x11FFL);
            // SRAM interna de 64 KiB usada como stack y workspace antes de DDR.
            BootSram sram = new BootSram(0x10000);
            bus.attachDevice(sram, romBase + 0x10000L, romBase + 0x1FFFFL);
            bus.attachDevice(rom, 0, mapped.length - 1); // alias de baja dirección
            bus.attachDevice(rom, 0x01000000L, 0x01000000L + mapped.length - 1); // ventana de ejecución ROM
            bus.attachDevice(rom, romBase, romBase + mapped.length - 1);
            cpu.setMsr(cpu.getMsr() | 0x8000000000000000L);
            cpu.setPc(romBase + 0x100);
            System.out.printf("[1BL] ROM mapeada: base=0x%016x size=0x%x PC=0x%016x%n",
                    romBase, mapped.length, cpu.getPc());
        } catch (IOException e) {
            throw new RuntimeException("No se pudo cargar 1BL: " + e.getMessage(), e);
        }
    }

    /** Carga una imagen NAND Xenon de solo lectura antes de ejecutar el firmware. */
    public void loadNand(java.nio.file.Path path) throws IOException {
        sfcx.loadNand(path);
        pcp.gpu.NandImage image = sfcx.getNandImage();
        System.out.printf("[SFCX] NAND %s: %d bytes, %s, %d páginas, %d bloques (solo lectura)%n",
                image.getSource(), image.getSize(), image.getFormat(),
                image.getPageCount(), image.getBlockCount());
    }

    /** Carga un binario plano en 'address' y apunta el PC allí. */
    public void loadBinary(String filename, long address) {
        try {
            byte[] program = Files.readAllBytes(Paths.get(filename));
            memory.writeBlock(address, program);
            cpu.setMsr(cpu.getMsr() | 0x8000000000000000L); // binarios planos: modo de 64 bits
            cpu.setPc(address);
        } catch (IOException e) {
            throw new RuntimeException("Error loading binary: " + e.getMessage(), e);
        }
    }

    public void loadBinary(String filename) {
        loadBinary(filename, 0);
    }

    public void loadELF(String filename) {
        try {
            ELFFile elf = new ELFFile(filename);
            // ELF de 64 bits = firmware tipo Xenon (GPU Xenos, tiling real); ELF de 32 bits = programa plano
            xell = containsAscii(elf.elfData, "Xenon Linux Loader");
            elfMode = elf.is64Bit() || xell; // firmware libxenon: GPU Xenos + tiling propio
            if (!threadsUserSet && xell) {
                threadCount = 6; // XeLL espera que los 6 hilos hardware se registren en su mailbox
            }
            if (elfMode && !fbBaseUserSet) {
                fbBase = FB_BASE_ELF;
            }
            if (elfMode) {
                // 0xC8000000 es la ventana de datos NAND lógica en Xenon real
                // (el MMIO SFCX está en 0xEA00C000). El bloque de control del framebuffer es
                // una extensión del emulador
                // para binarios planos y no debe interceptar accesos de firmware ELF.
                bus.moveDevice(frameBuffer.getRegisters(), 0xF0000000L, 0xF000000FL);
            }

            if (elf.getMachine() != ELFFile.EM_PPC && elf.getMachine() != ELFFile.EM_PPC64) {
                throw new RuntimeException("ELF not for PowerPC architecture");
            }
            if (elf.is64Bit() || elf.getMachine() == ELFFile.EM_PPC64) {
                System.err.println("Warning: 64-bit ELF detected; emulator may not support PPC64");
            }

            for (ELFSegment segment : elf.getSegments()) {
                if (segment.fileSize > 0) {
                    int start = (int) segment.fileOffset;
                    int size = (int) segment.fileSize;
                    long address = segment.physicalAddress;

                    if ((address & 0x1FFFFFFF) + size > MEMORY_SIZE) {
                        throw new RuntimeException("ELF segment out of memory bounds: address=0x"
                                + Long.toHexString(address) + ", size=" + size);
                    }
                    byte[] segmentData = Arrays.copyOfRange(elf.elfData, start, start + size);
                    System.out.println("Loading segment: paddr=0x" + Long.toHexString(address)
                            + ", size=" + size + ", offset=0x" + Integer.toHexString(start));
                    memory.writeBlock(address, segmentData);

                    if (segment.memorySize > segment.fileSize) {
                        byte[] zeros = new byte[(int) (segment.memorySize - segment.fileSize)];
                        memory.writeBlock(address + segment.fileSize, zeros);
                    }
                }
            }
            long entryPoint = elf.getEntryPoint();
            ELFSection opd = elf.getSectionByName(".opd");
            if (elf.is64Bit() && opd != null && entryPoint >= opd.address && entryPoint < opd.address + opd.size) {
                // ELFv1: e_entry apunta a un descriptor {entrada, TOC, entorno}
                long descPhys = entryPoint & 0x1FFFFFFF;
                long target = (memory.readMemoryWord(descPhys) & 0xFFFFFFFFL) << 32
                        | (memory.readMemoryWord(descPhys + 4) & 0xFFFFFFFFL);
                long toc = (memory.readMemoryWord(descPhys + 8) & 0xFFFFFFFFL) << 32
                        | (memory.readMemoryWord(descPhys + 12) & 0xFFFFFFFFL);
                System.out.printf("Entrada vía .opd: 0x%x (TOC 0x%x)%n", target, toc);
                entryPoint = target;
                cpu.setGPR(2, toc);
            }
            if (opd != null) {
                cpu.setOpdRange(opd.address, opd.address + opd.size);
            }
            entryPoint &= 0x1FFFFFFF;
            // SF=1 sólo para ELF64 (LK); los ELF32 (XeLL...) corren en modo de 32 bits
            if (elf.is64Bit()) {
                cpu.setMsr(cpu.getMsr() | 0x8000000000000000L);
            }
            cpu.setPc(entryPoint);
            System.out.println("Setting PC to 0x" + Long.toHexString(entryPoint));
            loadSymbols(elf.getSectionByName(".symtab"), elf.getSectionByName(".strtab"));
        } catch (ELFException e) {
            throw new RuntimeException("Error loading ELF: " + e.getMessage(), e);
        }
    }

    private static boolean containsAscii(byte[] data, String needle) {
        byte[] n = needle.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        outer:
        for (int i = 0; i + n.length <= data.length; i++) {
            for (int j = 0; j < n.length; j++) {
                if (data[i + j] != n[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private void loadSymbols(ELFSection symtab, ELFSection strtab) {
        if (symtab == null || strtab == null) {
            return;
        }
        debugger.loadSymbols(symtab, strtab);
    }

    /** Número de hilos hardware (el Xenon tiene 6: 3 núcleos x 2). Todos arrancan en el mismo punto de entrada. */
    public void setThreads(int n) {
        threadCount = Math.max(1, Math.min(6, n));
        threadsUserSet = true;
    }

    public void setTb(long n) {
        tb = n;
    }

    private void prepareThreads() {
        if (threadsPrepared) {
            return;
        }
        threadsPrepared = true;
        long inc = tb > 0 ? tb : (xell ? 1000 : 1);
        cpu.setTbIncrement(inc);
        cpu.setPir(0);
        for (int i = 1; i < threadCount; i++) {
            CPU t = new CPU(bus, new CacheController(bus, MEMORY_SIZE, true));
            t.setPir(i);
            t.setTbIncrement(inc);
            t.setPc(cpu.getPc());
            t.setMsr(cpu.getMsr());
            for (int r = 0; r < 32; r++) {
                t.setGPR(r, cpu.getGPRVal(r));
            }
            t.setOpdRange(cpu.getOpdStart(), cpu.getOpdEnd());
            for (long w : watches) {
                t.addWatchPc(w);
            }
            threads.add(t);
        }
        if (threadCount > 1) {
            System.out.println("[sim] " + threadCount + " hilos hardware (PIR 0.." + (threadCount - 1) + ")");
        }
    }

    /** Planificador round-robin de todos los hilos; devuelve las instrucciones ejecutadas por todos. */
    private long runAll(long maxInstr) {
        prepareThreads();
        long total = 0;
        final int quantum = 256;
        while (total < maxInstr) {
            boolean any = false;
            for (CPU t : threads) {
                if (t.isHalted()) {
                    continue;
                }
                any = true;
                total += t.run(quantum);
                while (t.hasReservation() && !t.isHalted()) { // no partir lwarx..stwcx.
                    t.step();
                    total++;
                }
            }
            if (!any || !running && !blocking) {
                break;
            }
        }
        return total;
    }

    private volatile boolean blocking = false;

    /** Ejecuta en un hilo aparte hasta que la CPU se detenga o se llame a stop(). */
    public void start() {
        attachFb();
        running = true;
        prepareThreads();
        for (CPU t : threads) {
            t.resume();
        }
        thread = new Thread(() -> {
            while (running && anyRunning()) {
                runAll(100_000);
            }
            running = false;
        }, "ppc-cpu");
        thread.setDaemon(true);
        thread.start();
    }

    /** Ejecuta en el hilo actual hasta maxInstr instrucciones. */
    public long runBlocking(long maxInstr) {
        attachFb();
        for (CPU t : threads) {
            t.resume();
        }
        blocking = true;
        try {
            return runAll(maxInstr);
        } finally {
            blocking = false;
        }
    }

    private boolean anyRunning() {
        for (CPU t : threads) {
            if (!t.isHalted()) {
                return true;
            }
        }
        return false;
    }

    public void stop() {
        running = false;
        for (CPU t : threads) {
            t.halt();
        }
    }

    public boolean isRunning() {
        return running && anyRunning();
    }

    public void step() {
        attachFb();
        if (debugMode) {
            debugger.step();
        } else {
            cpu.step();
        }
    }

    public void setDebugMode(boolean enabled) {
        debugMode = enabled;
    }

    private final java.util.List<Long> watches = new java.util.ArrayList<>();

    public void addWatch(long a) {
        watches.add(a);
        cpu.addWatchPc(a);
    }

    public void setSprTrace(boolean enabled) {
        cpu.setSprTrace(enabled);
    }

    public void setTrace(boolean enabled) {
        cpu.setTrace(enabled);
    }

    public void reset() {
        stop();
        memory.fill((byte) 0);
        cache.flush();
        cpu.resetRegisters();
        console.clear();
        frameBuffer.clear();
    }

    public Memory getMemory() {
        return memory;
    }

    public CPU getCpu() {
        return cpu;
    }

    public pcp.gpu.XenonUart getUart() {
        return uart;
    }

    public Console getConsole() {
        return console;
    }

    public FrameBuffer getFrameBuffer() {
        return frameBuffer;
    }
}
