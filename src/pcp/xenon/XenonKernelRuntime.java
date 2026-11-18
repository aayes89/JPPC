/*
 * The MIT License
 * Copyright 2026 Slam.
 */
package pcp.xenon;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import pcp.PPCSimulator;
import pcp.cpu.CPU;

/**
 * Carga HV+xboxkrnl reconstruidos y ejecuta la secuencia inicial del HV hasta
 * observar y ejecutar la primera instrucción del .text del kernel original.
 */
public final class XenonKernelRuntime {

    private static final int HYPERVISOR_SIZE = 0x40000;
    private static final int XENON_PVR = 0x00710000;
    private static final long MSR_SF = 0x8000000000000000L;
    private static final long HYPERVISOR_LOAD_BASE = 0x80000000L;
    private static final long MAX_BOOT_INSTRUCTIONS = 5_000_000L;

    private XenonKernelRuntime() {
    }

    public static final class Result {
        public final long firstKernelPc;
        public final int firstKernelInstruction;
        public final long kernelEntryPoint;
        public final long kernelTextStart;
        public final long kernelTextEnd;
        public final long kernelImageStart;
        public final long kernelImageEnd;
        public final PPCSimulator simulator;
        public final long handoffPc;
        public final long handoffMsr;
        public final long handoffHrmor;
        public final long handoffR1;
        public final long handoffR2;
        public final long handoffR3;
        public final long handoffLr;
        public final long instructionsToKernel;
        public final long instructionsExecutedInKernel;
        public final long finalPc;
        public final long finalMsr;
        public final List<XenonPostCodeTracker.Entry> postCodes;

        private Result(PPCSimulator simulator,
                long firstKernelPc, int firstKernelInstruction,
                long kernelEntryPoint, long kernelTextStart, long kernelTextEnd,
                long kernelImageStart, long kernelImageEnd,
                long handoffPc, long handoffMsr, long handoffHrmor,
                long handoffR1, long handoffR2, long handoffR3, long handoffLr,
                long instructionsToKernel, long instructionsExecutedInKernel,
                long finalPc, long finalMsr,
                List<XenonPostCodeTracker.Entry> postCodes) {
            this.simulator = simulator;
            this.kernelImageStart = kernelImageStart;
            this.kernelImageEnd = kernelImageEnd;
            this.handoffPc = handoffPc;
            this.handoffMsr = handoffMsr;
            this.handoffHrmor = handoffHrmor;
            this.handoffR1 = handoffR1;
            this.handoffR2 = handoffR2;
            this.handoffR3 = handoffR3;
            this.handoffLr = handoffLr;
            this.firstKernelPc = firstKernelPc;
            this.firstKernelInstruction = firstKernelInstruction;
            this.kernelEntryPoint = kernelEntryPoint;
            this.kernelTextStart = kernelTextStart;
            this.kernelTextEnd = kernelTextEnd;
            this.instructionsToKernel = instructionsToKernel;
            this.instructionsExecutedInKernel = instructionsExecutedInKernel;
            this.finalPc = finalPc;
            this.finalMsr = finalMsr;
            this.postCodes = postCodes;
        }
    }


    public enum StopReason {
        INSTRUCTION_LIMIT,
        CPU_HALTED,
        REPEATED_EXTERNAL_CONTROL_LOOP
    }

    /** Snapshot compacto de una instrucción ejecutada durante M12. */
    public static final class StepSnapshot {
        public final long pc;
        public final int opcode;
        public final long nextPc;
        public final long msr;
        public final long lr;
        public final long ctr;
        public final long cr;
        public final boolean inKernelImage;
        public final boolean inKernelText;
        public final long r1;
        public final long r12;
        public final long r13;
        /** Hash de GPR0-31, CTR, LR y CR tras la instrucción (detección de ciclos). */
        final long stateHash;

        private StepSnapshot(long pc, int opcode, long nextPc, long msr,
                long lr, long ctr, long cr, boolean inKernelImage,
                boolean inKernelText, long r1, long r12, long r13,
                long stateHash) {
            this.pc = pc;
            this.opcode = opcode;
            this.nextPc = nextPc;
            this.msr = msr;
            this.lr = lr;
            this.ctr = ctr;
            this.cr = cr;
            this.inKernelImage = inKernelImage;
            this.inKernelText = inKernelText;
            this.r1 = r1;
            this.r12 = r12;
            this.r13 = r13;
            this.stateHash = stateHash;
        }
    }

    private static long stateHash(CPU cpu) {
        long h = 0xCBF29CE484222325L;
        for (int i = 0; i < 32; i++) {
            h ^= cpu.getGPRVal(i);
            h *= 0x100000001B3L;
            h ^= h >>> 29;
        }
        h ^= Integer.toUnsignedLong(cpu.getCtr());
        h *= 0x100000001B3L;
        h ^= cpu.getLr();
        h *= 0x100000001B3L;
        h ^= cpu.getCr();
        h *= 0x100000001B3L;
        return h;
    }

    /** Estado final y ventana de traza de la ejecución sostenida del kernel. */
    public static final class ExecutionResult {
        public final long instructionsExecuted;
        public final long initialPc;
        public final long finalPc;
        public final long finalMsr;
        public final long srr0;
        public final long srr1;
        public final long dar;
        public final long dsisr;
        public final long lastIllegalInstruction;
        public final boolean halted;
        public final StopReason stopReason;
        public final long[] gpr0To10;
        public final long[] gpr0To31;
        public final List<StepSnapshot> trace;

        private ExecutionResult(long instructionsExecuted, long initialPc,
                long finalPc, long finalMsr, long srr0, long srr1,
                long dar, long dsisr, long lastIllegalInstruction,
                boolean halted, StopReason stopReason, long[] gpr0To10,
                long[] gpr0To31, List<StepSnapshot> trace) {
            this.instructionsExecuted = instructionsExecuted;
            this.initialPc = initialPc;
            this.finalPc = finalPc;
            this.finalMsr = finalMsr;
            this.srr0 = srr0;
            this.srr1 = srr1;
            this.dar = dar;
            this.dsisr = dsisr;
            this.lastIllegalInstruction = lastIllegalInstruction;
            this.halted = halted;
            this.stopReason = stopReason;
            this.gpr0To10 = gpr0To10.clone();
            this.gpr0To31 = gpr0To31.clone();
            this.trace = java.util.Collections.unmodifiableList(
                    new java.util.ArrayList<>(trace));
        }
    }

    /**
     * M12: continúa la ejecución desde el estado dejado por M11.
     * El presupuesto limita el trabajo; no instala manejadores ficticios ni
     * altera el estado de la CPU para ocultar excepciones.
     */
    public static ExecutionResult continueKernel(Result boot, long maxInstructions)
            throws IOException {
        if (boot == null || boot.simulator == null
                || boot.kernelImageEnd <= boot.kernelImageStart) {
            throw new IOException("Resultado M11 incompleto: falta CPU o rango PE");
        }
        return executeKernelWindow(boot.simulator, boot.kernelImageStart,
                boot.kernelImageEnd, boot.kernelTextStart, boot.kernelTextEnd,
                maxInstructions);
    }

    /**
     * Ejecuta instrucciones desde el PC actual y conserva las últimas 64
     * instrucciones. La variante pública permite probar el motor sin NAND.
     */
    public static ExecutionResult executeKernelWindow(PPCSimulator simulator,
            long imageStart, long imageEnd, long textStart, long textEnd,
            long maxInstructions) throws IOException {
        if (simulator == null || imageStart < 0 || imageEnd <= imageStart
                || textStart < imageStart || textEnd <= textStart
                || textEnd > imageEnd || maxInstructions <= 0) {
            throw new IOException("Parámetros inválidos para ejecución M12");
        }

        CPU cpu = simulator.getCpu();
        long initialPc = cpu.getPc();
        long executed = 0;
        java.util.ArrayDeque<StepSnapshot> trace = new java.util.ArrayDeque<>(64);
        StopReason stopReason = StopReason.INSTRUCTION_LIMIT;

        while (executed < maxInstructions && !cpu.isHalted()) {
            long pc = cpu.getPc();
            long normalizedPc = pc & 0xFFFFFFFFL;
            int opcode = 0;
            try {
                // La traza interna registra la instrucción que CPU.step realmente
                // decodifica; no se vuelve a hacer fetch ni se modifica MMU.
                cpu.step();
                executed++;
                if (cpu.getLastExecutedPc() == pc) {
                    opcode = cpu.getLastExecutedOpcode();
                }

                long nextPc = cpu.getPc();
                StepSnapshot snapshot = new StepSnapshot(pc, opcode, nextPc,
                        cpu.getMsr(), cpu.getLr(), Integer.toUnsignedLong(cpu.getCtr()),
                        cpu.getCr(), normalizedPc >= imageStart && normalizedPc < imageEnd,
                        normalizedPc >= textStart && normalizedPc < textEnd,
                        cpu.getGPRVal(1), cpu.getGPRVal(12), cpu.getGPRVal(13),
                        stateHash(cpu));
                if (trace.size() == 64) trace.removeFirst();
                trace.addLast(snapshot);
                if (hasRepeatedExternalCycle(trace)) {
                    stopReason = StopReason.REPEATED_EXTERNAL_CONTROL_LOOP;
                    break;
                }
            } catch (RuntimeException ex) {
                throw new IOException(String.format(
                        "Fallo del emulador durante M12 en PC=0x%016X tras %d instrucciones: %s",
                        pc, executed, ex.getMessage()), ex);
            }
        }

        long[] gpr = new long[11];
        long[] allGpr = new long[32];
        for (int i = 0; i < allGpr.length; i++) allGpr[i] = cpu.getGPRVal(i);
        System.arraycopy(allGpr, 0, gpr, 0, gpr.length);
        return new ExecutionResult(executed, initialPc, cpu.getPc(),
                cpu.getMsr(), cpu.getSrr0(), cpu.getSrr1(),
                cpu.getDar(), cpu.getDsisr(), cpu.getLastIllegalInsn(),
                cpu.isHalted(), cpu.isHalted() ? StopReason.CPU_HALTED
                        : stopReason,
                gpr, allGpr, new java.util.ArrayList<>(trace));
    }

    /**
     * Detecta ciclos deterministas fuera de la imagen del kernel: mismo PC,
     * opcode, destino y estado completo de registros en cada repetición. No
     * detiene bucles con progreso (p. ej. el barrido tlbiel del HV, donde
     * cambian r8/CTR) ni bucles de espera dentro del PE (polling legítimo).
     */
    private static boolean hasRepeatedExternalCycle(
            java.util.ArrayDeque<StepSnapshot> trace) {
        int n = trace.size();
        if (n < 3) return false;
        java.util.List<StepSnapshot> steps = new java.util.ArrayList<>(trace);
        int maxPeriod = Math.min(16, n / 3);
        for (int period = 1; period <= maxPeriod; period++) {
            int start = n - (period * 3);
            boolean repeated = true;
            for (int i = 0; i < period * 3; i++) {
                StepSnapshot current = steps.get(start + i);
                StepSnapshot pattern = steps.get(start + (i % period));
                if (current.inKernelImage || pattern.inKernelImage
                        || current.pc != pattern.pc
                        || current.opcode != pattern.opcode
                        || current.nextPc != pattern.nextPc
                        || current.stateHash != pattern.stateHash) {
                    repeated = false;
                    break;
                }
            }
            if (repeated) return true;
        }
        return false;
    }

    public static Result bootToKernelText(PPCSimulator simulator,
            XenonKernelPatchLoader.Result kernel, Path fusesPath)
            throws IOException {
        if (simulator == null || kernel == null || fusesPath == null) {
            throw new IOException("Runtime requiere simulator, imagen kernel y fuses");
        }
        byte[] image = kernel.getImage();
        byte[] hypervisor = null;
        byte[] kernelPe = null;
        try {
            if (image.length != kernel.newSize || image.length <= HYPERVISOR_SIZE) {
                throw new IOException("Tamaño de imagen HV/kernel inconsistente");
            }

            // M13: conservar el contexto real que CD dejó al transferir a CE.
            // El reset anterior borraba HRMOR, MSR, GPRs, SPRs y RAM de trabajo,
            // dejando al HV con stack/segmentos inválidos.
            CPU cpu = simulator.getCpu();
            long handoffPc = cpu.getPc();
            long handoffMsr = cpu.getMsr();
            long handoffHrmor = cpu.getHRMOR();
            long handoffR1 = cpu.getGPRVal(1);
            long handoffR2 = cpu.getGPRVal(2);
            long handoffR3 = cpu.getGPRVal(3);
            long handoffLr = cpu.getLr();
            simulator.detachBootExecutionAliases();
            XenonFuses lockdownFuses = new XenonFuses();
            lockdownFuses.load(fusesPath);
            int fuseLdv = XenonBootLockdownValidator.calculateFuseLdv(
                    lockdownFuses.getFuseSet(7));
            if (kernel.cfLdv != fuseLdv) {
                throw new IOException(String.format(
                        "Kernel CF LDV incompatible con eFuse07: CF=%d eFuse07=%d",
                        kernel.cfLdv, fuseLdv));
            }
            System.out.printf("[M10] CF LDV=%d eFuse07=%d VERIFIED%n", kernel.cfLdv, fuseLdv);

            hypervisor = Arrays.copyOfRange(image, 0, HYPERVISOR_SIZE);
            kernelPe = Arrays.copyOfRange(image, HYPERVISOR_SIZE, image.length);

            // El HV entra inicialmente por el alias 0x80000000 en SF=1.
            // Después de su RFI, el kernel accede a la misma imagen mediante
            // HRMOR + EA. Mantener ambos backing aliases evita que el cambio
            // de modo apunte a una región RAM vacía.
            long hrmor = cpu.getHRMOR();
            simulator.getMemory().writeBlock(HYPERVISOR_LOAD_BASE, hypervisor);
            if (hrmor != 0) {
                simulator.getMemory().writeBlock(hrmor + HYPERVISOR_LOAD_BASE, hypervisor);
            }

            XenonPeLoader peLoader = new XenonPeLoader();
            XenonPeLoader.LoadedImage loadedKernel
                    = peLoader.loadMemoryImage(kernelPe, simulator.getMemory());
            // El kernel retail descubre Xenos mediante la ventana virtual 0x7FC80000.
            // La superficie primaria inicial de Xenon ocupa 0x1E000000.
            simulator.setFbBase(PPCSimulator.FB_BASE_ELF);
            simulator.initializeXenonFramebuffer();
            if (hrmor != 0) {
                peLoader.loadMemoryImage(kernelPe, simulator.getMemory(), hrmor + loadedKernel.getImageBase());
            }
            if (loadedKernel.getImageBase() != kernel.imageInfo.kernelImageBase
                    || loadedKernel.getEntryPoint() != kernel.imageInfo.kernelEntryPoint
                    || loadedKernel.getSizeOfImage() != kernel.imageInfo.kernelSizeOfImage) {
                throw new IOException("XenonPeLoader no coincide con los metadatos del kernel reconstruido");
            }

            long textStart = -1;
            long textEnd = -1;
            for (XenonPeLoader.Section section : loadedKernel.getSections()) {
                if (".text".equals(section.getName())) {
                    textStart = loadedKernel.getImageBase() + section.getVirtualAddress();
                    textEnd = textStart + Math.max(section.getVirtualSize(), section.getRawSize());
                    break;
                }
            }
            if (textStart < 0 || textEnd <= textStart) {
                throw new IOException("No se encontró la sección .text del kernel");
            }

            cpu.setPvr(XENON_PVR);
            // MSR, HRMOR, stack y registros generales permanecen como los dejó CD.
            cpu.setPc(HYPERVISOR_LOAD_BASE + kernel.imageInfo.hypervisorEntryOffset);
            cpu.setHaltOnSelfBranch(false);

            System.out.printf("[M13] CD handoff PC=%016X MSR=%016X HRMOR=%016X r1=%016X%n",
                    handoffPc, handoffMsr, handoffHrmor, handoffR1);
            System.out.printf("[M13] HV aliases: EA=%016X HRMOR+EA=%016X%n",
                    HYPERVISOR_LOAD_BASE, handoffHrmor + HYPERVISOR_LOAD_BASE);
            System.out.printf("[M13] Kernel aliases: EA=%016X HRMOR+EA=%016X%n",
                    loadedKernel.getImageBase(), handoffHrmor + loadedKernel.getImageBase());
            System.out.printf("[M13] HV loaded @%016X entry=%016X (context preserved)%n",
                    HYPERVISOR_LOAD_BASE, cpu.getPc());
            System.out.printf("[M6] xboxkrnl loaded base=%08X entry=%08X .text=[%08X,%08X)%n",
                    loadedKernel.getImageBase(), loadedKernel.getEntryPoint(), textStart, textEnd);

            long firstKernelPc = -1;
            int firstInstruction = 0;
            long stepsToKernel = 0;
            for (long i = 0; i < MAX_BOOT_INSTRUCTIONS; i++) {
                long pc = cpu.getPc();
                long pc32 = pc & 0xFFFFFFFFL;
                if (pc32 >= textStart && pc32 < textEnd) {
                    firstKernelPc = pc;
                    firstInstruction = cpu.fetchInstruction();
                    break;
                }
                if (cpu.isHalted()) break;
                cpu.step();
                stepsToKernel++;
            }
            if (firstKernelPc < 0) {
                throw new IOException(String.format(
                        "HV no transfirió control al .text de xboxkrnl dentro de %d instrucciones; PC=0x%016X",
                        MAX_BOOT_INSTRUCTIONS, cpu.getPc()));
            }
            if (firstInstruction == 0 || firstInstruction == 0x4E4E4497) {
                throw new IOException(String.format(
                        "Primera instrucción kernel inválida en 0x%016X: %08X",
                        firstKernelPc, firstInstruction));
            }

            cpu.step();
            long executed = 1;
            if (executed != 1) {
                throw new IOException("La CPU no contabilizó la primera instrucción de xboxkrnl");
            }

            System.out.printf("[M6] KERNEL EXECUTED PC=%016X OP=%08X instructionsBefore=%d nextPC=%016X%n",
                    firstKernelPc, firstInstruction, stepsToKernel, cpu.getPc());
            long imageStart = loadedKernel.getImageBase() & 0xFFFFFFFFL;
            long imageEnd = imageStart + loadedKernel.getSizeOfImage();
            return new Result(simulator, firstKernelPc, firstInstruction,
                    loadedKernel.getEntryPoint(), textStart, textEnd,
                    imageStart, imageEnd, handoffPc, handoffMsr, handoffHrmor,
                    handoffR1, handoffR2, handoffR3, handoffLr,
                    stepsToKernel, executed, cpu.getPc(), cpu.getMsr(),
                    simulator.getBootController().getPostTracker().history());
        } finally {
            wipe(image);
            wipe(hypervisor);
            wipe(kernelPe);
        }
    }

    private static void wipe(byte[] data) {
        if (data != null) Arrays.fill(data, (byte) 0);
    }
}
