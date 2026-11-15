/*
 * The MIT License
 * Copyright 2026 Slam.
 */
package pcp.xenon;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import pcp.PPCSimulator;
import pcp.cpu.CPU;
import pcp.gpu.Device;
import pcp.system.Bus;
import pcp.system.Memory;

/**
 * Kernel Xenon mínimo para ejecutar XEX extraídos de NAND.
 * Los import thunks del XEX se convierten en pequeñas entradas PPC que
 * despachan a la semántica host del kernel. Se conserva la ABI PPC32:
 * argumentos r3-r10, resultado r3 y retorno por LR.
 *
 * Las APIs aún no modeladas retornan STATUS_NOT_IMPLEMENTED (0xC0000002)
 * en vez de aparentar éxito. Las APIs de sincronización/display implementadas
 * retornan el estado esperado y mantienen estado del subsistema.
 */
public final class TKernel {
    private static final int PPC_LI_R0_BASE = 0x38000000; // li r0, imm16
    private static final int PPC_SC = 0x44000002;
    private static final int STATUS_NOT_IMPLEMENTED = 0xC0000002;

    private final PPCSimulator simulator;
    private final Bus bus;
    private final Memory memory;
    private final Map<Integer, String> namedKernelOrdinals = new LinkedHashMap<>();
    private final Map<String, Long> importThunkAddresses = new HashMap<>();
    private long bootAnimationImageBase;
    private long bootAnimationImageSize;
    private boolean bootAnimationModuleLoaded;
    private boolean bootAnimationModuleHasEntryPoint;
    private int installedThunks;
    private int unknownThunks;
    private boolean enginesInitialized;
    private boolean ringBufferInitialized;
    private boolean displayModeSet;
    private volatile boolean bootAnimationRequested;
    private final Map<String, Long> bootAnimationResources = new LinkedHashMap<>();
    private final Map<Integer, Long> dispatchCounts = new HashMap<>();
    private XenonFlashFs flashFs;
    private final Map<Integer, GuestFile> guestFiles = new HashMap<>();
    private int nextGuestHandle = 0x100;

    public TKernel(PPCSimulator simulator) {
        if (simulator == null) throw new IllegalArgumentException("PPCSimulator no puede ser null");
        this.simulator = simulator;
        this.bus = simulator.getBus();
        this.memory = simulator.getMemory();
        loadKnownNames();
        simulator.setSystemCallHandler((cpu, serviceNumber) -> {
            long count = dispatchCounts.merge(serviceNumber, 1L, Long::sum);
            long pc = cpu.getPc();
            long lr = cpu.getLr();
            long[] args = new long[8];
            for (int i = 0; i < args.length; i++) args[i] = cpu.getGPRVal(i + 3) & 0xFFFFFFFFL;
            long result = invokeKernelExport(serviceNumber, cpu) & 0xFFFFFFFFL;
            String export = namedKernelOrdinals.getOrDefault(serviceNumber, "UNKNOWN");
            System.out.printf("[TKERNEL] SC #%d ordinal=%d name=%s PC=%08X LR=%08X "
                    + "r3=%08X r4=%08X r5=%08X r6=%08X r7=%08X r8=%08X r9=%08X r10=%08X result=%08X%n",
                    count, serviceNumber, export, pc, lr,
                    args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], result);
            if (serviceNumber == 678) {
                System.out.printf("[TKERNEL] DISPATCH ordinal=678 AniStartBootAnimation PC=%08X LR=%08X r3=%08X%n",
                        pc, lr, args[0]);
            }
            return result;
        });
    }

    private void loadKnownNames() {
        // Ordinal list is the one extracted from this NAND's boot_api_imports.csv.
        namedKernelOrdinals.put(90, "KeDelayExecutionThread");
        namedKernelOrdinals.put(678, "AniStartBootAnimation");
        namedKernelOrdinals.put(210, "NtCreateFile");
        namedKernelOrdinals.put(240, "NtReadFile");
        namedKernelOrdinals.put(450, "VdInitializeEngines");
        namedKernelOrdinals.put(451, "VdInitializeRingBuffer");
        namedKernelOrdinals.put(467, "VdSetDisplayMode");
        namedKernelOrdinals.put(468, "VdSetDisplayModeOverride");
        namedKernelOrdinals.put(476, "VdShutdownEngines");
        namedKernelOrdinals.put(477, "VdTurnDisplayOff");
        namedKernelOrdinals.put(548, "XMACreateContext");
        namedKernelOrdinals.put(550, "XMAReleaseContext");
    }

    private static final int STATUS_SUCCESS = 0;
    private static final int STATUS_INVALID_PARAMETER = 0xC000000D;
    private static final int STATUS_INVALID_HANDLE = 0xC0000008;
    private static final int STATUS_OBJECT_NAME_NOT_FOUND = 0xC0000034;
    private static final int FILE_OPEN = 1;

    private static final class GuestFile {
        final XenonFlashFs.Entry entry;
        long position;
        GuestFile(XenonFlashFs.Entry entry) { this.entry = entry; }
    }

    public void attachFlashFileSystem(XenonFlashFs fs) {
        this.flashFs = fs;
        System.out.println("[TKERNEL] FlashFS attached: "
                + (fs == null ? "none" : fs.rootInfo().toString()));
    }

    public PPCSimulator getSimulator() { return simulator; }
    public Bus getBus() { return bus; }
    public Memory getMemory() { return memory; }
    public int getInstalledThunks() { return installedThunks; }
    public int getUnknownThunks() { return unknownThunks; }
    public boolean isBootAnimationRequested() { return bootAnimationRequested; }

    /**
     * Instala thunks desde el manifiesto generado al extraer esta NAND.
     * type_flag=01 identifica las direcciones ejecutables de import thunk;
     * los registros 00 contienen la tabla ordinal y no se pisan.
     */
    public int installImports(Path manifest, String module) throws IOException {
        if (!Files.isRegularFile(manifest)) throw new IOException("No existe manifiesto de imports: " + manifest);
        int count = 0;
        int unknown = 0;
        try (BufferedReader in = Files.newBufferedReader(manifest, StandardCharsets.UTF_8)) {
            String line = in.readLine(); // header
            while ((line = in.readLine()) != null) {
                String[] c = line.split(",", -1);
                if (c.length < 8 || !module.equalsIgnoreCase(c[0]) || !"xboxkrnl.exe".equalsIgnoreCase(c[1]) || !"01".equals(c[4])) continue;
                int ordinal = Integer.parseInt(c[3], 16);
                long thunk = Long.parseLong(c[5], 16);
                if (thunk == 0 || (thunk & 3) != 0) continue;
                importThunkAddresses.put(importKey(module, "xboxkrnl.exe", ordinal), thunk & 0xFFFFFFFFL);
                // Real guest-side dispatch: li r0, ordinal; sc.
                // The CPU's system-call bridge executes the kernel service and
                // returns through the caller's LR, preserving r3-r10 arguments.
                memory.writeMemoryWord(thunk, PPC_LI_R0_BASE | (ordinal & 0xFFFF));
                memory.writeMemoryWord(thunk + 4, PPC_SC);
                if (!namedKernelOrdinals.containsKey(ordinal)) {
                    unknownThunks++;
                    unknown++;
                }
                installedThunks++;
                count++;
            }
        }
        System.out.printf("[TKERNEL] imports %s: %d thunks instalados (%d ordinals sin semántica nominal)%n",
                module, count, unknown);
        return count;
    }

    private static String importKey(String module, String library, int ordinal) {
        return module.toLowerCase(java.util.Locale.ROOT) + "|"
                + library.toLowerCase(java.util.Locale.ROOT) + "|" + ordinal;
    }

    public long getImportThunk(String module, String library, int ordinal) {
        Long address = importThunkAddresses.get(importKey(module, library, ordinal));
        if (address == null) throw new IllegalStateException("Import thunk no instalado: "
                + module + " -> " + library + " ordinal " + ordinal);
        return address;
    }

    /** Registra la imagen bootanim como DLL kernel-managed, sin inventar un entrypoint. */
    public void registerBootAnimationModule(XenonPeLoader.LoadedImage image) {
        if (image == null) throw new IllegalArgumentException("Imagen bootanim nula");
        bootAnimationImageBase = image.getImageBase();
        bootAnimationImageSize = image.getSizeOfImage();
        bootAnimationModuleHasEntryPoint = image.hasEntryPoint();
        bootAnimationModuleLoaded = true;
        bootAnimationResources.clear();
        System.out.printf("[TKERNEL] BOOTANIM REGISTERED base=%08X size=%08X entry=%08X hasEntry=%b%n",
                bootAnimationImageBase, bootAnimationImageSize, image.getEntryPoint(), bootAnimationModuleHasEntryPoint);
    }

    /**
     * Registra bootanim y mapea sus recursos XEX a las direcciones virtuales
     * declaradas por ResourceInfo. En esta NAND el recurso `audio` reside en
     * 0x9807A000 y se almacena fuera del SizeOfImage PE; cargar sólo el PE lo
     * dejaba ausente del espacio guest.
     */
    public int registerBootAnimationModule(XenonPeLoader.LoadedImage image,
            Path xexPath, Path extractedResourceDirectory) throws IOException {
        registerBootAnimationModule(image);
        if (xexPath == null || !Files.isRegularFile(xexPath)) {
            throw new IOException("No existe XEX original para leer ResourceInfo: " + xexPath);
        }
        if (extractedResourceDirectory == null || !Files.isDirectory(extractedResourceDirectory)) {
            throw new IOException("No existe directorio de recursos bootanim: " + extractedResourceDirectory);
        }
        XexFile xex = XexFile.parse(Files.readAllBytes(xexPath));
        int mapped = 0;
        for (XexFile.Resource resource : xex.resources()) {
            Path resourceFile = extractedResourceDirectory.resolve(resource.name);
            if (!Files.isRegularFile(resourceFile)) {
                resourceFile = extractedResourceDirectory.resolve(resource.name + ".wav");
            }
            if (!Files.isRegularFile(resourceFile)) {
                throw new IOException("Falta recurso XEX '" + resource.name + "' en " + extractedResourceDirectory);
            }
            long fileSize = Files.size(resourceFile);
            if (fileSize != resource.size) {
                throw new IOException("Tamaño de recurso XEX incorrecto para " + resource.name
                        + ": manifest=" + resource.size + " archivo=" + fileSize);
            }
            long physical = resource.address & 0x1FFFFFFFL;
            if (physical + resource.size > bus.getMemorySize()) {
                throw new IOException(String.format("Recurso %s fuera de RAM: VA=%08X size=%08X",
                        resource.name, resource.address, resource.size));
            }
            byte[] bytes = Files.readAllBytes(resourceFile);
            memory.writeBlock(resource.address, bytes);
            bootAnimationResources.put(resource.name.toLowerCase(java.util.Locale.ROOT), resource.address);
            mapped++;
            System.out.printf("[TKERNEL] BOOTANIM RESOURCE %s VA=%08X size=%08X source=%s%n",
                    resource.name, resource.address, resource.size, resourceFile);
        }
        return mapped;
    }

    public boolean isBootAnimationResourceMapped(String name) {
        return name != null && bootAnimationResources.containsKey(name.toLowerCase(java.util.Locale.ROOT));
    }

    public long getBootAnimationResourceAddress(String name) {
        Long address = name == null ? null : bootAnimationResources.get(name.toLowerCase(java.util.Locale.ROOT));
        if (address == null) throw new IllegalStateException("Recurso bootanim no mapeado: " + name);
        return address;
    }

    /**
     * Instala un shim de arranque PPC32 que llama al import real de XAM
     * AniStartBootAnimation y después transfiere el control al entrypoint XAM.
     * El shim es explícito y queda fuera de las imágenes retail.
     */
    public void installBootAnimationStartupHook(CPU cpu, long hookAddress,
            long aniStartThunk, long xamEntry, long threadExit) {
        checkGuestAddress(hookAddress, "hookAddress");
        checkGuestAddress(aniStartThunk, "aniStartThunk");
        checkGuestAddress(xamEntry, "xamEntry");
        checkGuestAddress(threadExit, "threadExit");
        int p = 0;
        p = emitLoadAddress(hookAddress, p, 12, aniStartThunk);
        memory.writeMemoryWord(hookAddress + p, 0x7D8903A6); p += 4; // mtctr r12
        memory.writeMemoryWord(hookAddress + p, 0x4E800421); p += 4; // bctrl -> import thunk
        p = emitLoadAddress(hookAddress, p, 12, threadExit);
        memory.writeMemoryWord(hookAddress + p, 0x7D8803A6); p += 4; // mtlr r12
        p = emitLoadAddress(hookAddress, p, 12, xamEntry);
        memory.writeMemoryWord(hookAddress + p, 0x7D8903A6); p += 4; // mtctr r12
        memory.writeMemoryWord(hookAddress + p, 0x4E800420); p += 4; // bctr -> XAM
        cpu.setMsr(cpu.getMsr() & 0x7FFFFFFFFFFFFFFFL);
        cpu.setGPR(1, 0x9FFFFDB0L);
        // XAM 17559 ejecuta `li r3, 1; bl AniStartBootAnimation`.
        // El shim debe reproducir ese argumento real, no entrar con r3=0.
        cpu.setGPR(3, 1L);
        cpu.setLr(threadExit);
        cpu.writeMemoryWord(0x9FFFFFF8L, (int) threadExit);
        cpu.writeMemoryWord(0x9FFFFF88L, (int) threadExit);
        memory.writeMemoryWord(threadExit, 0x48000000); // b . : thread-exit sentinel
        cpu.setPc(hookAddress);
        System.out.printf("[TKERNEL] BOOT HOOK=%08X -> AniStart thunk=%08X -> XAM=%08X%n",
                hookAddress, aniStartThunk, xamEntry);
    }

    private int emitLoadAddress(long base, int offset, int reg, long address) {
        int hi = (int) ((address >>> 16) & 0xFFFF);
        int lo = (int) (address & 0xFFFF);
        memory.writeMemoryWord(base + offset, 0x3C000000 | (reg << 21) | hi); offset += 4; // lis rN,hi
        memory.writeMemoryWord(base + offset, 0x60000000 | (reg << 21) | (reg << 16) | lo); offset += 4; // ori rN,rN,lo
        return offset;
    }

    /**
     * Enlaza imports de dash.xex cuya biblioteca es xam.xex. El manifiesto
     * boot_api_imports.csv contiene direcciones de exportación de XAM para
     * esta revisión de kernel (17559); se instala un tail thunk PPC para
     * conservar LR y la ABI del llamador.
     */
    public int installXamImports(Path importManifest, String module, Path exportManifest) throws IOException {
        if (!Files.isRegularFile(importManifest)) throw new IOException("No existe manifiesto de imports: " + importManifest);
        if (!Files.isRegularFile(exportManifest)) throw new IOException("No existe manifiesto de exports XAM: " + exportManifest);
        Map<Integer, Long> exports = new HashMap<>();
        try (BufferedReader in = Files.newBufferedReader(exportManifest, StandardCharsets.UTF_8)) {
            String line = in.readLine();
            while ((line = in.readLine()) != null) {
                String[] c = line.split(",", -1);
                if (c.length < 5 || !module.equalsIgnoreCase(c[0]) || !"xam.xex".equalsIgnoreCase(c[1])) continue;
                int ordinal = Integer.parseInt(c[2]);
                long address = Long.decode(c[4]);
                if (address != 0) exports.put(ordinal, address & 0xFFFFFFFFL);
            }
        }
        int linked = 0, unresolved = 0;
        try (BufferedReader in = Files.newBufferedReader(importManifest, StandardCharsets.UTF_8)) {
            String line = in.readLine();
            while ((line = in.readLine()) != null) {
                String[] c = line.split(",", -1);
                if (c.length < 8 || !module.equalsIgnoreCase(c[0]) || !"xam.xex".equalsIgnoreCase(c[1]) || !"01".equals(c[4])) continue;
                int ordinal = Integer.parseInt(c[3], 16);
                long thunk = Long.parseLong(c[5], 16);
                Long target = exports.get(ordinal);
                if (target == null || thunk == 0 || (thunk & 3) != 0) { unresolved++; continue; }
                int hi = (int) ((target >>> 16) & 0xFFFF);
                int lo = (int) (target & 0xFFFF);
                memory.writeMemoryWord(thunk,     0x3D800000 | hi); // lis r12, target@h
                memory.writeMemoryWord(thunk + 4, 0x618C0000 | lo); // ori r12,r12,target@l
                memory.writeMemoryWord(thunk + 8, 0x7D8903A6);      // mtctr r12
                memory.writeMemoryWord(thunk + 12,0x4E800420);      // bctr (tail call)
                linked++;
            }
        }
        installedThunks += linked;
        System.out.printf("[TKERNEL] imports %s <- xam.xex: %d enlazados a exports XAM, %d sin resolver%n", module, linked, unresolved);
        return linked;
    }


    /**
     * Alias experimental de kernel virtual para el arranque dashboard compat.
     * Mapea sólo FFFF8000-FFFF8FFF al último MiB de RAM física; no altera el
     * espejo normal 80000000-9FFFFFFF ni pisa la pila de arranque.
     */
    public void installCompatibilityKernelMirror() {
        final long physicalBase = 0x1FFF8000L;
        final long virtualBase = 0xFFFF8000L;
        bus.attachDevice(new Device() {
            @Override public boolean containsAddress(long offset) {
                return offset >= 0 && offset < 0x1000;
            }
            @Override public byte read(long offset) {
                return memory.read(physicalBase + offset);
            }
            @Override public void write(long offset, byte value) {
                memory.write(physicalBase + offset, value);
            }
        }, virtualBase, virtualBase + 0xFFF);
        for (long offset = 0; offset < 0x1000; offset += 4) {
            memory.writeMemoryWord(physicalBase + offset, 0x4E800020);
        }
        System.out.printf("[TKERNEL] COMPAT kernel mirror %08X-%08X -> %08X%n",
                virtualBase, virtualBase + 0xFFF, physicalBase);
    }

    /**
     * Enlaza los imports reales de un XEX contra exports XAM conocidos.
     * En modo compatibilidad los exports ausentes reciben un stub PPC32 que
     * devuelve STATUS_SUCCESS; queda aislado del arranque retail normal.
     */
    public int installXamImportsFromXex(XexFile xex, byte[] peImage,
            String module, Path exportManifest, boolean compatibilityStubs)
            throws IOException {
        if (xex == null || peImage == null) throw new IOException("XEX/PE nulo");
        if (!Files.isRegularFile(exportManifest)) {
            throw new IOException("No existe manifiesto de exports XAM: " + exportManifest);
        }
        Map<Integer, Long> exports = new HashMap<>();
        try (BufferedReader in = Files.newBufferedReader(exportManifest, StandardCharsets.UTF_8)) {
            String line = in.readLine();
            while ((line = in.readLine()) != null) {
                String[] c = line.split(",", -1);
                if (c.length < 5 || !module.equalsIgnoreCase(c[0])
                        || !"xam.xex".equalsIgnoreCase(c[1])) continue;
                int ordinal = Integer.parseInt(c[2]);
                long address = Long.decode(c[4]) & 0xFFFFFFFFL;
                if (address != 0) exports.put(ordinal, address);
            }
        }

        int linked = 0, stubbed = 0, ignored = 0;
        for (XexFile.ImportEntry entry : xex.importEntries(peImage)) {
            if (!"xam.xex".equalsIgnoreCase(entry.library) || entry.typeFlag != 1) {
                ignored++;
                continue;
            }
            Long target = exports.get(entry.ordinal);
            long thunk = entry.thunkAddress & 0xFFFFFFFFL;
            if (thunk == 0 || (thunk & 3L) != 0) {
                ignored++;
                continue;
            }
            if (target != null) {
                int hi = (int) ((target >>> 16) & 0xFFFF);
                int lo = (int) (target & 0xFFFF);
                memory.writeMemoryWord(thunk,      0x3D800000 | hi); // lis r12,target@h
                memory.writeMemoryWord(thunk + 4,  0x618C0000 | lo); // ori r12,r12,target@l
                memory.writeMemoryWord(thunk + 8,  0x7D8903A6);      // mtctr r12
                memory.writeMemoryWord(thunk + 12, 0x4E800420);      // bctr
                linked++;
            } else if (compatibilityStubs) {
                // li r3,0; blr. No modifica LR ni el frame del llamador.
                memory.writeMemoryWord(thunk, 0x38600000);
                memory.writeMemoryWord(thunk + 4, 0x4E800020);
                stubbed++;
            } else {
                ignored++;
            }
        }
        installedThunks += linked + stubbed;
        System.out.printf("[TKERNEL] XAM LINK %s: linked=%d compatibility-stubs=%d ignored=%d exports=%d%n",
                module, linked, stubbed, ignored, exports.size());
        return linked + stubbed;
    }

    /**
     * Prepara y reanuda un hilo PPC32 mediante un trampoline residente en RAM.
     * El trampoline carga la rutina en CTR y usa bctrl, por lo que LR conserva
     * el retorno al stub; al terminar la rutina, blr transfiere al thread-exit.
     *
     * @param cpu CPU invitada
     * @param routine dirección de la rutina guest
     * @param argument argumento inicial en r3
     * @param stackTop extremo superior virtual de la pila (16-byte aligned)
     * @param threadExit dirección de retorno/terminación del hilo
     * @param trampoline dirección RAM reservada para el stub (16 bytes)
     */
    public void startThread(CPU cpu, long routine, long argument,
            long stackTop, long threadExit, long trampoline) {
        if (cpu == null) throw new IllegalArgumentException("CPU nula");
        checkGuestAddress(routine, "routine");
        checkGuestAddress(stackTop, "stackTop");
        checkGuestAddress(threadExit, "threadExit");
        checkGuestAddress(trampoline, "trampoline");
        if ((stackTop & 15L) != 0 || (trampoline & 3L) != 0) {
            throw new IllegalArgumentException("Alineación PPC inválida");
        }
        long trampolineEnd = (trampoline + 20L) & 0xFFFFFFFFL;
        if (trampolineEnd < trampoline || trampolineEnd >= stackTop) {
            throw new IllegalArgumentException("Trampoline fuera de la región reservada");
        }

        int hi = (int) ((routine >>> 16) & 0xFFFF);
        int lo = (int) (routine & 0xFFFF);
        // lis r12,hi; ori r12,r12,lo; mtctr r12; bctrl; blr
        memory.writeMemoryWord(trampoline,      0x3D800000 | hi);
        memory.writeMemoryWord(trampoline + 4,  0x618C0000 | lo);
        memory.writeMemoryWord(trampoline + 8,  0x7D8903A6);
        memory.writeMemoryWord(trampoline + 12, 0x4E800421);
        memory.writeMemoryWord(trampoline + 16, 0x4E800020);

        cpu.setMsr(cpu.getMsr() & 0x7FFFFFFFFFFFFFFFL); // PPC32 / SF=0
        cpu.setGPR(1, (stackTop - 0x100L) & ~15L);
        cpu.setGPR(3, argument & 0xFFFFFFFFL);
        cpu.setGPR(4, 0L);
        cpu.setGPR(5, 0L);
        cpu.setGPR(6, 0L);
        cpu.setGPR(7, 0L);
        cpu.setGPR(8, 0L);
        cpu.setGPR(9, 0L);
        cpu.setGPR(10, 0L);
        cpu.setGPR(11, 0L);
        cpu.setGPR(12, 0L);
        cpu.setCtr(0L);
        cpu.setLr(threadExit & 0xFFFFFFFFL);
        cpu.setPc(trampoline & 0xFFFFFFFFL);
        System.out.printf("[TKERNEL] THREAD START routine=%08X arg=%08X PC=%08X r1=%08X LR=%08X%n",
                routine, argument, trampoline, cpu.getGPRVal(1), cpu.getLr());
    }

    private static void checkGuestAddress(long address, String field) {
        if (address <= 0 || address > 0xFFFFFFFFL || (address & 3L) != 0) {
            throw new IllegalArgumentException(field + " fuera de rango/alineación PPC: "
                    + Long.toHexString(address));
        }
    }

    /** Instala la capa mínima de servicios de display usada por bootanim. */
    public void initializeBootDisplay() {
        enginesInitialized = true;
        ringBufferInitialized = true;
        displayModeSet = true;
        System.out.println("[TKERNEL] VdInitializeEngines/RingBuffer/SetDisplayMode: estado host inicializado");
    }

    /** Atiende la solicitud exportada por XAM para iniciar la animación. */
    public long invokeKernelExport(int ordinal, CPU cpu) {
        String name = namedKernelOrdinals.get(ordinal);
        if (name == null) return unsigned(STATUS_NOT_IMPLEMENTED);
        switch (name) {
            case "AniStartBootAnimation":
                if (!bootAnimationModuleLoaded) return unsigned(STATUS_NOT_IMPLEMENTED);
                bootAnimationRequested = true;
                initializeBootDisplay();
                System.out.printf("[TKERNEL] AniStartBootAnimation accepted: module=%08X+%08X entry=%b audioMapped=%b audioVA=%08X%n",
                        bootAnimationImageBase, bootAnimationImageSize,
                        bootAnimationModuleHasEntryPoint,
                        isBootAnimationResourceMapped("audio"),
                        isBootAnimationResourceMapped("audio") ? getBootAnimationResourceAddress("audio") : 0L);
                if (!bootAnimationModuleHasEntryPoint) {
                    System.out.println("[TKERNEL] bootanim es DLL kernel-managed: no se ejecuta .text sin rutina de entrada identificada.");
                }
                return 0;
            case "KeDelayExecutionThread":
                // Scheduler cooperativo de un solo hilo: el tiempo avanza en el host.
                return 0;
            case "VdInitializeEngines":
                enginesInitialized = true;
                return 0;
            case "VdInitializeRingBuffer":
                ringBufferInitialized = true;
                return 0;
            case "VdSetDisplayMode":
            case "VdSetDisplayModeOverride":
                displayModeSet = true;
                return 0;
            case "VdShutdownEngines":
                enginesInitialized = false;
                return 0;
            case "VdTurnDisplayOff":
                displayModeSet = false;
                return 0;
            case "NtCreateFile":
                return ntCreateFile(cpu);
            case "NtReadFile":
                return ntReadFile(cpu);
            default:
                return unsigned(STATUS_NOT_IMPLEMENTED);
        }
    }

    /**
     * Atiende AniStartBootAnimation (ordinal 678 de xboxkrnl en esta NAND).
     * El módulo bootanim no contiene entrypoint PE; la solicitud activa los
     * servicios de display y deja registrada la transición para el scheduler.
     */
    public long startBootAnimation(CPU cpu) {
        bootAnimationRequested = true;
        initializeBootDisplay();
        System.out.printf("[TKERNEL] AniStartBootAnimation: dispatch real atendido, PC=%08X LR=%08X%n",
                cpu.getPc(), cpu.getLr());
        return 0;
    }


    /** NtCreateFile: apertura de solo lectura sobre la NAND flash montada. */
    private long ntCreateFile(CPU cpu) {
        if (flashFs == null) return unsigned(STATUS_NOT_IMPLEMENTED);
        long handleOut = u32(cpu.getGPRVal(3));
        long objectAttributes = u32(cpu.getGPRVal(5));
        long ioStatus = u32(cpu.getGPRVal(6));
        int disposition = (int) u32(cpu.getGPRVal(10));
        if (handleOut == 0 || objectAttributes == 0 || disposition != FILE_OPEN) {
            return unsigned(STATUS_INVALID_PARAMETER);
        }
        long objectName = u32(memory.readMemoryWord(objectAttributes + 8));
        if (objectName == 0) return unsigned(STATUS_INVALID_PARAMETER);
        int nameLength = memory.readMemoryWord(objectName) & 0xFFFF;
        long nameBuffer = u32(memory.readMemoryWord(objectName + 4));
        if (nameLength <= 0 || nameLength > 0x800 || (nameLength & 1) != 0 || nameBuffer == 0) {
            return unsigned(STATUS_INVALID_PARAMETER);
        }
        StringBuilder path = new StringBuilder(nameLength / 2);
        for (int i = 0; i < nameLength; i += 2) {
            int ch = ((memory.read(nameBuffer + i) & 0xFF) << 8)
                    | (memory.read(nameBuffer + i + 1) & 0xFF);
            path.append((char) ch);
        }
        String normalized = normalizeFlashPath(path.toString());
        XenonFlashFs.Entry entry = findFlashEntry(normalized);
        if (entry == null) {
            writeIoStatus(ioStatus, STATUS_OBJECT_NAME_NOT_FOUND, 0);
            return unsigned(STATUS_OBJECT_NAME_NOT_FOUND);
        }
        // Mantener sólo la referencia FAT: los XEX grandes se leen por
        // bloques mediante NtReadFile, sin duplicar el archivo completo.
        int handle = allocateGuestHandle();
        guestFiles.put(handle, new GuestFile(entry));
        memory.writeMemoryWord(handleOut, handle);
        writeIoStatus(ioStatus, STATUS_SUCCESS, 1);
        System.out.printf("[TKERNEL] NtCreateFile '%s' -> handle=%08X size=%08X%n",
                path, handle, entry.size);
        return unsigned(STATUS_SUCCESS);
    }

    /** NtReadFile: lectura síncrona desde el objeto abierto por NtCreateFile. */
    private long ntReadFile(CPU cpu) {
        int handle = (int) u32(cpu.getGPRVal(3));
        long ioStatus = u32(cpu.getGPRVal(7));
        long buffer = u32(cpu.getGPRVal(8));
        long length = u32(cpu.getGPRVal(9));
        long byteOffsetPtr = u32(cpu.getGPRVal(10));
        GuestFile file = guestFiles.get(handle);
        if (file == null) {
            writeIoStatus(ioStatus, STATUS_INVALID_HANDLE, 0);
            return unsigned(STATUS_INVALID_HANDLE);
        }
        if (buffer == 0 || length > Integer.MAX_VALUE) {
            writeIoStatus(ioStatus, STATUS_INVALID_PARAMETER, 0);
            return unsigned(STATUS_INVALID_PARAMETER);
        }
        long offset = file.position;
        if (byteOffsetPtr != 0) {
            long high = u32(memory.readMemoryWord(byteOffsetPtr));
            long low = u32(memory.readMemoryWord(byteOffsetPtr + 4));
            offset = (high << 32) | low;
        }
        if (offset < 0 || offset > file.entry.size) {
            writeIoStatus(ioStatus, STATUS_INVALID_PARAMETER, 0);
            return unsigned(STATUS_INVALID_PARAMETER);
        }
        int count = (int) Math.min(length, file.entry.size - offset);
        try {
            if (count > 0) memory.writeBlock(buffer, flashFs.read(file.entry, offset, count));
        } catch (IOException ex) {
            writeIoStatus(ioStatus, STATUS_OBJECT_NAME_NOT_FOUND, 0);
            return unsigned(STATUS_OBJECT_NAME_NOT_FOUND);
        }
        file.position = offset + count;
        writeIoStatus(ioStatus, STATUS_SUCCESS, count);
        return unsigned(STATUS_SUCCESS);
    }

    private int allocateGuestHandle() {
        int start = nextGuestHandle;
        do {
            int candidate = nextGuestHandle;
            nextGuestHandle += 4;
            if (nextGuestHandle <= 0 || nextGuestHandle > 0x7FFFFFF0) nextGuestHandle = 0x100;
            if (!guestFiles.containsKey(candidate)) return candidate;
        } while (nextGuestHandle != start);
        throw new IllegalStateException("Tabla de handles guest agotada");
    }

    private void writeIoStatus(long address, int status, int information) {
        if (address == 0) return;
        memory.writeMemoryWord(address, status);
        memory.writeMemoryWord(address + 4, information);
    }

    private XenonFlashFs.Entry findFlashEntry(String normalizedPath) {
        XenonFlashFs.Entry exact = flashFs.find(normalizedPath);
        if (exact != null) return exact;
        String base = normalizedPath;
        int slash = base.lastIndexOf('/');
        if (slash >= 0) base = base.substring(slash + 1);
        exact = flashFs.find(base);
        if (exact != null) return exact;
        for (XenonFlashFs.Entry entry : flashFs.entries()) {
            String candidate = normalizeFlashPath(entry.name);
            if (normalizedPath.endsWith("/" + candidate)) return entry;
        }
        return null;
    }

    private static String normalizeFlashPath(String value) {
        String path = value.replace('\\', '/').toLowerCase(Locale.ROOT);
        while (path.startsWith("/")) path = path.substring(1);
        while (path.contains("//")) path = path.replace("//", "/");
        return path;
    }

    private static long u32(long value) { return value & 0xFFFFFFFFL; }

    private static long unsigned(int value) { return value & 0xFFFFFFFFL; }
}
