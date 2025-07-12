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
 * Handles PowerPC paired-single (graphics) instructions for Xenon CPU emulation.
 *
 * @author Slam
 */
public class GraphicsOperations {
    private final CPU cpu;

    // FPSCR bit masks
    private static final long FPSCR_VXSNAN = 0x00001000; // Bit 19: Invalid operation (SNaN)
    private static final long FPSCR_VXISI = 0x00000800;  // Bit 20: Invalid operation (Inf - Inf)
    private static final long FPSCR_VXSQRT = 0x00000400; // Bit 21: Invalid square root
    private static final long FPSCR_FR = 0x00000004;     // Bit 29: Fraction rounded
    private static final long FPSCR_FI = 0x00000002;     // Bit 30: Fraction inexact

    public GraphicsOperations(CPU cpu) {
        this.cpu = cpu;
    }

    /**
     * Executes paired-single graphics instructions (opcode 4).
     *
     * @param fields Instruction fields from DecodingInstr.
     */
    public void execute(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("X") && !fields.format().equals("A")) {
            throw new IllegalArgumentException("Invalid format for graphics instruction: " + fields.format());
        }
        switch (fields.xo()) {
            case 0: // ps_cmpu0
                ps_cmpu0(fields.rt(), fields.ra(), fields.rb(), fields.rc());
                break;
            case 10: // ps_sum0
                ps_sum0(fields.rt(), fields.ra(), fields.rb(), fields.rc(), fields.rc());
                break;
            case 12: // ps_sum1
                ps_sum1(fields.rt(), fields.ra(), fields.rb(), fields.rc(), fields.rc());
                break;
            case 14: // ps_muls0
                ps_muls0(fields.rt(), fields.ra(), fields.rb(), fields.rc());
                break;
            case 15: // ps_muls1
                ps_muls1(fields.rt(), fields.ra(), fields.rb(), fields.rc());
                break;
            case 18: // ps_div
                ps_div(fields.rt(), fields.ra(), fields.rb(), fields.rc());
                break;
            case 20: // ps_sub
                ps_sub(fields.rt(), fields.ra(), fields.rb(), fields.rc());
                break;
            case 21: // ps_add
                ps_add(fields.rt(), fields.ra(), fields.rb(), fields.rc());
                break;
            case 23: // ps_sel
                ps_sel(fields.rt(), fields.ra(), fields.rb(), fields.rc());
                break;
            case 25: // ps_mul
                ps_mul(fields.rt(), fields.ra(), fields.rb(), fields.rc());
                break;
            case 28: // ps_msub
                ps_msub(fields.rt(), fields.ra(), fields.rb(), fields.rc(), fields.rc());
                break;
            case 29: // ps_madd
                ps_madd(fields.rt(), fields.ra(), fields.rb(), fields.rc(), fields.rc());
                break;
            case 30: // ps_nmsub
                ps_nmsub(fields.rt(), fields.ra(), fields.rb(), fields.rc(), fields.rc());
                break;
            case 31: // ps_nmadd
                ps_nmadd(fields.rt(), fields.ra(), fields.rb(), fields.rc(), fields.rc());
                break;
            case 32: // ps_cmpo0
                ps_cmpo0(fields.rt(), fields.ra(), fields.rb(), fields.rc());
                break;
            case 40: // ps_neg
                ps_neg(fields.rt(), fields.rb());
                break;
            case 64: // ps_cmpu1
                ps_cmpu1(fields.rt(), fields.ra(), fields.rb(), fields.rc());
                break;
            case 72: // ps_mr
                ps_mr(fields.rt(), fields.rb());
                break;
            case 96: // ps_cmpo1
                ps_cmpo1(fields.rt(), fields.ra(), fields.rb(), fields.rc());
                break;
            case 136: // ps_nabs
                ps_nabs(fields.rt(), fields.rb());
                break;
            case 168: // ps_res
                ps_res(fields.rt(), fields.rb());
                break;
            case 200: // ps_rsqrte
                ps_rsqrte(fields.rt(), fields.rb());
                break;
            case 528: // ps_merge00
                ps_merge00(fields.rt(), fields.ra(), fields.rb());
                break;
            case 560: // ps_merge01
                ps_merge01(fields.rt(), fields.ra(), fields.rb());
                break;
            case 592: // ps_merge10
                ps_merge10(fields.rt(), fields.ra(), fields.rb());
                break;
            case 593: // ps_merge11
                ps_merge11(fields.rt(), fields.ra(), fields.rb());
                break;
            default:
                throw new UnsupportedOperationException("Unsupported graphics XO: " + fields.xo());
        }
    }

    // --- Paired-Single Arithmetic Instructions ---

    public void ps_add(int frd, int fra, int frb, int rc) {
        long fpscr = cpu.getFPSCR();
        float[] a = unpackPairedSingle(cpu.getFPRVal(fra));
        float[] b = unpackPairedSingle(cpu.getFPRVal(frb));
        float[] result = new float[2];
        boolean inexact = false;

        for (int i = 0; i < 2; i++) {
            if (Float.isNaN(a[i]) || Float.isNaN(b[i])) {
                result[i] = Float.NaN;
                fpscr |= FPSCR_VXSNAN;
            } else if (Float.isInfinite(a[i]) && Float.isInfinite(b[i]) && a[i] != b[i]) {
                result[i] = Float.NaN;
                fpscr |= FPSCR_VXISI;
            } else {
                result[i] = a[i] + b[i];
                if (Float.isInfinite(result[i])) {
                    fpscr |= FPSCR_VXISI;
                } else if (Math.abs(result[i]) > Float.MAX_VALUE) {
                    inexact = true;
                }
            }
        }

        updateFPSCR(fpscr, inexact);
        cpu.setFPR(frd, packPairedSingle(result));
        if (rc != 0) updateCR1(result[0], result[1]);
    }

    public void ps_sub(int frd, int fra, int frb, int rc) {
        long fpscr = cpu.getFPSCR();
        float[] a = unpackPairedSingle(cpu.getFPRVal(fra));
        float[] b = unpackPairedSingle(cpu.getFPRVal(frb));
        float[] result = new float[2];
        boolean inexact = false;

        for (int i = 0; i < 2; i++) {
            if (Float.isNaN(a[i]) || Float.isNaN(b[i])) {
                result[i] = Float.NaN;
                fpscr |= FPSCR_VXSNAN;
            } else if (Float.isInfinite(a[i]) && Float.isInfinite(b[i]) && a[i] == b[i]) {
                result[i] = Float.NaN;
                fpscr |= FPSCR_VXISI;
            } else {
                result[i] = a[i] - b[i];
                if (Float.isInfinite(result[i])) {
                    fpscr |= FPSCR_VXISI;
                } else if (Math.abs(result[i]) > Float.MAX_VALUE) {
                    inexact = true;
                }
            }
        }

        updateFPSCR(fpscr, inexact);
        cpu.setFPR(frd, packPairedSingle(result));
        if (rc != 0) updateCR1(result[0], result[1]);
    }

    public void ps_mul(int frd, int fra, int frb, int rc) {
        long fpscr = cpu.getFPSCR();
        float[] a = unpackPairedSingle(cpu.getFPRVal(fra));
        float[] b = unpackPairedSingle(cpu.getFPRVal(frb));
        float[] result = new float[2];
        boolean inexact = false;

        for (int i = 0; i < 2; i++) {
            if (Float.isNaN(a[i]) || Float.isNaN(b[i])) {
                result[i] = Float.NaN;
                fpscr |= FPSCR_VXSNAN;
            } else if ((a[i] == 0.0f && Float.isInfinite(b[i])) || (b[i] == 0.0f && Float.isInfinite(a[i]))) {
                result[i] = Float.NaN;
                fpscr |= FPSCR_VXISI;
            } else {
                result[i] = a[i] * b[i];
                if (Float.isInfinite(result[i])) {
                    fpscr |= FPSCR_VXISI;
                } else if (Math.abs(result[i]) > Float.MAX_VALUE) {
                    inexact = true;
                }
            }
        }

        updateFPSCR(fpscr, inexact);
        cpu.setFPR(frd, packPairedSingle(result));
        if (rc != 0) updateCR1(result[0], result[1]);
    }

    public void ps_div(int frd, int fra, int frb, int rc) {
        long fpscr = cpu.getFPSCR();
        float[] a = unpackPairedSingle(cpu.getFPRVal(fra));
        float[] b = unpackPairedSingle(cpu.getFPRVal(frb));
        float[] result = new float[2];
        boolean inexact = false;

        for (int i = 0; i < 2; i++) {
            if (Float.isNaN(a[i]) || Float.isNaN(b[i])) {
                result[i] = Float.NaN;
                fpscr |= FPSCR_VXSNAN;
            } else if (a[i] == 0.0f && b[i] == 0.0f) {
                result[i] = Float.NaN;
                fpscr |= FPSCR_VXISI;
            } else if (b[i] == 0.0f) {
                result[i] = Float.POSITIVE_INFINITY;
                fpscr |= FPSCR_VXISI;
            } else {
                result[i] = a[i] / b[i];
                if (Float.isInfinite(result[i])) {
                    fpscr |= FPSCR_VXISI;
                } else if (Math.abs(result[i]) > Float.MAX_VALUE) {
                    inexact = true;
                }
            }
        }

        updateFPSCR(fpscr, inexact);
        cpu.setFPR(frd, packPairedSingle(result));
        if (rc != 0) updateCR1(result[0], result[1]);
    }

    public void ps_madd(int frd, int fra, int frb, int frc, int rc) {
        long fpscr = cpu.getFPSCR();
        float[] a = unpackPairedSingle(cpu.getFPRVal(fra));
        float[] b = unpackPairedSingle(cpu.getFPRVal(frb));
        float[] c = unpackPairedSingle(cpu.getFPRVal(frc));
        float[] result = new float[2];
        boolean inexact = false;

        for (int i = 0; i < 2; i++) {
            if (Float.isNaN(a[i]) || Float.isNaN(b[i]) || Float.isNaN(c[i])) {
                result[i] = Float.NaN;
                fpscr |= FPSCR_VXSNAN;
            } else if ((a[i] == 0.0f && Float.isInfinite(b[i])) || (b[i] == 0.0f && Float.isInfinite(a[i]))) {
                result[i] = Float.NaN;
                fpscr |= FPSCR_VXISI;
            } else {
                result[i] = a[i] * c[i] + b[i];
                if (Float.isInfinite(result[i])) {
                    fpscr |= FPSCR_VXISI;
                } else if (Math.abs(result[i]) > Float.MAX_VALUE) {
                    inexact = true;
                }
            }
        }

        updateFPSCR(fpscr, inexact);
        cpu.setFPR(frd, packPairedSingle(result));
        if (rc != 0) updateCR1(result[0], result[1]);
    }

    public void ps_msub(int frd, int fra, int frb, int frc, int rc) {
        long fpscr = cpu.getFPSCR();
        float[] a = unpackPairedSingle(cpu.getFPRVal(fra));
        float[] b = unpackPairedSingle(cpu.getFPRVal(frb));
        float[] c = unpackPairedSingle(cpu.getFPRVal(frc));
        float[] result = new float[2];
        boolean inexact = false;

        for (int i = 0; i < 2; i++) {
            if (Float.isNaN(a[i]) || Float.isNaN(b[i]) || Float.isNaN(c[i])) {
                result[i] = Float.NaN;
                fpscr |= FPSCR_VXSNAN;
            } else if ((a[i] == 0.0f && Float.isInfinite(c[i])) || (c[i] == 0.0f && Float.isInfinite(a[i]))) {
                result[i] = Float.NaN;
                fpscr |= FPSCR_VXISI;
            } else {
                result[i] = a[i] * c[i] - b[i];
                if (Float.isInfinite(result[i])) {
                    fpscr |= FPSCR_VXISI;
                } else if (Math.abs(result[i]) > Float.MAX_VALUE) {
                    inexact = true;
                }
            }
        }

        updateFPSCR(fpscr, inexact);
        cpu.setFPR(frd, packPairedSingle(result));
        if (rc != 0) updateCR1(result[0], result[1]);
    }

    public void ps_nmadd(int frd, int fra, int frb, int frc, int rc) {
        long fpscr = cpu.getFPSCR();
        float[] a = unpackPairedSingle(cpu.getFPRVal(fra));
        float[] b = unpackPairedSingle(cpu.getFPRVal(frb));
        float[] c = unpackPairedSingle(cpu.getFPRVal(frc));
        float[] result = new float[2];
        boolean inexact = false;

        for (int i = 0; i < 2; i++) {
            if (Float.isNaN(a[i]) || Float.isNaN(b[i]) || Float.isNaN(c[i])) {
                result[i] = Float.NaN;
                fpscr |= FPSCR_VXSNAN;
            } else if ((a[i] == 0.0f && Float.isInfinite(c[i])) || (c[i] == 0.0f && Float.isInfinite(a[i]))) {
                result[i] = Float.NaN;
                fpscr |= FPSCR_VXISI;
            } else {
                result[i] = -(a[i] * c[i] + b[i]);
                if (Float.isInfinite(result[i])) {
                    fpscr |= FPSCR_VXISI;
                } else if (Math.abs(result[i]) > Float.MAX_VALUE) {
                    inexact = true;
                }
            }
        }

        updateFPSCR(fpscr, inexact);
        cpu.setFPR(frd, packPairedSingle(result));
        if (rc != 0) updateCR1(result[0], result[1]);
    }

    public void ps_nmsub(int frd, int fra, int frb, int frc, int rc) {
        long fpscr = cpu.getFPSCR();
        float[] a = unpackPairedSingle(cpu.getFPRVal(fra));
        float[] b = unpackPairedSingle(cpu.getFPRVal(frb));
        float[] c = unpackPairedSingle(cpu.getFPRVal(frc));
        float[] result = new float[2];
        boolean inexact = false;

        for (int i = 0; i < 2; i++) {
            if (Float.isNaN(a[i]) || Float.isNaN(b[i]) || Float.isNaN(c[i])) {
                result[i] = Float.NaN;
                fpscr |= FPSCR_VXSNAN;
            } else if ((a[i] == 0.0f && Float.isInfinite(c[i])) || (c[i] == 0.0f && Float.isInfinite(a[i]))) {
                result[i] = Float.NaN;
                fpscr |= FPSCR_VXISI;
            } else {
                result[i] = -(a[i] * c[i] - b[i]);
                if (Float.isInfinite(result[i])) {
                    fpscr |= FPSCR_VXISI;
                } else if (Math.abs(result[i]) > Float.MAX_VALUE) {
                    inexact = true;
                }
            }
        }

        updateFPSCR(fpscr, inexact);
        cpu.setFPR(frd, packPairedSingle(result));
        if (rc != 0) updateCR1(result[0], result[1]);
    }

    public void ps_sum0(int frd, int fra, int frb, int frc, int rc) {
        long fpscr = cpu.getFPSCR();
        float[] a = unpackPairedSingle(cpu.getFPRVal(fra));
        float[] b = unpackPairedSingle(cpu.getFPRVal(frb));
        float[] c = unpackPairedSingle(cpu.getFPRVal(frc));
        float[] result = new float[2];
        boolean inexact = false;

        // Slot 0: a[0] + b[1]
        if (Float.isNaN(a[0]) || Float.isNaN(b[1])) {
            result[0] = Float.NaN;
            fpscr |= FPSCR_VXSNAN;
        } else if (Float.isInfinite(a[0]) && Float.isInfinite(b[1]) && a[0] != b[1]) {
            result[0] = Float.NaN;
            fpscr |= FPSCR_VXISI;
        } else {
            result[0] = a[0] + b[1];
            if (Float.isInfinite(result[0])) {
                fpscr |= FPSCR_VXISI;
            } else if (Math.abs(result[0]) > Float.MAX_VALUE) {
                inexact = true;
            }
        }

        // Slot 1: c[1]
        result[1] = c[1];
        if (Float.isNaN(c[1])) {
            fpscr |= FPSCR_VXSNAN;
        }

        updateFPSCR(fpscr, inexact);
        cpu.setFPR(frd, packPairedSingle(result));
        if (rc != 0) updateCR1(result[0], result[1]);
    }

    public void ps_sum1(int frd, int fra, int frb, int frc, int rc) {
        long fpscr = cpu.getFPSCR();
        float[] a = unpackPairedSingle(cpu.getFPRVal(fra));
        float[] b = unpackPairedSingle(cpu.getFPRVal(frb));
        float[] c = unpackPairedSingle(cpu.getFPRVal(frc));
        float[] result = new float[2];
        boolean inexact = false;

        // Slot 0: c[0]
        result[0] = c[0];
        if (Float.isNaN(c[0])) {
            fpscr |= FPSCR_VXSNAN;
        }

        // Slot 1: a[0] + b[1]
        if (Float.isNaN(a[0]) || Float.isNaN(b[1])) {
            result[1] = Float.NaN;
            fpscr |= FPSCR_VXSNAN;
        } else if (Float.isInfinite(a[0]) && Float.isInfinite(b[1]) && a[0] != b[1]) {
            result[1] = Float.NaN;
            fpscr |= FPSCR_VXISI;
        } else {
            result[1] = a[0] + b[1];
            if (Float.isInfinite(result[1])) {
                fpscr |= FPSCR_VXISI;
            } else if (Math.abs(result[1]) > Float.MAX_VALUE) {
                inexact = true;
            }
        }

        updateFPSCR(fpscr, inexact);
        cpu.setFPR(frd, packPairedSingle(result));
        if (rc != 0) updateCR1(result[0], result[1]);
    }

    public void ps_muls0(int frd, int fra, int frb, int rc) {
        long fpscr = cpu.getFPSCR();
        float[] a = unpackPairedSingle(cpu.getFPRVal(fra));
        float[] c = unpackPairedSingle(cpu.getFPRVal(frb));
        float[] result = new float[2];
        boolean inexact = false;

        // Slot 0 and 1: a[i] * c[0]
        for (int i = 0; i < 2; i++) {
            if (Float.isNaN(a[i]) || Float.isNaN(c[0])) {
                result[i] = Float.NaN;
                fpscr |= FPSCR_VXSNAN;
            } else if ((a[i] == 0.0f && Float.isInfinite(c[0])) || (c[0] == 0.0f && Float.isInfinite(a[i]))) {
                result[i] = Float.NaN;
                fpscr |= FPSCR_VXISI;
            } else {
                result[i] = a[i] * c[0];
                if (Float.isInfinite(result[i])) {
                    fpscr |= FPSCR_VXISI;
                } else if (Math.abs(result[i]) > Float.MAX_VALUE) {
                    inexact = true;
                }
            }
        }

        updateFPSCR(fpscr, inexact);
        cpu.setFPR(frd, packPairedSingle(result));
        if (rc != 0) updateCR1(result[0], result[1]);
    }

    public void ps_muls1(int frd, int fra, int frb, int rc) {
        long fpscr = cpu.getFPSCR();
        float[] a = unpackPairedSingle(cpu.getFPRVal(fra));
        float[] c = unpackPairedSingle(cpu.getFPRVal(frb));
        float[] result = new float[2];
        boolean inexact = false;

        // Slot 0 and 1: a[i] * c[1]
        for (int i = 0; i < 2; i++) {
            if (Float.isNaN(a[i]) || Float.isNaN(c[1])) {
                result[i] = Float.NaN;
                fpscr |= FPSCR_VXSNAN;
            } else if ((a[i] == 0.0f && Float.isInfinite(c[1])) || (c[1] == 0.0f && Float.isInfinite(a[i]))) {
                result[i] = Float.NaN;
                fpscr |= FPSCR_VXISI;
            } else {
                result[i] = a[i] * c[1];
                if (Float.isInfinite(result[i])) {
                    fpscr |= FPSCR_VXISI;
                } else if (Math.abs(result[i]) > Float.MAX_VALUE) {
                    inexact = true;
                }
            }
        }

        updateFPSCR(fpscr, inexact);
        cpu.setFPR(frd, packPairedSingle(result));
        if (rc != 0) updateCR1(result[0], result[1]);
    }

    public void ps_madds0(int frd, int fra, int frb, int rc) {
        long fpscr = cpu.getFPSCR();
        float[] a = unpackPairedSingle(cpu.getFPRVal(fra));
        float[] b = unpackPairedSingle(cpu.getFPRVal(frb));
        float[] c = unpackPairedSingle(cpu.getFPRVal(frb)); // Note: frb used for c
        float[] result = new float[2];
        boolean inexact = false;

        // Slot 0 and 1: a[i] * c[0] + b[i]
        for (int i = 0; i < 2; i++) {
            if (Float.isNaN(a[i]) || Float.isNaN(b[i]) || Float.isNaN(c[0])) {
                result[i] = Float.NaN;
                fpscr |= FPSCR_VXSNAN;
            } else if ((a[i] == 0.0f && Float.isInfinite(c[0])) || (c[0] == 0.0f && Float.isInfinite(a[i]))) {
                result[i] = Float.NaN;
                fpscr |= FPSCR_VXISI;
            } else {
                result[i] = a[i] * c[0] + b[i];
                if (Float.isInfinite(result[i])) {
                    fpscr |= FPSCR_VXISI;
                } else if (Math.abs(result[i]) > Float.MAX_VALUE) {
                    inexact = true;
                }
            }
        }

        updateFPSCR(fpscr, inexact);
        cpu.setFPR(frd, packPairedSingle(result));
        if (rc != 0) updateCR1(result[0], result[1]);
    }

    public void ps_madds1(int frd, int fra, int frb, int rc) {
        long fpscr = cpu.getFPSCR();
        float[] a = unpackPairedSingle(cpu.getFPRVal(fra));
        float[] b = unpackPairedSingle(cpu.getFPRVal(frb));
        float[] c = unpackPairedSingle(cpu.getFPRVal(frb)); // Note: frb used for c
        float[] result = new float[2];
        boolean inexact = false;

        // Slot 0 and 1: a[i] * c[1] + b[i]
        for (int i = 0; i < 2; i++) {
            if (Float.isNaN(a[i]) || Float.isNaN(b[i]) || Float.isNaN(c[1])) {
                result[i] = Float.NaN;
                fpscr |= FPSCR_VXSNAN;
            } else if ((a[i] == 0.0f && Float.isInfinite(c[1])) || (c[1] == 0.0f && Float.isInfinite(a[i]))) {
                result[i] = Float.NaN;
                fpscr |= FPSCR_VXISI;
            } else {
                result[i] = a[i] * c[1] + b[i];
                if (Float.isInfinite(result[i])) {
                    fpscr |= FPSCR_VXISI;
                } else if (Math.abs(result[i]) > Float.MAX_VALUE) {
                    inexact = true;
                }
            }
        }

        updateFPSCR(fpscr, inexact);
        cpu.setFPR(frd, packPairedSingle(result));
        if (rc != 0) updateCR1(result[0], result[1]);
    }

    // --- Paired-Single Reciprocal Estimates ---

    public void ps_res(int frd, int frb) {
        long fpscr = cpu.getFPSCR();
        float[] b = unpackPairedSingle(cpu.getFPRVal(frb));
        float[] result = new float[2];
        boolean inexact = false;

        for (int i = 0; i < 2; i++) {
            if (Float.isNaN(b[i])) {
                result[i] = Float.NaN;
                fpscr |= FPSCR_VXSNAN;
            } else if (b[i] == 0.0f) {
                result[i] = Float.POSITIVE_INFINITY;
                fpscr |= FPSCR_VXISI;
            } else {
                result[i] = 1.0f / b[i]; // Approximate reciprocal
                inexact = true; // Always inexact
            }
        }

        updateFPSCR(fpscr, inexact);
        cpu.setFPR(frd, packPairedSingle(result));
    }

    public void ps_rsqrte(int frd, int frb) {
        long fpscr = cpu.getFPSCR();
        float[] b = unpackPairedSingle(cpu.getFPRVal(frb));
        float[] result = new float[2];
        boolean inexact = false;

        for (int i = 0; i < 2; i++) {
            if (Float.isNaN(b[i])) {
                result[i] = Float.NaN;
                fpscr |= FPSCR_VXSNAN;
            } else if (b[i] < 0.0f) {
                result[i] = Float.NaN;
                fpscr |= FPSCR_VXSQRT;
            } else if (b[i] == 0.0f) {
                result[i] = Float.POSITIVE_INFINITY;
                fpscr |= FPSCR_VXISI;
            } else {
                result[i] = (float) (1.0 / Math.sqrt(b[i])); // Approximate reciprocal square root
                inexact = true; // Always inexact
            }
        }

        updateFPSCR(fpscr, inexact);
        cpu.setFPR(frd, packPairedSingle(result));
    }

    // --- Paired-Single Comparison Instructions ---

    public void ps_cmpu0(int frd, int fra, int frb, int rc) {
        float[] a = unpackPairedSingle(cpu.getFPRVal(fra));
        float[] b = unpackPairedSingle(cpu.getFPRVal(frb));
        int crf = frd; // CR field (0-7)
        int result;

        if (Float.isNaN(a[0]) || Float.isNaN(b[0])) {
            result = 0x1; // CR[LT]=0, CR[GT]=0, CR[EQ]=0, CR[UN]=1
        } else if (a[0] < b[0]) {
            result = 0x8; // CR[LT]=1
        } else if (a[0] > b[0]) {
            result = 0x4; // CR[GT]=1
        } else {
            result = 0x2; // CR[EQ]=1
        }

        cpu.updateCRField(crf, result);
        if (rc != 0) updateCR1(a[0], b[0]);
    }

    public void ps_cmpo0(int frd, int fra, int frb, int rc) {
        float[] a = unpackPairedSingle(cpu.getFPRVal(fra));
        float[] b = unpackPairedSingle(cpu.getFPRVal(frb));
        int crf = frd;
        long fpscr = cpu.getFPSCR();
        int result;

        if (Float.isNaN(a[0]) || Float.isNaN(b[0])) {
            result = 0x1;
            fpscr |= FPSCR_VXSNAN;
        } else if (a[0] < b[0]) {
            result = 0x8;
        } else if (a[0] > b[0]) {
            result = 0x4;
        } else {
            result = 0x2;
        }

        cpu.updateCRField(crf, result);
        updateFPSCR(fpscr, false);
        if (rc != 0) updateCR1(a[0], b[0]);
    }

    public void ps_cmpu1(int frd, int fra, int frb, int rc) {
        float[] a = unpackPairedSingle(cpu.getFPRVal(fra));
        float[] b = unpackPairedSingle(cpu.getFPRVal(frb));
        int crf = frd;
        int result;

        if (Float.isNaN(a[1]) || Float.isNaN(b[1])) {
            result = 0x1;
        } else if (a[1] < b[1]) {
            result = 0x8;
        } else if (a[1] > b[1]) {
            result = 0x4;
        } else {
            result = 0x2;
        }

        cpu.updateCRField(crf, result);
        if (rc != 0) updateCR1(a[1], b[1]);
    }

    public void ps_cmpo1(int frd, int fra, int frb, int rc) {
        float[] a = unpackPairedSingle(cpu.getFPRVal(fra));
        float[] b = unpackPairedSingle(cpu.getFPRVal(frb));
        int crf = frd;
        long fpscr = cpu.getFPSCR();
        int result;

        if (Float.isNaN(a[1]) || Float.isNaN(b[1])) {
            result = 0x1;
            fpscr |= FPSCR_VXSNAN;
        } else if (a[1] < b[1]) {
            result = 0x8;
        } else if (a[1] > b[1]) {
            result = 0x4;
        } else {
            result = 0x2;
        }

        cpu.updateCRField(crf, result);
        updateFPSCR(fpscr, false);
        if (rc != 0) updateCR1(a[1], b[1]);
    }

    // --- Paired-Single Selection and Merge ---

    public void ps_sel(int frd, int fra, int frb, int frc) {
        float[] a = unpackPairedSingle(cpu.getFPRVal(fra));
        float[] b = unpackPairedSingle(cpu.getFPRVal(frb));
        float[] c = unpackPairedSingle(cpu.getFPRVal(frc));
        float[] result = new float[2];

        for (int i = 0; i < 2; i++) {
            result[i] = (a[i] >= 0.0f) ? b[i] : c[i];
        }

        cpu.setFPR(frd, packPairedSingle(result));
    }

    public void ps_neg(int frd, int frb) {
        float[] b = unpackPairedSingle(cpu.getFPRVal(frb));
        float[] result = new float[2];

        for (int i = 0; i < 2; i++) {
            result[i] = -b[i];
        }

        cpu.setFPR(frd, packPairedSingle(result));
    }

    public void ps_mr(int frd, int frb) {
        float[] b = unpackPairedSingle(cpu.getFPRVal(frb));
        cpu.setFPR(frd, packPairedSingle(b));
    }

    public void ps_nabs(int frd, int frb) {
        float[] b = unpackPairedSingle(cpu.getFPRVal(frb));
        float[] result = new float[2];

        for (int i = 0; i < 2; i++) {
            result[i] = -Math.abs(b[i]);
        }

        cpu.setFPR(frd, packPairedSingle(result));
    }

    public void ps_merge00(int frd, int fra, int frb) {
        float[] a = unpackPairedSingle(cpu.getFPRVal(fra));
        float[] b = unpackPairedSingle(cpu.getFPRVal(frb));
        float[] result = new float[2];

        result[0] = a[0];
        result[1] = b[0];

        cpu.setFPR(frd, packPairedSingle(result));
    }

    public void ps_merge01(int frd, int fra, int frb) {
        float[] a = unpackPairedSingle(cpu.getFPRVal(fra));
        float[] b = unpackPairedSingle(cpu.getFPRVal(frb));
        float[] result = new float[2];

        result[0] = a[0];
        result[1] = b[1];

        cpu.setFPR(frd, packPairedSingle(result));
    }

    public void ps_merge10(int frd, int fra, int frb) {
        float[] a = unpackPairedSingle(cpu.getFPRVal(fra));
        float[] b = unpackPairedSingle(cpu.getFPRVal(frb));
        float[] result = new float[2];

        result[0] = a[1];
        result[1] = b[0];

        cpu.setFPR(frd, packPairedSingle(result));
    }

    public void ps_merge11(int frd, int fra, int frb) {
        float[] a = unpackPairedSingle(cpu.getFPRVal(fra));
        float[] b = unpackPairedSingle(cpu.getFPRVal(frb));
        float[] result = new float[2];

        result[0] = a[1];
        result[1] = b[1];

        cpu.setFPR(frd, packPairedSingle(result));
    }

    // --- Helper Methods ---

    private float[] unpackPairedSingle(double value) {
        long bits = Double.doubleToRawLongBits(value);
        float high = Float.intBitsToFloat((int) (bits >>> 32));
        float low = Float.intBitsToFloat((int) (bits & 0xFFFFFFFFL));
        return new float[]{high, low};
    }

    private double packPairedSingle(float[] values) {
        int highBits = Float.floatToRawIntBits(values[0]);
        int lowBits = Float.floatToRawIntBits(values[1]);
        return Double.longBitsToDouble(((long) highBits << 32) | (lowBits & 0xFFFFFFFFL));
    }

    private void updateFPSCR(long fpscr, boolean inexact) {
        if (inexact) {
            fpscr |= FPSCR_FI;
            fpscr |= FPSCR_FR; // Simplified: assume rounding occurs
        }
        cpu.setFPSCR((int) fpscr);
    }

    private void updateCR1(float high, float low) {
        int result;
        if (Float.isNaN(high) || Float.isNaN(low)) {
            result = 0x1; // CR1[UN]=1
        } else if (high < 0.0f || low < 0.0f) {
            result = 0x8; // CR1[LT]=1
        } else if (high > 0.0f || low > 0.0f) {
            result = 0x4; // CR1[GT]=1
        } else {
            result = 0x2; // CR1[EQ]=1
        }
        cpu.updateCRField(1, result);
    }
}