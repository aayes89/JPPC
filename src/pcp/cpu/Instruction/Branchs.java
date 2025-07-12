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
package pcp.cpu.Instruction;

import pcp.cpu.CPU;
import pcp.system.ExceptionHandler;
import pcp.utils.DecodingInstr;

/**
 * Handles PowerPC branch and condition register instructions for Xenon CPU
 * emulation.
 *
 * @author Slam
 */
public class Branchs {

    private final CPU cpu;

    public Branchs(CPU cpu) {
        this.cpu = cpu;
    }

    /**
     * Evaluates branch condition based on BO and BI fields.
     *
     * @param BO Branch option field (bits 6–10).
     * @param BI Condition register bit index (bits 11–15).
     * @return True if the branch condition is met, false otherwise.
     */
    private boolean evaluateBranchCondition(int BO, int BI) {
        boolean ctrOk = true;
        boolean condOk = true;
        if ((BO & 0x10) == 0) {
            int ctr = cpu.getCtr();
            cpu.setCtr(ctr - 1);
            ctrOk = (BO & 0x08) != 0 ? (ctr != 0) : (ctr == 0);
        }
        if ((BO & 0x04) == 0) {
            boolean conditionBit = ((cpu.getCr() >> (31 - BI)) & 1) == 1;
            condOk = (BO & 0x02) != 0 ? conditionBit : !conditionBit;
        }
        boolean result = ctrOk && condOk;
        System.out.println("evaluateBranchCondition: BO=0x" + Integer.toHexString(BO) + ", BI=" + BI + ", Result=" + result);
        return result;
    }

    /**
     * Branch conditional (opcode 18: bc, bcl).
     *
     * @param fields Instruction fields containing BO, BI, BD, AA, LK.
     * @return Target address if branch is taken, 0 otherwise.
     */
    public long bc(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("B")) {
            throw new IllegalArgumentException("Invalid format for bc: " + fields.format());
        }
        int BO = fields.rt(); // BO
        int BI = fields.ra(); // BI
        long target = (fields.aa() == 0 ? cpu.getPc() : 0) + fields.bd();
        if (fields.lk() == 1) {
            cpu.setLr(cpu.getPc() + 4);
        }
        if (evaluateBranchCondition(BO, BI)) {
            System.out.println("bc: Branch to 0x" + Long.toHexString(target) + " from PC=0x" + Long.toHexString(cpu.getPc()));
            return target;
        }
        return 0;
    }

    /**
     * Branch conditional to link register (opcode 19, xo 16: bclr).
     *
     * @param fields Instruction fields containing BO, BI, LK.
     * @return Target address (LR) if branch is taken, 0 otherwise.
     */
    public long bclr(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("XL")) {
            throw new IllegalArgumentException("Invalid format for bclr: " + fields.format());
        }
        int BO = fields.rt(); // BO
        int BI = fields.ra(); // BI
        if (fields.lk() == 1) {
            cpu.setLr(cpu.getPc() + 4);
        }
        if (evaluateBranchCondition(BO, BI)) {
            long target = cpu.getLr() & 0xFFFFFFFC;
            System.out.println("bclr: Branch to LR=0x" + Long.toHexString(target) + " from PC=0x" + Long.toHexString(cpu.getPc()));
            return target;
        }
        return 0;
    }

    /**
     * Branch conditional to count register (opcode 19, xo 528: bcctr, bctr).
     *
     * @param fields Instruction fields containing BO, BI, LK.
     * @return Target address (CTR) if branch is taken, 0 otherwise.
     */
    public long bcctr(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("XL")) {
            throw new IllegalArgumentException("Invalid format for bcctr: " + fields.format());
        }
        int BO = fields.rt(); // BO
        int BI = fields.ra(); // BI
        if (fields.lk() == 1) {
            cpu.setLr(cpu.getPc() + 4);
        }
        if (evaluateBranchCondition(BO, BI)) {
            long target = cpu.getCtr() & 0xFFFFFFFC;
            System.out.println("bcctr: Branch to CTR=0x" + Long.toHexString(target) + " from PC=0x" + Long.toHexString(cpu.getPc()));
            return target;
        }
        return 0;
    }

    /**
     * Unconditional branch (opcode 16: b, bl, ba, bla).
     *
     * @param fields Instruction fields containing LI, AA, LK.
     * @return Target address.
     */
    public long b(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("I")) {
            throw new IllegalArgumentException("Invalid format for b: " + fields.format());
        }
        long target = (fields.aa() == 0 ? cpu.getPc() : 0) + fields.li();
        if (fields.lk() == 1) {
            cpu.setLr(cpu.getPc() + 4);
        }
        System.out.println("b: Branch to 0x" + Long.toHexString(target) + " from PC=0x" + Long.toHexString(cpu.getPc()));
        return target;
    }

    /**
     * Branch to link register (opcode 19, xo 16: blr).
     *
     * @return Target address (LR).
     */
    public long blr() {
        long target = cpu.getLr() & 0xFFFFFFFC;
        System.out.println("blr: Return to LR=0x" + Long.toHexString(target) + " from PC=0x" + Long.toHexString(cpu.getPc()));
        return target;
    }

    /**
     * Branch to count register (opcode 19, xo 528: bctr).
     *
     * @return Target address (CTR).
     */
    public long bctr() {
        long target = cpu.getCtr() & 0xFFFFFFFC;
        System.out.println("bctr: Branch to CTR=0x" + Long.toHexString(target) + " from PC=0x" + Long.toHexString(cpu.getPc()));
        return target;
    }

    /**
     * System call (opcode 17: sc).
     */
    public void sc(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("SC")) {
            throw new IllegalArgumentException("Invalid format for sc: " + fields.format());
        }
        System.out.println("sc: System call at PC=0x" + Long.toHexString(cpu.getPc()));
        cpu.setSrr0(cpu.getPc() + 4);
        cpu.setSrr1(cpu.getMsr());
        cpu.setMsr(cpu.getMsr() & ~(CPU.MSR_IR | CPU.MSR_DR | CPU.MSR_PR)); // Disable translation, enter supervisor mode
        cpu.setPc(0x00000C00);
        cpu.setInterruptMode(true);
    }

    /**
     * Move condition register field (opcode 19, xo 0: mcrf).
     *
     * @param fields Instruction fields containing BF, BFA.
     */
    public void mcrf(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("XL")) {
            throw new IllegalArgumentException("Invalid format for mcrf: " + fields.format());
        }
        int bf = fields.rt(); // BF
        int bfa = fields.ra(); // BFA
        cpu.getLogical().copyField(fields);
        System.out.println("mcrf: Move CR field BF=" + bf + " to BFA=" + bfa);
    }

    /**
     * CR logical NOR (opcode 19, xo 33: crnor).
     *
     * @param fields Instruction fields containing CRBD, CRBA, CRBB.
     */
    public void crnor(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("XL")) {
            throw new IllegalArgumentException("Invalid format for crnor: " + fields.format());
        }
        cpu.getLogical().nor(fields); // CRBD = rt, CRBA = ra, CRBB = rb
        System.out.println("crnor: CRBD=" + fields.rt() + ", CRBA=" + fields.ra() + ", CRBB=" + fields.rb());
    }

    /**
     * CR logical AND with complement (opcode 19, xo 129: crandc).
     *
     * @param fields Instruction fields containing CRBD, CRBA, CRBB.
     */
    public void crandc(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("XL")) {
            throw new IllegalArgumentException("Invalid format for crandc: " + fields.format());
        }
        cpu.getLogical().andc(fields);
        System.out.println("crandc: CRBD=" + fields.rt() + ", CRBA=" + fields.ra() + ", CRBB=" + fields.rb());
    }

    /**
     * Instruction synchronize (opcode 19, xo 150: isync).
     */
    public void isync() {
        System.out.println("isync: Instruction synchronize at PC=0x" + Long.toHexString(cpu.getPc()));
        // No-op in emulation, ensures instruction fetch synchronization
    }

    /**
     * CR logical XOR (opcode 19, xo 193: crxor).
     *
     * @param fields Instruction fields containing CRBD, CRBA, CRBB.
     */
    public void crxor(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("XL")) {
            throw new IllegalArgumentException("Invalid format for crxor: " + fields.format());
        }
        cpu.getLogical().xor(fields);
        System.out.println("crxor: CRBD=" + fields.rt() + ", CRBA=" + fields.ra() + ", CRBB=" + fields.rb());
    }

    /**
     * CR logical NAND (opcode 19, xo 225: crnand).
     *
     * @param fields Instruction fields containing CRBD, CRBA, CRBB.
     */
    public void crnand(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("XL")) {
            throw new IllegalArgumentException("Invalid format for crnand: " + fields.format());
        }
        cpu.getLogical().nand(fields);
        System.out.println("crnand: CRBD=" + fields.rt() + ", CRBA=" + fields.ra() + ", CRBB=" + fields.rb());
    }

    /**
     * CR logical AND (opcode 19, xo 257: crand).
     *
     * @param fields Instruction fields containing CRBD, CRBA, CRBB.
     */
    public void crand(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("XL")) {
            throw new IllegalArgumentException("Invalid format for crand: " + fields.format());
        }
        cpu.getLogical().and(fields);
        System.out.println("crand: CRBD=" + fields.rt() + ", CRBA=" + fields.ra() + ", CRBB=" + fields.rb());
    }

    /**
     * CR logical equivalent (opcode 19, xo 289: creqv).
     *
     * @param fields Instruction fields containing CRBD, CRBA, CRBB.
     */
    public void creqv(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("XL")) {
            throw new IllegalArgumentException("Invalid format for creqv: " + fields.format());
        }
        cpu.getLogical().eqv(fields);
        System.out.println("creqv: CRBD=" + fields.rt() + ", CRBA=" + fields.ra() + ", CRBB=" + fields.rb());
    }

    /**
     * CR logical OR with complement (opcode 19, xo 417: crorc).
     *
     * @param fields Instruction fields containing CRBD, CRBA, CRBB.
     */
    public void crorc(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("XL")) {
            throw new IllegalArgumentException("Invalid format for crorc: " + fields.format());
        }
        cpu.getLogical().orc(fields);
        System.out.println("crorc: CRBD=" + fields.rt() + ", CRBA=" + fields.ra() + ", CRBB=" + fields.rb());
    }

    /**
     * CR logical OR (opcode 19, xo 449: cror).
     *
     * @param fields Instruction fields containing CRBD, CRBA, CRBB.
     */
    public void cror(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("XL")) {
            throw new IllegalArgumentException("Invalid format for cror: " + fields.format());
        }
        cpu.getLogical().or(fields);
        System.out.println("cror: CRBD=" + fields.rt() + ", CRBA=" + fields.ra() + ", CRBB=" + fields.rb());
    }

    /**
     * Branch conditional to link register with link (opcode 19, xo 528: bclrl).
     *
     * @param fields Instruction fields containing BO, BI.
     * @return Target address (LR) if branch is taken, 0 otherwise.
     */
    public long bclrl(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("XL")) {
            throw new IllegalArgumentException("Invalid format for bclrl: " + fields.format());
        }
        int BO = fields.rt(); // BO
        int BI = fields.ra(); // BI
        cpu.setLr(cpu.getPc() + 4);
        if (evaluateBranchCondition(BO, BI)) {
            long target = cpu.getLr() & 0xFFFFFFFC;
            System.out.println("bclrl: Branch to LR=0x" + Long.toHexString(target) + " from PC=0x" + Long.toHexString(cpu.getPc()));
            return target;
        }
        return 0;
    }

    /**
     * Trap word immediate (opcode 3: twi).
     *
     * @param fields Instruction fields containing TO, RA, SI.
     */
    public void twi(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("D")) {
            throw new IllegalArgumentException("Invalid format for twi: " + fields.format());
        }
        int TO = fields.rt();
        int RA = fields.ra();
        int SI = fields.si();
        long raValue = cpu.getGPRVal(RA);
        boolean trap = false;
        switch (TO) {
            case 4:
                trap = (raValue < SI);
                break;
            case 8:
                trap = (raValue > SI);
                break;
            case 12:
                trap = (raValue < SI) || (raValue > SI);
                break;
            case 16:
                trap = (raValue == SI);
                break;
            case 20:
                trap = (raValue >= SI);
                break;
            case 24:
                trap = (raValue <= SI);
                break;
            case 28:
                trap = (raValue != SI);
                break;
            default:
                break;
        }
        if (trap) {
            System.err.println("Error: Trap condition met (twi) at PC=0x" + Long.toHexString(cpu.getPc()));
            cpu.getExceptionHandler().handleException(ExceptionHandler.EXCEPTION_TWI, cpu.getPc(), 0);
        }
    }

    /**
     * Trap word (opcode 31, xo 4: tw).
     *
     * @param fields Instruction fields containing TO, RA, RB.
     */
    public void tw(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("X")) {
            throw new IllegalArgumentException("Invalid format for tw: " + fields.format());
        }
        int TO = fields.rt();
        int RA = fields.ra();
        int RB = fields.rb();
        long raValue = cpu.getGPRVal(RA);
        long rbValue = cpu.getGPRVal(RB);
        boolean trap = false;
        switch (TO) {
            case 4:
                trap = (raValue < rbValue);
                break;
            case 8:
                trap = (raValue > rbValue);
                break;
            case 12:
                trap = (raValue < rbValue) || (raValue > rbValue);
                break;
            case 16:
                trap = (raValue == rbValue);
                break;
            case 20:
                trap = (raValue >= rbValue);
                break;
            case 24:
                trap = (raValue <= rbValue);
                break;
            case 28:
                trap = (raValue != rbValue);
                break;
            default:
                break;
        }
        if (trap) {
            System.err.println("Error: Trap condition met (tw) at PC=0x" + Long.toHexString(cpu.getPc()));
            cpu.getExceptionHandler().handleException(ExceptionHandler.EXCEPTION_TWI, cpu.getPc(), 0);
        }
    }

    /**
     * Compare immediate (opcode 11: cmpi).
     *
     * @param fields Instruction fields containing BF, RA, SI.
     */
    public void cmpi(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("D")) {
            throw new IllegalArgumentException("Invalid format for cmpi: " + fields.format());
        }
        int crf = fields.rt(); // BF
        int RA = fields.ra();
        int SI = fields.si();
        long a = cpu.getGPRVal(RA);
        long b = SI;
        int crfValue = 0;
        if (a < b) {
            crfValue |= 0x8; // LT
        } else if (a > b) {
            crfValue |= 0x4; // GT
        } else {
            crfValue |= 0x2; // EQ
        }
        crfValue |= (cpu.getXer() & cpu.XER_SO) != 0 ? 0x1 : 0;
        cpu.updateCRField(crf, crfValue);
        System.out.println("cmpi: CRF=" + crf + ", RA=0x" + Long.toHexString(a) + ", SI=0x" + Integer.toHexString(SI) + ", CR=0x" + Long.toHexString(cpu.getCr()));
    }

    /**
     * Compare logical immediate (opcode 10: cmpli).
     *
     * @param fields Instruction fields containing BF, RA, UI.
     */
    public void cmpli(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("D")) {
            throw new IllegalArgumentException("Invalid format for cmpli: " + fields.format());
        }
        int crf = fields.rt(); // BF
        int RA = fields.ra();
        int UI = fields.si() & 0xFFFF; // Unsigned immediate
        long a = cpu.getGPRVal(RA) & 0xFFFFFFFFL;
        long b = UI & 0xFFFFFFFFL;
        int crfValue = 0;
        if (a < b) {
            crfValue |= 0x8; // LT
        } else if (a > b) {
            crfValue |= 0x4; // GT
        } else {
            crfValue |= 0x2; // EQ
        }
        crfValue |= (cpu.getXer() & cpu.XER_SO) != 0 ? 0x1 : 0;
        cpu.updateCRField(crf, crfValue);
        System.out.println("cmpli: CRF=" + crf + ", RA=0x" + Long.toHexString(a) + ", UI=0x" + Integer.toHexString(UI) + ", CR=0x" + Long.toHexString(cpu.getCr()));
    }

    /**
     * Compare (opcode 31, xo 0: cmp).
     *
     * @param fields Instruction fields containing BF, RA, RB.
     */
    public void cmp(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("X")) {
            throw new IllegalArgumentException("Invalid format for cmp: " + fields.format());
        }
        int crf = fields.rt(); // BF
        int RA = fields.ra();
        int RB = fields.rb();
        long a = cpu.getGPRVal(RA);
        long b = cpu.getGPRVal(RB);
        int crfValue = 0;
        if (a < b) {
            crfValue |= 0x8; // LT
        } else if (a > b) {
            crfValue |= 0x4; // GT
        } else {
            crfValue |= 0x2; // EQ
        }
        crfValue |= (cpu.getXer() & cpu.XER_SO) != 0 ? 0x1 : 0;
        cpu.updateCRField(crf, crfValue);
        System.out.println("cmp: CRF=" + crf + ", RA(r" + RA + ")=0x" + Long.toHexString(a) + ", RB(r" + RB + ")=0x" + Long.toHexString(b) + ", CR=0x" + Long.toHexString(cpu.getCr()));
    }

    /**
     * Compare logical (opcode 31, xo 32: cmpl).
     *
     * @param fields Instruction fields containing BF, RA, RB.
     */
    public void cmpl(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("X")) {
            throw new IllegalArgumentException("Invalid format for cmpl: " + fields.format());
        }
        int crf = fields.rt(); // BF
        int RA = fields.ra();
        int RB = fields.rb();
        long a = cpu.getGPRVal(RA) & 0xFFFFFFFFL;
        long b = cpu.getGPRVal(RB) & 0xFFFFFFFFL;
        int crfValue = 0;
        if (a < b) {
            crfValue |= 0x8; // LT
        } else if (a > b) {
            crfValue |= 0x4; // GT
        } else {
            crfValue |= 0x2; // EQ
        }
        crfValue |= (cpu.getXer() & cpu.XER_SO) != 0 ? 0x1 : 0;
        cpu.updateCRField(crf, crfValue);
        System.out.println("cmpl: CRF=" + crf + ", RA(r" + RA + ")=0x" + Long.toHexString(a) + ", RB(r" + RB + ")=0x" + Long.toHexString(b) + ", CR=0x" + Long.toHexString(cpu.getCr()));
    }

    /**
     * Executes extended branch and CR instructions (opcode 19).
     *
     * @param fields Instruction fields.
     */
    public void executeExtended(DecodingInstr.InstructionFields fields) {
        switch (fields.xo()) {
            case 0: // mcrf
                mcrf(fields);
                break;
            case 16: // bclr
                long targetBclr = bclr(fields);
                if (targetBclr != 0) {
                    cpu.setPc(targetBclr);
                }
                break;
            case 18: // bcctr
                long targetBcctr = bcctr(fields);
                if (targetBcctr != 0) {
                    cpu.setPc(targetBcctr);
                }
                break;
            case 33: // crnor
                crnor(fields);
                break;
            case 129: // crandc
                crandc(fields);
                break;
            case 150: // isync
                isync();
                break;
            case 193: // crxor
                crxor(fields);
                break;
            case 225: // crnand
                crnand(fields);
                break;
            case 257: // crand
                crand(fields);
                break;
            case 289: // creqv
                creqv(fields);
                break;
            case 417: // crorc
                crorc(fields);
                break;
            case 449: // cror
                cror(fields);
                break;
            case 528: // bclrl
                long targetBclrl = bclrl(fields);
                if (targetBclrl != 0) {
                    cpu.setPc(targetBclrl);
                }
                break;
            default:
                throw new UnsupportedOperationException("Unsupported branch extended opcode: " + fields.xo() + " at PC=0x" + Long.toHexString(cpu.getPc()));
        }
    }
}
