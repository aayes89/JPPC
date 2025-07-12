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
package pcp.utils;

import java.util.logging.Logger;

/**
 * Decodes PowerPC instructions into their constituent fields. Supports I, B, D,
 * X, XO, M, DS, A, MD, MDS, SC, and XL instruction formats.
 *
 * @author Slam
 */
public class DecodingInstr {

    private static final Logger LOGGER = Logger.getLogger(DecodingInstr.class.getName());

    // Instruction field masks
    private static final int OPCODE_MASK = 0xFC000000; // Bits 0–5 (opcode)
    private static final int RT_MASK = 0x03E00000;     // Bits 6–10 (RT/RS/FRT/FRS/BF/TO)
    private static final int RA_MASK = 0x001F0000;     // Bits 11–15 (RA/FRA)
    private static final int RB_MASK = 0x0000F800;     // Bits 16–20 (RB/FRB)
    private static final int XO_MASK = 0x000007FE;     // Bits 21–30 (XO for X/XO/XL)
    private static final int RC_MASK = 0x00000001;     // Bit 31 (Rc)
    private static final int BD_MASK = 0x0000FFFC;     // Bits 16–29 (BD)
    private static final int LI_MASK = 0x03FFFFFC;     // Bits 6–29 (LI)
    private static final int AA_MASK = 0x00000002;     // Bit 30 (AA)
    private static final int LK_MASK = 0x00000001;     // Bit 31 (LK)
    private static final int SI_MASK = 0x0000FFFF;     // Bits 16–31 (SI/UI)
    private static final int DS_MASK = 0x0000FFFC;     // Bits 16–29 (DS)
    private static final int SH_MASK = 0x0000F800;     // Bits 16–20 (SH for M-form)
    private static final int MB_MASK = 0x000007C0;     // Bits 21–25 (MB for M-form)
    private static final int ME_MASK = 0x0000003E;     // Bits 26–30 (ME for M-form)
    private static final int FRC_MASK = 0x000007C0;    // Bits 21–25 (FRC for A-form)

    // Record to hold decoded instruction fields
    public record InstructionFields(
            int instruction,
            int opcode, // Primary opcode (bits 0–5)
            int rt, // RT/RS/FRT/FRS/BF/TO (bits 6–10)
            int ra, // RA/FRA/BI (bits 11–15)
            int rb, // RB/FRB (bits 16–20)
            int xo, // Extended opcode (bits 21–30 for X/XO/XL, 27–30 for MD/MDS)
            int rc, // Rc bit (bit 31)
            int bd, // Branch displacement (bits 16–29, B-form)
            int li, // Long immediate (bits 6–29, I-form)
            int aa, // Absolute address bit (bit 30)
            int lk, // Link bit (bit 31)
            int si, // Signed/unsigned immediate (bits 16–31, D/DS-form) or FRC (A-form)
            int sh, // Shift amount (bits 16–20, M-form)
            int mb, // Mask begin (bits 21–25, M-form)
            int me, // Mask end (bits 26–30, M-form)
            String format // Instruction format (I, B, D, X, XO, M, DS, A, MD, MDS, SC, XL)
            ) {

    }

    /**
     * Decodes a PowerPC instruction and returns its fields.
     *
     * @param instruction The 32-bit instruction.
     * @return InstructionFields containing decoded fields.
     * @throws IllegalArgumentException if the opcode is unsupported.
     */
    public static InstructionFields decode(int instruction) {
        int opcode = (instruction & OPCODE_MASK) >>> 26;
        String format = "";
        int rt = 0, ra = 0, rb = 0, xo = 0, rc = 0, bd = 0, li = 0, aa = 0, lk = 0, si = 0;
        int sh = 0, mb = 0, me = 0;

        switch (opcode) {
            case 0: // NOP or reserved
                format = "D";
                rt = (instruction & RT_MASK) >>> 21; // RS
                ra = (instruction & RA_MASK) >>> 16;
                si = instruction & SI_MASK;
                break;
            case 2: // tw (X-form)
                format = "X";
                rt = (instruction & RT_MASK) >>> 21; // TO
                ra = (instruction & RA_MASK) >>> 16;
                rb = (instruction & RB_MASK) >>> 11;
                xo = (instruction & XO_MASK) >>> 1;
                rc = instruction & RC_MASK;
                break;
            case 3: // twi (D-form)
                format = "D";
                rt = (instruction & RT_MASK) >>> 21; // TO
                ra = (instruction & RA_MASK) >>> 16;
                si = instruction & SI_MASK;
                if ((si & 0x8000) != 0) {
                    si |= 0xFFFF_0000; // Sign-extend
                }
                break;
            case 4, 5, 6, 62: // Vector instructions (X-form, e.g., lvx, stvx)
                format = "X";
                rt = (instruction & RT_MASK) >>> 21;
                ra = (instruction & RA_MASK) >>> 16;
                rb = (instruction & RB_MASK) >>> 11;
                xo = (instruction & XO_MASK) >>> 1; // Bits 21–30
                rc = instruction & RC_MASK;
                if (opcode == 62 && (xo == 0 || xo == 1)) { // stdu, ldu
                    format = "DS";
                    si = (instruction & DS_MASK) >>> 2;
                    if ((si & 0x2000) != 0) {
                        si |= 0xFFFFC000; // Sign-extend
                    }
                    xo = si;
                }
                break;
            case 7, 8, 10, 11, 12, 13, 14, 15: // Arithmetic (D-form)
                format = "D";
                rt = (instruction & RT_MASK) >>> 21; // RT or BF (for cmpli/cmpi)
                ra = (instruction & RA_MASK) >>> 16;
                si = instruction & SI_MASK;
                if (opcode == 15) {
                    si = si << 16; // Shift for addis
                }
                if ((si & 0x8000) != 0 && opcode != 10) { // Sign-extend except for cmpli
                    si |= 0xFFFF_0000;
                }
                if (opcode == 10 || opcode == 11) {
                    rt = (instruction >>> 23) & 0x7; // BF
                    rb = (instruction >>> 22) & 0x1; // L
                }
                break;
            case 16: // b (I-form)
                format = "I";
                li = (instruction & LI_MASK) >>> 2;
                if ((li & 0x00800000) != 0) {
                    li |= 0xFF000000; // Sign-extend
                }
                aa = (instruction & AA_MASK) >>> 1;
                lk = instruction & LK_MASK;
                break;
            case 17: // sc (SC-form)
                format = "SC";
                xo = (instruction >>> 5) & 0xFFF; // LEV
                lk = instruction & LK_MASK;
                break;
            case 18: // bc (B-form)
                format = "B";
                rt = (instruction & RT_MASK) >>> 21; // BO
                ra = (instruction & RA_MASK) >>> 16; // BI
                bd = (instruction & BD_MASK) >>> 2;
                if ((bd & 0x2000) != 0) {
                    bd |= 0xFFFFC000; // Sign-extend
                }
                aa = (instruction & AA_MASK) >>> 1;
                lk = instruction & LK_MASK;
                break;
            case 19: // CR operations, branches, special (XL-form)
                format = "XL";
                rt = (instruction & RT_MASK) >>> 21; // BF/CRBD
                ra = (instruction & RA_MASK) >>> 16; // BI/CRBA
                rb = (instruction & RB_MASK) >>> 11; // CRBB
                xo = (instruction & XO_MASK) >>> 1; // Bits 21–30
                rc = instruction & RC_MASK;
                break;
            case 20, 21, 23: // Rotations (M-form: rlwimi, rlwinm, rlwnm)
                format = "M";
                rt = (instruction & RT_MASK) >>> 21; // RS
                ra = (instruction & RA_MASK) >>> 16;
                rb = (instruction & RB_MASK) >>> 11; // SH (rlwnm)
                sh = (instruction & SH_MASK) >>> 11; // SH (rlwimi, rlwinm)
                mb = (instruction & MB_MASK) >>> 6;
                me = (instruction & ME_MASK) >>> 1;
                rc = instruction & RC_MASK;
                break;
            case 22: // rlmi (Xenon-specific, M-form)
                format = "M";
                rt = (instruction & RT_MASK) >>> 21; // RS
                ra = (instruction & RA_MASK) >>> 16;
                rb = (instruction & RB_MASK) >>> 11; // SH
                sh = (instruction & SH_MASK) >>> 11; // SH
                mb = (instruction & MB_MASK) >>> 6;
                me = (instruction & ME_MASK) >>> 1;
                rc = instruction & RC_MASK;
                break;
            case 24, 25, 26, 27, 28, 29: // Logical (D-form: ori, oris, xori, xoris, andi., andis.)
                format = "D";
                rt = (instruction & RT_MASK) >>> 21; // RS
                ra = (instruction & RA_MASK) >>> 16;
                si = instruction & SI_MASK;
                if (opcode == 26 || opcode == 27) { // Sign-extend for xori, xoris
                    if ((si & 0x8000) != 0) {
                        si |= 0xFFFF_0000;
                    }
                }
                break;
            case 30: // 64-bit rotations (MD/MDS-form: rldicl, rldicr, etc.)
                format = (instruction & 0x2) == 0 ? "MD" : "MDS";
                rt = (instruction & RT_MASK) >>> 21; // RS
                ra = (instruction & RA_MASK) >>> 16;
                rb = (instruction & RB_MASK) >>> 11; // SH/RB (MDS-form)
                sh = (instruction & SH_MASK) >>> 11; // SH (MD-form)
                mb = (instruction & MB_MASK) >>> 6; // MB or ME
                me = mb; // For MD/MDS, MB and ME are often the same field
                rc = instruction & RC_MASK;
                xo = (instruction & 0x1F); // Bits 27–30 (MD) or 26–30 (MDS)
                break;
            case 31: // Arithmetic, logical, memory, special (X/XO/XL/XFX-form)
                format = "X";
                rt = (instruction & RT_MASK) >>> 21;
                ra = (instruction & RA_MASK) >>> 16;
                rb = (instruction & RB_MASK) >>> 11;
                xo = (instruction & XO_MASK) >>> 1; // Bits 21–30
                rc = instruction & RC_MASK;
                break;
            case 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55: // Memory (D-form)
                format = "D";
                rt = (instruction & RT_MASK) >>> 21; // RT/FRT/RS
                ra = (instruction & RA_MASK) >>> 16;
                si = instruction & SI_MASK;
                if ((si & 0x8000) != 0) {
                    si |= 0xFFFF_0000; // Sign-extend
                }
                break;
            case 56, 57: // lfq, lfqu (optional, not supported in 603e)
                throw new IllegalArgumentException("Opcode " + opcode + " (lfq/lfqu) not supported");
            case 58: // ld, ldu (DS-form)
                format = "DS";
                rt = (instruction & RT_MASK) >>> 21;
                ra = (instruction & RA_MASK) >>> 16;
                si = (instruction & DS_MASK) >>> 2;
                if ((si & 0x2000) != 0) {
                    si |= 0xFFFFC000; // Sign-extend
                }
                break;
            case 59: // Floating-point single-precision (A/X-form)
                format = "A";
                rt = (instruction & RT_MASK) >>> 21; // FRT
                ra = (instruction & RA_MASK) >>> 16; // FRA
                rb = (instruction & RB_MASK) >>> 11; // FRB
                si = (instruction & FRC_MASK) >>> 6; // FRC
                xo = (instruction & XO_MASK) >>> 1; // Bits 21–30
                rc = instruction & RC_MASK;
                break;
            case 60, 61: // Reserved or optional
                throw new IllegalArgumentException("Opcode " + opcode + " not supported");
            case 63: // Floating-point double-precision (A/X-form)
                format = "A";
                rt = (instruction & RT_MASK) >>> 21; // FRT
                ra = (instruction & RA_MASK) >>> 16; // FRA
                rb = (instruction & RB_MASK) >>> 11; // FRB
                si = (instruction & FRC_MASK) >>> 6; // FRC
                xo = (instruction & XO_MASK) >>> 1; // Bits 21–30
                rc = instruction & RC_MASK;
                break;
            default:
                throw new IllegalArgumentException("Unsupported opcode: " + opcode);
        }

        return new InstructionFields(instruction, opcode, rt, ra, rb, xo, rc, bd, li, aa, lk, si, sh, mb, me, format);
    }

    /**
     * Decodes and logs detailed instruction fields for debugging.
     *
     * @param instruction The 32-bit instruction.
     * @param pc The program counter (for context).
     */
    public static void decodeVerbose(int instruction, long pc) {
        InstructionFields fields = decode(instruction);
        StringBuilder log = new StringBuilder();
        log.append(String.format("PC=0x%08X, Instruction=0x%08X, Format=%s, Mnemonic=%s%n",
                pc, instruction, fields.format, getMnemonic(fields, instruction)));
        log.append(String.format("  Opcode: %d (0x%02X)%n", fields.opcode, fields.opcode));
        if (fields.rt != 0) {
            log.append(String.format("  RT/TO/BF: r%d (0x%02X)%n", fields.rt, fields.rt));
        }
        if (fields.ra != 0) {
            log.append(String.format("  RA/BI: r%d (0x%02X)%n", fields.ra, fields.ra));
        }
        if (fields.rb != 0) {
            log.append(String.format("  RB: r%d (0x%02X)%n", fields.rb, fields.rb));
        }
        if (fields.xo != 0) {
            log.append(String.format("  XO: %d (0x%03X)%n", fields.xo, fields.xo));
        }
        if (fields.rc != 0) {
            log.append(String.format("  Rc: %d%n", fields.rc));
        }
        if (fields.bd != 0) {
            log.append(String.format("  BD: %d (0x%04X)%n", fields.bd, fields.bd));
        }
        if (fields.li != 0) {
            log.append(String.format("  LI: %d (0x%06X)%n", fields.li, fields.li));
        }
        if (fields.aa != 0) {
            log.append(String.format("  AA: %d%n", fields.aa));
        }
        if (fields.lk != 0) {
            log.append(String.format("  LK: %d%n", fields.lk));
        }
        if (fields.si != 0) {
            String fieldName = fields.format.equals("A") ? "FRC" : fields.format.equals("DS") ? "DS" : "SI";
            log.append(String.format("  %s: %d (0x%04X)%n", fieldName, fields.si, fields.si));
        }
        if (fields.sh != 0) {
            log.append(String.format("  SH: %d (0x%02X)%n", fields.sh, fields.sh));
        }
        if (fields.mb != 0) {
            log.append(String.format("  MB: %d (0x%02X)%n", fields.mb, fields.mb));
        }
        if (fields.me != 0) {
            log.append(String.format("  ME: %d (0x%02X)%n", fields.me, fields.me));
        }
        LOGGER.info(log.toString());
    }

    /**
     * Compatibility method to match existing log format.
     *
     * @param instruction The 32-bit instruction.
     * @return String in format "Opcode: <opcode>, SubOpcode and XO:
     * <opcode>,<xo>, Mnemonic: <mnemonic>".
     */
    public static String decodeForLog(int instruction) {
        InstructionFields f = decode(instruction);
        return String.format(
                "Opcode: %d, SubOpcode and XO: %d,%d, Mnemonic: %s",
                f.opcode, f.opcode, f.xo, getMnemonic(f, instruction)
        );
    }

    public static String getMnemonic(InstructionFields fields, int instruction) {
        return switch (fields.opcode()) {
            case 0 ->
                instruction == 0x60000000 ? "nop" : instruction == 0x323 ? "invalid_0x323" : instruction==0? "nop":" invalid!";
            case 1 ->
                "tdi";
            case 2 ->
                "twi";
            case 4, 5, 6 ->
                switch (fields.xo()) {
                    case 4 ->
                        "vaddubm";
                    case 5 ->
                        "vaddubm";
                    case 68 ->
                        "vaddshs";
                    case 132 ->
                        "vadduhs";
                    case 196 ->
                        "vaddsws";
                    case 260 ->
                        "vadduws";
                    case 512 ->
                        "evaddw";
                    case 516 ->
                        "evsubfw";
                    case 520 ->
                        "evmulhw";
                    case 522 ->
                        "evmullw";
                    case 529 ->
                        "evand";
                    case 536 ->
                        "evor";
                    case 538 ->
                        "evxor";
                    case 1024 ->
                        "vsububm";
                    case 1092 ->
                        "vsubshs";
                    case 1156 ->
                        "vsubuhs";
                    case 1224 ->
                        "evmra";
                    case 1284 ->
                        "vsubsws";
                    case 1348 ->
                        "vsubuwm";
                    default ->
                        "unknown_vector";
                };
            case 7 ->
                "mulli";
            case 8 ->
                "subfic";
            case 9 ->
                "icbt";
            case 10 ->
                "cmpli";
            case 11 ->
                "cmpi";
            case 12 ->
                "addic";
            case 13 ->
                "addic.";
            case 14 ->
                "addi";
            case 15 ->
                "addis";
            case 16 ->
                fields.aa() == 0 ? (fields.lk() == 0 ? "b" : "bl") : (fields.lk() == 0 ? "ba" : "bla");
            case 17 ->
                "sc";
            case 18 ->
                fields.lk() == 0 ? "bc" : "bcl";
            case 19 ->
                switch (fields.xo()) {
                    case 0 ->
                        "mcrf";
                    case 16 ->
                        "bclr";
                    case 18 ->
                        "bcctr";
                    case 33 ->
                        "crnor";
                    case 50 ->
                        "rfi";
                    case 129 ->
                        "crandc";
                    case 150 ->
                        "isync";
                    case 193 ->
                        "crxor";
                    case 225 ->
                        "crnand";
                    case 257 ->
                        "crand";
                    case 289 ->
                        "creqv";
                    case 417 ->
                        "crorc";
                    case 449 ->
                        "cror";
                    case 528 ->
                        "bclrl";
                    default ->
                        "unknown_19";
                };
            case 20 ->
                fields.rc() == 0 ? "rlwimi" : "rlwimi.";
            case 21 ->
                fields.rc() == 0 ? "rlwinm" : "rlwinm.";
            case 22 ->
                fields.rc() == 0 ? "rlmi" : "rlmi.";
            case 23 ->
                fields.rc() == 0 ? "rlwnm" : "rlwnm.";
            case 24 ->
                "ori";
            case 25 ->
                "oris";
            case 26 ->
                "xori";
            case 27 ->
                "xoris";
            case 28 ->
                "andi.";
            case 29 ->
                "andis.";
            case 30 ->
                switch (fields.xo()) {
                    case 0, 1 ->
                        fields.rc() == 0 ? "rldicl" : "rldicl.";
                    case 2, 3 ->
                        fields.rc() == 0 ? "rldicr" : "rldicr.";
                    case 4, 5 ->
                        fields.rc() == 0 ? "rldic" : "rldic.";
                    case 6, 7, 14 ->
                        fields.rc() == 0 ? "rldimi" : "rldimi.";
                    case 8, 9 ->
                        fields.rc() == 0 ? "rldcl" : "rldcl.";
                    case 10 ->
                        fields.rc() == 0 ? "rldcr" : "rldcr.";
                    default ->
                        "unknown_30";
                };
            case 31 ->
                switch (fields.xo()) {
                    case 0 ->
                        "cmp";
                    case 4 ->
                        "tw";
                    case 8 ->
                        "subfc";
                    case 9 ->
                        "subfco";
                    case 10 ->
                        "addc";
                    case 11 ->
                        "mulhwu";
                    case 19 ->
                        "mfcr";
                    case 20 ->
                        "lwarx";
                    case 23 ->
                        "lwzx";
                    case 24 ->
                        "slw";
                    case 26 ->
                        "cntlzw";
                    case 27 ->
                        "sld";
                    case 28 ->
                        fields.rc() == 0 ? "and" : "and.";
                    case 29 ->
                        fields.rc() == 0 ? "andc" : "andc.";
                    case 40 ->
                        "subf";
                    case 42 ->
                        "addco";
                    case 50 ->
                        "rfi";
                    case 54 ->
                        "dcbst";
                    case 60 ->
                        "andc";
                    case 72 ->
                        "subfo";
                    case 75 ->
                        "mulhw";
                    case 83 ->
                        "mfmsr";
                    case 86 ->
                        "dcbf";
                    case 87 ->
                        "lbzx";
                    case 104 ->
                        fields.rc() == 0 ? "neg" : "nego";
                    case 119 ->
                        "lbzux";
                    case 122 ->
                        "popcntb";
                    case 124 ->
                        "nor";
                    case 136 ->
                        "subfe";
                    case 138 ->
                        "adde";
                    case 144 ->
                        "mtcrf";
                    case 146 ->
                        "mtmsr";
                    case 150 ->
                        "stwcx.";
                    case 151 ->
                        "stwx";
                    case 178 ->
                        "subfeo";
                    case 183 ->
                        "sthx";
                    case 200 ->
                        "subfze";
                    case 202 ->
                        "addze";
                    case 210 ->
                        "mtsr";
                    case 211 ->
                        "mfsr";
                    case 215 ->
                        "stbx";
                    case 231 ->
                        "stvx";
                    case 232 ->
                        "subfme";
                    case 234 ->
                        "addme";
                    case 235 ->
                        fields.rc() == 0 ? "mullw" : "mullwo";
                    case 242 ->
                        "mtsrin";
                    case 246 ->
                        "dcbtst";
                    case 247 ->
                        "stbux";
                    case 266 ->
                        "add";
                    case 278 ->
                        "dcbt";
                    case 279 ->
                        "lhzx";
                    case 284 ->
                        fields.rc() == 0 ? "eqv" : "eqv.";
                    case 298 ->
                        "addo";
                    case 306 ->
                        "tlbie";
                    case 310 ->
                        "eciwx";
                    case 311 ->
                        "lfdx";
                    case 316 ->
                        fields.rc() == 0 ? "xor" : "xor.";
                    case 339 ->
                        "mfspr";
                    case 341 ->
                        "lhax";
                    case 343 ->
                        "lhaux";
                    case 370 ->
                        "tlbia";
                    case 371 ->
                        "mftb";
                    case 378 ->
                        "popcntw";
                    case 407 ->
                        "stfsx";
                    case 412 ->
                        "orc";
                    case 438 ->
                        "ecowx";
                    case 439 ->
                        "stfdx";
                    case 444 ->
                        fields.rc() == 0 ? "or" : "or.";
                    case 459 ->
                        "divwu";
                    case 470 ->
                        "dcbi";
                    case 476 ->
                        fields.rc() == 0 ? "nand" : "nand.";
                    case 491 ->
                        fields.rc() == 0 ? "divw" : "divwuo";
                    case 508 ->
                        "cmpb";
                    case 523 ->
                        "divwo";
                    case 534 ->
                        "lwbrx";
                    case 536 ->
                        "srw";
                    case 537 ->
                        "srd";
                    case 539 ->
                        "sraw";
                    case 566 ->
                        "tlbsync";
                    case 595 ->
                        "mfsr";
                    case 597 ->
                        "stswi";
                    case 598 ->
                        "sync";
                    case 661 ->
                        "lswx";
                    case 662 ->
                        "stbux";
                    case 663 ->
                        "lswi";
                    case 667 ->
                        "stswx";
                    case 758 ->
                        "dcba";
                    case 778 ->
                        "addox";
                    case 790 ->
                        "lhbrx";
                    case 792 ->
                        "srawi";
                    case 824 ->
                        "srawi";
                    case 854 ->
                        "eieio";
                    case 918 ->
                        "sthbrx";
                    case 922 ->
                        "extsh";
                    case 954 ->
                        "extsb";
                    case 978 ->
                        "tlbld";
                    case 982 ->
                        "icbi";
                    case 986 ->
                        "extsw";
                    case 1010 ->
                        "tlbli";
                    case 1014 ->
                        "dcbz";
                    default ->
                        "unknown_31";
                };
            case 32 ->
                "lwz";
            case 33 ->
                "lwzu";
            case 34 ->
                "lbz";
            case 35 ->
                "lbzu";
            case 36 ->
                "stw";
            case 37 ->
                "stwu";
            case 38 ->
                "stb";
            case 39 ->
                "stbu";
            case 40 ->
                "lhz";
            case 41 ->
                "lhzu";
            case 42 ->
                "lha";
            case 43 ->
                "lhau";
            case 44 ->
                "sth";
            case 45 ->
                "sthu";
            case 46 ->
                "lmw";
            case 47 ->
                "stmw";
            case 48 ->
                "lfs";
            case 49 ->
                "lfsu";
            case 50 ->
                "lfd";
            case 51 ->
                "lfdu";
            case 52 ->
                "stfs";
            case 53 ->
                "stfsu";
            case 54 ->
                "stfd";
            case 55 ->
                "stfdu";
            case 56 ->
                "lfdp";
            case 57 ->
                "lfdpx";
            case 58 ->
                switch (fields.xo()) {
                    case 0 ->
                        "ld";
                    case 1 ->
                        "ldu";
                    case 2 ->
                        "ldarx";
                    default ->
                        "unknown_58";
                };
            case 59 ->
                switch (fields.xo()) {
                    case 18 ->
                        "fdivs";
                    case 20 ->
                        "fsubs";
                    case 21 ->
                        "fadds";
                    case 22 ->
                        "fsqrts";
                    case 24 ->
                        "fres";
                    case 25 ->
                        "fmuls";
                    case 26 ->
                        "frsqrtes";
                    case 28 ->
                        fields.rc() == 0 ? "fmsubs" : "fmsubs.";
                    case 29 ->
                        fields.rc() == 0 ? "fmadds" : "fmadds.";
                    case 30 ->
                        fields.rc() == 0 ? "fnmsubs" : "fnmsubs.";
                    case 31 ->
                        fields.rc() == 0 ? "fnmadds" : "fnmadds.";
                    default ->
                        "unknown_fp";
                };
            case 60 ->
                "stfdp";
            case 61 ->
                "stfdpx";
            case 62 ->
                switch (fields.xo()) {
                    case 0 ->
                        "std";
                    case 1 ->
                        "stdu";
                    case 2 ->
                        "stdcx.";
                    case 103 ->
                        "lvx";
                    case 231 ->
                        "stvx";
                    default ->
                        "unknown_62";
                };
            case 63 ->
                switch (fields.xo()) {
                    case 0 ->
                        "fcmpu";
                    case 12 ->
                        "frsp";
                    case 14 ->
                        "fctiw";
                    case 15 ->
                        "fctiwz";
                    case 18 ->
                        "fdiv";
                    case 20 ->
                        "fsub";
                    case 21 ->
                        "fadd";
                    case 22 ->
                        "fsqrt";
                    case 24 ->
                        "fres";
                    case 25 ->
                        "fmul";
                    case 26 ->
                        "frsqrte";
                    case 28 ->
                        "fmsub";
                    case 29 ->
                        "fmadd";
                    case 30 ->
                        "fnmsub";
                    case 31 ->
                        "fnmadd";
                    case 32 ->
                        "fcmpo";
                    case 40 ->
                        "fneg";
                    case 72 ->
                        "fmr";
                    case 136 ->
                        "fnabs";
                    case 264 ->
                        "fabs";
                    case 583 ->
                        "mffs";
                    case 711 ->
                        "fsel";
                    case 663 ->
                        "stfsx";
                    case 695 ->
                        "stfsux";
                    case 727 ->
                        "stfdx";
                    case 759 ->
                        "stfdux";
                    case 814 ->
                        "fctid";
                    case 815 ->
                        "fctidz";
                    case 846 ->
                        "fcfid";
                    case 887 ->
                        "lfdx";
                    case 983 ->
                        "stfiwx";
                    case 38 ->
                        "mtfsb1";
                    case 70 ->
                        "mtfsb0";
                    case 134 ->
                        "mtfsfi";
                    default ->
                        "unknown_fp";
                };
            default ->
                "unknown";
        };
    }
}
