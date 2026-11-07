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
package pcp.cpu;

import pcp.system.ExceptionHandler;
import pcp.system.MMU;
import pcp.system.CacheController;
import pcp.cpu.Instruction.Arithmetics;
import pcp.cpu.Instruction.Branchs;
import pcp.cpu.Instruction.EmbeddedFP;
import pcp.cpu.Instruction.FloatingPoint;
import pcp.cpu.Instruction.GraphicsOperations;
import pcp.cpu.Instruction.Logical;
import pcp.cpu.Instruction.MemoryInst;
import pcp.cpu.Instruction.Rotation;
import pcp.cpu.Instruction.SignalProcessing;
import pcp.cpu.Instruction.SpecialInstr;
import pcp.cpu.Instruction.VectorOperations;
import pcp.system.Bus;
import pcp.utils.DecodingInstr;
import pcp.utils.ELFFile;

/**
 *
 * @author Slam
 */
public class CPU {

    private DecodingInstr decode = new DecodingInstr();
    private final Bus bus;
    // Variable para modo debug
    boolean debugMode = false;

    private boolean interruptPending = false;
    private int machine;
    // Registros
    private long pc;
    private long acc; // Accumulator
    // Registros de propósito general (r0-r31)
    private long[] gpr = new long[32];
    // Registro de condición (CR: 32 bits, 8 campos de 4 bits)
    private long cr;
    private long lr;   // Link Register
    private long ctr;  // Count Register (64 bits)
    private long xer;  // Fixed-Point Exception Register (SO, OV, CA)
    private long msr;      // Machine State Register
    private long srr0;     // Save/Restore Register 0
    private long srr1;     // Save/Restore Register 1
    private int sprg0;    // Special Purpose Register General 0
    private int sprg1;    // Special Purpose Register General 1
    private int sprg2;    // Special Purpose Register General 2
    private int sprg3;    // Special Purpose Register General 3
    private long hrmorValue;
    private long hid1Value;
    private long tlbSPR3B3;
    private long tlbSPR3B4;
    private long tlbSPR3B5;
    private long msrValue;
    private int SDR1;
    private int MQ;     // m quotient
    private int RTCU, RTCL;
    private int EAR;
    private int TBL, TBU;
    private int IBAT0U, IBAT0L, IBAT1U, IBAT1L, IBAT2U, IBAT2L, IBAT3U, IBAT3L;
    private int DBAT0U, DBAT0L, DBAT1U, DBAT1L, DBAT2U, DBAT2L, DBAT3U, DBAT3L;
    private long dar;  // Data Address Register
    private long dsisr; // Data Storage Interrupt Status Register
    private int pvr;  // Processor Version Register
    private long timeBase; // Combinación de TBU y TBL (64 bits)
    private int dec;       // Decrementer
    private long fpscr; // Floating-Point Status and Control Register
    private long vscr;  // Vector Status and Control Register

    private boolean interruptMode;
    private double[] fpr = new double[32];
    private int[][] vr = new int[32][4];
    private int[] sr = new int[16];
    private long reserveAddress = -1;

    // Máscaras para bits del XER    
    public static final int XER_SO = 0x80000000; // Summary Overflow (bit 0 PPC)
    public static final int XER_OV = 0x40000000; // Overflow
    public static final int XER_CA = 0x20000000; // Carry
    // Bits del MSR
    // Definir constantes para MSR
    //public static final int MSR_EE = 0x8000; // External Interrupt Enable
    public static final int MSR_PR = 1 << 14; // Problem State
    public static final int MSR_EE = 1 << 15; // External Interrupt Enable
    public static final int MSR_FP = 1 << 13; // Floating-Point Available    
    public static final int MSR_IR = 1 << 5; // 0x20
    public static final int MSR_DR = 1 << 4; // 0x10

    // Bits del FPSCR
    public static final int FPSCR_FX = 1 << 31; // Floating-Point Exception Summary
    public static final int FPSCR_FEX = 1 << 30; // Floating-Point Enabled Exception Summary
    public static final int FPSCR_VX = 1 << 29; // Floating-Point Invalid Operation Exception Summary
    public static final int FPSCR_OX = 1 << 28; // Floating-Point Overflow Exception
    public static final int FPSCR_UX = 1 << 27; // Floating-Point Underflow Exception
    public static final int FPSCR_ZX = 1 << 26; // Floating-Point Zero Divide Exception
    public static final int FPSCR_XX = 1 << 25; // Floating-Point Inexact Exception
    public static final int FPSCR_VXSNAN = 1 << 24; // Invalid Operation Exception (SNaN)
    public static final int FPSCR_VXSQRT = 0x00000400;

    // Clases de operaciones
    private final Arithmetics integerArithmetic;
    private final Logical logical;
    private final Branchs branchs;
    private final MemoryInst memoryOps;
    private final SpecialInstr specialInstr;
    private final FloatingPoint fp;
    private final VectorOperations vector;
    private final EmbeddedFP efp;
    private final SignalProcessing spe;
    private final GraphicsOperations gfx;
    private final ExceptionHandler exceptionHandler;
    private final MMU mmu;
    private final CacheController cache;
    private final Rotation rotations;

    public CPU(Bus bus, CacheController cache) {
        this.bus = bus;
        this.cache = cache;
        this.mmu = new MMU(this);
        this.integerArithmetic = new Arithmetics(this);
        this.logical = new Logical(this);
        this.branchs = new Branchs(this);
        this.memoryOps = new MemoryInst(this);
        this.specialInstr = new SpecialInstr(this);
        this.fp = new FloatingPoint(this);
        this.vector = new VectorOperations(this);
        this.efp = new EmbeddedFP(this);
        this.spe = new SignalProcessing(this);
        this.gfx = new GraphicsOperations(this);
        this.exceptionHandler = new ExceptionHandler(this);
        this.rotations = new Rotation(this);
    }

    // =====================================================================
    // Núcleo de ejecución PowerPC 64 bits (modo SF=1 como el Xenon real).
    // Los GPR son de 64 bits; las instrucciones "de palabra" siguen la ISA.
    // Los accesos a memoria usan los 32 bits bajos de la dirección efectiva.
    // =====================================================================
    private static final long M32 = 0xFFFFFFFFL;
    private long nextPc;

    private long addressWidth(long address) {
        return sf() ? address : address & M32;
    }
    private boolean decPending = false;
    private volatile boolean halted = false;
    private long instructionCount = 0;
    private boolean haltOnSelfBranch = true;
    // Ring buffer de instrucciones realmente obtenidas por fetch desde la RAM invitada.
    private static final int EXEC_TRACE_CAPACITY = 256;
    private final long[] execTracePc = new long[EXEC_TRACE_CAPACITY];
    private final int[] execTraceOpcode = new int[EXEC_TRACE_CAPACITY];
    private int execTraceWrite;
    private int execTraceSize;

    public synchronized long[][] getExecutionTrace() {
        long[][] out = new long[execTraceSize][2];
        int start = (execTraceWrite - execTraceSize + EXEC_TRACE_CAPACITY) % EXEC_TRACE_CAPACITY;
        for (int i = 0; i < execTraceSize; i++) {
            int at = (start + i) % EXEC_TRACE_CAPACITY;
            out[i][0] = execTracePc[at];
            out[i][1] = execTraceOpcode[at] & 0xFFFFFFFFL;
        }
        return out;
    }

    private synchronized void recordExecution(long address, int opcode) {
        execTracePc[execTraceWrite] = address;
        execTraceOpcode[execTraceWrite] = opcode;
        execTraceWrite = (execTraceWrite + 1) % EXEC_TRACE_CAPACITY;
        if (execTraceSize < EXEC_TRACE_CAPACITY) execTraceSize++;
    }
    private final long[] sprOther = new long[1024];
    private long lastIllegalInsn = 0;

    public void setTrace(boolean t) {
        debugMode = t;
    }

    public boolean isHalted() {
        return halted;
    }

    public void halt() {
        halted = true;
    }

    public void resume() {
        halted = false;
    }

    public long getInstructionCount() {
        return instructionCount;
    }

    public void setHaltOnSelfBranch(boolean v) {
        haltOnSelfBranch = v;
    }


    private long r(int n) {
        return gpr[n];
    }

    private void w(int n, long v) {
        gpr[n] = v;
    }

    private long ra0(int ra) {
        return ra == 0 ? 0 : gpr[ra];
    }

    private static final long MSR_SF = 0x8000000000000000L;

    /**
     * MSR[SF]: modo de 64 bits. Con SF=0 las comparaciones, CR0,
     * acarreo/overflow y CTR usan 32 bits.
     */
    private boolean sf() {
        return (msr & MSR_SF) != 0;
    }

    private int crBit(int bi) {
        return (int) ((cr >>> (31 - bi)) & 1);
    }

    private void crSetBit(int bi, int v) {
        int sh = 31 - bi;
        cr = ((cr & ~(1L << sh)) | ((long) (v & 1) << sh)) & M32;
    }

    private void crSetField(int f, int v) {
        int sh = (7 - f) * 4;
        cr = ((cr & ~(0xFL << sh)) | ((long) (v & 0xF) << sh)) & M32;
    }

    private void cmpCR(int crf, int cmp) {
        int f = cmp < 0 ? 8 : (cmp > 0 ? 4 : 2);
        if ((xer & 0x80000000L) != 0) {
            f |= 1;
        }
        crSetField(crf, f);
    }

    private void cr0(long v) {
        cmpCR(0, sf() ? Long.signum(v) : Integer.signum((int) v));
    }

    private int caIn() {
        return (xer & 0x20000000L) != 0 ? 1 : 0;
    }

    private void setCAf(boolean c) {
        xer = c ? (xer | 0x20000000L) : (xer & ~0x20000000L);
    }

    private void setOVf(boolean ov) {
        xer = ov ? (xer | 0xC0000000L) : (xer & ~0x40000000L);
    }

    /**
     * Máscara con numeración PowerPC de 64 bits (bit 0 = MSB).
     */
    private static long mask64(int mb, int me) {
        long a = -1L >>> mb;
        long b = -1L << (63 - me);
        return mb <= me ? (a & b) : (a | b);
    }

    private static boolean isXOArith(int x9) {
        switch (x9) {
            case 8:
            case 9:
            case 10:
            case 11:
            case 40:
            case 73:
            case 75:
            case 104:
            case 136:
            case 138:
            case 200:
            case 202:
            case 232:
            case 233:
            case 234:
            case 235:
            case 266:
            case 457:
            case 459:
            case 489:
            case 491:
                return true;
            default:
                return false;
        }
    }

    /**
     * rD = a + b + cin (64 bits) con carry/overflow/Rc opcionales.
     */
    private void arith(int rd, long a, long b, int cin, boolean setCA, boolean oe, boolean rc) {
        if (!sf()) { // modo de 32 bits
            long t = (a & M32) + (b & M32) + cin;
            int r32 = (int) t;
            w(rd, r32 & M32);
            if (setCA) {
                setCAf((t >>> 32) != 0);
            }
            if (oe) {
                setOVf(((~((int) a ^ (int) b)) & ((int) a ^ r32)) < 0);
            }
            if (rc) {
                cr0(r32);
            }
            return;
        }
        long res = a + b + cin;
        w(rd, res);
        if (setCA) {
            setCAf(cin == 0 ? Long.compareUnsigned(res, a) < 0 : Long.compareUnsigned(res, a) <= 0);
        }
        if (oe) {
            setOVf(((~(a ^ b)) & (a ^ res)) < 0);
        }
        if (rc) {
            cr0(res);
        }
    }

    private boolean trapCond(int to, long a, long b) {
        return ((to & 16) != 0 && a < b) || ((to & 8) != 0 && a > b) || ((to & 4) != 0 && a == b)
                || ((to & 2) != 0 && Long.compareUnsigned(a, b) < 0)
                || ((to & 1) != 0 && Long.compareUnsigned(a, b) > 0);
    }

    private long ea(int ra, long d) {
        return ra0(ra) + d;
    }

    private long eaX(int ra, int rb) {
        return ra0(ra) + r(rb);
    }

    public long readMemoryDouble(long ea) {
        long value = (readMemoryWord(ea) << 32) | readMemoryWord(ea + 4);
        if (pc == 0x010034F8L || pc == 0x010035D0L || pc == 0x01003470L) {
            System.out.printf("[1BL-GLOBAL] PC=%016X EA=%016X VALUE=%016X%n", pc, ea, value);
        }
        if (pc == 0x01003714L || pc == 0x010036BCL) {
            System.out.printf("[1BL-POINTER] PC=%016X EA=%016X VALUE=%016X%n", pc, ea, value);
        }
        return value;
    }

    public void writeMemoryDouble(long ea, long v) {
        writeMemoryWord(ea, v >>> 32);
        writeMemoryWord(ea + 4, v & M32);
    }

    /**
     * size: 1,2,4,8. sign: extensión de signo (lha/lwa).
     */
    private void doLoad(int size, boolean sign, int rd, long ea) {
        long v;
        switch (size) {
            case 1:
                v = readMemoryByte(ea) & 0xFF;
                break;
            case 2:
                v = readMemoryHalf(ea) & 0xFFFF;
                if (sign) {
                    v = (short) v;
                }
                break;
            case 4:
                v = readMemoryWord(ea);
                if (sign) {
                    v = (int) v;
                }
                break;
            default:
                v = readMemoryDouble(ea);
        }
        w(rd, v);
    }

    private void doStore(int size, long v, long ea) {
        switch (size) {
            case 1:
                writeMemoryByte(ea, (byte) v);
                break;
            case 2:
                writeMemoryHalf(ea, (int) v);
                break;
            case 4:
                writeMemoryWord(ea, v & M32);
                break;
            default:
                writeMemoryDouble(ea, v);
        }
    }

    private int pir = 0;

    public void setPir(int p) {
        pir = p;
    }

    public int getPir() {
        return pir;
    }

    public long getHRMOR() {
        return hrmorValue;
    }

    public long getHID1() {
        return hid1Value;
    }

    /**
     * true entre un lwarx/ldarx y su stwcx./stdcx. (el planificador no debe
     * cambiar de hilo ahí).
     */
    public boolean hasReservation() {
        return reserveAddress != -1;
    }

    private boolean sprTrace = false;
    private final java.util.Set<String> sprSeen = new java.util.HashSet<>();

    private final java.util.Set<Long> watchPcs = new java.util.HashSet<>();

    /**
     * Diagnóstico: imprime registros cada vez que el PC alcanza esta dirección
     * (se compara con los 32 bits bajos).
     */
    public void addWatchPc(long a) {
        watchPcs.add(a & 0xFFFFFFFFL & 0x1FFFFFFFL);
    }

    private void watchDump() {
        System.out.printf("[watch] hilo %d PC=0x%x LR=0x%x CTR=0x%x r1=0x%x r3=0x%x r4=0x%x r5=0x%x r6=0x%x r7=0x%x%n",
                pir, pc, lr, ctr, gpr[1], gpr[3], gpr[4], gpr[5], gpr[6], gpr[7]);
    }

    public void setSprTrace(boolean t) {
        sprTrace = t;
    }

    private void sprLog(String k, int spr, long v) {
        if (sprTrace && sprSeen.add(k + spr + ":" + Long.toHexString(v))) {
            System.out.printf("[spr] %s SPR %d (0x%x) valor=0x%x pc=0x%x%n", k, spr, spr, v, pc);
        }
    }

    private long getSPR(int spr) {
        switch (spr) {
            case 1:
                return xer;
            case 8:
                return lr;
            case 9:
                return ctr;
            case 18:
                return dsisr;
            case 19:
                return dar;
            case 22:
                return dec & M32;
            case 25:
                return SDR1 & M32;
            case 26:
                return srr0;
            case 27:
                return srr1;
            case 0x139:
                return hrmorValue;

            case 0x3F1:
                return hid1Value;

            case 0x3B2:
            case 0x3B3:
            case 0x3B4:
            case 0x3B5:
                return sprOther[spr];
            case 268:
            case 284:
                return timeBase & M32; // TBL: 32 bits inferiores
            case 269:
            case 285:
                return (timeBase >>> 32) & M32; // TBU: 32 bits superiores
            case 272:
                return sprg0 & M32;
            case 273:
                return sprg1 & M32;
            case 274:
                return sprg2 & M32;
            case 275:
                return sprg3 & M32;
            case 287:
                return pvr & M32;
            case 1023:
                return pir; // PIR: índice del hilo hardware
            default:
                sprLog("mfspr", spr, spr >= 0 && spr < sprOther.length ? sprOther[spr] : 0);
                return spr >= 0 && spr < sprOther.length ? sprOther[spr] : 0;
        }
    }

    private void setSPR(int spr, long v) {
        switch (spr) {
            case 1:
                xer = v & M32;
                break;

            case 8:
                lr = v;
                break;

            case 9:
                ctr = v;
                break;

            case 18:
                dsisr = v & M32;
                break;

            case 19:
                dar = v;
                break;

            case 22:
                dec = (int) v;
                decPending = false;
                break;

            case 25:
                SDR1 = (int) v;
                break;

            case 26:
                srr0 = v;
                break;

            case 27:
                srr1 = v;
                break;

            case 272:
                sprg0 = (int) v;
                break;

            case 273:
                sprg1 = (int) v;
                break;

            case 274:
                sprg2 = (int) v;
                break;

            case 275:
                sprg3 = (int) v;
                break;

            case 284:
                timeBase = (timeBase & ~M32) | (v & M32);
                break;

            case 285:
                timeBase = (timeBase & M32) | (v << 32);
                break;

            // HRMOR
            case 0x139:
                hrmorValue = v;
                sprOther[spr] = v;
                sprLog("HRMOR", spr, v);
                System.out.printf("[CB_A-SPR] HRMOR=0x%016X PC=0x%016X%n", v, pc);
                break;

            // HID1
            case 0x3F1:
                hid1Value = v;
                sprOther[spr] = v;
                sprLog("HID1", spr, v);
                System.out.printf("[CB_A-SPR] HID1=0x%016X PC=0x%016X%n", v, pc);
                break;

            // PPE TLB configuration registers
            case 0x3B2:
            case 0x3B3:
            case 0x3B4:
            case 0x3B5:
                sprOther[spr] = v;

                switch (spr) {
                    case 0x3B3:
                        tlbSPR3B3 = v;
                        break;
                    case 0x3B4:
                        tlbSPR3B4 = v;
                        break;
                    case 0x3B5:
                        tlbSPR3B5 = v;
                        break;
                }

                sprLog("PPE-TLB", spr, v);
                System.out.printf("[CB_A-SPR] SPR[%03X]=0x%016X PC=0x%016X%n", spr, v, pc);
                break;

            default:
                sprLog("mtspr", spr, v);

                if (spr >= 0 && spr < sprOther.length) {
                    sprOther[spr] = v;
                }
                break;
        }
    }

    /**
     * kind: 0 = bc, 1 = bclr, 2 = bcctr
     */
    private void branchConditional(int insn, int kind) {
        int bo = (insn >>> 21) & 31, bi = (insn >>> 16) & 31;
        if ((bo & 4) == 0) {
            ctr--;
        }
        boolean ctrZero = sf() ? ctr == 0 : (int) ctr == 0;
        boolean ctrOk = (bo & 4) != 0 || (!ctrZero ^ ((bo & 2) != 0));
        boolean condOk = (bo & 16) != 0 || (crBit(bi) == ((bo & 8) != 0 ? 1 : 0));
        long target;

        if (kind == 0) {
            long d = (short) (insn & 0xFFFC);
            target = (insn & 2) != 0 ? d : pc + d;
        } else if (kind == 1) {
            target = lr & ~3L;
        } else {
            target = ctr & ~3L;
        }
        if ((insn & 1) != 0) {
            lr = addressWidth(pc + 4);
        }
        if (kind == 0 && pc == 0x100359CL) {
            System.out.printf(
                    "[BC-TRACE] PC=%016X BO=%02d BI=%02d CR=%08X CRBIT=%d CTR=%016X ctrOk=%b condOk=%b TARGET=%016X%n",
                    pc, bo, bi, cr, crBit(bi), ctr, ctrOk, condOk, target
            );
        }
        if (insn == 0x43200000 && pc == 0x010044D4L) {
            System.out.printf(
                    "[1BL-LOOP] PC=%016X CTR=%016X TAKEN=%b NEXT=%016X%n",
                    pc, ctr, ctrOk && condOk,
                    ctrOk && condOk ? target : addressWidth(pc + 4)
            );
        }
        if (ctrOk && condOk) {
            nextPc = addressWidth(target);
        }
    }

    /**
     * Entrada unificada para fallos de traducción/almacenamiento. DSISR bit 25
     * identifica una operación de escritura; DAR conserva la EA. La excepción
     * se entrega por la misma ruta que el resto de excepciones PPC.
     */
    public void raiseStorageException(boolean instruction, long effectiveAddress, boolean write) {
        if (instruction) {
            takeException(ExceptionHandler.EXCEPTION_INSTRUCTION_STORAGE, pc, 0x40000000L);
            return;
        }
        dar = effectiveAddress;
        dsisr = write ? 0x02000000L : 0L;
        takeException(ExceptionHandler.EXCEPTION_DATA_STORAGE, pc, 0L);
    }

    /**
     * Entrada de excepción PowerPC. savedPc = valor para SRR0.
     */
    private void takeException(int vector, long savedPc, long extra) {
        srr0 = savedPc;
        srr1 = (msr & ~0xFFFFL) | (msr & 0xFFFFL) | extra;
        msr &= ~(long) (MSR_EE | MSR_PR | MSR_FP | MSR_IR | MSR_DR);
        interruptMode = true;
        nextPc = addressWidth(vector);
        if (bus.readWord(vector) == 0) {
            System.err.println("[CPU] Excepción 0x" + Integer.toHexString(vector) + " en PC=0x"
                    + Long.toHexString(savedPc) + " sin manejador instalado: CPU detenida"
                    + (lastIllegalInsn != 0 ? String.format(" (insn 0x%08x)", lastIllegalInsn) : ""));
            halted = true;
        }
    }

    private void illegal(int insn) {
        System.err.println(String.format("[CPU] Instrucción ilegal/no implementada 0x%08x en PC=0x%x",
                insn, pc));
        lastIllegalInsn = insn & M32;
        takeException(ExceptionHandler.EXCEPTION_PROGRAM, pc, 0x00080000L);
    }

    private static double sd(double v) { // redondeo a precisión simple
        return (double) (float) v;
    }

    public void execute(int insn, DecodingInstr.InstructionFields fields) {
        final int op = insn >>> 26;
        final int rd = (insn >>> 21) & 31, ra = (insn >>> 16) & 31, rb = (insn >>> 11) & 31;
        final long simm = (short) insn;
        final long uimm = insn & 0xFFFFL;
        final boolean rc = (insn & 1) != 0;
        nextPc = addressWidth(pc + 4);
        if (debugMode) {
            System.out.printf("PC=%08x insn=%08x%n", pc, insn);
        }

        if (pc == 0x010044D4L && insn == 0x43200000) {

            long remaining = ctr & 0xFFFFFFFFL;

            if (remaining == 0x765EL
                    || remaining == 0x7000L
                    || remaining == 0x4000L
                    || remaining == 0x1000L
                    || remaining == 1L
                    || remaining == 0L) {

                System.out.printf(
                        "[1BL-DELAY] PC=%016X CTR=%08X%n",
                        pc,
                        remaining
                );
                
            }
        }

        try {
            switch (op) {
                case 2: // tdi
                    if (trapCond(rd, r(ra), simm)) {
                        takeException(ExceptionHandler.EXCEPTION_PROGRAM, pc, 0x00020000L);
                    }
                    break;
                case 3: // twi
                    if (trapCond(rd, (int) r(ra), (int) simm)) {
                        takeException(ExceptionHandler.EXCEPTION_PROGRAM, pc, 0x00020000L);
                    }
                    break;
                case 7: // mulli
                    w(rd, r(ra) * simm);
                    break;
                case 8: // subfic: rD = SI - rA
                    arith(rd, ~r(ra), simm, 1, true, false, false);
                    break;
                case 10: { // cmpli
                    int crf = (insn >>> 23) & 7;
                    long crBefore = cr;

                    int cmp;
                    if ((insn & 0x00200000) != 0 && sf()) {
                        cmp = Long.compareUnsigned(r(ra), uimm);
                    } else {
                        cmp = Integer.compareUnsigned((int) r(ra), (int) uimm);
                    }

                    cmpCR(crf, cmp);

                    if (pc == 0x1003598L) {
                        System.out.printf(
                                "[CMP-TRACE] PC=%016X CRF=%d RA=%016X UIMM=%d CMP=%d CR_BEFORE=%08X CR_AFTER=%08X CR26=%d%n",
                                pc, crf, r(ra), uimm, cmp,
                                crBefore, cr, crBit(26)
                        );
                    }
                    break;
                }
                /*case 10: { // cmpli
                    if (pc == 0x1003598L) {
                        System.out.printf(
                                "[CMP-TRACE] PC=%016X RA=%016X UIMM=%d CR=%08X CR26=%d%n",
                                pc, r(ra), uimm, cr, crBit(26)
                        );
                    }
                    int crf = (insn >>> 23) & 7;
                    if ((insn & 0x00200000) != 0 && sf()) {
                        cmpCR(crf, Long.compareUnsigned(r(ra), uimm));
                    } else {
                        cmpCR(crf, Integer.compareUnsigned((int) r(ra), (int) uimm));
                    }
                    break;
                }*/
                case 11: { // cmpi
                    int crf = (insn >>> 23) & 7;
                    if ((insn & 0x00200000) != 0 && sf()) {
                        cmpCR(crf, Long.compare(r(ra), simm));
                    } else {
                        cmpCR(crf, Integer.compare((int) r(ra), (int) simm));
                    }
                    break;
                }
                case 12: // addic
                case 13: // addic.
                    arith(rd, r(ra), simm, 0, true, false, op == 13);
                    break;
                case 14: // addi / li
                    w(rd, ra0(ra) + simm);
                    break;
                case 15: // addis / lis
                    w(rd, ra0(ra) + (simm << 16));
                    break;
                case 16: // bc
                    branchConditional(insn, 0);
                    break;
                case 17: // sc
                    takeException(ExceptionHandler.EXCEPTION_SYSTEM_CALL, pc + 4, 0);
                    break;
                case 18: { // b
                    long li = ((insn & 0x03FFFFFC) << 6) >> 6;
                    long target = (insn & 2) != 0 ? li : pc + li;
                    if ((insn & 1) != 0) {
                        lr = addressWidth(pc + 4);
                    }
                    nextPc = addressWidth(target);
                    break;
                }
                case 19: {
                    int xo = (insn >>> 1) & 0x3FF;
                    switch (xo) {
                        case 16:
                            branchConditional(insn, 1);
                            break;   // bclr
                        case 528:
                            branchConditional(insn, 2);
                            break;  // bcctr
                        case 50: // rfi
                        case 18: // rfid
                        {
                            int xxo = (insn >>> 1) & 0x3FF;
                            long savedSRR0 = srr0;
                            long savedSRR1 = srr1;

                            System.out.printf("[RETURN-TRACE] PC=%016X XO=%d SRR0=%016X SRR1=%016X MSR_OLD=%016X%n", pc, xxo, savedSRR0, savedSRR1, msr);

                            // Restaurar primero el estado del procesador.
                            msr = savedSRR1;
                            msrValue = msr;

                            interruptMode = false;

                            // Calcular la dirección con el estado restaurado.
                            nextPc = addressWidth(savedSRR0 & ~3L);

                            System.out.printf("[RETURN-RESTORE] PC=%016X NEXT=%016X MSR=%016X SF=%b%n", pc, nextPc, msr, sf());
                            break;
                        }
                        case 150:
                            break; // isync
                        case 0: { // mcrf
                            int src = (int) getCrField((insn >>> 18) & 7);
                            crSetField((insn >>> 23) & 7, src);
                            break;
                        }
                        case 257:
                            crSetBit(rd, crBit(ra) & crBit(rb));
                            break;        // crand
                        case 129:
                            crSetBit(rd, crBit(ra) & ~crBit(rb));
                            break;       // crandc
                        case 289:
                            crSetBit(rd, ~(crBit(ra) ^ crBit(rb)));
                            break;     // creqv
                        case 225:
                            crSetBit(rd, ~(crBit(ra) & crBit(rb)));
                            break;     // crnand
                        case 33:
                            crSetBit(rd, ~(crBit(ra) | crBit(rb)));
                            break;      // crnor
                        case 449:
                            crSetBit(rd, crBit(ra) | crBit(rb));
                            break;        // cror
                        case 417:
                            crSetBit(rd, crBit(ra) | ~crBit(rb));
                            break;       // crorc
                        case 193:
                            crSetBit(rd, crBit(ra) ^ crBit(rb));
                            break;        // crxor
                        default:
                            illegal(insn);
                    }
                    break;
                }
                case 20: { // rlwimi
                    long m = mask64(((insn >>> 6) & 31) + 32, ((insn >>> 1) & 31) + 32);
                    int rot = Integer.rotateLeft((int) r(rd), rb);
                    long rr = (rot & M32) | ((long) rot << 32);
                    long v = (rr & m) | (r(ra) & ~m);
                    w(ra, v);
                    if (rc) {
                        cr0(v);
                    }
                    break;
                }
                case 21: { // rlwinm
                    long m = mask64(((insn >>> 6) & 31) + 32, ((insn >>> 1) & 31) + 32);
                    int rot = Integer.rotateLeft((int) r(rd), rb);
                    long v = ((rot & M32) | ((long) rot << 32)) & m;
                    w(ra, v);
                    if (rc) {
                        cr0(v);
                    }
                    break;
                }
                case 23: { // rlwnm
                    long m = mask64(((insn >>> 6) & 31) + 32, ((insn >>> 1) & 31) + 32);
                    int rot = Integer.rotateLeft((int) r(rd), (int) (r(rb) & 31));
                    long v = ((rot & M32) | ((long) rot << 32)) & m;
                    w(ra, v);
                    if (rc) {
                        cr0(v);
                    }
                    break;
                }
                case 24:
                    w(ra, r(rd) | uimm);
                    break;                  // ori
                case 25:
                    w(ra, r(rd) | (uimm << 16));
                    break;          // oris
                case 26:
                    w(ra, r(rd) ^ uimm);
                    break;                  // xori
                case 27:
                    w(ra, r(rd) ^ (uimm << 16));
                    break;          // xoris
                case 28: {
                    long v = r(rd) & uimm;
                    w(ra, v);
                    cr0(v);
                    break;
                }           // andi.
                case 29: {
                    long v = r(rd) & (uimm << 16);
                    w(ra, v);
                    cr0(v);
                    break;
                }   // andis.
                case 30:
                    execRotate64(insn, rd, ra, rb, rc);
                    break;
                case 31:
                    execOp31(insn, rd, ra, rb, rc);
                    break;
                case 32:
                    doLoad(4, false, rd, ea(ra, simm));
                    break;                    // lwz
                case 33: {
                    long e = ea(ra, simm);
                    doLoad(4, false, rd, e);
                    w(ra, e);
                    break;
                } // lwzu
                case 34:
                    doLoad(1, false, rd, ea(ra, simm));
                    break;                    // lbz
                case 35: {
                    long e = ea(ra, simm);
                    doLoad(1, false, rd, e);
                    w(ra, e);
                    break;
                }
                case 36:
                    doStore(4, r(rd), ea(ra, simm));
                    break;                       // stw
                case 37: {
                    long e = ea(ra, simm);
                    doStore(4, r(rd), e);
                    w(ra, e);
                    break;
                }
                case 38:
                    doStore(1, r(rd), ea(ra, simm));
                    break;                       // stb
                case 39: {
                    long e = ea(ra, simm);
                    doStore(1, r(rd), e);
                    w(ra, e);
                    break;
                }
                case 40:
                    doLoad(2, false, rd, ea(ra, simm));
                    break;                    // lhz
                case 41: {
                    long e = ea(ra, simm);
                    doLoad(2, false, rd, e);
                    w(ra, e);
                    break;
                }
                case 42:
                    doLoad(2, true, rd, ea(ra, simm));
                    break;                     // lha
                case 43: {
                    long e = ea(ra, simm);
                    doLoad(2, true, rd, e);
                    w(ra, e);
                    break;
                }
                case 44:
                    doStore(2, r(rd), ea(ra, simm));
                    break;                       // sth
                case 45: {
                    long e = ea(ra, simm);
                    doStore(2, r(rd), e);
                    w(ra, e);
                    break;
                }
                case 46: { // lmw
                    long e = ea(ra, simm);
                    for (int i = rd; i < 32; i++, e += 4) {
                        doLoad(4, false, i, e);
                    }
                    break;
                }
                case 47: { // stmw
                    long e = ea(ra, simm);
                    for (int i = rd; i < 32; i++, e += 4) {
                        doStore(4, r(i), e);
                    }
                    break;
                }
                case 48:
                    fpr[rd] = (double) Float.intBitsToFloat((int) readMemoryWord(ea(ra, simm)));
                    break; // lfs
                case 49: {
                    long e = ea(ra, simm);
                    fpr[rd] = (double) Float.intBitsToFloat((int) readMemoryWord(e));
                    w(ra, e);
                    break;
                }
                case 50:
                    fpr[rd] = Double.longBitsToDouble(readMemoryDouble(ea(ra, simm)));
                    break;          // lfd
                case 51: {
                    long e = ea(ra, simm);
                    fpr[rd] = Double.longBitsToDouble(readMemoryDouble(e));
                    w(ra, e);
                    break;
                }
                case 52:
                    writeMemoryWord(ea(ra, simm), Float.floatToRawIntBits((float) fpr[rd]) & M32);
                    break; // stfs
                case 53: {
                    long e = ea(ra, simm);
                    writeMemoryWord(e, Float.floatToRawIntBits((float) fpr[rd]) & M32);
                    w(ra, e);
                    break;
                }
                case 54:
                    writeMemoryDouble(ea(ra, simm), Double.doubleToRawLongBits(fpr[rd]));
                    break;       // stfd
                case 55: {
                    long e = ea(ra, simm);
                    writeMemoryDouble(e, Double.doubleToRawLongBits(fpr[rd]));
                    w(ra, e);
                    break;
                }
                case 58: { // ld / ldu / lwa (DS-form)
                    long d = (short) (insn & 0xFFFC);
                    long e = ea(ra, d);
                    switch (insn & 3) {
                        case 0:
                            doLoad(8, false, rd, e);
                            break;
                        case 1:
                            doLoad(8, false, rd, e);
                            w(ra, e);
                            break;
                        case 2:
                            doLoad(4, true, rd, e);
                            break;
                        default:
                            illegal(insn);
                    }
                    break;
                }
                case 62: { // std / stdu
                    long d = (short) (insn & 0xFFFC);
                    long e = ea(ra, d);
                    switch (insn & 3) {
                        case 0:
                            doStore(8, r(rd), e);
                            break;
                        case 1:
                            doStore(8, r(rd), e);
                            w(ra, e);
                            break;
                        default:
                            illegal(insn);
                    }
                    break;
                }
                case 59:
                    execFP(insn, true);
                    break;
                case 63:
                    execFP(insn, false);
                    break;
                case 4: // AltiVec
                    if (!execAltivec(insn)) {
                        illegal(insn);
                    }
                    break;
                case 5:
                case 6: // VMX128 del Xenon: no implementado
                    illegal(insn);
                    break;
                default:
                    illegal(insn);
            }
        } catch (MMU.TranslationFault fault) {
            // La MMU ya entregó DSI/ISI y fijó DAR/DSISR. No ejecutar acceso físico ni
            // convertir el fallo de almacenamiento en una excepción de programa.
        } catch (RuntimeException e) {
            System.err.println("[CPU] Error ejecutando 0x" + Integer.toHexString(insn) + " en PC=0x"
                    + Long.toHexString(pc) + ": " + e);
            lastIllegalInsn = insn & M32;
            takeException(ExceptionHandler.EXCEPTION_PROGRAM, pc, 0x00080000L);
        }
        if (nextPc != addressWidth(pc + 4)) {
            /*System.out.printf(
                    "[PC-CHANGE] OLD=%016X NEXT=%016X INS=%08X OP=%02X LR=%016X CTR=%016X%n",
                    pc, nextPc, insn, op, lr, ctr
            );*/
        }
        pc = addressWidth(nextPc);
        instructionCount++;
    }

    // ------------------------------------------------------------------
    // AltiVec / VMX (opcode 4 y cargas/almacenes del opcode 31).
    // 32 registros de 128 bits; vreg[4*n + k] = palabra k (0 = la más significativa, big-endian).
    // VMX128 (Xenon, opcodes 5/6 y variantes de 128 registros) NO está implementado.
    // ------------------------------------------------------------------
    private final int[] vreg = new int[128];

    public int[] getVectorRegs128() {
        return vreg;
    }

    private byte[] vbytes(int r) {
        byte[] b = new byte[16];
        for (int k = 0; k < 4; k++) {
            int w = vreg[4 * r + k];
            b[4 * k] = (byte) (w >>> 24);
            b[4 * k + 1] = (byte) (w >>> 16);
            b[4 * k + 2] = (byte) (w >>> 8);
            b[4 * k + 3] = (byte) w;
        }
        return b;
    }

    private void vsetBytes(int r, byte[] b) {
        for (int k = 0; k < 4; k++) {
            vreg[4 * r + k] = ((b[4 * k] & 0xFF) << 24) | ((b[4 * k + 1] & 0xFF) << 16)
                    | ((b[4 * k + 2] & 0xFF) << 8) | (b[4 * k + 3] & 0xFF);
        }
    }

    private float vf(int r, int k) {
        return Float.intBitsToFloat(vreg[4 * r + k]);
    }

    private void vsetf(int r, int k, float v) {
        vreg[4 * r + k] = Float.floatToRawIntBits(v);
    }

    /**
     * Cargas/almacenes vectoriales del opcode 31. Devuelve false si no es una
     * de ellas.
     */
    private boolean execVectorMem(int key, int rd, int ra, int rb) {
        long e = eaX(ra, rb);
        switch (key) {
            case 103:
            case 359: { // lvx / lvxl
                long a = e & ~15L;
                byte[] b = new byte[16];
                for (int i = 0; i < 16; i++) {
                    b[i] = readMemoryByte(a + i);
                }
                vsetBytes(rd, b);
                return true;
            }
            case 231:
            case 487: { // stvx / stvxl
                long a = e & ~15L;
                byte[] b = vbytes(rd);
                for (int i = 0; i < 16; i++) {
                    writeMemoryByte(a + i, b[i]);
                }
                return true;
            }
            case 6:
            case 38: { // lvsl / lvsr
                int sh = (int) (e & 15);
                byte[] b = new byte[16];
                for (int i = 0; i < 16; i++) {
                    b[i] = (byte) (key == 6 ? sh + i : 16 - sh + i);
                }
                vsetBytes(rd, b);
                return true;
            }
            case 71: { // lvewx
                int k = (int) ((e & 15) >> 2);
                vreg[4 * rd + k] = (int) readMemoryWord(e & ~3L);
                return true;
            }
            case 199: { // stvewx
                int k = (int) ((e & 15) >> 2);
                writeMemoryWord(e & ~3L, vreg[4 * rd + k] & M32);
                return true;
            }
            case 519:
            case 775: { // lvlx / lvlxl (Xenon): bytes desde EA hasta el final del bloque, a la izquierda
                int n = 16 - (int) (e & 15);
                byte[] b = new byte[16];
                for (int i = 0; i < n; i++) {
                    b[i] = readMemoryByte(e + i);
                }
                vsetBytes(rd, b);
                return true;
            }
            case 551:
            case 807: { // lvrx / lvrxl: bytes anteriores a EA en el bloque, a la derecha
                int n = (int) (e & 15);
                byte[] b = new byte[16];
                for (int i = 0; i < n; i++) {
                    b[16 - n + i] = readMemoryByte((e & ~15L) + i);
                }
                vsetBytes(rd, b);
                return true;
            }
            case 647:
            case 903: { // stvlx / stvlxl
                int n = 16 - (int) (e & 15);
                byte[] b = vbytes(rd);
                for (int i = 0; i < n; i++) {
                    writeMemoryByte(e + i, b[i]);
                }
                return true;
            }
            case 679:
            case 935: { // stvrx / stvrxl
                int n = (int) (e & 15);
                byte[] b = vbytes(rd);
                for (int i = 0; i < n; i++) {
                    writeMemoryByte((e & ~15L) + i, b[16 - n + i]);
                }
                return true;
            }
            case 342:
            case 374:
            case 822:
            case 566 + 1000: // dst/dstt/dss: sin efecto
                return true;
            default:
                return false;
        }
    }

    /**
     * Opcode 4 (AltiVec). Devuelve false si la instrucción no está
     * implementada.
     */
    private boolean execAltivec(int insn) {
        final int vd = (insn >>> 21) & 31, va  = (insn >>> 16) & 31, vb = (insn >>> 11) & 31, vc = (insn >>> 6) & 31;
        final int va6 = insn & 0x3F;
        switch (va6) { // formato VA
            case 43: { // vperm
                byte[] a = vbytes(va), b = vbytes(vb), c = vbytes(vc), o = new byte[16];
                for (int i = 0; i < 16; i++) {
                    int idx = c[i] & 31;
                    o[i] = idx < 16 ? a[idx] : b[idx - 16];
                }
                vsetBytes(vd, o);
                return true;
            }
            case 42: // vsel
                for (int k = 0; k < 4; k++) {
                    vreg[4 * vd + k] = (vreg[4 * va  + k] & ~vreg[4 * vc + k]) | (vreg[4 * vb + k] & vreg[4 * vc + k]);
                }
                return true;
            case 44: { // vsldoi
                byte[] a = vbytes(va), b = vbytes(vb), o = new byte[16];
                int sh = (insn >>> 6) & 15;
                for (int i = 0; i < 16; i++) {
                    int idx = sh + i;
                    o[i] = idx < 16 ? a[idx] : b[idx - 16];
                }
                vsetBytes(vd, o);
                return true;
            }
            case 46: // vmaddfp vD = vA*vC + vB
                for (int k = 0; k < 4; k++) {
                    vsetf(vd, k, Math.fma(vf(va, k), vf(vc, k), vf(vb, k)));
                }
                return true;
            case 47: // vnmsubfp vD = -(vA*vC - vB)
                for (int k = 0; k < 4; k++) {
                    vsetf(vd, k, -Math.fma(vf(va, k), vf(vc, k), -vf(vb, k)));
                }
                return true;
            default:
                break;
        }
        int xo = insn & 0x7FF;
        switch (xo) {
            case 0:
            case 64:
            case 128:
            case 1024:
            case 1088:
            case 1152: { // vaddu?m / vsubu?m
                boolean sub = xo >= 1024;
                int size = (xo & 0x3FF) == 0 ? 1 : ((xo & 0x3FF) == 64 ? 2 : 4);
                byte[] a = vbytes(va), b = vbytes(vb), o = new byte[16];
                for (int i = 0; i < 16; i += size) {
                    long x = 0, y = 0;
                    for (int j = 0; j < size; j++) {
                        x = (x << 8) | (a[i + j] & 0xFF);
                        y = (y << 8) | (b[i + j] & 0xFF);
                    }
                    long r = sub ? x - y : x + y;
                    for (int j = size - 1; j >= 0; j--) {
                        o[i + j] = (byte) r;
                        r >>= 8;
                    }
                }
                vsetBytes(vd, o);
                return true;
            }
            case 1028:
            case 1092:
            case 1156:
            case 1220:
            case 1284: // vand vandc vor vxor vnor
                for (int k = 0; k < 4; k++) {
                    int x = vreg[4 * va  + k], y = vreg[4 * vb + k], r;
                    switch (xo) {
                        case 1028:
                            r = x & y;
                            break;
                        case 1092:
                            r = x & ~y;
                            break;
                        case 1156:
                            r = x | y;
                            break;
                        case 1220:
                            r = x ^ y;
                            break;
                        default:
                            r = ~(x | y);
                    }
                    vreg[4 * vd + k] = r;
                }
                return true;
            case 10:
            case 74: // vaddfp / vsubfp
                for (int k = 0; k < 4; k++) {
                    vsetf(vd, k, xo == 10 ? vf(va, k) + vf(vb, k) : vf(va, k) - vf(vb, k));
                }
                return true;
            case 524:
            case 588:
            case 652: { // vspltb / vsplth / vspltw
                int size = xo == 524 ? 1 : (xo == 588 ? 2 : 4);
                int idx = va  & (16 / size - 1);
                byte[] b = vbytes(vb), o = new byte[16];
                for (int i = 0; i < 16; i++) {
                    o[i] = b[idx * size + (i % size)];
                }
                vsetBytes(vd, o);
                return true;
            }
            case 780:
            case 844:
            case 908: { // vspltisb / vspltish / vspltisw
                int simm = (va  << 27) >> 27;
                int size = xo == 780 ? 1 : (xo == 844 ? 2 : 4);
                byte[] o = new byte[16];
                for (int i = 0; i < 16; i++) {
                    int sh = 8 * (size - 1 - (i % size));
                    o[i] = (byte) (sh >= 32 ? (simm >> 31) : (simm >> sh));
                }
                vsetBytes(vd, o);
                return true;
            }
            case 12:
            case 76:
            case 140:
            case 268:
            case 332:
            case 396: { // vmrgh*/vmrgl*
                int size = (xo & 0xFF) == 12 ? 1 : ((xo & 0xFF) == 76 ? 2 : 4);
                boolean low = xo >= 268;
                byte[] a = vbytes(va), b = vbytes(vb), o = new byte[16];
                int base = low ? 8 : 0;
                for (int i = 0; i < 8 / size; i++) {
                    for (int j = 0; j < size; j++) {
                        o[(2 * i) * size + j] = a[base + i * size + j];
                        o[(2 * i + 1) * size + j] = b[base + i * size + j];
                    }
                }
                vsetBytes(vd, o);
                return true;
            }
            case 388:
            case 644:
            case 900: // vslw / vsrw / vsraw
                for (int k = 0; k < 4; k++) {
                    int sh = vreg[4 * vb + k] & 31, x = vreg[4 * va  + k];
                    vreg[4 * vd + k] = xo == 388 ? x << sh : (xo == 644 ? x >>> sh : x >> sh);
                }
                return true;
            case 1540: // mfvscr
                for (int k = 0; k < 4; k++) {
                    vreg[4 * vd + k] = 0;
                }
                return true;
            case 1604: // mtvscr
                return true;
            default:
                break;
        }
        // vcmpequb/h/w (+ Rc en el bit 10)
        int cx = xo & 0x3FF;
        if (cx == 6 || cx == 70 || cx == 134) {
            int size = cx == 6 ? 1 : (cx == 70 ? 2 : 4);
            byte[] a = vbytes(va), b = vbytes(vb), o = new byte[16];
            boolean all = true, none = true;
            for (int i = 0; i < 16; i += size) {
                boolean eq = true;
                for (int j = 0; j < size; j++) {
                    eq &= a[i + j] == b[i + j];
                }
                for (int j = 0; j < size; j++) {
                    o[i + j] = (byte) (eq ? 0xFF : 0);
                }
                all &= eq;
                none &= !eq;
            }
            vsetBytes(vd, o);
            if ((xo & 0x400) != 0) {
                crSetField(6, (all ? 8 : 0) | (none ? 2 : 0));
            }
            return true;
        }
        return false;
    }

    /**
     * Opcode 30: rotaciones de 64 bits (MD y MDS).
     */
    private void execRotate64(int insn, int rs, int ra, int rb, boolean rc) {
        int sh = rb | ((insn & 2) << 4);
        int mb = ((insn >>> 6) & 0x1F) | (insn & 0x20);
        long src = r(rs);
        long v;
        switch ((insn >>> 2) & 7) {
            case 0:
                v = Long.rotateLeft(src, sh) & mask64(mb, 63);
                break;                    // rldicl
            case 1:
                v = Long.rotateLeft(src, sh) & mask64(0, mb);
                break;                     // rldicr
            case 2:
                v = Long.rotateLeft(src, sh) & mask64(mb, 63 - sh);
                break;               // rldic
            case 3: {                                                                         // rldimi
                long m = mask64(mb, 63 - sh);
                v = (Long.rotateLeft(src, sh) & m) | (r(ra) & ~m);
                break;
            }
            case 4: { // rldcl / rldcr
                int n = (int) (r(rb) & 63);
                if (((insn >>> 1) & 1) == 0) {
                    v = Long.rotateLeft(src, n) & mask64(mb, 63);
                } else {
                    v = Long.rotateLeft(src, n) & mask64(0, mb);
                }
                break;
            }
            default:
                illegal(insn);
                return;
        }
        w(ra, v);
        if (rc) {
            cr0(v);
        }
    }

    private void execOp31(int insn, int rd, int ra, int rb, boolean rc) {
        int xo = (insn >>> 1) & 0x3FF;
        int key = xo;
        boolean oe = false;
        if ((xo & 0x200) != 0 && isXOArith(xo & 0x1FF)) {
            key = xo & 0x1FF;
            oe = true;
        }
        long a = r(ra), b = r(rb), s = r(rd);
        switch (key) {
            case 0: // cmp
                if ((insn & 0x00200000) != 0 && sf()) {
                    cmpCR((insn >>> 23) & 7, Long.compare(a, b));
                } else {
                    cmpCR((insn >>> 23) & 7, Integer.compare((int) a, (int) b));
                }
                break;
            case 32: // cmpl
                if ((insn & 0x00200000) != 0 && sf()) {
                    cmpCR((insn >>> 23) & 7, Long.compareUnsigned(a, b));
                } else {
                    cmpCR((insn >>> 23) & 7, Integer.compareUnsigned((int) a, (int) b));
                }
                break;
            case 4: // tw
                if (trapCond(rd, (int) a, (int) b)) {
                    takeException(ExceptionHandler.EXCEPTION_PROGRAM, pc, 0x00020000L);
                }
                break;
            case 68: // td
                if (trapCond(rd, a, b)) {
                    takeException(ExceptionHandler.EXCEPTION_PROGRAM, pc, 0x00020000L);
                }
                break;
            case 8:
                arith(rd, ~a, b, 1, true, oe, rc);
                break;            // subfc
            case 9: {
                long v = Math.unsignedMultiplyHigh(a, b);
                w(rd, v);
                if (rc) {
                    cr0(v);
                }
                break;
            } // mulhdu
            case 10:
                arith(rd, a, b, 0, true, oe, rc);
                break;            // addc
            case 11: {
                long v = ((a & M32) * (b & M32)) >>> 32;
                w(rd, v);
                if (rc) {
                    cr0(v);
                }
                break;
            } // mulhwu
            case 19: // mfcr / mfocrf
                if ((insn & 0x00100000) != 0) {
                    int fxm = (insn >>> 12) & 0xFF;
                    long v = 0;
                    for (int n = 0; n < 8; n++) {
                        if ((fxm & (0x80 >> n)) != 0) {
                            v |= cr & (0xFL << ((7 - n) * 4));
                        }
                    }
                    w(rd, v);
                } else {
                    w(rd, cr);
                }
                break;
            case 20: {
                long e = eaX(ra, rb);
                doLoad(4, false, rd, e);
                reserveAddress = e & M32;
                break;
            } // lwarx
            case 21:
                doLoad(8, false, rd, eaX(ra, rb));
                break;           // ldx
            case 23:
                doLoad(4, false, rd, eaX(ra, rb));
                break;           // lwzx
            case 24: {
                long v = (b & 63) >= 32 ? 0 : ((s & M32) << (int) (b & 63)) & M32;
                w(ra, v);
                if (rc) {
                    cr0(v);
                }
                break;
            } // slw
            case 26: {
                long v = Integer.numberOfLeadingZeros((int) s);
                w(ra, v);
                if (rc) {
                    cr0(v);
                }
                break;
            } // cntlzw
            case 27: {
                long v = (b & 127) >= 64 ? 0 : s << (int) (b & 63);
                w(ra, v);
                if (rc) {
                    cr0(v);
                }
                break;
            } // sld
            case 28: {
                long v = s & b;
                w(ra, v);
                if (rc) {
                    cr0(v);
                }
                break;
            }       // and
            case 40:
                arith(rd, ~a, b, 1, false, oe, rc);
                break;          // subf
            case 53: {
                long e = eaX(ra, rb);
                doLoad(8, false, rd, e);
                w(ra, e);
                break;
            } // ldux
            case 54:
            case 86:
            case 246:
            case 278:
            case 982:
            case 598:
            case 854:
            case 470:
            case 306:
            case 566:
            case 498:
            case 434:
            case 402:
            case 370:
            case 595:
            case 659:
            case 210:
            case 242:
            case 307:
            case 886:
            case 274:
            case 466:
            case 851:
            case 82:
            case 978:
                break; // dcbst/dcbf/dcbtst/dcbt/icbi/sync/eieio/dcbi/tlbie/tlbsync/slb*/mtsr...: sin efecto
            case 55: {
                long e = eaX(ra, rb);
                doLoad(4, false, rd, e);
                w(ra, e);
                break;
            } // lwzux
            case 58: {
                long v = Long.numberOfLeadingZeros(s);
                w(ra, v);
                if (rc) {
                    cr0(v);
                }
                break;
            } // cntlzd
            case 60: {
                long v = s & ~b;
                w(ra, v);
                if (rc) {
                    cr0(v);
                }
                break;
            }      // andc
            case 73: {
                long v = Math.multiplyHigh(a, b);
                w(rd, v);
                if (rc) {
                    cr0(v);
                }
                break;
            } // mulhd
            case 75: {
                long v = ((long) (int) a * (int) b) >> 32;
                w(rd, (int) v);
                if (rc) {
                    cr0((int) v);
                }
                break;
            } // mulhw
            case 83:
                w(rd, msr);
                break;                                   // mfmsr
            case 84: {
                long e = eaX(ra, rb);
                doLoad(8, false, rd, e);
                reserveAddress = e & M32;
                break;
            } // ldarx
            case 87:
                doLoad(1, false, rd, eaX(ra, rb));
                break;           // lbzx
            case 104:
                arith(rd, ~a, 0, 1, false, oe, rc);
                break;         // neg
            case 119: {
                long e = eaX(ra, rb);
                doLoad(1, false, rd, e);
                w(ra, e);
                break;
            } // lbzux
            case 124: {
                long v = ~(s | b);
                w(ra, v);
                if (rc) {
                    cr0(v);
                }
                break;
            }   // nor
            case 136:
                arith(rd, ~a, b, caIn(), true, oe, rc);
                break;     // subfe
            case 138:
                arith(rd, a, b, caIn(), true, oe, rc);
                break;      // adde
            case 144: { // mtcrf / mtocrf
                int fxm = (insn >>> 12) & 0xFF;
                for (int n = 0; n < 8; n++) {
                    if ((fxm & (0x80 >> n)) != 0) {
                        crSetField(n, (int) ((s >>> ((7 - n) * 4)) & 0xF));
                    }
                }
                break;
            }
            case 146:
                msr = s;
                break;                                     // mtmsr
            case 178:
                msr = s;
                break;                                     // mtmsrd
            case 149:
                doStore(8, s, eaX(ra, rb));
                break;                 // stdx
            case 150: { // stwcx.
                long e = eaX(ra, rb);
                boolean ok = reserveAddress == (e & M32);
                if (ok) {
                    doStore(4, s, e);
                }
                reserveAddress = -1;
                crSetField(0, (ok ? 2 : 0) | ((xer & 0x80000000L) != 0 ? 1 : 0));
                break;
            }
            case 151:
                doStore(4, s, eaX(ra, rb));
                break;                 // stwx
            case 181: {
                long e = eaX(ra, rb);
                doStore(8, s, e);
                w(ra, e);
                break;
            } // stdux
            case 183: {
                long e = eaX(ra, rb);
                doStore(4, s, e);
                w(ra, e);
                break;
            } // stwux
            case 200:
                arith(rd, ~a, 0, caIn(), true, oe, rc);
                break;     // subfze
            case 202:
                arith(rd, a, 0, caIn(), true, oe, rc);
                break;      // addze
            case 214: { // stdcx.
                long e = eaX(ra, rb);
                boolean ok = reserveAddress == (e & M32);
                if (ok) {
                    doStore(8, s, e);
                }
                reserveAddress = -1;
                crSetField(0, (ok ? 2 : 0) | ((xer & 0x80000000L) != 0 ? 1 : 0));
                break;
            }
            case 215:
                doStore(1, s, eaX(ra, rb));
                break;                 // stbx
            case 232:
                arith(rd, ~a, -1, caIn(), true, oe, rc);
                break;    // subfme
            case 233: { // mulld
                long v = a * b;
                w(rd, v);
                if (oe) {
                    setOVf(Math.multiplyHigh(a, b) != (v >> 63));
                }
                if (rc) {
                    cr0(v);
                }
                break;
            }
            case 234:
                arith(rd, a, -1, caIn(), true, oe, rc);
                break;     // addme
            case 235: { // mullw
                long p = (long) (int) a * (int) b;
                w(rd, p);
                if (oe) {
                    setOVf(p != (int) p);
                }
                if (rc) {
                    cr0(p);
                }
                break;
            }
            case 247: {
                long e = eaX(ra, rb);
                doStore(1, s, e);
                w(ra, e);
                break;
            } // stbux
            case 266:
                arith(rd, a, b, 0, false, oe, rc);
                break;          // add
            case 279:
                doLoad(2, false, rd, eaX(ra, rb));
                break;          // lhzx
            case 284: {
                long v = ~(s ^ b);
                w(ra, v);
                if (rc) {
                    cr0(v);
                }
                break;
            }   // eqv
            case 311: {
                long e = eaX(ra, rb);
                doLoad(2, false, rd, e);
                w(ra, e);
                break;
            } // lhzux
            case 316: {
                long v = s ^ b;
                w(ra, v);
                if (rc) {
                    cr0(v);
                }
                break;
            }      // xor
            case 339: // mfspr
            case 371: // mftb
                w(rd, getSPR(((insn >>> 16) & 31) | (((insn >>> 11) & 31) << 5)));
                break;
            case 341:
                doLoad(4, true, rd, eaX(ra, rb));
                break;           // lwax
            case 343:
                doLoad(2, true, rd, eaX(ra, rb));
                break;           // lhax
            case 373: {
                long e = eaX(ra, rb);
                doLoad(4, true, rd, e);
                w(ra, e);
                break;
            } // lwaux
            case 375: {
                long e = eaX(ra, rb);
                doLoad(2, true, rd, e);
                w(ra, e);
                break;
            } // lhaux
            case 407:
                doStore(2, s, eaX(ra, rb));
                break;                 // sthx
            case 412: {
                long v = s | ~b;
                w(ra, v);
                if (rc) {
                    cr0(v);
                }
                break;
            }     // orc
            case 826:
            case 827: { // sradi (xo de 10 bits: 826/827 según el bit sh5)
                int sh = ((insn >>> 11) & 31) | ((insn & 2) << 4);
                long v = s >> sh;
                setCAf(s < 0 && sh != 0 && (s & ((1L << sh) - 1)) != 0);
                w(ra, v);
                if (rc) {
                    cr0(v);
                }
                break;
            }
            case 439: {
                long e = eaX(ra, rb);
                doStore(2, s, e);
                w(ra, e);
                break;
            } // sthux
            case 444: {
                long v = s | b;
                w(ra, v);
                if (rc) {
                    cr0(v);
                }
                break;
            }      // or
            case 457: { // divdu
                long v = b == 0 ? 0 : Long.divideUnsigned(a, b);
                w(rd, v);
                if (oe) {
                    setOVf(b == 0);
                }
                if (rc) {
                    cr0(v);
                }
                break;
            }
            case 459: { // divwu
                long v = (int) b == 0 ? 0 : Integer.divideUnsigned((int) a, (int) b) & M32;
                w(rd, v);
                if (oe) {
                    setOVf((int) b == 0);
                }
                if (rc) {
                    cr0(v);
                }
                break;
            }
            case 467: { // mtspr
                int spr = ((insn >>> 16) & 31) | (((insn >>> 11) & 31) << 5);
                long value = r(rd);
                setSPR(spr, value);
                break;
            }
            case 476: {
                long v = ~(s & b);
                w(ra, v);
                if (rc) {
                    cr0(v);
                }
                break;
            }   // nand
            case 489: { // divd
                boolean bad = b == 0 || (a == Long.MIN_VALUE && b == -1);
                long v = bad ? 0 : a / b;
                w(rd, v);
                if (oe) {
                    setOVf(bad);
                }
                if (rc) {
                    cr0(v);
                }
                break;
            }
            case 491: { // divw
                boolean bad = (int) b == 0 || ((int) a == Integer.MIN_VALUE && (int) b == -1);
                long v = bad ? 0 : (int) a / (int) b;
                w(rd, v);
                if (oe) {
                    setOVf(bad);
                }
                if (rc) {
                    cr0(v);
                }
                break;
            }
            case 534: {
                long v = Integer.reverseBytes((int) readMemoryWord(eaX(ra, rb))) & M32;
                w(rd, v);
                break;
            } // lwbrx
            case 535:
                fpr[rd] = (double) Float.intBitsToFloat((int) readMemoryWord(eaX(ra, rb)));
                break;      // lfsx
            case 536: {
                long v = (b & 63) >= 32 ? 0 : (s & M32) >>> (int) (b & 63);
                w(ra, v);
                if (rc) {
                    cr0(v);
                }
                break;
            } // srw
            case 539: {
                long v = (b & 127) >= 64 ? 0 : s >>> (int) (b & 63);
                w(ra, v);
                if (rc) {
                    cr0(v);
                }
                break;
            } // srd
            case 567: {
                long e = eaX(ra, rb);
                fpr[rd] = (double) Float.intBitsToFloat((int) readMemoryWord(e));
                w(ra, e);
                break;
            } // lfsux
            case 599:
                fpr[rd] = Double.longBitsToDouble(readMemoryDouble(eaX(ra, rb)));
                break;               // lfdx
            case 631: {
                long e = eaX(ra, rb);
                fpr[rd] = Double.longBitsToDouble(readMemoryDouble(e));
                w(ra, e);
                break;
            } // lfdux
            case 662:
                doStore(4, Integer.reverseBytes((int) s) & M32, eaX(ra, rb));
                break;                  // stwbrx
            case 663:
                writeMemoryWord(eaX(ra, rb), Float.floatToRawIntBits((float) fpr[rd]) & M32);
                break;  // stfsx
            case 695: {
                long e = eaX(ra, rb);
                writeMemoryWord(e, Float.floatToRawIntBits((float) fpr[rd]) & M32);
                w(ra, e);
                break;
            } // stfsux
            case 727:
                writeMemoryDouble(eaX(ra, rb), Double.doubleToRawLongBits(fpr[rd]));
                break;            // stfdx
            case 759: {
                long e = eaX(ra, rb);
                writeMemoryDouble(e, Double.doubleToRawLongBits(fpr[rd]));
                w(ra, e);
                break;
            } // stfdux
            case 790: {
                long h = readMemoryHalf(eaX(ra, rb)) & 0xFFFF;
                w(rd, (Integer.reverseBytes((int) h) >>> 16) & 0xFFFF);
                break;
            } // lhbrx
            case 792: { // sraw
                int sh = (int) (b & 63);
                int si = (int) s;
                long v = sh >= 32 ? (si < 0 ? -1L : 0L) : (long) (si >> sh);
                setCAf(si < 0 && (sh >= 32 || (si & ((1 << sh) - 1)) != 0));
                w(ra, v);
                if (rc) {
                    cr0(v);
                }
                break;
            }
            case 794: { // srad
                int sh = (int) (b & 127);
                long v;
                boolean ca;
                if (sh >= 64) {
                    v = s < 0 ? -1L : 0L;
                    ca = s < 0;
                } else {
                    v = s >> sh;
                    ca = s < 0 && sh != 0 && (s & ((1L << sh) - 1)) != 0;
                }
                setCAf(ca);
                w(ra, v);
                if (rc) {
                    cr0(v);
                }
                break;
            }
            case 824: { // srawi
                int sh = (insn >>> 11) & 31;
                int si = (int) s;
                long v = (long) (si >> sh);
                setCAf(si < 0 && sh != 0 && (si & ((1 << sh) - 1)) != 0);
                w(ra, v);
                if (rc) {
                    cr0(v);
                }
                break;
            }
            case 918:
                doStore(2, Integer.reverseBytes((int) s) >>> 16, eaX(ra, rb));
                break;     // sthbrx
            case 922: {
                long v = (short) s;
                w(ra, v);
                if (rc) {
                    cr0(v);
                }
                break;
            }  // extsh
            case 954: {
                long v = (byte) s;
                w(ra, v);
                if (rc) {
                    cr0(v);
                }
                break;
            }   // extsb
            case 986: {
                long v = (int) s;
                w(ra, v);
                if (rc) {
                    cr0(v);
                }
                break;
            }    // extsw
            case 983:
                writeMemoryWord(eaX(ra, rb), Double.doubleToRawLongBits(fpr[rd]) & M32);
                break; // stfiwx
            case 597:
            case 533: { // lswi / lswx: carga de cadena
                long e = key == 597 ? ra0(ra) : eaX(ra, rb);
                int n = key == 597 ? (rb == 0 ? 32 : rb) : (int) (xer & 0x7F);
                int reg = rd;
                int shift = 24;
                long acc = 0;
                for (int i = 0; i < n; i++) {
                    if (i % 4 == 0) {
                        acc = 0;
                        shift = 24;
                    }
                    acc |= (long) (readMemoryByte(e + i) & 0xFF) << shift;
                    shift -= 8;
                    if (i % 4 == 3 || i == n - 1) {
                        w(reg, acc);
                        reg = (reg + 1) & 31;
                    }
                }
                break;
            }
            case 725:
            case 661: { // stswi / stswx: almacena cadena
                long e = key == 725 ? ra0(ra) : eaX(ra, rb);
                int n = key == 725 ? (rb == 0 ? 32 : rb) : (int) (xer & 0x7F);
                int reg = rd;
                for (int i = 0; i < n; i++) {
                    int sh = 24 - 8 * (i % 4);
                    writeMemoryByte(e + i, (byte) (r(reg) >>> sh));
                    if (i % 4 == 3) {
                        reg = (reg + 1) & 31;
                    }
                }
                break;
            }
            case 6:
            case 7:
            case 38:
            case 71:
            case 103:
            case 199:
            case 231:
            case 342:
            case 359:
            case 374:
            case 487:
            case 519:
            case 551:
            case 647:
            case 679:
            case 775:
            case 807:
            case 822:
            case 903:
            case 935:
                if (!execVectorMem(key, rd, ra, rb)) {
                    illegal(insn);
                }
                break;
            case 1014: { // dcbz (32 bytes) / dcbz128 (rd=1, línea de 128 bytes del Xenon)
                int line = rd == 1 ? 128 : 32;
                long e = eaX(ra, rb) & ~(long) (line - 1);
                for (int i = 0; i < line; i += 4) {
                    writeMemoryWord(e + i, 0);
                }
                break;
            }
            default:
                illegal(insn);
        }
    }

    // ------------------------------------------------------------------
    // Punto flotante (opcodes 59 = simple, 63 = doble). FPR = double.
    // ------------------------------------------------------------------
    private void fpCompare(int crf, double a, double b) {
        int f;
        if (Double.isNaN(a) || Double.isNaN(b)) {
            f = 1;
        } else if (a < b) {
            f = 8;
        } else if (a > b) {
            f = 4;
        } else {
            f = 2;
        }
        crSetField(crf, f);
        fpscr = (fpscr & ~0x0000F000L) | ((long) f << 12);
    }

    private static final int FPSCR_RN_MASK = 3;

    private double roundByMode(double v) {
        switch ((int) (fpscr & FPSCR_RN_MASK)) {
            case 1:
                return v < 0 ? Math.ceil(v) : Math.floor(v);   // hacia cero
            case 2:
                return Math.ceil(v);
            case 3:
                return Math.floor(v);
            default:
                return Math.rint(v);
        }
    }

    private void execFP(int insn, boolean single) {
        final int frd = (insn >>> 21) & 31, fra = (insn >>> 16) & 31, frb = (insn >>> 11) & 31, frc = (insn >>> 6) & 31;
        final boolean rc = (insn & 1) != 0;
        final int xo5 = (insn >>> 1) & 0x1F;
        double a = fpr[fra], b = fpr[frb], c = fpr[frc], res;
        switch (xo5) {
            case 18:
                res = a / b;
                break;                                  // fdiv(s)
            case 20:
                res = a - b;
                break;                                  // fsub(s)
            case 21:
                res = a + b;
                break;                                  // fadd(s)
            case 22:
                res = Math.sqrt(b);
                break;                           // fsqrt(s)
            case 23:                                                       // fsel
                if (!single) {
                    fpr[frd] = (a >= 0.0 || a == -0.0) && !Double.isNaN(a) ? c : b;
                    if (rc) {
                        crSetField(1, (int) ((fpscr >>> 28) & 0xF));
                    }
                    return;
                }
                illegal(insn);
                return;
            case 24:
                res = 1.0 / b;
                break;                                // fres
            case 25:
                res = a * c;
                break;                                  // fmul(s)
            case 26:
                res = 1.0 / Math.sqrt(b);
                break;                     // frsqrte
            case 28:
                res = Math.fma(a, c, -b);
                break;                     // fmsub(s)
            case 29:
                res = Math.fma(a, c, b);
                break;                      // fmadd(s)
            case 30:
                res = -Math.fma(a, c, -b);
                break;                    // fnmsub(s)
            case 31:
                res = -Math.fma(a, c, b);
                break;                     // fnmadd(s)
            default:
                if (single) {
                    if (((insn >>> 1) & 0x3FF) == 846) { // fcfids
                        fpr[frd] = sd((double) Double.doubleToRawLongBits(b));
                        return;
                    }
                    illegal(insn);
                    return;
                }
                execFPX(insn, frd, fra, frb, rc);
                return;
        }
        fpr[frd] = single ? sd(res) : res;
        if (rc) {
            crSetField(1, (int) ((fpscr >>> 28) & 0xF));
        }
    }

    private void execFPX(int insn, int frd, int fra, int frb, boolean rc) {
        int xo = (insn >>> 1) & 0x3FF;
        double b = fpr[frb];
        switch (xo) {
            case 0:
                fpCompare((insn >>> 23) & 7, fpr[fra], b);
                break;     // fcmpu
            case 32:
                fpCompare((insn >>> 23) & 7, fpr[fra], b);
                break;    // fcmpo
            case 12:
                fpr[frd] = sd(b);
                break;                              // frsp
            case 14:
            case 15: {                                            // fctiw / fctiwz
                double v = xo == 15 ? (b < 0 ? Math.ceil(b) : Math.floor(b)) : roundByMode(b);
                int iv = Double.isNaN(v) ? Integer.MIN_VALUE : (v >= 2147483647.0 ? Integer.MAX_VALUE
                        : (v <= -2147483648.0 ? Integer.MIN_VALUE : (int) v));
                fpr[frd] = Double.longBitsToDouble(0xFFF8000000000000L | (iv & M32));
                break;
            }
            case 814:
            case 815: {                                          // fctid / fctidz
                double v = xo == 815 ? (b < 0 ? Math.ceil(b) : Math.floor(b)) : roundByMode(b);
                long lv = Double.isNaN(v) ? Long.MIN_VALUE : (long) v;
                fpr[frd] = Double.longBitsToDouble(lv);
                break;
            }
            case 846:
                fpr[frd] = (double) Double.doubleToRawLongBits(b);
                break; // fcfid
            case 40:
                fpr[frd] = -b;
                break;                                 // fneg
            case 72:
                fpr[frd] = b;
                break;                                  // fmr
            case 136:
                fpr[frd] = -Math.abs(b);
                break;                      // fnabs
            case 264:
                fpr[frd] = Math.abs(b);
                break;                       // fabs
            case 583:
                fpr[frd] = Double.longBitsToDouble(fpscr & M32);
                break; // mffs
            case 711: {                                                    // mtfsf
                int fm = (insn >>> 17) & 0xFF;
                long v = Double.doubleToRawLongBits(b) & M32;
                for (int i = 0; i < 8; i++) {
                    if ((fm & (0x80 >> i)) != 0) {
                        long m = 0xFL << ((7 - i) * 4);
                        fpscr = (fpscr & ~m) | (v & m);
                    }
                }
                break;
            }
            case 134: {                                                    // mtfsfi
                int i = (insn >>> 23) & 7;
                long v = (long) ((insn >>> 12) & 0xF) << ((7 - i) * 4);
                fpscr = (fpscr & ~(0xFL << ((7 - i) * 4))) | v;
                break;
            }
            case 38:
                fpscr |= 1L << (31 - ((insn >>> 21) & 31));
                break;    // mtfsb1
            case 70:
                fpscr &= ~(1L << (31 - ((insn >>> 21) & 31)));
                break; // mtfsb0
            case 64:
                break;                                                // mcrfs
            default:
                illegal(insn);
        }
        if (rc) {
            crSetField(1, (int) ((fpscr >>> 28) & 0xF));
        }
    }

    // ELFv1 (PPC64): una dirección dentro de .opd es un descriptor {entrada, TOC, entorno}.
    // Algunos firmwares (p. ej. LK) hacen blr/bctr directamente a un descriptor; se resuelve aquí.
    private long opdStart = 0, opdEnd = 0;
    private boolean opdNoted = false;

    public long getOpdStart() {
        return opdStart;
    }

    public long getOpdEnd() {
        return opdEnd;
    }

    public void setOpdRange(long start, long end) {
        opdStart = start;
        opdEnd = end;
    }

    /**
     * Ejecuta una instrucción completa (fetch + execute + tick +
     * interrupciones).
     */
    public void step() {
        if (opdEnd > opdStart && pc >= opdStart && pc < opdEnd) {
            long target = readMemoryDouble(pc);
            long toc = readMemoryDouble(pc + 8);
            if (!opdNoted) {
                opdNoted = true;
                System.out.printf("[CPU] salto a descriptor .opd 0x%x -> 0x%x (TOC 0x%x)%n", pc, target, toc);
            }
            gpr[2] = toc;
            pc = addressWidth(target);
        }
        if (!watchPcs.isEmpty() && watchPcs.contains(pc & 0x1FFFFFFFL)) {
            watchDump();
        }
        int insn;
        try {
            insn = fetchInstruction();
        } catch (MMU.TranslationFault fault) {
            // ISI ya fue entregada por la MMU; no decodificar una instrucción sustituta.
            pc = addressWidth(nextPc);
            tick();
            checkInterrupts();
            return;
        }
        recordExecution(pc, insn);
        if (haltOnSelfBranch && insn == 0x48000000) {
            System.out.println("[CPU] Halt: 'b .' en PC=0x" + Long.toHexString(pc));
            halted = true;
            return;
        }
        execute(insn, null);
        tick();
        checkInterrupts();
    }

    /**
     * Ejecuta hasta maxInstr instrucciones o hasta que la CPU se detenga.
     */
    public long run(long maxInstr) {
        long n = 0;
        while (!halted && n < maxInstr) {
            step();
            n++;
        }
        return n;
    }

    public void run() {
        run(Long.MAX_VALUE);
    }

    public void handleInterrupt(int vector) {
        takeException(vector, pc, 0);
        pc = addressWidth(nextPc);
    }

    public void setMSRBit(int bit, boolean value) {
        if (value) {
            msr |= bit;
        } else {
            msr &= ~(long) bit;
        }
    }

    public boolean getMSRBit(int bit) {
        return (msr & bit) != 0;
    }

    public void returnFromInterrupt() {
        pc = addressWidth(srr0); // Restaurar PC
        msr = srr1 & M32; // Restaurar MSR
        interruptMode = false;
    }

    public long getTimeBase() {
        return timeBase;
    }

    public void setTimeBase(long value) {
        this.timeBase = value;
    }

    private long tbIncrement = 1;

    /**
     * Ticks del time base por instrucción ejecutada (acelera los retardos
     * basados en mftb).
     */
    public void setTbIncrement(long n) {
        tbIncrement = Math.max(1, n);
    }

    public void tick() {
        timeBase += tbIncrement;
        TBU = (int) (timeBase >>> 32);
        TBL = (int) timeBase;

        // DEC es un contador descendente de 32 bits. La excepción se genera
        // cuando el contador cruza de no-negativo a negativo (underflow), no
        // cuando alcanza cero. La aritmética debe conservar el wrap de 32 bits.
        int previous = dec;
        dec -= (int) tbIncrement;
        if (previous >= 0 && dec < 0) {
            decPending = true;
        }
    }

    public long getFPSCR() {
        return fpscr;
    }

    public void setFPSCR(int value) {
        this.fpscr = value;
    }

    public void setFPSCRBit(int bit) {
        fpscr |= bit;
    }

    public long getVSCR() {
        return vscr;
    }

    public void setVSCR(long value) {
        this.vscr = value;
    }

    public long getGPRVal(int pos) {
        return gpr[pos];
    }

    public void setGPR(int pos, long val) {
        gpr[pos] = val;
    }

    public long getCr() {
        return cr;
    }

    public void setCr(long cr) {
        this.cr = cr & M32;
    }

    public long getLr() {
        return lr;
    }

    public void setLr(long lr) {
        this.lr = lr;
    }

    public int getCtr() {
        return (int) ctr;
    }

    public void setCtr(long ctr) {
        this.ctr = ctr;
    }

    public long getXer() {
        return xer;
    }

    public void setXer(long xer) {
        this.xer = xer & M32;
    }

    public long getPc() {
        return pc;
    }

    public void setPc(long pc) {
        this.pc = addressWidth(pc);
        if (debugMode) {
            System.out.println("Set PC: 0x" + Long.toHexString(this.pc));
        }
    }

    public boolean isInterruptMode() {
        return interruptMode;
    }

    public void setInterruptMode(boolean interruptMode) {
        this.interruptMode = interruptMode;
    }

    public double getFPRVal(int pos) {
        return fpr[pos];
    }

    public void setFPR(int pos, double fpr) {
        this.fpr[pos] = fpr;
    }

    public int[] getVRVal(int pos) {
        return vr[pos];
    }

    public void setVR(int pos, int[] vr) {
        this.vr[pos] = vr;
    }

    public int[] getSr() {
        return sr;
    }

    public void setSr(int[] sr) {
        this.sr = sr;
    }

    // Métodos para operaciones atómicas
    public long getReserveAddress() {
        return reserveAddress;
    }

    public void setReserveAddress(long address) {
        reserveAddress = address;
    }

    public void clearReserve() {
        reserveAddress = -1;
    }

    public long getMsr() {
        return msr;
    }

    public void setMsr(long msr) {
        this.msr = msr;
    }

    public long getSrr0() {
        return srr0;
    }

    public void setSrr0(long srr0) {
        this.srr0 = srr0;
    }

    public long getSrr1() {
        return srr1;
    }

    public void setSrr1(long srr1) {
        this.srr1 = srr1;
    }

    public int getSprg0() {
        return sprg0;
    }

    public void setSprg0(int sprg0) {
        this.sprg0 = sprg0;
    }

    public int getSprg1() {
        return sprg1;
    }

    public void setSprg1(int sprg1) {
        this.sprg1 = sprg1;
    }

    public int getSprg2() {
        return sprg2;
    }

    public void setSprg2(int sprg2) {
        this.sprg2 = sprg2;
    }

    public int getSprg3() {
        return sprg3;
    }

    public void setSprg3(int sprg3) {
        this.sprg3 = sprg3;
    }

    public long getDar() {
        return dar;
    }

    public void setDar(long dar) {
        this.dar = dar;
    }

    public long getDsisr() {
        return dsisr;
    }

    public void setDsisr(int dsisr) {
        this.dsisr = dsisr;
    }

    public int getPvr() {
        return pvr;
    }

    public void setPvr(int pvr) {
        this.pvr = pvr;
    }

    public int getDec() {
        return dec;
    }

    public void setDec(int dec) {
        // El registro contiene un patrón de 32 bits sin signo; conservar
        // también valores con el bit 31 activo es necesario para modelar DEC.
        this.dec = dec;
        this.decPending = false;
    }

    public boolean isInterrupt() {
        return interruptMode;
    }

    public void setInterrupt(boolean interrupt) {
        this.interruptMode = interrupt;
    }

    // Método para obtener un campo específico del CR
    public long getCrField(long crf) {
        long shift = (7 - crf) * 4;
        return (cr >> shift) & 0xF;
    }

    // Manejo de excepciones
    public ExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }

    // Método para establecer un campo específico del CR
    public void setCrField(int crf, long value) {
        int shift = (7 - crf) * 4;
        int mask = 0xF << shift;
        cr = (cr & ~mask) | ((value & 0xF) << shift);
    }

    /**
     * Actualiza un campo del CR (0-7)
     *
     * @param crf Campo del CR a actualizar (0-7)
     * @param value Puede ser: - Un valor entero para calcular los flags
     * automáticamente - Un valor de 4 bits directo (para operaciones de punto
     * flotante)
     * @param direct Si true, usa value como valor directo de 4 bits Si false,
     * calcula los flags a partir del valor entero
     */
    public void updateCRField(long crf, long value, boolean direct) {
        long crfValue;

        if (direct) {
            // Valor directo de 4 bits
            crfValue = value & 0xF;
        } else {
            // Calcular flags a partir de valor entero
            crfValue = 0;
            if (value < 0) {
                crfValue |= 0x8; // LT
            } else if (value > 0) {
                crfValue |= 0x4; // GT
            } else {
                crfValue |= 0x2; // EQ
            }
            // Copia bit SO desde XER
            crfValue |= (xer & 0x80000000L) != 0 ? 0x1 : 0;
        }

        long shift = (7 - crf) * 4;
        int mask = 0xF << shift;
        cr = (cr & ~mask) | (crfValue << shift);
    }

    // Versión sobrecargada para mantener compatibilidad con código existente
    public void updateCRField(long crf, long value) {
        // Por defecto, asumimos que es un valor entero (no directo)
        updateCRField(crf, value, false);
    }

    // Establece bit Carry en XER
    public void setCA(boolean carry) {
        if (carry) {
            xer |= 0x20000000L;
        } else {
            xer &= ~0x20000000L;
        }
    }

    // Establece bits Overflow y Summary Overflow
    public void setOV(boolean overflow) {
        if (overflow) {
            xer |= 0xC0000000L;
        } else {
            xer &= ~0x40000000L; // SO permanece activo hasta reset
        }
    }

    // ---- Acceso a memoria: dirección efectiva -> MMU -> bus (sin pasar por la caché) ----
    // La MMU (BAT/páginas) está desactivada por defecto: los firmwares del Xenon (XeLL, LK) corren
    // en modo real/hipervisor y sólo activan IR/DR de forma nominal.
    private boolean mmuEnabled = false;

    public void setMmuEnabled(boolean e) {
        mmuEnabled = e;
    }

    private long xlateData(long address, boolean write) {
        if (mmuEnabled) {
            return mmu.translateAddress(address, write, false);
        }

        if (!sf()) {
            return translateRealAddress(address & M32);
        }

        return address;
    }

    public int fetchInstruction() {
        long physicalAddress;

        if (mmuEnabled) {
            physicalAddress = mmu.translateAddress(pc, false, true);
        } else if (!sf()) {
            physicalAddress = translateRealAddress(pc & M32);
        } else {
            physicalAddress = pc;
        }

        return bus.readWord(physicalAddress);
    }

    public byte readMemoryByte(long address) {
        return bus.read(xlateData(address, false));
    }

    public void writeMemoryByte(long address, byte value) {
        bus.write(xlateData(address, true), value);
    }

    public int readMemoryHalf(long address) {
        return bus.readHalf(xlateData(address, false));
    }

    public void writeMemoryHalf(long address, int value) {
        bus.writeHalf(xlateData(address, true), value);
    }

    public long readMemoryWord(long address) {
        long physical = xlateData(address, false);
        long value = bus.readWord(physical) & M32;

        if (isBootMmioRange(physical)) {
            System.out.printf("[CPU-MMIO] READ32 PC=%016X EA=%016X PA=%016X VALUE=%08X%n", pc, address, physical, value);
        }

        return value;
    }

    public void writeMemoryWord(long address, long value) {
        long physical = xlateData(address, true);

        if (isBootMmioRange(physical)) {
            System.out.printf(
                    "[CPU-MMIO] WRITE32 PC=%016X EA=%016X PA=%016X VALUE=%08X%n",
                    pc, address, physical, value & M32
            );
        }

        bus.writeWord(physical, value);
    }

    private boolean isBootMmioRange(long address) {
        return Long.compareUnsigned(address, 0x8000020000030000L) >= 0
                && Long.compareUnsigned(address, 0x8000020000070000L) <= 0;
    }

    public void checkInterrupts() {
        if (decPending && (msr & MSR_EE) != 0) {
            decPending = false;
            handleInterrupt(ExceptionHandler.EXCEPTION_DECREMENTER);
        }
    }

    public void setMachine(int machine) {
        this.machine = machine;
    }

    public int getMachine() {
        return machine;
    }

    public boolean isBigEndian() {
        // PowerPC es big-endian por defecto
        return true;
    }

    public boolean is64Bit() {
        return machine == ELFFile.EM_PPC64;
    }

    public int getSDR1() {
        return SDR1;
    }

    public void setSDR1(int SDR1) {
        this.SDR1 = SDR1;
    }

    public int getMQ() {
        return MQ;
    }

    public void setMQ(int MQ) {
        this.MQ = MQ;
    }

    public int getRTCU() {
        return RTCU;
    }

    public void setRTCU(int RTCU) {
        this.RTCU = RTCU;
    }

    public int getRTCL() {
        return RTCL;
    }

    public void setRTCL(int RTCL) {
        this.RTCL = RTCL;
    }

    public int getEAR() {
        return EAR;
    }

    public void setEAR(int EAR) {
        this.EAR = EAR;
    }

    public int getTBL() {
        return TBL;
    }

    public void setTBL(int TBL) {
        this.TBL = TBL;
    }

    public int getTBU() {
        return TBU;
    }

    public void setTBU(int TBU) {
        this.TBU = TBU;
    }

    public int getIBAT0U() {
        return IBAT0U;
    }

    public void setIBAT0U(int IBAT0U) {
        this.IBAT0U = IBAT0U;
    }

    public int getIBAT0L() {
        return IBAT0L;
    }

    public void setIBAT0L(int IBAT0L) {
        this.IBAT0L = IBAT0L;
    }

    public int getIBAT1U() {
        return IBAT1U;
    }

    public void setIBAT1U(int IBAT1U) {
        this.IBAT1U = IBAT1U;
    }

    public int getIBAT1L() {
        return IBAT1L;
    }

    public void setIBAT1L(int IBAT1L) {
        this.IBAT1L = IBAT1L;
    }

    public int getIBAT2U() {
        return IBAT2U;
    }

    public void setIBAT2U(int IBAT2U) {
        this.IBAT2U = IBAT2U;
    }

    public int getIBAT2L() {
        return IBAT2L;
    }

    public void setIBAT2L(int IBAT2L) {
        this.IBAT2L = IBAT2L;
    }

    public int getIBAT3U() {
        return IBAT3U;
    }

    public void setIBAT3U(int IBAT3U) {
        this.IBAT3U = IBAT3U;
    }

    public int getIBAT3L() {
        return IBAT3L;
    }

    public void setIBAT3L(int IBAT3L) {
        this.IBAT3L = IBAT3L;
    }

    public int getDBAT0U() {
        return DBAT0U;
    }

    public void setDBAT0U(int DBAT0U) {
        this.DBAT0U = DBAT0U;
    }

    public int getDBAT0L() {
        return DBAT0L;
    }

    public void setDBAT0L(int DBAT0L) {
        this.DBAT0L = DBAT0L;
    }

    public int getDBAT1U() {
        return DBAT1U;
    }

    public void setDBAT1U(int DBAT1U) {
        this.DBAT1U = DBAT1U;
    }

    public int getDBAT1L() {
        return DBAT1L;
    }

    public void setDBAT1L(int DBAT1L) {
        this.DBAT1L = DBAT1L;
    }

    public int getDBAT2U() {
        return DBAT2U;
    }

    public void setDBAT2U(int DBAT2U) {
        this.DBAT2U = DBAT2U;
    }

    public int getDBAT2L() {
        return DBAT2L;
    }

    public void setDBAT2L(int DBAT2L) {
        this.DBAT2L = DBAT2L;
    }

    public int getDBAT3U() {
        return DBAT3U;
    }

    public void setDBAT3U(int DBAT3U) {
        this.DBAT3U = DBAT3U;
    }

    public int getDBAT3L() {
        return DBAT3L;
    }

    public void setDBAT3L(int DBAT3L) {
        this.DBAT3L = DBAT3L;
    }

    public MMU getMmu() {
        return mmu;
    }

    public Logical getLogical() {
        return logical;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("CPU{");
        sb.append("debugMode=").append(debugMode);
        sb.append(", pc=").append(pc);
        sb.append(", gpr=").append(gpr);
        sb.append(", cr=").append(cr);
        sb.append(", lr=").append(lr);
        sb.append(", ctr=").append(ctr);
        sb.append(", xer=").append(xer);
        sb.append(", msr=").append(msr);
        sb.append(", srr0=").append(srr0);
        sb.append(", srr1=").append(srr1);
        sb.append(", sprg0=").append(sprg0);
        sb.append(", sprg1=").append(sprg1);
        sb.append(", sprg2=").append(sprg2);
        sb.append(", sprg3=").append(sprg3);
        sb.append(", interruptMode=").append(interruptMode);
        sb.append(", fpr=").append(fpr);
        sb.append(", vr=").append(vr);
        sb.append(", sr=").append(sr);
        sb.append(", reserveAddress=").append(reserveAddress);
        sb.append(", integerArithmetic=").append(integerArithmetic);
        sb.append(", logical=").append(logical);
        sb.append(", branchs=").append(branchs);
        sb.append(", memoryOps=").append(memoryOps);
        sb.append(", specialInstr=").append(specialInstr);
        sb.append(", fp=").append(fp);
        sb.append(", vector=").append(vector);
        sb.append(", efp=").append(efp);
        sb.append(", spe=").append(spe);
        sb.append(", gfx=").append(gfx);
        sb.append(", exceptionHandler=").append(exceptionHandler);
        sb.append(", mmu=").append(mmu);
        sb.append(", cache=").append(cache);
        sb.append(", memory=").append(bus);
        sb.append('}');
        return sb.toString();
    }

    public void resetRegisters() {
        // Resetear todos los registros
        debugMode = false;
        pc = 0;
        gpr = new long[32];
        cr = 0;
        lr = 0;
        ctr = 0;
        xer = 0;
        msr = 0;
        srr0 = 0;
        srr1 = 0;
        sprg0 = 0;
        sprg1 = 0;
        sprg2 = 0;
        sprg3 = 0;
        hrmorValue = 0;
        hid1Value = 0;

        tlbSPR3B3 = 0;
        tlbSPR3B4 = 0;
        tlbSPR3B5 = 0;
        dar = 0;
        dsisr = 0;
        lastIllegalInsn = 0;
        nextPc = 0;
        msrValue = 0;

        java.util.Arrays.fill(sprOther, 0);
        sprSeen.clear();
        interruptMode = false;
        fpr = new double[32];
        vr = new int[32][4];
        sr = new int[16];
        reserveAddress = -1;
        halted = false;
        decPending = false;
        timeBase = 0;
        dec = 0;
        instructionCount = 0;
        synchronized (this) { execTraceWrite = 0; execTraceSize = 0; }
    }

    public void clearFPSCRBit(int crbD) {
        this.fpscr = crbD;
    }

    public void setAcc(long packWords) {
        this.acc = packWords;
    }

    public long getAcc() {
        return this.acc;
    }

    public long getPpeTlbSPR(int spr) {
        if (spr < 0x3B2 || spr > 0x3B5) {
            throw new IllegalArgumentException("SPR PPE TLB fuera de rango: " + spr);
        }

        return sprOther[spr];
    }

    private long translateRealAddress(long effectiveAddress) {
        if (!sf() && effectiveAddress >= 0
                && effectiveAddress <= 0xFFFFFFFFL) {

            return getHRMOR() + effectiveAddress;
        }

        return effectiveAddress;
    }

    public long getMsrValue() {
        return msrValue;
    }

    public long getMSR() {
        return msr;
    }

    public long getSRR0() {
        return srr0;
    }

    public long getSRR1() {
        return srr1;
    }

    public long getTLBSPR3B3() {
        return tlbSPR3B3;
    }

    public long getTLBSPR3B4() {
        return tlbSPR3B4;
    }

    public long getTLBSPR3B5() {
        return tlbSPR3B5;
    }

}
