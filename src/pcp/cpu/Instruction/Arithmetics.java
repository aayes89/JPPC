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
import pcp.utils.Utilities;

/**
 *
 * @author Slam
 */
public class Arithmetics {

    CPU cpu;

    public Arithmetics(CPU cpuulator) {
        this.cpu = cpuulator;
    }

    public void executePrimary(int opcode, int instruction) {
        int RT = (instruction >> 21) & 0x1F;
        int RA = (instruction >> 16) & 0x1F;
        int SI = (short) (instruction & 0xFFFF);  // Sign-extended
        int UI = instruction & 0xFFFF;            // Unsigned immediate

        switch (opcode) {
            // Operaciones básicas
            case 14: // addi
                addi(RT, RA, SI);
                break;
            case 15: // addis
                addis(RT, RA, SI);
                break;
            case 12: // addic (sin actualizar CR)
                addic(RT, RA, SI, false);
                break;
            case 13: // addic. (actualiza CR)
                addic(RT, RA, SI, true);
                break;
            case 8:  // subfic
                subfic(RT, RA, SI);
                break; 
            case 7:  // mulli
                mulli(RT, RA, SI);
                break;

            default:
                cpu.getExceptionHandler().handleException(
                        ExceptionHandler.EXCEPTION_PROGRAM,
                        cpu.getPc(),
                        instruction
                );
                break;
        }
    }

    public void executeExtended(int extendedOpcode, int instruction) {
        int RT = (instruction >> 21) & 0x1F;
        int RA = (instruction >> 16) & 0x1F;
        int RB = (instruction >> 11) & 0x1F;
        boolean oe = ((instruction >> 10) & 1) != 0;
        boolean dot = (instruction & 1) != 0;

        switch (extendedOpcode) {
            // Operaciones básicas
            case 266: // add
                add(RT, RA, RB, dot, oe);
                break;
            case 10:  // addc
                addc(RT, RA, RB, dot, oe);
                break;
            case 138: // adde
                adde(RT, RA, RB, dot, oe);
                break;
            case 40:  // subf
                subf(RT, RA, RB, dot, oe);
                break;
            case 8:   // subfc
                subfc(RT, RA, RB, dot, oe);
                break;
            case 136: // subfe
                subfe(RT, RA, RB, dot, oe);
                break;
            case 104: // neg
                neg(RT, RA, dot, oe);
                break;

            // Operaciones con carry extendido
            case 234: // addme
                addme(RT, RA, dot, oe);
                break;
            case 202: // addze
                addze(RT, RA, dot, oe);
                break;
            case 232: // subfme
                subfme(RT, RA, dot, oe);
                break;
            case 200: // subfze
                subfze(RT, RA, dot, oe);
                break;

            // Multiplicación/División
            case 235: // mullw
                mullw(RT, RA, RB, dot, oe);
                break;
            case 75:  // mulhw
                mulhw(RT, RA, RB, dot);
                break;
            case 11:  // mulhwu
                mulhwu(RT, RA, RB, dot);
                break;
            case 491: // divw
                divw(RT, RA, RB, dot, oe);
                break;
            case 459: // divwu
                divwu(RT, RA, RB, dot, oe);
                break;

            // Manipulación de bits
            case 934: // extsw (Extend Sign Word)                
                extsw(RT, RA, dot);
                break;
            case 986: // extsb
                extsb(RT, RA, dot);
                break;
            case 922: // extsh
                extsh(RT, RA, dot);
                break;
            case 996:
            case 26:  // cntlzw
                cntlzw(RT, RA, dot);
                break;
            case 792: // sraw
                sraw(RT, RA, RB);
                break;
            case 824: // srawi
                int SH = (instruction >> 11) & 0x1F;  // Campo SH (bits 11-15)
                srawi(RT, RA, SH);
                break;
            default:
                cpu.getExceptionHandler().handleException(
                        ExceptionHandler.EXCEPTION_PROGRAM,
                        cpu.getPc(),
                        instruction
                );
                break;
        }

    }
    // --- INSTRUCCIONES DE ARITMÉTICA ENTERA ---

    // ADD: Suma
    public void add(int RT, int RA, int RB, boolean dot, boolean oe) {
        long a = cpu.getGPRVal(RA);
        long b = cpu.getGPRVal(RB);
        long result = a + b;

        // Detectar overflow (signos iguales pero resultado signo diferente)
        boolean overflow = ((a ^ b) >= 0) && ((a ^ result) < 0);

        cpu.setGPR(RT, result);

        if (dot) {
            cpu.updateCRField(0, result);
        }
        if (oe) {
            cpu.setOV(overflow);
        }
    }

    // ADDC: Suma con carry out
    public void addc(int RT, int RA, int RB, boolean dot, boolean oe) {
        long a = cpu.getGPRVal(RA);
        long b = cpu.getGPRVal(RB);
        long longResult = (long) a + (long) b;
        int result = (int) longResult;

        boolean carry = (longResult >>> 32) != 0;
        boolean overflow = ((a ^ b) >= 0) && ((a ^ result) < 0);

        cpu.setGPR(RT, result);
        cpu.setCA(carry);

        if (dot) {
            cpu.updateCRField(0, result);
        }
        if (oe) {
            cpu.setOV(overflow);
        }
    }

    // ADDE: Suma con carry in
    public void adde(int RT, int RA, int RB, boolean dot, boolean oe) {
        long a = cpu.getGPRVal(RA);
        long b = cpu.getGPRVal(RB);
        int carryIn = (cpu.getXer() & cpu.XER_CA) != 0 ? 1 : 0;
        long longResult = (long) a + (long) b + (long) carryIn;
        int result = (int) longResult;

        boolean carry = (longResult >>> 32) != 0;
        boolean overflow = ((a ^ b) >= 0) && ((a ^ result) < 0);

        cpu.setGPR(RT, result);
        cpu.setCA(carry);

        if (dot) {
            cpu.updateCRField(0, result);
        }
        if (oe) {
            cpu.setOV(overflow);
        }
    }

    // SUBF: Resta
    public void subf(int RT, int RA, int RB, boolean dot, boolean oe) {
        long a = cpu.getGPRVal(RA);
        long b = cpu.getGPRVal(RB);
        long result = b - a;  // RT = RB - RA

        // Overflow si signos diferentes y resultado signo diferente a RB
        boolean overflow = ((a ^ b) < 0) && ((b ^ result) < 0);

        cpu.setGPR(RT, result);

        if (dot) {
            cpu.updateCRField(0, result);
        }
        if (oe) {
            cpu.setOV(overflow);
        }
    }

    // SUBFC: Resta con carry out
    public void subfc(int RT, int RA, int RB, boolean dot, boolean oe) {
        long a = cpu.getGPRVal(RA);
        long b = cpu.getGPRVal(RB);
        long longResult = (long) b - (long) a;
        int result = (int) longResult;

        // Carry = 1 si no hay borrow (b >= a)
        boolean carry = (b >= a) || ((b < 0) && (a > 0)); // Considera wraparound
        boolean overflow = ((a ^ b) < 0) && ((b ^ result) < 0);

        cpu.setGPR(RT, result);
        cpu.setCA(carry);

        if (dot) {
            cpu.updateCRField(0, result);
        }
        if (oe) {
            cpu.setOV(overflow);
        }
    }

    // SUBFE: Resta con carry in
    public void subfe(int RT, int RA, int RB, boolean dot, boolean oe) {
        long a = cpu.getGPRVal(RA);
        long b = cpu.getGPRVal(RB);
        int carryIn = (cpu.getXer() & cpu.XER_CA) != 0 ? 1 : 0;
        long longResult = (long) b + (long) (~a) + (long) carryIn;
        int result = (int) longResult;

        boolean carry = (longResult >>> 32) != 0;
        boolean overflow = ((b ^ ~a) >= 0) && ((b ^ result) < 0);

        cpu.setGPR(RT, result);
        cpu.setCA(carry);

        if (dot) {
            cpu.updateCRField(0, result);
        }
        if (oe) {
            cpu.setOV(overflow);
        }
    }

    // NEG: Negación (equivalente a SUBF desde cero)
    public void neg(int RT, int RA, boolean dot, boolean oe) {
        long a = cpu.getGPRVal(RA);
        long result = -a;

        // Overflow solo si a es Integer.MIN_VALUE
        boolean overflow = (a == Integer.MIN_VALUE);

        cpu.setGPR(RT, result);

        if (dot) {
            cpu.updateCRField(0, result);
        }
        if (oe) {
            cpu.setOV(overflow);
        }
    }

    // MULLW: Multiplicación baja
    public void mullw(int RT, int RA, int RB, boolean dot, boolean oe) {
        long a = cpu.getGPRVal(RA);
        long b = cpu.getGPRVal(RB);
        long product = a * b;
        int result = (int) product;

        // Overflow si el resultado excede 32 bits con signo
        boolean overflow = (product != (long) result);

        cpu.setGPR(RT, result);

        if (dot) {
            cpu.updateCRField(0, result);
        }
        if (oe) {
            cpu.setOV(overflow);
        }
    }

    // MULHW: Multiplicación alta (con signo)
    public void mulhw(int RT, int RA, int RB, boolean dot) {
        long a = cpu.getGPRVal(RA);
        long b = cpu.getGPRVal(RB);
        long product =  a * b;
        int high = (int) (product >> 32);

        cpu.setGPR(RT, high);
        if (dot) {
            cpu.updateCRField(0, high);
        }
    }

    // MULHWU: Multiplicación alta (sin signo)
    public void mulhwu(int RT, int RA, int RB, boolean dot) {
        long a = cpu.getGPRVal(RA) & 0xFFFFFFFFL;
        long b = cpu.getGPRVal(RB) & 0xFFFFFFFFL;
        long product = a * b;
        int high = (int) (product >> 32);

        cpu.setGPR(RT, high);
        if (dot) {
            cpu.updateCRField(0, high);
        }
    }

    // DIVW: División con signo
    public void divw(int RT, int RA, int RB, boolean dot, boolean oe) {
        long a = cpu.getGPRVal(RA);
        long b = cpu.getGPRVal(RB);
        long result = 0;
        boolean overflow = false;

        if (b == 0 || (a == Integer.MIN_VALUE && b == -1)) {
            overflow = true; // División por cero o overflow
        } else if (b != 0) {
            result = a / b;
        }

        cpu.setGPR(RT, result);

        if (dot) {
            cpu.updateCRField(0, result);
        }
        if (oe) {
            cpu.setOV(overflow);
        }
    }

    // DIVWU: División sin signo
    public void divwu(int RT, int RA, int RB, boolean dot, boolean oe) {
        long a = cpu.getGPRVal(RA) & 0xFFFFFFFFL;
        long b = cpu.getGPRVal(RB) & 0xFFFFFFFFL;
        int result = 0;
        boolean overflow = false;

        if (b == 0) {
            overflow = true;
        } else {
            result = (int) (a / b);
        }

        cpu.setGPR(RT, result);

        if (dot) {
            cpu.updateCRField(0, result);
        }
        if (oe) {
            cpu.setOV(overflow);
        }
    }

    // ADDIC: Suma inmediata con carry
    public void addic(int RT, int RA, int SI, boolean dot) {
        int immediate = SI;
        long a = cpu.getGPRVal(RA);
        long longResult = (long) a + (long) immediate;
        int result = (int) longResult;

        boolean carry = (longResult >>> 32) != 0;

        cpu.setGPR(RT, result);
        cpu.setCA(carry);
        if (dot) {
            cpu.updateCRField(0, result);
        }
    }

    // SUBFIC: Resta inmediata con carry
    public void subfic(int RT, int RA, int SI) {
        long a = cpu.getGPRVal(RA);
        int immediate = SI;
        long longResult = (long) immediate - (long) a;
        int result = (int) longResult;

        // Carry = 1 si no hay borrow (immediate >= a)
        boolean carry = (immediate >= a) || ((immediate < 0) && (a > 0));

        cpu.setGPR(RT, result);
        cpu.setCA(carry);
    }

    // ADDI: Suma inmediata (r0 = 0)
    public void addi(int RT, int RA, int SI) {
        int immediate = SI;
        long a = (RA == 0) ? 0 : cpu.getGPRVal(RA);
        cpu.setGPR(RT, a + immediate);
    }

    // ADDIS: Suma inmediata desplazada
    public void addis(int RT, int RA, int SI) {
        int immediate = SI << 16;
        long a = (RA == 0) ? 0 : cpu.getGPRVal(RA);
        cpu.setGPR(RT, a + immediate);
    }

    // ADDME: Add to Minus One Extended
    public void addme(int RT, int RA, boolean dot, boolean oe) {
        long a = cpu.getGPRVal(RA);
        int carryIn = (cpu.getXer() & cpu.XER_CA) != 0 ? 1 : 0;
        long result = a + (-1) + carryIn;

        // Detectar carry y overflow
        boolean carry = (a == 0) ? (carryIn == 1) : (carryIn == 1 || a > 0);
        boolean overflow = (a > 0 && result < 0) || (a < 0 && result > 0);

        cpu.setGPR(RT, result);
        cpu.setCA(carry);
        if (dot) {
            cpu.updateCRField(0, result);
        }
        if (oe) {
            cpu.setOV(overflow);
        }
    }

    // ADDZE: Add to Zero Extended
    public void addze(int RT, int RA, boolean dot, boolean oe) {
        long a = cpu.getGPRVal(RA);
        int carryIn = (cpu.getXer() & cpu.XER_CA) != 0 ? 1 : 0;
        long result = a + carryIn;

        // Detectar carry y overflow
        boolean carry = (a == 0) ? (carryIn == 1) : (carryIn == 1 || a > 0);
        boolean overflow = (a > 0 && result < 0) || (a < 0 && result > 0);

        cpu.setGPR(RT, result);
        cpu.setCA(carry);
        if (dot) {
            cpu.updateCRField(0, result);
        }
        if (oe) {
            cpu.setOV(overflow);
        }
    }

    // SUBFME: Subtract From Minus One Extended
    public void subfme(int RT, int RA, boolean dot, boolean oe) {
        long a = cpu.getGPRVal(RA);
        int carryIn = (cpu.getXer() & cpu.XER_CA) != 0 ? 1 : 0;
        long result = (-1) - a + carryIn;

        // Detectar carry (no borrow) y overflow
        boolean carry = (a == 0) ? (carryIn == 1) : true;
        boolean overflow = (a == Integer.MIN_VALUE);

        cpu.setGPR(RT, result);
        cpu.setCA(carry);
        if (dot) {
            cpu.updateCRField(0, result);
        }
        if (oe) {
            cpu.setOV(overflow);
        }
    }

    // SUBFZE: Subtract From Zero Extended
    public void subfze(int RT, int RA, boolean dot, boolean oe) {
        long a = cpu.getGPRVal(RA);
        int carryIn = (cpu.getXer() & cpu.XER_CA) != 0 ? 1 : 0;
        long result = 0 - a + carryIn;

        // Detectar carry (no borrow) y overflow
        boolean carry = (a == 0) ? (carryIn == 1) : true;
        boolean overflow = (a == Integer.MIN_VALUE && carryIn == 0);

        cpu.setGPR(RT, result);
        cpu.setCA(carry);
        if (dot) {
            cpu.updateCRField(0, result);
        }
        if (oe) {
            cpu.setOV(overflow);
        }
    }

    // MULLI: Multiply Low Immediate
    public void mulli(int RT, int RA, int SI) {
        long product = (long) cpu.getGPRVal(RA) * (long) SI;
        cpu.setGPR(RT, (int) product);
    }

    // EXTSB: Extend Sign Byte
    public void extsb(int RT, int RA, boolean dot) {
        long value = cpu.getGPRVal(RA) & 0xFF;
        if ((value & 0x80) != 0) {
            value |= 0xFFFFFF00;
        }
        cpu.setGPR(RT, value);
        if (dot) {
            cpu.updateCRField(0, value);
        }
    }

    // EXTSH: Extend Sign Halfword
    public void extsh(int RT, int RA, boolean dot) {
        long value = cpu.getGPRVal(RA) & 0xFFFF;
        if ((value & 0x8000) != 0) {
            value |= 0xFFFF0000;
        }
        cpu.setGPR(RT, value);
        if (dot) {
            cpu.updateCRField(0, value);
        }
    }

    // CNTLZW: Count Leading Zero Words
    public void cntlzw(int RT, int RA, boolean dot) {
        long value = cpu.getGPRVal(RA);
        int count = 0;
        for (int i = 31; i >= 0; i--) {
            if ((value & (1 << i)) != 0) {
                break;
            }
            count++;
        }
        cpu.setGPR(RT, count);
        if (dot) {
            cpu.updateCRField(0, count);
        }
    }

    // EXTSW: Extend Sign Word (PowerPC 64-bit)
    public void extsw(int RT, int RA, boolean dot) {
        long value = cpu.getGPRVal(RA);
        // En arquitectura 64-bit esto extendería a 64 bits, pero en 32-bit es similar a una operación nop
        // Simulamos el comportamiento manteniendo el valor igual
        cpu.setGPR(RT, value);

        if (dot) {
            cpu.updateCRField(0, value);
        }
    }

    public void sraw(int RA, int RS, int RB) {
        long value = cpu.getGPRVal(RS); // Valor de RS
        long shift = cpu.getGPRVal(RB) & 0x1F; // Máximo 31 bits

        long result = value >> shift; // Desplazamiento aritmético (signo preservado)
        boolean carry = false;
        if (shift > 0 && (value & (1 << (shift - 1))) != 0) {
            carry = true; // Bit significativo desplazado
        }
        cpu.setGPR(RA, result);
        cpu.setCA(carry); // Actualizar bit CA del XER
    }

    public void srawi(int RT, int RA, int RB) {
        long value = cpu.getGPRVal(RT);
        long result = value >> RB; // shift aritmético

        if ((value & 0x80000000) != 0) {
            result |= (-1 << (32 - RB)); // extensión de signo
        }

        cpu.setGPR(RA, result);
    }

}
