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
    private int ctr;  // Count Register
    private long xer;  // Fixed-Point Exception Register (SO, OV, CA)
    private long msr;      // Machine State Register
    private long srr0;     // Save/Restore Register 0
    private long srr1;     // Save/Restore Register 1
    private int sprg0;    // Special Purpose Register General 0
    private int sprg1;    // Special Purpose Register General 1
    private int sprg2;    // Special Purpose Register General 2
    private int sprg3;    // Special Purpose Register General 3
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
    public static final int XER_SO = 1 << 0; // Summary Overflow
    public static final int XER_OV = 1 << 1; // Overflow
    public static final int XER_CA = 1 << 2; // Carry
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

    // Método intérprete principal
    public void execute(int instruction, DecodingInstr.InstructionFields fields) {
        long oldPC = pc;
        try {

            if (instruction != 0) {
                System.out.println("Executing: PC=0x" + Long.toHexString(pc) + ", instruction=0x" + Integer.toHexString(instruction));
            }
            switch (fields.opcode()) {
                case 0: // nop or reserved
                    if (instruction != 0x60000000) {
                        throw new UnsupportedOperationException("Invalid instruction for opcode 0: " + Integer.toHexString(instruction));
                    }
                    break;
                case 1: // tdi (trap doubleword immediate, 64-bit)
                    specialInstr.tdi(fields.rt(), fields.ra(), fields.si()); // TO = rt, SI = si
                    break;
                case 2: // tw
                    specialInstr.tw(fields.rt(), fields.ra(), fields.ra(), instruction); // TO = rt, RA = ra
                    break;
                case 3: // twi
                    specialInstr.twi(fields.rt(), fields.ra(), fields.si()); // TO = rt, SI = si
                    break;
                case 4:
                    if (fields.xo() >= 512) { // SPE instructions
                        spe.execute(fields);
                    } else { // Paired-single instructions
                        gfx.execute(fields);
                    }
                    break;
                case 5:
                case 6:
                case 62:
                    vector.execute(fields);
                    break;
                case 7: // mulli
                    gpr[fields.rt()] = gpr[fields.ra()] * fields.si();
                    break;
                case 8: // subfic
                    gpr[fields.rt()] = fields.si() - gpr[fields.ra()];
                    // Update carry bit in XER if needed
                    break;
                case 9: // icbt (Xenon-specific)
                    // Implement instruction cache block touch (likely a no-op in emulation)
                    break;
                case 10: // cmpli
                    branchs.cmpli(fields);
                    break;
                case 11: // cmpi
                    branchs.cmpi(fields);
                    break;
                case 12: // addic
                    long result12 = (long) gpr[fields.ra()] + fields.si();
                    gpr[fields.rt()] = (int) result12;
                    // Update carry bit in XER
                    break;
                case 13: // addic.
                    long result13 = (long) gpr[fields.ra()] + fields.si();
                    gpr[fields.rt()] = (int) result13;
                    // Update CR and carry bit in XER
                    break;
                case 14: // addi / li
                    // LI es addi fields.rt(),0,IMM  (fields.ra()==0).  El inm. es signo‑extendido (16 bits).
                    gpr[fields.rt()] = (fields.ra() == 0 ? 0 : gpr[fields.ra()]) + fields.si();

                    /*if (isArithmeticExtended(opcode)) {
                        integerArithmetic.executeExtended(extendedOpcode, instruction);
                    } else {
                        integerArithmetic.executePrimary(opcode, instruction);
                    }*/
                    break;
                case 15: // addis / lis
                    gpr[fields.rt()] = (fields.ra() == 0 ? 0 : gpr[fields.ra()]) + (fields.si() << 16);
                    break;
                case 16: // b / bl / ba / bla
                    pc = (fields.aa() == 0 ? pc : 0) + fields.li();
                    if (fields.lk() == 1) {
                        gpr[14] = (int) (oldPC + 4); // Link register
                    }
                    break;
                case 17: // sc
                    branchs.sc(fields);
                    break;
                case 18: // bc / bcl
                    long target = branchs.bc(fields);
                    if (target != 0) {
                        pc = target;
                    }
                    break;
                case 19: // CR operations, branches, special
                    branchs.executeExtended(fields);
                    break;

                case 20: // rlwimi
                case 21: // rlwinm
                case 22: // rlmi (Xenon-specific)
                case 23: // rlwnm
                    rotations.executePrimary(fields);
                    break;
                case 24: // ori
                case 25: // oris
                case 26: // xori
                case 27: // xoris
                case 28: // andi.
                case 29: // andis.
                    logical.executePrimary(fields);
                    break;
                case 30: // 64-bit rotations (rldicl, rldicr, rldimi, etc.)
                    rotations.executeExtended(fields);
                    break;
                case 31: // Arithmetic, logical, memory, special
                    switch (fields.xo()) {
                        case 0: // cmp
                            branchs.cmp(fields);
                            break;
                        case 4: // tw
                            branchs.tw(fields);
                            break;
                        case 8: // subfc
                            gpr[fields.rt()] = gpr[fields.rb()] - gpr[fields.ra()];
                            // Update carry bit in XER
                            break;
                        case 10: // addc
                            long result10 = (long) gpr[fields.ra()] + gpr[fields.rb()];
                            gpr[fields.rt()] = (int) result10;
                            // Update carry bit in XER
                            break;
                        case 11: // mulhwu
                            long mul11 = (long) gpr[fields.ra()] * gpr[fields.rb()];
                            gpr[fields.rt()] = (int) (mul11 >>> 32);
                            break;
                        case 19: // mfcr
                            gpr[fields.rt()] = cr;
                            break;
                        case 20: // lwarx
                        case 23: // lwzx
                        case 55: // lwzux
                        case 84: // ldarx
                        case 86: // dcbf
                        case 54: // dcbst
                        case 119: // lbzx
                        case 150: // stwcx.
                        case 151: // stwx
                        case 183: // stwux
                        case 214: // stdcx.
                        case 215: // stbx
                        case 246: // dcbtst
                        case 278: // dcbt
                        case 375: // lhaux
                        case 438: // ecowx
                        case 439: // sthux
                        case 533: // lswx
                        case 598: // sync
                        case 661: // stswx
                        case 660: // stbux
                        case 694: // stwbrx
                        case 790: // lhbrx
                        case 918: // sthbrx
                        case 982: // icbi
                        case 1014: // dcbz
                            memoryOps.executeExtended(fields);
                            break;
                        case 24: // slw
                            gpr[fields.ra()] = gpr[fields.rt()] << (gpr[fields.rb()] & 0x1F);
                            break;
                        case 26: // cntlzw
                        case 28: // and
                        case 60: // andc
                        case 124: // nor
                        case 284: // eqv
                        case 316: // xor
                        case 412: // orc
                        case 444: // or
                        case 476: // nand
                        case 536: // srw
                        case 792: // slw
                        case 824: // sraw
                        case 826: // srawi
                        case 922: // extsh
                        case 954: // extsb
                        case 986: // extsw
                            logical.executeExtended(fields);
                            break;
                        case 27: // sld
                            gpr[fields.ra()] = gpr[fields.rt()] << (gpr[fields.rb()] & 0x3F); // 64-bit
                            break;
                        case 83: // mfmsr
                            gpr[fields.rt()] = (int) (msr & 0xFFFFFFFFL);
                            break;
                        case 146: // mtmsr
                            msr = gpr[fields.rt()] & 0xFFFFFFFFL;
                            break;
                        // Add more opcode 31 cases as needed (e.g., add, subf, divw, etc.)
                        default:
                            throw new UnsupportedOperationException("Unknown opcode 31, xo: " + fields.xo());
                    }
                    break;
                case 32: // lbz
                case 33: // lbzu
                case 34: // lhz
                case 35: // lhzu
                case 36: // lwz
                case 37: // lwzu
                case 38: // lha
                case 40: // stb
                case 41: // stbu
                case 42: // sth
                case 43: // sthu
                case 44: // stw
                case 45: // stwu
                case 46: // lmw
                case 47: // stmw
                    memoryOps.executePrimary(fields);
                    break;

                case 39: // stbu
                    long addr39 = (fields.ra() == 0 ? 0 : gpr[fields.ra()]) + fields.si();
                    writeMemoryByte(addr39, (byte) (gpr[fields.rt()] & 0xFF));
                    if (fields.ra() != 0) {
                        gpr[fields.ra()] = (int) addr39;
                    }
                    break;

                case 48: // lfs
                case 49: // lfsu
                case 50: // lfd
                case 51: // lfdu
                case 52: // stfs
                case 53: // stfsu
                case 54: // stfd
                case 55: // stfdu
                case 56: // lfdp
                case 57: // lfdpx
                case 60: // stfdp
                case 61: // stfdpx
                    fp.executePrimary(fields);
                    break;
                case 59: // Single-precision extended
                case 63: // Double-precision and other extended
                    fp.executeExtended(fields);
                    break;
                case 58: // ld, ldu, ldarx
                    switch (fields.xo()) {
                        case 0: // ld
                            gpr[fields.rt()] = readMemoryWord((fields.ra() == 0 ? 0 : gpr[fields.ra()]) + fields.si());
                            break;
                        case 1: // ldu
                            long addr58 = (fields.ra() == 0 ? 0 : gpr[fields.ra()]) + fields.si();
                            gpr[fields.rt()] = readMemoryWord(addr58);
                            if (fields.ra() != 0) {
                                gpr[fields.ra()] = (int) addr58;
                            }
                            break;
                        case 2: // ldarx
                            memoryOps.ldarx(fields.rt(), fields.ra(), fields.rb());
                            break;
                        default:
                            throw new UnsupportedOperationException("Unknown opcode 58, xo: " + fields.xo());
                    }
                    break;
                default:
                    throw new UnsupportedOperationException("Unknown opcode: " + fields.opcode());
            }
        } catch (UnsupportedOperationException e) {
            System.err.println("[CPU] Program exception at PC=0x" + Long.toHexString(oldPC) + ", Opcode: " + fields.opcode() + " : " + e.getMessage());
            handleInterrupt(0x700); // Program Interrupt
        }

        if (pc == oldPC) {
            pc += 4;
        }
    }

    public void handleInterrupt(int vector) {
        if (interruptMode) {
            // Avoid nested interrupts to prevent loops
            System.err.println("[CPU] Nested interrupt detected at PC=0x" + Long.toHexString(pc) + ", vector=0x" + Integer.toHexString(vector));
            return;
        }

        srr0 = pc; // Save PC
        srr1 = msr; // Save MSR
        setMSRBit(MSR_EE, false); // Disable external interrupts
        setMSRBit(MSR_IR, false); // Disable instruction address translation
        setMSRBit(MSR_DR, false); // Disable data address translation
        interruptMode = true;
        pc = vector; // Jump to interrupt vector
    }

    public void setMSRBit(int bit, boolean value) {
        if (value) {
            msr |= bit;
        } else {
            msr &= ~bit;
        }
    }

    public boolean getMSRBit(int bit) {
        return (msr & bit) != 0;
    }

    public void returnFromInterrupt() {
        pc = srr0; // Restaurar PC
        msr = srr1; // Restaurar MSR
        interruptMode = false;
    }

    public long getTimeBase() {
        return timeBase;
    }

    public void setTimeBase(long value) {
        this.timeBase = value;
    }

    public void tick() {
        timeBase++; // Incrementar Time Base (64 bits)
        TBU = (int) (timeBase >>> 32); // Parte alta
        TBL = (int) (timeBase & 0xFFFFFFFF); // Parte baja
        if (dec > 0) {
            dec--;
            if (dec == 0 && getMSRBit(MSR_EE)) {
                handleInterrupt(0x900); // Interrupción de Decrementer
            }
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
        this.cr = cr;
    }

    public long getLr() {
        return lr;
    }

    public void setLr(long lr) {
        this.lr = lr;
    }

    public int getCtr() {
        return ctr;
    }

    public void setCtr(long ctr) {
        this.ctr = (int) ctr;
    }

    public long getXer() {
        return xer;
    }

    public void setXer(long xer) {
        this.xer = xer;
    }

    public long getPc() {
        return pc;
    }

    public void setPc(long pc) {
        this.pc = pc & 0xFFFFFFFFL;
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
        this.dec = dec;
        if (this.dec < 0) {
            this.dec = 0;
        }

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
            crfValue |= (xer & XER_SO) != 0 ? 0x1 : 0;
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
            xer |= XER_CA;
        } else {
            xer &= ~XER_CA;
        }
    }

    // Establece bits Overflow y Summary Overflow
    public void setOV(boolean overflow) {
        if (overflow) {
            xer |= (XER_OV | XER_SO);
        } else {
            xer &= ~XER_OV; // SO permanece activo hasta reset
        }
    }

    // Obtiene las instrucciones
    public int fetchInstruction() {
        int instruction = 0;
        for (int i = 0; i < 4; i++) {
            byte b = bus.read(pc + i);
            instruction = (instruction << 8) | (b & 0xFF);
        }
        return instruction;
    }

    // Métodos de acceso a memoria modificados
    public byte readMemoryByte(long address) {
        return bus.read(address);
    }

    public void writeMemoryByte(long address, byte value) {
        bus.write(address, value);
    }

    public long readMemoryWord(long address) {
        long physAddress = mmu.translateAddress(address, false, false);
        return cache.readWord(physAddress) & 0xFFFFFFFF;
    }

    public void writeMemoryWord(long address, long value) {
        long physAddress = mmu.translateAddress(address, true, false);
        cache.writeWord(physAddress, value);
    }

    public void run() {
        int maxCycles = Integer.MAX_VALUE;
        int cycle = 0;
        while (cycle++ < maxCycles) {
            tick();
            int instruction = fetchInstruction();
            long currentPc = getPc();
            if (currentPc == 0x700 || currentPc == 0x704) {
                System.out.println("Interrupt handler at PC=0x" + Long.toHexString(currentPc) + ", instruction=0x" + Integer.toHexString(instruction));
            }
            DecodingInstr.InstructionFields fields = DecodingInstr.decode(instruction);
            System.out.println(DecodingInstr.decodeForLog(instruction));
            //DecodingInstr.decodeVerbose(instruction, currentPc); // Optional for debug
            try {
                execute(instruction, fields);
            } catch (Exception e) {
                System.err.println("Error at PC=0x" + Long.toHexString(currentPc) + ": " + e.getMessage());
                break;
            }
            checkInterrupts();
        }
        if (cycle >= maxCycles) {
            System.err.println("Emulation halted: Max cycles reached");
        }
    }

    public void checkInterrupts() {
        if (getMSRBit(MSR_EE) && !interruptPending) { // Solo si interrupciones externas están habilitadas
            if (dec == 0) {
                interruptPending = true;
                handleInterrupt(ExceptionHandler.EXCEPTION_SYSTEM_CALL); //0x900 Decrementer Interrupt
            }
            if (interruptPending) {
                handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT); //0x500 External Interrupt
            }
            // añadir otras aquí
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
        interruptMode = false;
        fpr = new double[32];
        vr = new int[32][4];
        sr = new int[16];
        reserveAddress = -1;
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

}
