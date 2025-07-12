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
import pcp.utils.DecodingInstr;

/**
 *
 * @author Slam
 */
public class Logical {

    CPU cpu;

    public Logical(CPU cpuulator) {
        this.cpu = cpuulator;
    }

    public void executePrimary(DecodingInstr.InstructionFields fields) {
        switch (fields.opcode()) {
            case 20: // rlwinm
                rlwinm(fields);
                break;
            case 21: // rlwinm.
                rlwinm(fields);
                break;
            case 23: // rlwimi
                rlwimi(fields);
                break;
            case 24: // ori
                ori(fields);
                break;
            case 25: // oris
                oris(fields);
                break;
            case 26: // xori
                xori(fields);
                break;
            case 27: // xoris
                xoris(fields);
                break;
            case 28: // andi.
                andi(fields);
                break;
            case 29: // andis.
                andis(fields);
                break;
            default:
                throw new UnsupportedOperationException("Unsupported primary logical opcode: " + fields.opcode());
        }
    }

    /**
     * Executes extended logical instructions (opcode 31).
     *
     * @param fields Instruction fields.
     */
    public void executeExtended(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("X")) {
            throw new IllegalArgumentException("Invalid format for extended logical: " + fields.format());
        }
        switch (fields.xo()) {
            case 26: // cntlzw
                cntlzw(fields);
                break;
            case 28: // and
                and(fields);
                break;
            case 60: // andc
                andc(fields);
                break;
            case 124: // nor
                nor(fields);
                break;
            case 284: // eqv
                eqv(fields);
                break;
            case 316: // xor
                xor(fields);
                break;
            case 412: // orc
                orc(fields);
                break;
            case 444: // or
                or(fields);
                break;
            case 476: // nand
                nand(fields);
                break;
            case 792: // slw
                slw(fields);
                break;
            case 824: // sraw
                sraw(fields);
                break;
            case 826: // srawi
                srawi(fields);
                break;
            case 536: // srw
                srw(fields);
                break;
            case 922: // extsh
                extsh(fields);
                break;
            case 954: // extsb
                extsb(fields);
                break;
            case 986: // extsw
                extsw(fields);
                break;
            default:
                throw new UnsupportedOperationException("Unsupported extended logical opcode: " + fields.xo());
        }
    }

    // --- Operaciones básicas ---
    /**
     * AND: and RA, RS, RB (opcode 31, xo 28).
     * @param fields
     */
    public void and(DecodingInstr.InstructionFields fields) {
        long result = cpu.getGPRVal(fields.rt()) & cpu.getGPRVal(fields.rb());
        cpu.setGPR(fields.ra(), result & 0xFFFFFFFFL);
        if (fields.rc() == 1) {
            cpu.updateCRField(0, result);
        }
    }

    /**
     * OR: or RA, RS, RB (opcode 31, xo 444).
     * @param fields
     */
    public void or(DecodingInstr.InstructionFields fields) {
        long result = cpu.getGPRVal(fields.rt()) | cpu.getGPRVal(fields.rb());
        cpu.setGPR(fields.ra(), result & 0xFFFFFFFFL);
        if (fields.rc() == 1) {
            cpu.updateCRField(0, result);
        }
    }

    /**
     * XOR: xor RA, RS, RB (opcode 31, xo 316).
     * @param fields
     */
    public void xor(DecodingInstr.InstructionFields fields) {
        long result = cpu.getGPRVal(fields.rt()) ^ cpu.getGPRVal(fields.rb());
        cpu.setGPR(fields.ra(), result & 0xFFFFFFFFL);
        if (fields.rc() == 1) {
            cpu.updateCRField(0, result);
        }
    }

    /**
     * NAND: nand RA, RS, RB (opcode 31, xo 476).
     * @param fields
     */
    public void nand(DecodingInstr.InstructionFields fields) {
        long result = ~(cpu.getGPRVal(fields.rt()) & cpu.getGPRVal(fields.rb()));
        cpu.setGPR(fields.ra(), result & 0xFFFFFFFFL);
        if (fields.rc() == 1) {
            cpu.updateCRField(0, result);
        }
    }

    /**
     * NOR: nor RA, RS, RB (opcode 31, xo 124).
     * @param fields
     */
    public void nor(DecodingInstr.InstructionFields fields) {
        long result = ~(cpu.getGPRVal(fields.rt()) | cpu.getGPRVal(fields.rb()));
        cpu.setGPR(fields.ra(), result & 0xFFFFFFFFL);
        if (fields.rc() == 1) {
            cpu.updateCRField(0, result);
        }
    }

    /**
     * AND with complement: andc RA, RS, RB (opcode 31, xo 60).
     * @param fields
     */
    public void andc(DecodingInstr.InstructionFields fields) {
        long result = cpu.getGPRVal(fields.rt()) & ~cpu.getGPRVal(fields.rb());
        cpu.setGPR(fields.ra(), result & 0xFFFFFFFFL);
        if (fields.rc() == 1) {
            cpu.updateCRField(0, result);
        }
    }

    /**
     * OR with complement: orc RA, RS, RB (opcode 31, xo 412).
     * @param fields
     */
    public void orc(DecodingInstr.InstructionFields fields) {
        long result = cpu.getGPRVal(fields.rt()) | ~cpu.getGPRVal(fields.rb());
        cpu.setGPR(fields.ra(), result & 0xFFFFFFFFL);
        if (fields.rc() == 1) {
            cpu.updateCRField(0, result);
        }
    }

    /**
     * Equivalent: eqv RA, RS, RB (opcode 31, xo 284).
     * @param fields
     */
    public void eqv(DecodingInstr.InstructionFields fields) {
        long result = ~(cpu.getGPRVal(fields.rt()) ^ cpu.getGPRVal(fields.rb()));
        cpu.setGPR(fields.ra(), result & 0xFFFFFFFFL);
        if (fields.rc() == 1) {
            cpu.updateCRField(0, result);
        }
    }

    // --- Operaciones inmediatas ---
    /**
     * AND immediate: andi.RA, RS, UI (opcode 28).
     * @param fields
     */
    public void andi(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("D")) {
            throw new IllegalArgumentException("Invalid format for andi: " + fields.format());
        }
        long result = cpu.getGPRVal(fields.rt()) & (fields.si() & 0xFFFF);
        cpu.setGPR(fields.ra(), result & 0xFFFFFFFFL);
        cpu.updateCRField(0, result);
    }

    /**
     * AND immediate shifted: andis.RA, RS, UI (opcode 29).
     * @param fields
     */
    public void andis(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("D")) {
            throw new IllegalArgumentException("Invalid format for andis: " + fields.format());
        }
        long result = cpu.getGPRVal(fields.rt()) & (fields.si() << 16);
        cpu.setGPR(fields.ra(), result & 0xFFFFFFFFL);
        cpu.updateCRField(0, result);
    }

    /**
     * OR immediate: ori RA, RS, UI (opcode 24).
     * @param fields
     */
    public void ori(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("D")) {
            throw new IllegalArgumentException("Invalid format for ori: " + fields.format());
        }
        long result = cpu.getGPRVal(fields.rt()) | (fields.si() & 0xFFFF);
        cpu.setGPR(fields.ra(), result & 0xFFFFFFFFL);
    }

    /**
     * OR immediate shifted: oris RA, RS, UI (opcode 25).
     * @param fields
     */
    public void oris(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("D")) {
            throw new IllegalArgumentException("Invalid format for oris: " + fields.format());
        }
        long result = cpu.getGPRVal(fields.rt()) | (fields.si() << 16);
        cpu.setGPR(fields.ra(), result & 0xFFFFFFFFL);
    }

    /**
     * XOR immediate: xori RA, RS, UI (opcode 26).
     * @param fields
     */
    public void xori(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("D")) {
            throw new IllegalArgumentException("Invalid format for xori: " + fields.format());
        }
        long result = cpu.getGPRVal(fields.rt()) ^ (fields.si() & 0xFFFF);
        cpu.setGPR(fields.ra(), result & 0xFFFFFFFFL);
    }

    /**
     * XOR immediate shifted: xoris RA, RS, UI (opcode 27).
     * @param fields
     */
    public void xoris(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("D")) {
            throw new IllegalArgumentException("Invalid format for xoris: " + fields.format());
        }
        long result = cpu.getGPRVal(fields.rt()) ^ (fields.si() << 16);
        cpu.setGPR(fields.ra(), result & 0xFFFFFFFFL);
    }

    // --- Operaciones de conteo ---
    /**
     * Count leading zeros word: cntlzw RA, RS (opcode 31, xo 26).
     * @param fields
     */
    public void cntlzw(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("X")) {
            throw new IllegalArgumentException("Invalid format for cntlzw: " + fields.format());
        }
        long value = cpu.getGPRVal(fields.rt());
        int count = Long.numberOfLeadingZeros(value & 0xFFFFFFFFL);
        cpu.setGPR(fields.ra(), count & 0xFFFFFFFFL);
        if (fields.rc() == 1) {
            cpu.updateCRField(0, count);
        }
    }

    // --- Operaciones de rotación y desplazamiento ---
    /**
     * Rotate left word immediate then AND with mask: rlwinm RA, RS, SH, MB, ME
     * (opcode 20, 21).
     * @param fields
     */
    public void rlwinm(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("M")) {
            throw new IllegalArgumentException("Invalid format for rlwinm: " + fields.format());
        }
        long value = cpu.getGPRVal(fields.rt());
        long rotated = Long.rotateLeft(value, fields.sh());
        int mask = createMask(fields.mb(), fields.me());
        long result = rotated & mask;
        cpu.setGPR(fields.ra(), result & 0xFFFFFFFFL);
        if (fields.opcode() == 21 || fields.rc() == 1) { // rlwinm.
            cpu.updateCRField(0, result);
        }
    }

    /**
     * Rotate left word immediate then mask insert: rlwimi RA, RS, SH, MB, ME
     * (opcode 23, 24).
     * @param fields
     */
    public void rlwimi(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("M")) {
            throw new IllegalArgumentException("Invalid format for rlwimi: " + fields.format());
        }
        long src = cpu.getGPRVal(fields.rt());
        long dest = cpu.getGPRVal(fields.ra());
        long rotated = Long.rotateLeft(src, fields.sh());
        int mask = createMask(fields.mb(), fields.me());
        long result = (dest & ~mask) | (rotated & mask);
        cpu.setGPR(fields.ra(), result & 0xFFFFFFFFL);
        if (fields.opcode() == 24 || fields.rc() == 1) { // rlwimi.
            cpu.updateCRField(0, result);
        }
    }

    /**
     * Shift left word: slw RA, RS, RB (opcode 31, xo 792).
     * @param fields
     */
    public void slw(DecodingInstr.InstructionFields fields) {
        long shiftAmount = cpu.getGPRVal(fields.rb()) & 0x3F;
        long result = shiftAmount > 31 ? 0 : cpu.getGPRVal(fields.rt()) << shiftAmount;
        cpu.setGPR(fields.ra(), result & 0xFFFFFFFFL);
        if (fields.rc() == 1) {
            cpu.updateCRField(0, result);
        }
    }

    /**
     * Shift right word: srw RA, RS, RB (opcode 31, xo 536).
     * @param fields
     */
    public void srw(DecodingInstr.InstructionFields fields) {
        long shiftAmount = cpu.getGPRVal(fields.rb()) & 0x3F;
        long result = shiftAmount > 31 ? 0 : cpu.getGPRVal(fields.rt()) >>> shiftAmount;
        cpu.setGPR(fields.ra(), result & 0xFFFFFFFFL);
        if (fields.rc() == 1) {
            cpu.updateCRField(0, result);
        }
    }

    /**
     * Shift right algebraic word: sraw RA, RS, RB (opcode 31, xo 824).
     * @param fields
     */
    public void sraw(DecodingInstr.InstructionFields fields) {
        long value = cpu.getGPRVal(fields.rt());
        long shiftAmount = cpu.getGPRVal(fields.rb()) & 0x3F;
        long result = value >> Math.min(shiftAmount, 31);
        cpu.setGPR(fields.ra(), result & 0xFFFFFFFFL);
        boolean carry = shiftAmount > 0 && shiftAmount < 32 && value < 0 && (value & ((1L << shiftAmount) - 1)) != 0;
        cpu.setCA(carry);
        if (fields.rc() == 1) {
            cpu.updateCRField(0, result);
        }
    }

    /**
     * Shift right algebraic word immediate: srawi RA, RS, SH (opcode 31, xo
     * 826).
     * @param fields
     */
    public void srawi(DecodingInstr.InstructionFields fields) {
        long value = cpu.getGPRVal(fields.rt());
        int sh = fields.sh();
        long result = value >> Math.min(sh, 31);
        cpu.setGPR(fields.ra(), result & 0xFFFFFFFFL);
        boolean carry = sh > 0 && sh < 32 && value < 0 && (value & ((1L << sh) - 1)) != 0;
        cpu.setCA(carry);
        if (fields.rc() == 1) {
            cpu.updateCRField(0, result);
        }
    }

    /**
     * Extend sign byte: extsb RA, RS (opcode 31, xo 954).
     * @param fields
     */
    public void extsb(DecodingInstr.InstructionFields fields) {
        long value = cpu.getGPRVal(fields.rt());
        cpu.setGPR(fields.ra(), ((value & 0x80) != 0 ? (value | 0xFFFFFFFFFFFFFF00L) : (value & 0xFF)) & 0xFFFFFFFFL);
    }

    /**
     * Extend sign halfword: extsh RA, RS (opcode 31, xo 922).
     * @param fields
     */
    public void extsh(DecodingInstr.InstructionFields fields) {
        long value = cpu.getGPRVal(fields.rt());
        cpu.setGPR(fields.ra(), ((value & 0x8000) != 0 ? (value | 0xFFFFFFFFFFFF0000L) : (value & 0xFFFF)) & 0xFFFFFFFFL);
    }

    /**
     * Extend sign word: extsw RA, RS (opcode 31, xo 986).
     * @param fields
     */
    public void extsw(DecodingInstr.InstructionFields fields) {
        long value = cpu.getGPRVal(fields.rt());
        cpu.setGPR(fields.ra(), value & 0xFFFFFFFFL);
    }

    /**
     * Move condition register field: mcrf BF, BFA (opcode 19, xo 0).
     * @param fields
     */
    public void copyField(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("XL")) {
            throw new IllegalArgumentException("Invalid format for mcrf: " + fields.format());
        }
        int bf = fields.rt(); // BF
        int bfa = fields.ra(); // BFA
        long cr = cpu.getCr();
        int shiftBf = (7 - bf) * 4;
        int shiftBfa = (7 - bfa) * 4;
        long value = (cr >>> shiftBfa) & 0xF;
        cr = (cr & ~(0xFL << shiftBf)) | (value << shiftBf);
        cpu.setCr(cr);
        System.out.println("mcrf: Move CR field BF=" + bf + " to BFA=" + bfa);
    }

    // --- Métodos auxiliares ---
    private int createMask(int mb, int me) {
        if (mb <= me) {
            return ((1 << (32 - mb)) - 1) & ~((1 << (31 - me)) - 1);
        } else {
            return ~(((1 << (31 - me)) - 1) & ~((1 << (31 - mb)) - 1));
        }
    }
}
