package pcp;

import java.awt.Point;
import pcp.cpu.CPU;
import pcp.utils.ELFFile;
import pcp.system.Debugger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import pcp.gpu.Console;
import pcp.gpu.FrameBuffer;
import pcp.system.Bus;
import pcp.system.CacheController;
import pcp.system.ELFException;
import pcp.system.Memory;
import pcp.utils.DecodingInstr;
import pcp.utils.ELFSection;
import pcp.utils.ELFSegment;

/**
 *
 * @author Slam
 */
public class PPCSimulator {

    private static final int MEMORY_SIZE = 1024 * 1024 * 512; // 512MB

    // Componentes principales    
    private final Memory memory = new Memory(MEMORY_SIZE);
    private final Bus bus = new Bus(memory);
    private final CacheController cache = new CacheController(bus, MEMORY_SIZE, true);
    private final CPU cpu = new CPU(bus, cache);
    private final Debugger debugger = new Debugger(cpu);

    // Dispositivos de E/S
    private final Console console = new Console();
    private final FrameBuffer frameBuffer = new FrameBuffer(1024, 768);

    // Estado de la simulación
    private boolean running = false;
    private boolean debugMode = false;

    public PPCSimulator() {
        // Inicializar la única ventana framebuffer desde aquí
        bus.attachDevice(console, 0x0FFF0000L, 0x0FFF000FL);
        bus.attachDevice(frameBuffer, 0x10000000L, 0x10257FFFL);
        bus.attachDevice(frameBuffer, 0xC8000000L, 0xC8001FFFL);
    }

    public void loadBinary(String filename) {
        try {
            byte[] program = Files.readAllBytes(Paths.get(filename));
            memory.writeBlock(0, program);
            cpu.setPc(0);
        } catch (IOException e) {
            System.err.println("Error loading binary: " + e.getMessage());
        }
    }

    public void loadELF(String filename) {
        try {
            ELFFile elf = new ELFFile(filename);

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
                    long address = segment.physicalAddress; // Prefer physicalAddress

                    if ((address & 0x1FFFFFFF) + size > MEMORY_SIZE) {
                        throw new RuntimeException("ELF segment out of memory bounds: address=0x"
                                + Long.toHexString(address) + ", size=" + size);
                    }

                    if ((address & (segment.align - 1)) != 0) {
                        throw new RuntimeException("Misaligned ELF segment: address=0x"
                                + Long.toHexString(address) + ", align=" + segment.align);
                    }

                    byte[] segmentData = Arrays.copyOfRange(elf.elfData, start, start + size);
                    System.out.println("Loading segment: paddr=0x" + Long.toHexString(address)
                            + ", size=" + size + ", offset=0x" + Integer.toHexString(start));
                    memory.writeBlock(address, segmentData);
                    memory.dumpMemory(address, Math.min((int) segment.fileSize, 32)); // Dump first 32 bytes

                    if (segment.memorySize > segment.fileSize) {
                        byte[] zeros = new byte[(int) (segment.memorySize - segment.fileSize)];
                        memory.writeBlock(address + segment.fileSize, zeros);
                    }
                }
            }

            long entryPoint = elf.getEntryPoint() & 0x1FFFFFFF;
            cpu.setPc((int) entryPoint);
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
        System.out.println("Symbols loaded (stub)");
        debugger.loadSymbols(symtab, strtab);
    }

    public void start() {
        running = true;
        new Thread(() -> {
            while (running) {
                cpu.run();
            }
        }).start();
    }

    public void stop() {
        running = false;
    }

    public void step() {
        if (debugMode) {
            debugger.step();
        } else {
            int instruction = cpu.fetchInstruction();
            DecodingInstr.InstructionFields fields = DecodingInstr.decode(instruction);
            cpu.execute(instruction, fields);
            cpu.checkInterrupts();
        }
    }

    public void setDebugMode(boolean enabled) {
        debugMode = enabled;
    }

    public void reset() {
        cpu.setPc(0);
        memory.fill((byte) 0);
        cpu.resetRegisters();
        console.clear();
        frameBuffer.clear();
    }

    // Métodos para acceso a dispositivos
    public Console getConsole() {
        return console;
    }

    public FrameBuffer getFrameBuffer() {
        return frameBuffer;
    }

}
