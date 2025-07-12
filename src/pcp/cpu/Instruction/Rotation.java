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
import pcp.utils.Utilities;

/**
 * Handles PowerPC rotation instructions for Xenon CPU emulation.
 *
 * @author Slam
 */
public class Rotation {

    private final CPU cpu;

    public Rotation(CPU cpu) {
        this.cpu = cpu;
    }

    /**
     * Executes primary rotation instructions (opcodes 20, 21, 22, 23).
     *
     * @param fields Instruction fields.
     */
    public void executePrimary(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("M")) {
            throw new IllegalArgumentException("Invalid format for rotation: " + fields.format());
        }
        switch (fields.opcode()) {
            case 20: // rlwimi
                rlwimi(fields);
                break;
            case 21: // rlwinm
                rlwinm(fields);
                break;
            case 22: // rlmi (Xenon-specific)
                rlmi(fields);
                break;
            case 23: // rlwnm
                rlwnm(fields);
                break;
            default:
                throw new UnsupportedOperationException("Unsupported primary rotation opcode: " + fields.opcode());
        }
    }

    /**
     * Executes extended rotation instructions (opcode 30).
     *
     * @param fields Instruction fields.
     */
    public void executeExtended(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("MD") && !fields.format().equals("MDS")) {
            throw new IllegalArgumentException("Invalid format for extended rotation: " + fields.format());
        }
        switch (fields.xo()) {
            case 0, 1: // rldicl
                rldicl(fields);
                break;
            case 2, 3: // rldicr
                rldicr(fields);
                break;
            case 4, 5: // rldic
                rldic(fields);
                break;
            case 6, 7, 14: // rldimi
                rldimi(fields);
                break;
            case 8, 9: // rldcl
                rldcl(fields);
                break;
            case 10: // rldcr
                rldcr(fields);
                break;
            default:
                throw new UnsupportedOperationException("Unsupported extended rotation opcode: " + fields.xo());
        }
    }

    /**
     * Rotate left word immediate then mask insert: rlwimi RA, RS, SH, MB, ME
     * (opcode 20).
     * @param fields
     */
    public void rlwimi(DecodingInstr.InstructionFields fields) {
        long value = cpu.getGPRVal(fields.rt());
        long rotated = Long.rotateLeft(value, fields.sh());
        long mask = Utilities.MaskFromMBME32(fields.mb(), fields.me());
        long current = cpu.getGPRVal(fields.ra());
        long result = (rotated & mask) | (current & ~mask);
        cpu.setGPR(fields.ra(), result & 0xFFFFFFFFL);
        if (fields.rc() == 1 || fields.opcode() == 20) { // rlwimi.
            cpu.updateCRField(0, result);
        }
    }

    /**
     * Rotate left word immediate then AND with mask: rlwinm RA, RS, SH, MB, ME
     * (opcode 21).
     * @param fields
     */
    public void rlwinm(DecodingInstr.InstructionFields fields) {
        long value = cpu.getGPRVal(fields.rt());
        long rotated = Long.rotateLeft(value, fields.sh());
        long result = rotated & Utilities.MaskFromMBME32(fields.mb(), fields.me());
        cpu.setGPR(fields.ra(), result & 0xFFFFFFFFL);
        if (fields.rc() == 1 || fields.opcode() == 21) { // rlwinm.
            cpu.updateCRField(0, result);
        }
    }

    /**
     * Rotate left word then AND with mask: rlwnm RA, RS, RB, MB, ME (opcode
     * 23).
     * @param fields
     */
    public void rlwnm(DecodingInstr.InstructionFields fields) {
        long value = cpu.getGPRVal(fields.rt());
        long shiftAmount = cpu.getGPRVal(fields.rb()) & 0x1F; // 5-bit shift for 32-bit
        long rotated = Long.rotateLeft(value, (int) shiftAmount);
        long result = rotated & Utilities.MaskFromMBME32(fields.mb(), fields.me());
        cpu.setGPR(fields.ra(), result & 0xFFFFFFFFL);
        if (fields.rc() == 1) { // rlwnm.
            cpu.updateCRField(0, result);
        }
    }

    /**
     * Rotate left word immediate (Xenon-specific): rlmi RA, RS, SH, MB, ME
     * (opcode 22).
     */
    public void rlmi(DecodingInstr.InstructionFields fields) {
        // Assuming rlmi is similar to rlwimi but with Xenon-specific behavior
        long value = cpu.getGPRVal(fields.rt());
        long rotated = Long.rotateLeft(value, fields.sh());
        long mask = Utilities.MaskFromMBME32(fields.mb(), fields.me());
        long current = cpu.getGPRVal(fields.ra());
        long result = (rotated & mask) | (current & ~mask);
        cpu.setGPR(fields.ra(), result & 0xFFFFFFFFL);
        if (fields.rc() == 1) { // rlmi.
            cpu.updateCRField(0, result);
        }
    }

    /**
     * Rotate left doubleword immediate then clear left: rldicl RA, RS, SH, MB
     * (opcode 30, xo 0, 1).
     * @param fields
     */
    public void rldicl(DecodingInstr.InstructionFields fields) {
        long value = cpu.getGPRVal(fields.rt());
        long rotated = Long.rotateLeft(value, fields.sh() & 0x3F); // 6-bit shift
        long result = rotated & Utilities.MaskFromMBME32(fields.mb(), 31); // ME=31 for 32-bit
        cpu.setGPR(fields.ra(), result & 0xFFFFFFFFL);
        if (fields.rc() == 1) {
            cpu.updateCRField(0, result);
        }
    }

    /**
     * Rotate left doubleword immediate then clear right: rldicr RA, RS, SH, ME
     * (opcode 30, xo 2, 3).
     * @param fields
     */
    public void rldicr(DecodingInstr.InstructionFields fields) {
        long value = cpu.getGPRVal(fields.rt());
        long rotated = Long.rotateLeft(value, fields.sh() & 0x3F); // 6-bit shift
        long result = rotated & Utilities.MaskFromMBME32(0, fields.me()); // MB=0
        cpu.setGPR(fields.ra(), result & 0xFFFFFFFFL);
        if (fields.rc() == 1) {
            cpu.updateCRField(0, result);
        }
    }

    /**
     * Rotate left doubleword immediate then clear: rldic RA, RS, SH, MB (opcode
     * 30, xo 4, 5).
     * @param fields
     */
    public void rldic(DecodingInstr.InstructionFields fields) {
        long value = cpu.getGPRVal(fields.rt());
        long rotated = Long.rotateLeft(value, fields.sh() & 0x3F); // 6-bit shift
        long result = rotated & Utilities.MaskFromMBME32(fields.mb(), 31 - fields.mb()); // ME=31-MB
        cpu.setGPR(fields.ra(), result & 0xFFFFFFFFL);
        if (fields.rc() == 1) {
            cpu.updateCRField(0, result);
        }
    }

    /**
     * Rotate left doubleword immediate then mask insert: rldimi RA, RS, SH, MB
     * (opcode 30, xo 6, 7, 14).
     * @param fields
     */
    public void rldimi(DecodingInstr.InstructionFields fields) {
        long value = cpu.getGPRVal(fields.rt());
        long rotated = Long.rotateLeft(value, fields.sh() & 0x3F); // 6-bit shift
        long mask = Utilities.MaskFromMBME32(fields.mb(), 31 - fields.mb()); // ME=31-MB
        long current = cpu.getGPRVal(fields.ra());
        long result = (rotated & mask) | (current & ~mask);
        cpu.setGPR(fields.ra(), result & 0xFFFFFFFFL);
        if (fields.rc() == 1) {
            cpu.updateCRField(0, result);
        }
    }

    /**
     * Rotate left doubleword then clear left: rldcl RA, RS, RB, MB (opcode 30,
     * xo 8, 9).
     * @param fields
     */
    public void rldcl(DecodingInstr.InstructionFields fields) {
        long value = cpu.getGPRVal(fields.rt());
        long shiftAmount = cpu.getGPRVal(fields.rb()) & 0x3F; // 6-bit shift
        long rotated = Long.rotateLeft(value, (int) shiftAmount);
        long result = rotated & Utilities.MaskFromMBME32(fields.mb(), 31); // ME=31
        cpu.setGPR(fields.ra(), result & 0xFFFFFFFFL);
        if (fields.rc() == 1) {
            cpu.updateCRField(0, result);
        }
    }

    /**
     * Rotate left doubleword then clear right: rldcr RA, RS, RB, ME (opcode 30,
     * xo 10).
     * @param fields
     */
    public void rldcr(DecodingInstr.InstructionFields fields) {
        long value = cpu.getGPRVal(fields.rt());
        long shiftAmount = cpu.getGPRVal(fields.rb()) & 0x3F; // 6-bit shift
        long rotated = Long.rotateLeft(value, (int) shiftAmount);
        long result = rotated & Utilities.MaskFromMBME32(0, fields.me()); // MB=0
        cpu.setGPR(fields.ra(), result & 0xFFFFFFFFL);
        if (fields.rc() == 1) {
            cpu.updateCRField(0, result);
        }
    }
}
