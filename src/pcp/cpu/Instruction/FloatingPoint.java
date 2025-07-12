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
 * Handles PowerPC floating-point instructions for Xenon CPU emulation.
 *
 * @author Slam
 */
public class FloatingPoint {

    private final CPU cpu;

    public FloatingPoint(CPU cpu) {
        this.cpu = cpu;
    }

    /**
     * Executes primary floating-point instructions (opcodes 48, 49, 50, 51, 52,
     * 53, 54, 55, 56, 57, 60, 61).
     *
     * @param fields Instruction fields from DecodingInstr.
     */
    public void executePrimary(DecodingInstr.InstructionFields fields) {
        int op = fields.opcode();
        if (!fields.format().equals("D") && ((op != 56) && (op != 57) && (op != 60) && (op != 61))) {
            throw new IllegalArgumentException("Invalid format for primary floating-point: " + fields.format());
        }
        switch (fields.opcode()) {
            case 48: // lfs
                lfs(fields.rt(), fields.ra(), fields.si());
                break;
            case 49: // lfsu
                lfsu(fields.rt(), fields.ra(), fields.si());
                break;
            case 50: // lfd
                lfd(fields.rt(), fields.ra(), fields.si());
                break;
            case 51: // lfdu
                lfdu(fields.rt(), fields.ra(), fields.si());
                break;
            case 52: // stfs
                stfs(fields.rt(), fields.ra(), fields.si());
                break;
            case 53: // stfsu
                stfsu(fields.rt(), fields.ra(), fields.si());
                break;
            case 54: // stfd
                stfd(fields.rt(), fields.ra(), fields.si());
                break;
            case 55: // stfdu
                stfdu(fields.rt(), fields.ra(), fields.si());
                break;
            case 56: // lfdp (Xenon-specific)
                lfdp(fields.rt(), fields.ra(), fields.si());
                break;
            case 57: // lfdpx (Xenon-specific)
                lfdpx(fields.rt(), fields.ra(), fields.rb());
                break;
            case 60: // stfdp (Xenon-specific)
                stfdp(fields.rt(), fields.ra(), fields.si());
                break;
            case 61: // stfdpx (Xenon-specific)
                stfdpx(fields.rt(), fields.ra(), fields.rb());
                break;
            default:
                throw new UnsupportedOperationException("Unsupported primary floating-point opcode: " + fields.opcode());
        }
    }

    /**
     * Executes extended floating-point instructions (opcodes 59, 63).
     *
     * @param fields Instruction fields from DecodingInstr.
     */
    public void executeExtended(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("A") && !fields.format().equals("X")) {
            throw new IllegalArgumentException("Invalid format for extended floating-point: " + fields.format());
        }
        switch (fields.opcode()) {
            case 59: // Single-precision extended (A-form)
                switch (fields.xo()) {
                    case 18: // fdivs
                        fdivs(fields.rt(), fields.ra(), fields.rb());
                        break;
                    case 20: // fsubs
                        fsubs(fields.rt(), fields.ra(), fields.rb());
                        break;
                    case 21: // fadds
                        fadds(fields.rt(), fields.ra(), fields.rb());
                        break;
                    case 22: // fsqrts
                        fsqrts(fields.rt(), fields.rb());
                        break;
                    case 24: // fres
                        fres(fields.rt(), fields.rb());
                        break;
                    case 25: // fmuls
                        fmuls(fields.rt(), fields.ra(), fields.rb());
                        break;
                    case 26: // frsqrtes
                        frsqrtes(fields.rt(), fields.rb());
                        break;
                    case 28: // fmsubs
                        fmsubs(fields);
                        break;
                    case 29: // fmadds
                        fmadds(fields);
                        break;
                    case 30: // fnmsubs
                        fnmsubs(fields);
                        break;
                    case 31: // fnmadds
                        fnmadds(fields);
                        break;
                    default:
                        throw new UnsupportedOperationException("Unsupported single-precision XO: " + fields.xo());
                }
                break;
            case 63: // Double-precision and other extended (A/X-form)
                switch (fields.xo()) {
                    case 0: // fcmpu
                        fcmpu(fields.rt(), fields.ra(), fields.rb());
                        break;
                    case 12: // frsp
                        frsp(fields.rt(), fields.rb());
                        break;
                    case 14: // fctiw
                        fctiw(fields.rt(), fields.rb());
                        break;
                    case 15: // fctiwz
                        fctiwz(fields.rt(), fields.rb());
                        break;
                    case 18: // fdiv
                        fdiv(fields.rt(), fields.ra(), fields.rb());
                        break;
                    case 20: // fsub
                        fsub(fields.rt(), fields.ra(), fields.rb());
                        break;
                    case 21: // fadd
                        fadd(fields.rt(), fields.ra(), fields.rb());
                        break;
                    case 22: // fsqrt
                        fsqrt(fields.rt(), fields.rb());
                        break;
                    case 24: // fres
                        fres(fields.rt(), fields.rb());
                        break;
                    case 25: // fmul
                        fmul(fields.rt(), fields.ra(), fields.rb());
                        break;
                    case 26: // frsqrte
                        frsqrte(fields.rt(), fields.rb());
                        break;
                    case 28: // fmsub
                        fmsub(fields.rt(), fields.ra(), fields.rb(), fields.si());
                        break;
                    case 29: // fmadd
                        fmadd(fields.rt(), fields.ra(), fields.rb(), fields.si());
                        break;
                    case 30: // fnmsub
                        fnmsub(fields.rt(), fields.ra(), fields.rb(), fields.si());
                        break;
                    case 31: // fnmadd
                        fnmadd(fields.rt(), fields.ra(), fields.rb(), fields.si());
                        break;
                    case 32: // fcmpo
                        fcmpo(fields.rt(), fields.ra(), fields.rb());
                        break;
                    case 38: // mtfsb1
                        mtfsb1(fields.rt());
                        break;
                    case 40: // fneg
                        fneg(fields.rt(), fields.rb());
                        break;
                    case 70: // mtfsb0
                        mtfsb0(fields.rt());
                        break;
                    case 72: // fmr
                        fmr(fields.rt(), fields.rb());
                        break;
                    case 134: // mtfsfi
                        mtfsfi(fields.rt(), fields.ra());
                        break;
                    case 136: // fnabs
                        fnabs(fields.rt(), fields.rb());
                        break;
                    case 264: // fabs
                        fabs(fields.rt(), fields.rb());
                        break;
                    case 583: // mffs
                        mffs(fields.rt());
                        break;
                    case 711: // fsel
                        fsel(fields.rt(), fields.ra(), fields.rb(), fields.si());
                        break;
                    case 814: // fctid
                        fctid(fields.rt(), fields.rb());
                        break;
                    case 815: // fctidz
                        fctidz(fields.rt(), fields.rb());
                        break;
                    case 846: // fcfid
                        fcfid(fields.rt(), fields.rb());
                        break;
                    case 663: // stfsx
                        stfsx(fields.rt(), fields.ra(), fields.rb());
                        break;
                    case 695: // stfsux
                        stfsux(fields.rt(), fields.ra(), fields.rb());
                        break;
                    case 727: // stfdx
                        stfdx(fields.rt(), fields.ra(), fields.rb());
                        break;
                    case 759: // stfdux
                        stfdux(fields.rt(), fields.ra(), fields.rb());
                        break;
                    case 887: // lfdx
                        lfdx(fields.rt(), fields.ra(), fields.rb());
                        break;
                    case 983: // stfiwx
                        stfiwx(fields.rt(), fields.ra(), fields.rb());
                        break;
                    default:
                        throw new UnsupportedOperationException("Unsupported double-precision XO: " + fields.xo());
                }
                break;
            default:
                throw new UnsupportedOperationException("Unsupported extended floating-point opcode: " + fields.opcode());
        }
    }

    // --- Floating-Point Instructions ---
    // Floating Compare Unordered (opcode 63, xo 0)
    private void fcmpu(int bf, int fra, int frb) {
        double a = cpu.getFPRVal(fra);
        double b = cpu.getFPRVal(frb);
        int crfValue = 0;

        if (Double.isNaN(a) || Double.isNaN(b)) {
            crfValue = 0x1; // Unordered
            cpu.setFPSCRBit(CPU.FPSCR_VXSNAN);
            cpu.setFPSCRBit(CPU.FPSCR_FX);
        } else if (a < b) {
            crfValue = 0x8; // Less than
        } else if (a > b) {
            crfValue = 0x4; // Greater than
        } else {
            crfValue = 0x2; // Equal
        }

        crfValue |= (cpu.getXer() & CPU.XER_SO) != 0 ? 0x1 : 0;
        cpu.updateCRField(bf, crfValue, true);
    }

    // Floating Compare Ordered (opcode 63, xo 32)
    private void fcmpo(int bf, int fra, int frb) {
        double a = cpu.getFPRVal(fra);
        double b = cpu.getFPRVal(frb);
        int crfValue = 0;

        if (Double.isNaN(a) || Double.isNaN(b)) {
            crfValue = 0x1; // Unordered
            cpu.setFPSCRBit(CPU.FPSCR_VXSNAN);
            cpu.setFPSCRBit(CPU.FPSCR_FX);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_FP);
            return;
        } else if (a < b) {
            crfValue = 0x8; // Less than
        } else if (a > b) {
            crfValue = 0x4; // Greater than
        } else {
            crfValue = 0x2; // Equal
        }

        crfValue |= (cpu.getXer() & CPU.XER_SO) != 0 ? 0x1 : 0;
        cpu.updateCRField(bf, crfValue, true);
    }

    // Floating Add Single (opcode 59, xo 21)
    private void fadds(int frt, int fra, int frb) {
        float a = (float) cpu.getFPRVal(fra);
        float b = (float) cpu.getFPRVal(frb);
        float result = a + b;
        updateFPSCRSingle(result);
        cpu.setFPR(frt, result);
    }

    // Floating Subtract Single (opcode 59, xo 20)
    private void fsubs(int frt, int fra, int frb) {
        float a = (float) cpu.getFPRVal(fra);
        float b = (float) cpu.getFPRVal(frb);
        float result = a - b;
        updateFPSCRSingle(result);
        cpu.setFPR(frt, result);
    }

    // Floating Multiply Single (opcode 59, xo 25)
    private void fmuls(int frt, int fra, int frb) {
        float a = (float) cpu.getFPRVal(fra);
        float b = (float) cpu.getFPRVal(frb);
        float result = a * b;
        updateFPSCRSingle(result);
        cpu.setFPR(frt, result);
    }

    // Floating Divide Single (opcode 59, xo 18)
    private void fdivs(int frt, int fra, int frb) {
        float a = (float) cpu.getFPRVal(fra);
        float b = (float) cpu.getFPRVal(frb);
        if (b == 0.0f) {
            cpu.setFPSCRBit(CPU.FPSCR_ZX);
            cpu.setFPSCRBit(CPU.FPSCR_FX);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_FP);
            return;
        }
        float result = a / b;
        updateFPSCRSingle(result);
        cpu.setFPR(frt, result);
    }

    // Floating Square Root Single (opcode 59, xo 22)
    private void fsqrts(int frt, int frb) {
        float value = (float) cpu.getFPRVal(frb);
        if (value < 0.0f) {
            cpu.setFPSCRBit(CPU.FPSCR_VXSQRT);
            cpu.setFPSCRBit(CPU.FPSCR_FX);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_FP);
            return;
        }
        float result = (float) Math.sqrt(value);
        updateFPSCRSingle(result);
        cpu.setFPR(frt, result);
    }

    // Floating Reciprocal Estimate Single (opcode 59, xo 24)
    // Floating Reciprocal Estimate Double (opcode 63, xo 24)
    private void fres(int frt, int frb) {
        double b = cpu.getFPRVal(frb);
        if (b == 0.0) {
            cpu.setFPSCRBit(CPU.FPSCR_ZX);
            cpu.setFPSCRBit(CPU.FPSCR_FX);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_FP);
            return;
        }
        double result = 1.0 / b;
        updateFPSCRDouble(result);
        cpu.setFPR(frt, result);
    }

    // Floating Reciprocal Square Root Estimate Single (opcode 59, xo 26)
    private void frsqrtes(int frt, int frb) {
        float b = (float) cpu.getFPRVal(frb);
        if (b < 0.0f) {
            cpu.setFPSCRBit(CPU.FPSCR_VXSQRT);
            cpu.setFPSCRBit(CPU.FPSCR_FX);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_FP);
            return;
        }
        float result = (float) (1.0 / Math.sqrt(b));
        updateFPSCRSingle(result);
        cpu.setFPR(frt, result);
    }

    // Floating Multiply-Add Single (opcode 59, xo 29)
    private void fmadds(DecodingInstr.InstructionFields fields) {
        float a = (float) cpu.getFPRVal(fields.ra());
        float b = (float) cpu.getFPRVal(fields.rb());
        float c = (float) cpu.getFPRVal(fields.rc());
        float result = a * b + c;
        updateFPSCRSingle(result);
        cpu.setFPR(fields.rt(), result);
        if (fields.rc() == 1) {
            cpu.updateCRField(0, Double.doubleToRawLongBits(result), true);
        }
    }

    // Floating Multiply-Subtract Single (opcode 59, xo 28)
    private void fmsubs(DecodingInstr.InstructionFields fields) {
        float a = (float) cpu.getFPRVal(fields.ra());
        float b = (float) cpu.getFPRVal(fields.rb());
        float c = (float) cpu.getFPRVal(fields.rc());
        float result = a * b - c;
        updateFPSCRSingle(result);
        cpu.setFPR(fields.rt(), result);
        if (fields.rc() == 1) {
            cpu.updateCRField(0, Double.doubleToRawLongBits(result), true);
        }
    }

    // Floating Negative Multiply-Add Single (opcode 59, xo 31)
    private void fnmadds(DecodingInstr.InstructionFields fields) {
        float a = (float) cpu.getFPRVal(fields.ra());
        float b = (float) cpu.getFPRVal(fields.rb());
        float c = (float) cpu.getFPRVal(fields.rc());
        float result = -(a * b + c);
        updateFPSCRSingle(result);
        cpu.setFPR(fields.rt(), result);
        if (fields.rc() == 1) {
            cpu.updateCRField(0, Double.doubleToRawLongBits(result), true);
        }
    }

    // Floating Negative Multiply-Subtract Single (opcode 59, xo 30)
    private void fnmsubs(DecodingInstr.InstructionFields fields) {
        float a = (float) cpu.getFPRVal(fields.ra());
        float b = (float) cpu.getFPRVal(fields.rb());
        float c = (float) cpu.getFPRVal(fields.rc());
        float result = -(a * b - c);
        updateFPSCRSingle(result);
        cpu.setFPR(fields.rt(), result);
        if (fields.rc() == 1) {
            cpu.updateCRField(0, Double.doubleToRawLongBits(result), true);
        }
    }

    // Floating Round to Single Precision (opcode 63, xo 12)
    private void frsp(int frt, int frb) {
        double value = cpu.getFPRVal(frb);
        float result = (float) value;
        updateFPSCRSingle(result);
        cpu.setFPR(frt, result);
    }

    // Floating Convert to Integer Word (opcode 63, xo 14)
    private void fctiw(int frt, int frb) {
        double value = cpu.getFPRVal(frb);
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            cpu.setFPSCRBit(CPU.FPSCR_VX);
            cpu.setFPSCRBit(CPU.FPSCR_FX);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_FP);
            return;
        }
        int result = (int) Math.rint(value); // Round to nearest
        cpu.setFPR(frt, Float.intBitsToFloat(result));
    }

    // Floating Convert to Integer Word with Round to Zero (opcode 63, xo 15)
    private void fctiwz(int frt, int frb) {
        double value = cpu.getFPRVal(frb);
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            cpu.setFPSCRBit(CPU.FPSCR_VX);
            cpu.setFPSCRBit(CPU.FPSCR_FX);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_FP);
            return;
        }
        int result = (int) value; // Truncate to zero
        cpu.setFPR(frt, Float.intBitsToFloat(result));
    }

    // Floating Negate (opcode 63, xo 40)
    private void fneg(int frt, int frb) {
        double value = cpu.getFPRVal(frb);
        cpu.setFPR(frt, -value);
    }

    // Floating Move Register (opcode 63, xo 72)
    private void fmr(int frt, int frb) {
        cpu.setFPR(frt, cpu.getFPRVal(frb));
    }

    // Floating Negative Absolute Value (opcode 63, xo 136)
    private void fnabs(int frt, int frb) {
        double absVal = Math.abs(cpu.getFPRVal(frb));
        cpu.setFPR(frt, -absVal);
    }

    // Floating Absolute Value (opcode 63, xo 264)
    private void fabs(int frt, int frb) {
        double absVal = Math.abs(cpu.getFPRVal(frb));
        cpu.setFPR(frt, absVal);
    }

    // Floating Select (opcode 63, xo 711)
    private void fsel(int frt, int fra, int frb, int frc) {
        double a = cpu.getFPRVal(fra);
        double b = cpu.getFPRVal(frb);
        double c = cpu.getFPRVal(frc);
        double result = (a >= 0.0) ? b : c;
        cpu.setFPR(frt, result);
    }

    // Floating Add Double (opcode 63, xo 21)
    private void fadd(int frt, int fra, int frb) {
        double a = cpu.getFPRVal(fra);
        double b = cpu.getFPRVal(frb);
        double result = a + b;
        updateFPSCRDouble(result);
        cpu.setFPR(frt, result);
    }

    // Floating Subtract Double (opcode 63, xo 20)
    private void fsub(int frt, int fra, int frb) {
        double a = cpu.getFPRVal(fra);
        double b = cpu.getFPRVal(frb);
        double result = a - b;
        updateFPSCRDouble(result);
        cpu.setFPR(frt, result);
    }

    // Floating Multiply Double (opcode 63, xo 25)
    private void fmul(int frt, int fra, int frb) {
        double a = cpu.getFPRVal(fra);
        double b = cpu.getFPRVal(frb);
        double result = a * b;
        updateFPSCRDouble(result);
        cpu.setFPR(frt, result);
    }

    // Floating Divide Double (opcode 63, xo 18)
    private void fdiv(int frt, int fra, int frb) {
        double a = cpu.getFPRVal(fra);
        double b = cpu.getFPRVal(frb);
        if (b == 0.0) {
            cpu.setFPSCRBit(CPU.FPSCR_ZX);
            cpu.setFPSCRBit(CPU.FPSCR_FX);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_FP);
            return;
        }
        double result = a / b;
        updateFPSCRDouble(result);
        cpu.setFPR(frt, result);
    }

    // Floating Square Root Double (opcode 63, xo 22)
    private void fsqrt(int frt, int frb) {
        double value = cpu.getFPRVal(frb);
        if (value < 0.0) {
            cpu.setFPSCRBit(CPU.FPSCR_VXSQRT);
            cpu.setFPSCRBit(CPU.FPSCR_FX);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_FP);
            return;
        }
        double result = Math.sqrt(value);
        updateFPSCRDouble(result);
        cpu.setFPR(frt, result);
    }

    // Floating Reciprocal Square Root Estimate Double (opcode 63, xo 26)
    private void frsqrte(int frt, int frb) {
        double b = cpu.getFPRVal(frb);
        if (b < 0.0) {
            cpu.setFPSCRBit(CPU.FPSCR_VXSQRT);
            cpu.setFPSCRBit(CPU.FPSCR_FX);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_FP);
            return;
        }
        double result = 1.0 / Math.sqrt(b);
        updateFPSCRDouble(result);
        cpu.setFPR(frt, result);
    }

    // Floating Multiply-Add Double (opcode 63, xo 29)
    private void fmadd(int frt, int fra, int frb, int frc) {
        double a = cpu.getFPRVal(fra);
        double b = cpu.getFPRVal(frb);
        double c = cpu.getFPRVal(frc);
        double result = a * b + c;
        updateFPSCRDouble(result);
        cpu.setFPR(frt, result);
    }

    // Floating Multiply-Subtract Double (opcode 63, xo 28)
    private void fmsub(int frt, int fra, int frb, int frc) {
        double a = cpu.getFPRVal(fra);
        double b = cpu.getFPRVal(frb);
        double c = cpu.getFPRVal(frc);
        double result = a * b - c;
        updateFPSCRDouble(result);
        cpu.setFPR(frt, result);
    }

    // Floating Negative Multiply-Add Double (opcode 63, xo 31)
    private void fnmadd(int frt, int fra, int frb, int frc) {
        double a = cpu.getFPRVal(fra);
        double b = cpu.getFPRVal(frb);
        double c = cpu.getFPRVal(frc);
        double result = -(a * b + c);
        updateFPSCRDouble(result);
        cpu.setFPR(frt, result);
    }

    // Floating Negative Multiply-Subtract Double (opcode 63, xo 30)
    private void fnmsub(int frt, int fra, int frb, int frc) {
        double a = cpu.getFPRVal(fra);
        double b = cpu.getFPRVal(frb);
        double c = cpu.getFPRVal(frc);
        double result = -(a * b - c);
        updateFPSCRDouble(result);
        cpu.setFPR(frt, result);
    }

    // Move from FPSCR (opcode 63, xo 583)
    private void mffs(int frt) {
        long fpscr = cpu.getFPSCR();
        cpu.setFPR(frt, Double.longBitsToDouble(fpscr));
    }

    // Move to FPSCR Bit 1 (opcode 63, xo 38)
    private void mtfsb1(int crbD) {
        cpu.setFPSCRBit(crbD);
    }

    // Move to FPSCR Bit 0 (opcode 63, xo 70)
    private void mtfsb0(int crbD) {
        cpu.clearFPSCRBit(crbD);
    }

    // Move to FPSCR Fields Immediate (opcode 63, xo 134)
    private void mtfsfi(int crfD, int imm) {
        int shift = (7 - crfD) * 4;
        long mask = ~(0xF << shift);
        long value = ((long) imm) << shift;
        long fpscr = cpu.getFPSCR();
        fpscr = (fpscr & mask) | value;
        cpu.setFPSCR((int) fpscr);
    }

    // Floating Convert to Integer Doubleword (opcode 63, xo 814)
    private void fctid(int frt, int frb) {
        double value = cpu.getFPRVal(frb);
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            cpu.setFPSCRBit(CPU.FPSCR_VX);
            cpu.setFPSCRBit(CPU.FPSCR_FX);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_FP);
            return;
        }
        long result = (long) Math.rint(value); // Round to nearest
        cpu.setFPR(frt, Double.longBitsToDouble(result));
    }

    // Floating Convert to Integer Doubleword with Round to Zero (opcode 63, xo 815)
    private void fctidz(int frt, int frb) {
        double value = cpu.getFPRVal(frb);
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            cpu.setFPSCRBit(CPU.FPSCR_VX);
            cpu.setFPSCRBit(CPU.FPSCR_FX);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_FP);
            return;
        }
        long result = (long) value; // Truncate to zero
        cpu.setFPR(frt, Double.longBitsToDouble(result));
    }

    // Floating Convert from Integer Doubleword (opcode 63, xo 846)
    private void fcfid(int frt, int frb) {
        double value = Double.longBitsToDouble((long) cpu.getFPRVal(frb));
        cpu.setFPR(frt, value);
    }

    // --- Memory Access Instructions ---
    // Load Floating Single (opcode 48)
    private void lfs(int frt, int ra, int d) {
        long addr = (ra == 0) ? d : cpu.getGPRVal(ra) + d;
        if (addr % 4 != 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        long bits = cpu.readMemoryWord(addr);
        double value = Double.doubleToLongBits(bits);
        cpu.setFPR(frt, value);
    }

    // Load Floating Single with Update (opcode 49)
    private void lfsu(int frt, int ra, int d) {
        if (ra == 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_INVALID);
            return;
        }
        long addr = cpu.getGPRVal(ra) + d;
        if (addr % 4 != 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        long bits = cpu.readMemoryWord(addr);
        double value = Double.doubleToLongBits(bits);
        cpu.setFPR(frt, value);
        cpu.setGPR(ra, addr);
    }

    // Load Floating Double (opcode 50)
    private void lfd(int frt, int ra, int d) {
        long addr = (ra == 0) ? d : cpu.getGPRVal(ra) + d;
        if (addr % 8 != 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        long high = cpu.readMemoryWord(addr) & 0xFFFFFFFFL;
        long low = cpu.readMemoryWord(addr + 4) & 0xFFFFFFFFL;
        long bits = (high << 32) | low;
        double value = Double.longBitsToDouble(bits);
        cpu.setFPR(frt, value);
    }

    // Load Floating Double with Update (opcode 51)
    private void lfdu(int frt, int ra, int d) {
        if (ra == 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_INVALID);
            return;
        }
        long addr = cpu.getGPRVal(ra) + d;
        if (addr % 8 != 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        long high = cpu.readMemoryWord(addr) & 0xFFFFFFFFL;
        long low = cpu.readMemoryWord(addr + 4) & 0xFFFFFFFFL;
        long bits = (high << 32) | low;
        double value = Double.longBitsToDouble(bits);
        cpu.setFPR(frt, value);
        cpu.setGPR(ra, addr);
    }

    // Store Floating Single (opcode 52)
    private void stfs(int frt, int ra, int d) {
        long addr = (ra == 0) ? d : cpu.getGPRVal(ra) + d;
        if (addr % 4 != 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        float f = (float) cpu.getFPRVal(frt);
        int bits = Float.floatToIntBits(f);
        cpu.writeMemoryWord(addr, bits);
    }

    // Store Floating Single with Update (opcode 53)
    private void stfsu(int frt, int ra, int d) {
        if (ra == 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_INVALID);
            return;
        }
        long addr = cpu.getGPRVal(ra) + d;
        if (addr % 4 != 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        float f = (float) cpu.getFPRVal(frt);
        int bits = Float.floatToIntBits(f);
        cpu.writeMemoryWord(addr, bits);
        cpu.setGPR(ra, addr);
    }

    // Store Floating Double (opcode 54)
    private void stfd(int frt, int ra, int d) {
        long addr = (ra == 0) ? d : cpu.getGPRVal(ra) + d;
        if (addr % 8 != 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        long bits = Double.doubleToLongBits(cpu.getFPRVal(frt));
        cpu.writeMemoryWord(addr, (int) (bits >>> 32));
        cpu.writeMemoryWord(addr + 4, (int) (bits & 0xFFFFFFFF));
    }

    // Store Floating Double with Update (opcode 55)
    private void stfdu(int frt, int ra, int d) {
        if (ra == 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_INVALID);
            return;
        }
        long addr = cpu.getGPRVal(ra) + d;
        if (addr % 8 != 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        long bits = Double.doubleToLongBits(cpu.getFPRVal(frt));
        cpu.writeMemoryWord(addr, (int) (bits >>> 32));
        cpu.writeMemoryWord(addr + 4, (int) (bits & 0xFFFFFFFF));
        cpu.setGPR(ra, addr);
    }

    // Load Floating Double Pair (opcode 56, Xenon-specific)
    private void lfdp(int frt, int ra, int d) {
        if (frt % 2 != 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_INVALID);
            return;
        }
        long addr = (ra == 0) ? d : cpu.getGPRVal(ra) + d;
        if (addr % 8 != 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        // Load first double (FRT)
        long high = cpu.readMemoryWord(addr) & 0xFFFFFFFFL;
        long low = cpu.readMemoryWord(addr + 4) & 0xFFFFFFFFL;
        long bits = (high << 32) | low;
        cpu.setFPR(frt, Double.longBitsToDouble(bits));
        // Load second double (FRT+1)
        high = cpu.readMemoryWord(addr + 8) & 0xFFFFFFFFL;
        low = cpu.readMemoryWord(addr + 12) & 0xFFFFFFFFL;
        bits = (high << 32) | low;
        cpu.setFPR(frt + 1, Double.longBitsToDouble(bits));
    }

    // Load Floating Double Pair Indexed (opcode 57, Xenon-specific)
    private void lfdpx(int frt, int ra, int rb) {
        if (frt % 2 != 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_INVALID);
            return;
        }
        long addr = (ra == 0) ? cpu.getGPRVal(rb) : cpu.getGPRVal(ra) + cpu.getGPRVal(rb);
        if (addr % 8 != 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        // Load first double (FRT)
        long high = cpu.readMemoryWord(addr) & 0xFFFFFFFFL;
        long low = cpu.readMemoryWord(addr + 4) & 0xFFFFFFFFL;
        long bits = (high << 32) | low;
        cpu.setFPR(frt, Double.longBitsToDouble(bits));
        // Load second double (FRT+1)
        high = cpu.readMemoryWord(addr + 8) & 0xFFFFFFFFL;
        low = cpu.readMemoryWord(addr + 12) & 0xFFFFFFFFL;
        bits = (high << 32) | low;
        cpu.setFPR(frt + 1, Double.longBitsToDouble(bits));
    }

    // Store Floating Double Pair (opcode 60, Xenon-specific)
    private void stfdp(int frt, int ra, int d) {
        if (frt % 2 != 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_INVALID);
            return;
        }
        long addr = (ra == 0) ? d : cpu.getGPRVal(ra) + d;
        if (addr % 8 != 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        // Store first double (FRT)
        long bits = Double.doubleToLongBits(cpu.getFPRVal(frt));
        cpu.writeMemoryWord(addr, (int) (bits >>> 32));
        cpu.writeMemoryWord(addr + 4, (int) (bits & 0xFFFFFFFF));
        // Store second double (FRT+1)
        bits = Double.doubleToLongBits(cpu.getFPRVal(frt + 1));
        cpu.writeMemoryWord(addr + 8, (int) (bits >>> 32));
        cpu.writeMemoryWord(addr + 12, (int) (bits & 0xFFFFFFFF));
    }

    // Store Floating Double Pair Indexed (opcode 61, Xenon-specific)
    private void stfdpx(int frt, int ra, int rb) {
        if (frt % 2 != 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_INVALID);
            return;
        }
        long addr = (ra == 0) ? cpu.getGPRVal(rb) : cpu.getGPRVal(ra) + cpu.getGPRVal(rb);
        if (addr % 8 != 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        // Store first double (FRT)
        long bits = Double.doubleToLongBits(cpu.getFPRVal(frt));
        cpu.writeMemoryWord(addr, (int) (bits >>> 32));
        cpu.writeMemoryWord(addr + 4, (int) (bits & 0xFFFFFFFF));
        // Store second double (FRT+1)
        bits = Double.doubleToLongBits(cpu.getFPRVal(frt + 1));
        cpu.writeMemoryWord(addr + 8, (int) (bits >>> 32));
        cpu.writeMemoryWord(addr + 12, (int) (bits & 0xFFFFFFFF));
    }

    // Store Floating-Point Integer Word Indexed (opcode 63, xo 983)
    private void stfiwx(int frt, int ra, int rb) {
        long addr = (ra == 0) ? cpu.getGPRVal(rb) : cpu.getGPRVal(ra) + cpu.getGPRVal(rb);
        if (addr % 4 != 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        long bits = Double.doubleToLongBits(cpu.getFPRVal(frt));
        cpu.writeMemoryWord(addr, (int) (bits & 0xFFFFFFFF));
    }

    // Store Floating Single Indexed (opcode 63, xo 663)
    private void stfsx(int frt, int ra, int rb) {
        long addr = (ra == 0) ? cpu.getGPRVal(rb) : cpu.getGPRVal(ra) + cpu.getGPRVal(rb);
        if (addr % 4 != 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        float f = (float) cpu.getFPRVal(frt);
        int bits = Float.floatToIntBits(f);
        cpu.writeMemoryWord(addr, bits);
    }

    // Store Floating Single Indexed with Update (opcode 63, xo 695)
    private void stfsux(int frt, int ra, int rb) {
        if (ra == 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_INVALID);
            return;
        }
        long addr = cpu.getGPRVal(ra) + cpu.getGPRVal(rb);
        if (addr % 4 != 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        float f = (float) cpu.getFPRVal(frt);
        int bits = Float.floatToIntBits(f);
        cpu.writeMemoryWord(addr, bits);
        cpu.setGPR(ra, addr);
    }

    // Store Floating Double Indexed (opcode 63, xo 727)
    private void stfdx(int frt, int ra, int rb) {
        long addr = (ra == 0) ? cpu.getGPRVal(rb) : cpu.getGPRVal(ra) + cpu.getGPRVal(rb);
        if (addr % 8 != 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        long bits = Double.doubleToLongBits(cpu.getFPRVal(frt));
        cpu.writeMemoryWord(addr, (int) (bits >>> 32));
        cpu.writeMemoryWord(addr + 4, (int) (bits & 0xFFFFFFFF));
    }

    // Store Floating Double Indexed with Update (opcode 63, xo 759)
    private void stfdux(int frt, int ra, int rb) {
        if (ra == 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_INVALID);
            return;
        }
        long addr = cpu.getGPRVal(ra) + cpu.getGPRVal(rb);
        if (addr % 8 != 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        long bits = Double.doubleToLongBits(cpu.getFPRVal(frt));
        cpu.writeMemoryWord(addr, (int) (bits >>> 32));
        cpu.writeMemoryWord(addr + 4, (int) (bits & 0xFFFFFFFF));
        cpu.setGPR(ra, addr);
    }

    // Load Floating Double Indexed (opcode 63, xo 887)
    private void lfdx(int frt, int ra, int rb) {
        long addr = (ra == 0) ? cpu.getGPRVal(rb) : cpu.getGPRVal(ra) + cpu.getGPRVal(rb);
        if (addr % 8 != 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        long high = cpu.readMemoryWord(addr) & 0xFFFFFFFFL;
        long low = cpu.readMemoryWord(addr + 4) & 0xFFFFFFFFL;
        long bits = (high << 32) | low;
        double value = Double.longBitsToDouble(bits);
        cpu.setFPR(frt, value);
    }

    // --- Helper Methods ---
    private void updateFPSCRSingle(float result) {
        if (Float.isNaN(result)) {
            cpu.setFPSCRBit(CPU.FPSCR_VX);
            cpu.setFPSCRBit(CPU.FPSCR_FX);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_FP);
        } else if (Float.isInfinite(result)) {
            cpu.setFPSCRBit(CPU.FPSCR_OX);
            cpu.setFPSCRBit(CPU.FPSCR_FX);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_FP);
        } else if (Math.abs(result) > Float.MAX_VALUE) {
            cpu.setFPSCRBit(CPU.FPSCR_OX);
            cpu.setFPSCRBit(CPU.FPSCR_FX);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_FP);
        } else if (result != 0 && Math.abs(result) < Float.MIN_NORMAL) {
            cpu.setFPSCRBit(CPU.FPSCR_UX);
            cpu.setFPSCRBit(CPU.FPSCR_FX);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_FP);
        } else if ((float) ((double) result) != result) {
            cpu.setFPSCRBit(CPU.FPSCR_XX);
            cpu.setFPSCRBit(CPU.FPSCR_FX);
        }
    }

    private void updateFPSCRDouble(double result) {
        if (Double.isNaN(result)) {
            cpu.setFPSCRBit(CPU.FPSCR_VX);
            cpu.setFPSCRBit(CPU.FPSCR_FX);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_FP);
        } else if (Double.isInfinite(result)) {
            cpu.setFPSCRBit(CPU.FPSCR_OX);
            cpu.setFPSCRBit(CPU.FPSCR_FX);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_FP);
        } else if (Math.abs(result) > Double.MAX_VALUE) {
            cpu.setFPSCRBit(CPU.FPSCR_OX);
            cpu.setFPSCRBit(CPU.FPSCR_FX);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_FP);
        } else if (result != 0 && Math.abs(result) < Double.MIN_NORMAL) {
            cpu.setFPSCRBit(CPU.FPSCR_UX);
            cpu.setFPSCRBit(CPU.FPSCR_FX);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_FP);
        } else if ((double) ((float) result) != result) {
            cpu.setFPSCRBit(CPU.FPSCR_XX);
            cpu.setFPSCRBit(CPU.FPSCR_FX);
        }
    }
}
