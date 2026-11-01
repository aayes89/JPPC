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
    private final Debugger debugger = new Debugger(cpu);
    private final Console console = new Console();
    private final pcp.gpu.XenonUart uart = new pcp.gpu.XenonUart(console);
    private final FrameBuffer frameBuffer;
    private final int fbW, fbH;
    private long fbBase = FB_BASE;
    private boolean fbAttached = false;
    private boolean fbBaseUserSet = false;
    private boolean elfMode = false;
    public static final long FB_BASE_ELF = 0x1E000000L; // lejos de la imagen de LK (0x10000000)

    private volatile boolean running = false;
    private boolean debugMode = false;
    private Thread thread;

    public PPCSimulator() {
        this(800, 600);
    }

    public PPCSimulator(int fbWidth, int fbHeight) {
        frameBuffer = new FrameBuffer(fbWidth, fbHeight);
        fbW = fbWidth;
        fbH = fbHeight;
        bus.attachDevice(console, CONSOLE_BASE, CONSOLE_BASE + 0xF);
        bus.attachDevice(frameBuffer.getRegisters(), FB_REGS, FB_REGS + 0xF);
        bus.attachDevice(uart, pcp.gpu.XenonUart.BASE, pcp.gpu.XenonUart.BASE + 0x1F);
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
        if (!fbAttached) {
            bus.attachDevice(frameBuffer, fbBase, fbBase + frameBuffer.sizeBytes() - 1);
            bus.attachDevice(new pcp.gpu.ToyGpu(bus, frameBuffer), pcp.gpu.ToyGpu.BASE,
                    pcp.gpu.ToyGpu.BASE + pcp.gpu.ToyGpu.SIZE - 1);
            if (elfMode) {
                // Firmware tipo libxenon/LK: GPU Xenos con los registros D1GRPH y tiling real del Xenos
                bus.attachDevice(new pcp.gpu.XenosGpu(fbBase, fbW, fbH), pcp.gpu.XenosGpu.BASE,
                        pcp.gpu.XenosGpu.BASE + pcp.gpu.XenosGpu.SIZE - 1);
                frameBuffer.setTileMode(2);
            }
            fbAttached = true;
        }
    }

    public Bus getBus() {
        return bus;
    }

    /** Carga un binario plano en 'address' y apunta el PC allí. */
    public void loadBinary(String filename, long address) {
        try {
            byte[] program = Files.readAllBytes(Paths.get(filename));
            memory.writeBlock(address, program);
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
            elfMode = elf.is64Bit();
            if (elfMode && !fbBaseUserSet) {
                fbBase = FB_BASE_ELF;
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
            // modo 64 bits (SF=1) como el Xenon real
            cpu.setMsr(cpu.getMsr() | 0x8000000000000000L);
            cpu.setPc(entryPoint);
            System.out.println("Setting PC to 0x" + Long.toHexString(entryPoint));
            loadSymbols(elf.getSectionByName(".symtab"), elf.getSectionByName(".strtab"));
        } catch (ELFException e) {
            throw new RuntimeException("Error loading ELF: " + e.getMessage(), e);
        }
    }

    private void loadSymbols(ELFSection symtab, ELFSection strtab) {
        if (symtab == null || strtab == null) {
            return;
        }
        debugger.loadSymbols(symtab, strtab);
    }

    /** Ejecuta en un hilo aparte hasta que la CPU se detenga o se llame a stop(). */
    public void start() {
        attachFb();
        running = true;
        cpu.resume();
        thread = new Thread(() -> {
            while (running && !cpu.isHalted()) {
                cpu.run(100_000);
            }
            running = false;
        }, "ppc-cpu");
        thread.setDaemon(true);
        thread.start();
    }

    /** Ejecuta en el hilo actual hasta maxInstr instrucciones. */
    public long runBlocking(long maxInstr) {
        attachFb();
        cpu.resume();
        return cpu.run(maxInstr);
    }

    public void stop() {
        running = false;
        cpu.halt();
    }

    public boolean isRunning() {
        return running && !cpu.isHalted();
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
