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
 * Handles PowerPC SPE (Signal Processing Engine) instructions for Xenon CPU emulation.
 *
 * @author Slam
 */
public class SignalProcessing {
    private final CPU cpu;

    public SignalProcessing(CPU cpu) {
        this.cpu = cpu;
    }

    /**
     * Executes SPE instructions (opcode 4, specific xo values).
     *
     * @param fields Instruction fields from DecodingInstr.
     */
    public void execute(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("A") && !fields.format().equals("X") && !fields.format().equals("D")) {
            throw new IllegalArgumentException("Invalid format for SPE instruction: " + fields.format());
        }
        switch (fields.xo()) {
            // Arithmetic
            case 512: // evaddw
                evaddw(fields.rt(), fields.ra(), fields.rb());
                break;
            case 513: // evsubfw
                evsubfw(fields.rt(), fields.ra(), fields.rb());
                break;
            case 516: // evmra
                evmra(fields.rt(), fields.ra());
                break;
            case 519: // evmhessf
                evmhessf(fields.rt(), fields.ra(), fields.rb());
                break;
            case 520: // evmheumi
                evmheumi(fields.rt(), fields.ra(), fields.rb());
                break;
            case 523: // evmhessf
                evmhessfs(fields.rt(), fields.ra(), fields.rb());
                break;
            case 527: // evmhogsmfaa
                evmhogsmfaa(fields.rt(), fields.ra(), fields.rb());
                break;
            case 567: // evmwhssf
                evmwhssf(fields.rt(), fields.ra(), fields.rb());
                break;
            case 568: // evmwlumi
                evmwlumi(fields.rt(), fields.ra(), fields.rb());
                break;
            case 569: // evmwhumi
                evmwhumi(fields.rt(), fields.ra(), fields.rb());
                break;
            case 543: // evmwsmfaa
                evmwsmfaa(fields.rt(), fields.ra(), fields.rb());
                break;
            // Logical
            case 529: // evand
                evand(fields.rt(), fields.ra(), fields.rb());
                break;
            case 534: // evxor
                evxor(fields.rt(), fields.ra(), fields.rb());
                break;
            case 535: // evor
                evor(fields.rt(), fields.ra(), fields.rb());
                break;
            case 536: // evnand
                evnand(fields.rt(), fields.ra(), fields.rb());
                break;
            case 537: // eveqv
                eveqv(fields.rt(), fields.ra(), fields.rb());
                break;
            case 538: // evnor
                evnor(fields.rt(), fields.ra(), fields.rb());
                break;
            // Shift/Rotate
            case 528: // evslw
                evslw(fields.rt(), fields.ra(), fields.rb());
                break;
            case 530: // evsrw
                evsrw(fields.rt(), fields.ra(), fields.rb());
                break;
            case 531: // evsrwu
                evsrwu(fields.rt(), fields.ra(), fields.rb());
                break;
            case 552: // evslwi
                evslwi(fields.rt(), fields.ra(), fields.si());
                break;
            case 562: // evsrwi
                evsrwi(fields.rt(), fields.ra(), fields.si());
                break;
            case 563: // evsraw
                evsraw(fields.rt(), fields.ra(), fields.si());
                break;
            // Comparison
            case 544: // evcmpgts
                evcmpgts(fields.rt(), fields.ra(), fields.rb());
                break;
            case 545: // evcmplts
                evcmplts(fields.rt(), fields.ra(), fields.rb());
                break;
            case 546: // evcmpeq
                evcmpeq(fields.rt(), fields.ra(), fields.rb());
                break;
            // Merge/Select
            case 556: // evmergehi
                evmergehi(fields.rt(), fields.ra(), fields.rb());
                break;
            case 557: // evmergelo
                evmergelo(fields.rt(), fields.ra(), fields.rb());
                break;
            case 560: // evsel
                evsel(fields.rt(), fields.ra(), fields.rb());
                break;
            // Load/Store
            case 769: // evldd
                evldd(fields.rt(), fields.ra(), fields.si());
                break;
            case 770: // evldw
                evldw(fields.rt(), fields.ra(), fields.si());
                break;
            case 772: // evlhh
                evlhh(fields.rt(), fields.ra(), fields.si());
                break;
            case 801: // evstdd
                evstdd(fields.rt(), fields.ra(), fields.si());
                break;
            case 802: // evstdw
                evstdw(fields.rt(), fields.ra(), fields.si());
                break;
            case 804: // evsth
                evsth(fields.rt(), fields.ra(), fields.si());
                break;
            default:
                throw new UnsupportedOperationException("Unsupported SPE XO: " + fields.xo());
        }
    }

    // --- Arithmetic Instructions ---

    public void evaddw(int rt, int ra, int rb) {
        long[] a = unpackWords(cpu.getGPRVal(ra));
        long[] b = unpackWords(cpu.getGPRVal(rb));
        long[] result = new long[2];
        boolean overflow = false;

        for (int i = 0; i < 2; i++) {
            long sum = (long) a[i] + (long) b[i];
            result[i] = (int) sum;
            if (sum > Integer.MAX_VALUE || sum < Integer.MIN_VALUE) {
                overflow = true;
            }
        }

        cpu.setGPR(rt, packWords(result));
        updateXER(overflow);
    }

    public void evsubfw(int rt, int ra, int rb) {
        long[] a = unpackWords(cpu.getGPRVal(ra));
        long[] b = unpackWords(cpu.getGPRVal(rb));
        long[] result = new long[2];
        boolean overflow = false;

        for (int i = 0; i < 2; i++) {
            long diff = (long) a[i] - (long) b[i];
            result[i] = (int) diff;
            if (diff > Integer.MAX_VALUE || diff < Integer.MIN_VALUE) {
                overflow = true;
            }
        }

        cpu.setGPR(rt, packWords(result));
        updateXER(overflow);
    }

    public void evmra(int rt, int ra) {
        long[] a = unpackWords(cpu.getGPRVal(ra));
        long[] acc = unpackWords(cpu.getAcc());
        long[] result = new long[2];

        for (int i = 0; i < 2; i++) {
            long product = (long) a[i] * (long) a[i];
            result[i] = (int) (product >> 32); // High 32 bits
            acc[i] += result[i];
        }

        cpu.setGPR(rt, packWords(result));
        cpu.setAcc(packWords(acc));
    }

    public void evmhessf(int rt, int ra, int rb) {
        short[] a = unpackHalfwords(cpu.getGPRVal(ra));
        short[] b = unpackHalfwords(cpu.getGPRVal(rb));
        long[] result = new long[2];

        for (int i = 0; i < 2; i += 2) { // Even halfwords (0, 2)
            long product = (long) a[i] * (long) b[i];
            result[i / 2] = (int) (product >> 15); // Fractional, shift right 15
        }

        cpu.setGPR(rt, packWords(result));
    }

    public void evmhessfs(int rt, int ra, int rb) {
        short[] a = unpackHalfwords(cpu.getGPRVal(ra));
        short[] b = unpackHalfwords(cpu.getGPRVal(rb));
        long[] result = new long[2];

        for (int i = 0; i < 2; i += 2) {
            long product = (long) a[i] * (long) b[i];
            long shifted = product >> 15;
            result[i / 2] = (shifted > Integer.MAX_VALUE) ? Integer.MAX_VALUE :
                            (shifted < Integer.MIN_VALUE) ? Integer.MIN_VALUE : (int) shifted;
        }

        cpu.setGPR(rt, packWords(result));
    }

    public void evmheumi(int rt, int ra, int rb) {
        short[] a = unpackHalfwords(cpu.getGPRVal(ra));
        short[] b = unpackHalfwords(cpu.getGPRVal(rb));
        long[] result = new long[2];

        for (int i = 0; i < 2; i += 2) {
            long product = (long) (a[i] & 0xFFFF) * (long) (b[i] & 0xFFFF);
            result[i / 2] = (int) product;
        }

        cpu.setGPR(rt, packWords(result));
    }

    public void evmhogsmfaa(int rt, int ra, int rb) {
        short[] a = unpackHalfwords(cpu.getGPRVal(ra));
        short[] b = unpackHalfwords(cpu.getGPRVal(rb));
        long[] acc = unpackWords(cpu.getAcc());
        long[] result = new long[2];

        for (int i = 1; i < 2; i += 2) { // Odd halfwords (1, 3)
            long product = (long) a[i] * (long) b[i];
            result[i / 2] = (int) (product >> 15);
            acc[i / 2] += result[i / 2];
        }

        cpu.setGPR(rt, packWords(result));
        cpu.setAcc(packWords(acc));
    }

    public void evmwhssf(int rt, int ra, int rb) {
        long[] a = unpackWords(cpu.getGPRVal(ra));
        long[] b = unpackWords(cpu.getGPRVal(rb));
        long[] result = new long[2];

        for (int i = 0; i < 2; i++) {
            long product = (long) a[i] * (long) b[i];
            result[i] = (int) (product >> 31); // High 32 bits, fractional
        }

        cpu.setGPR(rt, packWords(result));
    }

    public void evmwlumi(int rt, int ra, int rb) {
        long[] a = unpackWords(cpu.getGPRVal(ra));
        long[] b = unpackWords(cpu.getGPRVal(rb));
        long[] result = new long[2];

        for (int i = 0; i < 2; i++) {
            long product = (long) (a[i] & 0xFFFFFFFFL) * (long) (b[i] & 0xFFFFFFFFL);
            result[i] = (int) product; // Low 32 bits
        }

        cpu.setGPR(rt, packWords(result));
    }

    public void evmwhumi(int rt, int ra, int rb) {
        long[] a = unpackWords(cpu.getGPRVal(ra));
        long[] b = unpackWords(cpu.getGPRVal(rb));
        long[] result = new long[2];

        for (int i = 0; i < 2; i++) {
            long product = (long) (a[i] & 0xFFFFFFFFL) * (long) (b[i] & 0xFFFFFFFFL);
            result[i] = (int) (product >> 32); // High 32 bits
        }

        cpu.setGPR(rt, packWords(result));
    }

    public void evmwsmfaa(int rt, int ra, int rb) {
        long[] a = unpackWords(cpu.getGPRVal(ra));
        long[] b = unpackWords(cpu.getGPRVal(rb));
        long[] acc = unpackWords(cpu.getAcc());
        long[] result = new long[2];

        for (int i = 0; i < 2; i++) {
            long product = (long) a[i] * (long) b[i];
            result[i] = (int) (product >> 31);
            acc[i] += result[i];
        }

        cpu.setGPR(rt, packWords(result));
        cpu.setAcc(packWords(acc));
    }

    // --- Logical Instructions ---

    public void evand(int rt, int ra, int rb) {
        long a = cpu.getGPRVal(ra);
        long b = cpu.getGPRVal(rb);
        cpu.setGPR(rt, a & b);
    }

    public void evor(int rt, int ra, int rb) {
        long a = cpu.getGPRVal(ra);
        long b = cpu.getGPRVal(rb);
        cpu.setGPR(rt, a | b);
    }

    public void evxor(int rt, int ra, int rb) {
        long a = cpu.getGPRVal(ra);
        long b = cpu.getGPRVal(rb);
        cpu.setGPR(rt, a ^ b);
    }

    public void evnand(int rt, int ra, int rb) {
        long a = cpu.getGPRVal(ra);
        long b = cpu.getGPRVal(rb);
        cpu.setGPR(rt, ~(a & b));
    }

    public void evnor(int rt, int ra, int rb) {
        long a = cpu.getGPRVal(ra);
        long b = cpu.getGPRVal(rb);
        cpu.setGPR(rt, ~(a | b));
    }

    public void eveqv(int rt, int ra, int rb) {
        long a = cpu.getGPRVal(ra);
        long b = cpu.getGPRVal(rb);
        cpu.setGPR(rt, ~(a ^ b));
    }

    // --- Shift and Rotate Instructions ---

    public void evslw(int rt, int ra, int rb) {
        long[] a = unpackWords(cpu.getGPRVal(ra));
        long[] b = unpackWords(cpu.getGPRVal(rb));
        long[] result = new long[2];

        for (int i = 0; i < 2; i++) {
            long shift = b[i] & 0x1F; // Lower 5 bits
            result[i] = a[i] << shift;
        }

        cpu.setGPR(rt, packWords(result));
    }

    public void evsrw(int rt, int ra, int rb) {
        long[] a = unpackWords(cpu.getGPRVal(ra));
        long[] b = unpackWords(cpu.getGPRVal(rb));
        long[] result = new long[2];

        for (int i = 0; i < 2; i++) {
            long shift = b[i] & 0x1F;
            result[i] = a[i] >>> shift; // Logical shift
        }

        cpu.setGPR(rt, packWords(result));
    }

    public void evsrwu(int rt, int ra, int rb) {
        long[] a = unpackWords(cpu.getGPRVal(ra));
        long[] b = unpackWords(cpu.getGPRVal(rb));
        long[] result = new long[2];

        for (int i = 0; i < 2; i++) {
            long shift = b[i] & 0x1F;
            result[i] = (a[i] & 0xFFFFFFFF) >>> shift; // Unsigned shift
        }

        cpu.setGPR(rt, packWords(result));
    }

    public void evslwi(int rt, int ra, int si) {
        long[] a = unpackWords(cpu.getGPRVal(ra));
        long[] result = new long[2];

        for (int i = 0; i < 2; i++) {
            result[i] = a[i] << (si & 0x1F);
        }

        cpu.setGPR(rt, packWords(result));
    }

    public void evsrwi(int rt, int ra, int si) {
        long[] a = unpackWords(cpu.getGPRVal(ra));
        long[] result = new long[2];

        for (int i = 0; i < 2; i++) {
            result[i] = a[i] >>> (si & 0x1F);
        }

        cpu.setGPR(rt, packWords(result));
    }

    public void evsraw(int rt, int ra, int si) {
        long[] a = unpackWords(cpu.getGPRVal(ra));
        long[] result = new long[2];

        for (int i = 0; i < 2; i++) {
            result[i] = a[i] >> (si & 0x1F); // Arithmetic shift
        }

        cpu.setGPR(rt, packWords(result));
    }

    // --- Comparison Instructions ---

    public void evcmpgts(int rt, int ra, int rb) {
        long[] a = unpackWords(cpu.getGPRVal(ra));
        long[] b = unpackWords(cpu.getGPRVal(rb));
        int result = 0;

        for (int i = 0; i < 2; i++) {
            if (a[i] > b[i]) {
                result |= (i == 0) ? 0x8 : 0x2; // LT=0, GT=1, EQ=0 for slot 0; LT=0, GT=0, EQ=1 for slot 1
            }
        }

        cpu.updateCRField(6, result);
    }

    public void evcmplts(int rt, int ra, int rb) {
        long[] a = unpackWords(cpu.getGPRVal(ra));
        long[] b = unpackWords(cpu.getGPRVal(rb));
        int result = 0;

        for (int i = 0; i < 2; i++) {
            if (a[i] < b[i]) {
                result |= (i == 0) ? 0x8 : 0x2;
            }
        }

        cpu.updateCRField(6, result);
    }

    public void evcmpeq(int rt, int ra, int rb) {
        long[] a = unpackWords(cpu.getGPRVal(ra));
        long[] b = unpackWords(cpu.getGPRVal(rb));
        int result = 0;

        for (int i = 0; i < 2; i++) {
            if (a[i] == b[i]) {
                result |= (i == 0) ? 0x2 : 0x1;
            }
        }

        cpu.updateCRField(6, result);
    }

    // --- Merge/Select Instructions ---

    public void evmergehi(int rt, int ra, int rb) {
        long[] a = unpackWords(cpu.getGPRVal(ra));
        long[] b = unpackWords(cpu.getGPRVal(rb));
        long[] result = new long[2];

        result[0] = a[0];
        result[1] = b[0];

        cpu.setGPR(rt, packWords(result));
    }

    public void evmergelo(int rt, int ra, int rb) {
        long[] a = unpackWords(cpu.getGPRVal(ra));
        long[] b = unpackWords(cpu.getGPRVal(rb));
        long[] result = new long[2];

        result[0] = a[1];
        result[1] = b[1];

        cpu.setGPR(rt, packWords(result));
    }

    public void evsel(int rt, int ra, int rb) {
        long[] a = unpackWords(cpu.getGPRVal(ra));
        long[] b = unpackWords(cpu.getGPRVal(rb));
        long cr6 = cpu.getCrField(6); // CR6 bits
        long[] result = new long[2];

        for (int i = 0; i < 2; i++) {
            boolean selectA = (i == 0) ? (cr6 & 0x2) != 0 : (cr6 & 0x1) != 0; // EQ bit for slot 0, slot 1
            result[i] = selectA ? a[i] : b[i];
        }

        cpu.setGPR(rt, packWords(result));
    }

    // --- Load/Store Instructions ---

    public void evldd(int rt, int ra, int displacement) {
        long address = calculateAddress(ra, displacement);
        if ((address & 0x7) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, false, false);
            long value = ((long) cpu.readMemoryWord(physAddress) << 32) |
                         (cpu.readMemoryWord(physAddress + 4) & 0xFFFFFFFFL);
            cpu.setGPR(rt, value);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void evldw(int rt, int ra, int displacement) {
        long address = calculateAddress(ra, displacement);
        if ((address & 0x3) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, false, false);
            long[] result = new long[2];
            result[0] = cpu.readMemoryWord(physAddress);
            result[1] = cpu.readMemoryWord(physAddress + 4);
            cpu.setGPR(rt, packWords(result));
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void evlhh(int rt, int ra, int displacement) {
        long address = calculateAddress(ra, displacement);
        if ((address & 0x1) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, false, false);
            long[] result = new long[2];
            result[0] = (cpu.readMemoryByte(physAddress) & 0xFF) << 16 |
                        (cpu.readMemoryByte(physAddress + 1) & 0xFF);
            result[1] = (cpu.readMemoryByte(physAddress + 2) & 0xFF) << 16 |
                        (cpu.readMemoryByte(physAddress + 3) & 0xFF);
            cpu.setGPR(rt, packWords(result));
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void evstdd(int rt, int ra, int displacement) {
        long address = calculateAddress(ra, displacement);
        if ((address & 0x7) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, true, false);
            long value = cpu.getGPRVal(rt);
            cpu.writeMemoryWord(physAddress, (int) (value >>> 32));
            cpu.writeMemoryWord(physAddress + 4, (int) value);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void evstdw(int rt, int ra, int displacement) {
        long address = calculateAddress(ra, displacement);
        if ((address & 0x3) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, true, false);
            long[] value = unpackWords(cpu.getGPRVal(rt));
            cpu.writeMemoryWord(physAddress, value[0]);
            cpu.writeMemoryWord(physAddress + 4, value[1]);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void evsth(int rt, int ra, int displacement) {
        long address = calculateAddress(ra, displacement);
        if ((address & 0x1) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, true, false);
            long[] value = unpackWords(cpu.getGPRVal(rt));
            cpu.writeMemoryByte(physAddress, (byte) (value[0] >> 16));
            cpu.writeMemoryByte(physAddress + 1, (byte) value[0]);
            cpu.writeMemoryByte(physAddress + 2, (byte) (value[1] >> 16));
            cpu.writeMemoryByte(physAddress + 3, (byte) value[1]);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    // --- Helper Methods ---

    private long[] unpackWords(long value) {
        return new long[]{ (value >>> 32), value};
    }

    private short[] unpackHalfwords(long value) {
        return new short[]{(short) (value >>> 48), (short) (value >>> 32),
                          (short) (value >>> 16), (short) value};
    }

    private long packWords(long[] values) {
        return ((long) values[0] << 32) | (values[1] & 0xFFFFFFFFL);
    }

    private long calculateAddress(int ra, int displacement) {
        return (ra == 0 ? 0 : cpu.getGPRVal(ra)) + displacement;
    }

    private void updateXER(boolean overflow) {
        if (overflow) {
            long xer = cpu.getXer();
            xer |= (1L << 31); // Set OV
            xer |= (1L << 30); // Set SO
            cpu.setXer(xer);
        }
    }
}