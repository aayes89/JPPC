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
 * Handles PowerPC memory instructions for Xenon CPU emulation.
 *
 * @author Slam
 */
public class MemoryInst {

    private final CPU cpu;

    public MemoryInst(CPU cpu) {
        this.cpu = cpu;
    }

    /**
     * Executes primary memory instructions (opcodes 32–47, excluding FP).
     *
     * @param fields Instruction fields from DecodingInstr.
     */
    public void executePrimary(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("D")) {
            throw new IllegalArgumentException("Invalid format for primary memory instruction: " + fields.format());
        }
        switch (fields.opcode()) {
            case 32: // lbz
                lbz(fields.rt(), fields.ra(), fields.si());
                break;
            case 33: // lbzu
                lbzu(fields.rt(), fields.ra(), fields.si());
                break;
            case 34: // lhz
                lhz(fields.rt(), fields.ra(), fields.si());
                break;
            case 35: // lhzu
                lhzu(fields.rt(), fields.ra(), fields.si());
                break;
            case 36: // lwz
                lwz(fields.rt(), fields.ra(), fields.si());
                break;
            case 37: // lwzu
                lwzu(fields.rt(), fields.ra(), fields.si());
                break;
            case 38: // lha
                lha(fields.rt(), fields.ra(), fields.si());
                break;
            case 40: // stb
                stb(fields.rt(), fields.ra(), fields.si());
                break;
            case 41: // stbu
                stbu(fields.rt(), fields.ra(), fields.si());
                break;
            case 42: // sth
                sth(fields.rt(), fields.ra(), fields.si());
                break;
            case 43: // sthu
                sthu(fields.rt(), fields.ra(), fields.si());
                break;
            case 44: // stw
                stw(fields.rt(), fields.ra(), fields.si());
                break;
            case 45: // stwu
                stwu(fields.rt(), fields.ra(), fields.si());
                break;
            case 46: // lmw
                lmw(fields.rt(), fields.ra(), fields.si());
                break;
            case 47: // stmw
                stmw(fields.rt(), fields.ra(), fields.si());
                break;
            default:
                throw new UnsupportedOperationException("Unsupported primary memory opcode: " + fields.opcode());
        }
    }

    /**
     * Executes extended memory instructions (opcode 31).
     *
     * @param fields Instruction fields from DecodingInstr.
     */
    public void executeExtended(DecodingInstr.InstructionFields fields) {
        if (!fields.format().equals("X")) {
            throw new IllegalArgumentException("Invalid format for extended memory instruction: " + fields.format());
        }
        switch (fields.xo()) {
            case 20: // lwarx
                lwarx(fields.rt(), fields.ra(), fields.rb());
                break;
            case 23: // lwzx
                lwzx(fields.rt(), fields.ra(), fields.rb());
                break;
            case 55: // lwzux
                lwzux(fields.rt(), fields.ra(), fields.rb());
                break;
            case 84: // ldarx
                ldarx(fields.rt(), fields.ra(), fields.rb());
                break;
            case 86: // dcbf
                dcbf(fields.ra(), fields.rb());
                break;
            case 54: // dcbst
                dcbst(fields.ra(), fields.rb());
                break;
            case 119: // lbzx
                lbzx(fields.rt(), fields.ra(), fields.rb());
                break;
            case 150: // stwcx.
                stwcx(fields.rt(), fields.ra(), fields.rb());
                break;
            case 151: // stwx
                stwx(fields.rt(), fields.ra(), fields.rb());
                break;
            case 183: // stwux
                stwux(fields.rt(), fields.ra(), fields.rb());
                break;
            case 214: // stdcx.
                stdcx(fields.rt(), fields.ra(), fields.rb());
                break;
            case 215: // stbx
                stbx(fields.rt(), fields.ra(), fields.rb());
                break;
            case 246: // dcbtst
                dcbtst(fields.ra(), fields.rb());
                break;
            case 278: // dcbt
                dcbt(fields.ra(), fields.rb());
                break;
            case 375: // lhaux
                lhaux(fields.rt(), fields.ra(), fields.rb());
                break;
            case 438: // ecowx
                ecowx(fields.rt(), fields.ra(), fields.rb());
                break;
            case 439: // sthux
                sthux(fields.rt(), fields.ra(), fields.rb());
                break;
            case 533: // lswx
                lswx(fields.rt(), fields.ra(), fields.rb());
                break;
            case 598: // sync
                sync();
                break;
            case 661: // stswx
                stswx(fields.rt(), fields.ra(), fields.rb());
                break;
            case 660: // stbux
                stbux(fields.rt(), fields.ra(), fields.rb());
                break;
            case 694: // stwbrx
                stwbrx(fields.rt(), fields.ra(), fields.rb());
                break;
            case 790: // lhbrx
                lhbrx(fields.rt(), fields.ra(), fields.rb());
                break;
            case 918: // sthbrx
                sthbrx(fields.rt(), fields.ra(), fields.rb());
                break;
            case 982: // icbi
                icbi(fields.ra(), fields.rb());
                break;
            case 1014: // dcbz
                dcbz(fields.ra(), fields.rb());
                break;
            default:
                throw new UnsupportedOperationException("Unsupported extended memory XO: " + fields.xo());
        }
    }

    // --- Byte Access ---

    public void lbz(int rt, int ra, int displacement) {
        long address = calculateAddress(ra, displacement);
        try {
            long physAddress = cpu.getMmu().translateAddress(address, false, false);
            cpu.setGPR(rt, cpu.readMemoryByte(physAddress) & 0xFF);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000); // Protection violation
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void lbzu(int rt, int ra, int displacement) {
        if (ra == 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_INVALID);
            return;
        }
        long address = cpu.getGPRVal(ra) + displacement;
        try {
            long physAddress = cpu.getMmu().translateAddress(address, false, false);
            cpu.setGPR(rt, cpu.readMemoryByte(physAddress) & 0xFF);
            cpu.setGPR(ra, address);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void lbzx(int rt, int ra, int rb) {
        long address = calculateAddress(ra, cpu.getGPRVal(rb));
        try {
            long physAddress = cpu.getMmu().translateAddress(address, false, false);
            cpu.setGPR(rt, cpu.readMemoryByte(physAddress) & 0xFF);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void lbzux(int rt, int ra, int rb) {
        if (ra == 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_INVALID);
            return;
        }
        long address = calculateAddress(ra, cpu.getGPRVal(rb));
        try {
            long physAddress = cpu.getMmu().translateAddress(address, false, false);
            cpu.setGPR(rt, cpu.readMemoryByte(physAddress) & 0xFF);
            cpu.setGPR(ra, address);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void stb(int rs, int ra, int displacement) {
        long address = calculateAddress(ra, displacement);
        try {
            long physAddress = cpu.getMmu().translateAddress(address, true, false);
            cpu.writeMemoryByte(physAddress, (byte) (cpu.getGPRVal(rs) & 0xFF));
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void stbu(int rs, int ra, int displacement) {
        if (ra == 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_INVALID);
            return;
        }
        long address = cpu.getGPRVal(ra) + displacement;
        try {
            long physAddress = cpu.getMmu().translateAddress(address, true, false);
            cpu.writeMemoryByte(physAddress, (byte) (cpu.getGPRVal(rs) & 0xFF));
            cpu.setGPR(ra, address);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void stbx(int rs, int ra, int rb) {
        long address = calculateAddress(ra, cpu.getGPRVal(rb));
        try {
            long physAddress = cpu.getMmu().translateAddress(address, true, false);
            cpu.writeMemoryByte(physAddress, (byte) (cpu.getGPRVal(rs) & 0xFF));
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void stbux(int rs, int ra, int rb) {
        if (ra == 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_INVALID);
            return;
        }
        long address = calculateAddress(ra, cpu.getGPRVal(rb));
        try {
            long physAddress = cpu.getMmu().translateAddress(address, true, false);
            cpu.writeMemoryByte(physAddress, (byte) (cpu.getGPRVal(rs) & 0xFF));
            cpu.setGPR(ra, address);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    // --- Halfword Access ---

    public void lhz(int rt, int ra, int displacement) {
        long address = calculateAddress(ra, displacement);
        if ((address & 0x1) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, false, false);
            long value = cpu.readMemoryWord(physAddress) >>> 16;
            cpu.setGPR(rt, value & 0xFFFF);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void lhzu(int rt, int ra, int displacement) {
        if (ra == 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_INVALID);
            return;
        }
        long address = cpu.getGPRVal(ra) + displacement;
        if ((address & 0x1) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, false, false);
            long value = cpu.readMemoryWord(physAddress) >>> 16;
            cpu.setGPR(rt, value & 0xFFFF);
            cpu.setGPR(ra, address);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void lhzx(int rt, int ra, int rb) {
        long address = calculateAddress(ra, cpu.getGPRVal(rb));
        if ((address & 0x1) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, false, false);
            long value = cpu.readMemoryWord(physAddress) >>> 16;
            cpu.setGPR(rt, value & 0xFFFF);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void lha(int rt, int ra, int displacement) {
        long address = calculateAddress(ra, displacement);
        if ((address & 0x1) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, false, false);
            long value = cpu.readMemoryWord(physAddress) >>> 16;
            cpu.setGPR(rt, (short) value);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void lhax(int rt, int ra, int rb) {
        long address = calculateAddress(ra, cpu.getGPRVal(rb));
        if ((address & 0x1) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, false, false);
            long value = cpu.readMemoryWord(physAddress) >>> 16;
            cpu.setGPR(rt, (short) value);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void lhaux(int rt, int ra, int rb) {
        if (ra == 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_INVALID);
            return;
        }
        long address = calculateAddress(ra, cpu.getGPRVal(rb));
        if ((address & 0x1) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, false, false);
            long value = cpu.readMemoryWord(physAddress) >>> 16;
            cpu.setGPR(rt, (short) value);
            cpu.setGPR(ra, address);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void sth(int rs, int ra, int displacement) {
        long address = calculateAddress(ra, displacement);
        if ((address & 0x1) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, true, false);
            cpu.writeMemoryWord(physAddress, (int) cpu.getGPRVal(rs) << 16);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void sthu(int rs, int ra, int displacement) {
        if (ra == 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_INVALID);
            return;
        }
        long address = cpu.getGPRVal(ra) + displacement;
        if ((address & 0x1) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, true, false);
            cpu.writeMemoryWord(physAddress, (int) cpu.getGPRVal(rs) << 16);
            cpu.setGPR(ra, address);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void sthx(int rs, int ra, int rb) {
        long address = calculateAddress(ra, cpu.getGPRVal(rb));
        if ((address & 0x1) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, true, false);
            cpu.writeMemoryWord(physAddress, (int) cpu.getGPRVal(rs) << 16);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void sthux(int rs, int ra, int rb) {
        if (ra == 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_INVALID);
            return;
        }
        long address = calculateAddress(ra, cpu.getGPRVal(rb));
        if ((address & 0x1) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, true, false);
            cpu.writeMemoryWord(physAddress, (int) cpu.getGPRVal(rs) << 16);
            cpu.setGPR(ra, address);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void lhbrx(int rt, int ra, int rb) {
        long address = calculateAddress(ra, cpu.getGPRVal(rb));
        if ((address & 0x1) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, false, false);
            long value = cpu.readMemoryWord(physAddress) >>> 16;
            long reversed = ((value & 0xFF) << 8) | ((value >> 8) & 0xFF);
            cpu.setGPR(rt, reversed);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void sthbrx(int rs, int ra, int rb) {
        long address = calculateAddress(ra, cpu.getGPRVal(rb));
        if ((address & 0x1) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, true, false);
            int value = (int) cpu.getGPRVal(rs) & 0xFFFF;
            int reversed = ((value & 0xFF) << 8) | ((value >> 8) & 0xFF);
            cpu.writeMemoryWord(physAddress, reversed << 16);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    // --- Word Access ---

    public void lwz(int rt, int ra, int displacement) {
        long address = calculateAddress(ra, displacement);
        if ((address & 0x3) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, false, false);
            cpu.setGPR(rt, cpu.readMemoryWord(physAddress) & 0xFFFFFFFFL);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void lwzu(int rt, int ra, int displacement) {
        if (ra == 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_INVALID);
            return;
        }
        long address = cpu.getGPRVal(ra) + displacement;
        if ((address & 0x3) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, false, false);
            cpu.setGPR(rt, cpu.readMemoryWord(physAddress) & 0xFFFFFFFFL);
            cpu.setGPR(ra, address);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void lwzx(int rt, int ra, int rb) {
        long address = calculateAddress(ra, cpu.getGPRVal(rb));
        if ((address & 0x3) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, false, false);
            cpu.setGPR(rt, cpu.readMemoryWord(physAddress) & 0xFFFFFFFFL);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void lwzux(int rt, int ra, int rb) {
        if (ra == 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_INVALID);
            return;
        }
        long address = calculateAddress(ra, cpu.getGPRVal(rb));
        if ((address & 0x3) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, false, false);
            cpu.setGPR(rt, cpu.readMemoryWord(physAddress) & 0xFFFFFFFFL);
            cpu.setGPR(ra, address);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void stw(int rs, int ra, int displacement) {
        long address = calculateAddress(ra, displacement);
        if ((address & 0x3) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, true, false);
            cpu.writeMemoryWord(physAddress, (int) cpu.getGPRVal(rs));
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void stwu(int rs, int ra, int displacement) {
        if (ra == 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_INVALID);
            return;
        }
        long address = cpu.getGPRVal(ra) + displacement;
        if ((address & 0x3) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, true, false);
            cpu.writeMemoryWord(physAddress, (int) cpu.getGPRVal(rs));
            cpu.setGPR(ra, address);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void stwx(int rs, int ra, int rb) {
        long address = calculateAddress(ra, cpu.getGPRVal(rb));
        if ((address & 0x3) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, true, false);
            cpu.writeMemoryWord(physAddress, (int) cpu.getGPRVal(rs));
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void stwux(int rs, int ra, int rb) {
        if (ra == 0) {
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_INVALID);
            return;
        }
        long address = calculateAddress(ra, cpu.getGPRVal(rb));
        if ((address & 0x3) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, true, false);
            cpu.writeMemoryWord(physAddress, (int) cpu.getGPRVal(rs));
            cpu.setGPR(ra, address);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void lwbrx(int rt, int ra, int rb) {
        long address = calculateAddress(ra, cpu.getGPRVal(rb));
        if ((address & 0x3) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, false, false);
            long value = cpu.readMemoryWord(physAddress);
            long reversed = ((value & 0xFF) << 24) | (((value >> 8) & 0xFF) << 16) |
                           (((value >> 16) & 0xFF) << 8) | ((value >> 24) & 0xFF);
            cpu.setGPR(rt, reversed & 0xFFFFFFFFL);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void stwbrx(int rs, int ra, int rb) {
        long address = calculateAddress(ra, cpu.getGPRVal(rb));
        if ((address & 0x3) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, true, false);
            int value = (int) cpu.getGPRVal(rs);
            int reversed = ((value & 0xFF) << 24) | (((value >> 8) & 0xFF) << 16) |
                           (((value >> 16) & 0xFF) << 8) | ((value >> 24) & 0xFF);
            cpu.writeMemoryWord(physAddress, reversed);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    // --- Multiple Word Access ---

    public void lmw(int rt, int ra, int displacement) {
        long address = calculateAddress(ra, displacement);
        if ((address & 0x3) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            for (int reg = rt; reg <= 31; reg++) {
                long physAddress = cpu.getMmu().translateAddress(address, false, false);
                cpu.setGPR(reg, cpu.readMemoryWord(physAddress) & 0xFFFFFFFFL);
                address += 4;
            }
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void stmw(int rs, int ra, int displacement) {
        long address = calculateAddress(ra, displacement);
        if ((address & 0x3) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            for (int reg = rs; reg <= 31; reg++) {
                long physAddress = cpu.getMmu().translateAddress(address, true, false);
                cpu.writeMemoryWord(physAddress, (int) cpu.getGPRVal(reg));
                address += 4;
            }
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    // --- Atomic Operations ---

    public void lwarx(int rt, int ra, int rb) {
        long address = calculateAddress(ra, cpu.getGPRVal(rb));
        if ((address & 0x3) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, false, false);
            cpu.setGPR(rt, cpu.readMemoryWord(physAddress) & 0xFFFFFFFFL);
            cpu.setReserveAddress(physAddress);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void ldarx(int rt, int ra, int rb) {
        long address = calculateAddress(ra, cpu.getGPRVal(rb));
        if ((address & 0x3) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, false, false);
            cpu.setGPR(rt, cpu.readMemoryWord(physAddress) & 0xFFFFFFFFL);
            cpu.setReserveAddress(physAddress);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void stwcx(int rs, int ra, int rb) {
        long address = calculateAddress(ra, cpu.getGPRVal(rb));
        if ((address & 0x3) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, true, false);
            int crf = 0; // Failure
            if (cpu.getReserveAddress() == physAddress) {
                cpu.writeMemoryWord(physAddress, (int) cpu.getGPRVal(rs));
                crf = 0x2; // Success (CR0[EQ]=1)
            }
            cpu.clearReserve();
            cpu.updateCRField(0, crf);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void stdcx(int rs, int ra, int rb) {
        long address = calculateAddress(ra, cpu.getGPRVal(rb));
        if ((address & 0x3) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, true, false);
            int crf = 0; // Failure
            if (cpu.getReserveAddress() == physAddress) {
                cpu.writeMemoryWord(physAddress, (int) cpu.getGPRVal(rs));
                crf = 0x2; // Success (CR0[EQ]=1)
            }
            cpu.clearReserve();
            cpu.updateCRField(0, crf);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    // --- String Operations ---

    public void lswi(int rt, int ra, int nb) {
        long address = calculateAddress(ra, 0);
        int reg = rt;
        int bytesRemaining = (nb == 0) ? 32 : nb;
        try {
            while (bytesRemaining > 0) {
                long physAddress = cpu.getMmu().translateAddress(address, false, false);
                int bytesToRead = Math.min(4, bytesRemaining);
                int value = 0;
                for (int i = 0; i < bytesToRead; i++) {
                    value = (value << 8) | (cpu.readMemoryByte(physAddress + i) & 0xFF);
                }
                if (bytesToRead < 4) {
                    value <<= (4 - bytesToRead) * 8;
                }
                cpu.setGPR(reg, value & 0xFFFFFFFFL);
                reg = (reg + 1) % 32;
                address += bytesToRead;
                bytesRemaining -= bytesToRead;
            }
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void lswx(int rt, int ra, int rb) {
        long address = calculateAddress(ra, cpu.getGPRVal(rb));
        int reg = rt;
        int bytesRemaining = (int) (cpu.getXer() & 0x7F); // XER[TBC]
        try {
            while (bytesRemaining > 0) {
                long physAddress = cpu.getMmu().translateAddress(address, false, false);
                int bytesToRead = Math.min(4, bytesRemaining);
                int value = 0;
                for (int i = 0; i < bytesToRead; i++) {
                    value = (value << 8) | (cpu.readMemoryByte(physAddress + i) & 0xFF);
                }
                if (bytesToRead < 4) {
                    value <<= (4 - bytesToRead) * 8;
                }
                cpu.setGPR(reg, value & 0xFFFFFFFFL);
                reg = (reg + 1) % 32;
                address += bytesToRead;
                bytesRemaining -= bytesToRead;
            }
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void stswi(int rs, int ra, int nb) {
        long address = calculateAddress(ra, 0);
        int reg = rs;
        int bytesRemaining = (nb == 0) ? 32 : nb;
        try {
            while (bytesRemaining > 0) {
                long physAddress = cpu.getMmu().translateAddress(address, true, false);
                int bytesToWrite = Math.min(4, bytesRemaining);
                long value = cpu.getGPRVal(reg);
                for (int i = 0; i < bytesToWrite; i++) {
                    cpu.writeMemoryByte(physAddress + i, (byte) (value >> (24 - i * 8)));
                }
                reg = (reg + 1) % 32;
                address += bytesToWrite;
                bytesRemaining -= bytesToWrite;
            }
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void stswx(int rs, int ra, int rb) {
        long address = calculateAddress(ra, cpu.getGPRVal(rb));
        int reg = rs;
        int bytesRemaining = (int) (cpu.getXer() & 0x7F); // XER[TBC]
        try {
            while (bytesRemaining > 0) {
                long physAddress = cpu.getMmu().translateAddress(address, true, false);
                int bytesToWrite = Math.min(4, bytesRemaining);
                long value = cpu.getGPRVal(reg);
                for (int i = 0; i < bytesToWrite; i++) {
                    cpu.writeMemoryByte(physAddress + i, (byte) (value >> (24 - i * 8)));
                }
                reg = (reg + 1) % 32;
                address += bytesToWrite;
                bytesRemaining -= bytesToWrite;
            }
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    // --- Cache and Synchronization Operations ---

    public void dcbf(int ra, int rb) {
        // Data Cache Block Flush (no-op in simple emulator)
        long address = calculateAddress(ra, cpu.getGPRVal(rb));
        try {
            cpu.getMmu().translateAddress(address, true, false); // Validate address
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void dcbst(int ra, int rb) {
        // Data Cache Block Store (no-op in simple emulator)
        long address = calculateAddress(ra, cpu.getGPRVal(rb));
        try {
            cpu.getMmu().translateAddress(address, true, false); // Validate address
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void dcbt(int ra, int rb) {
        // Data Cache Block Touch (no-op in simple emulator)
        long address = calculateAddress(ra, cpu.getGPRVal(rb));
        try {
            cpu.getMmu().translateAddress(address, false, false); // Validate address
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void dcbtst(int ra, int rb) {
        // Data Cache Block Touch for Store (no-op in simple emulator)
        long address = calculateAddress(ra, cpu.getGPRVal(rb));
        try {
            cpu.getMmu().translateAddress(address, true, false); // Validate address
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void dcbz(int ra, int rb) {
        long address = calculateAddress(ra, cpu.getGPRVal(rb));
        if ((address & 0x1F) != 0) { // 32-byte cache line alignment
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, true, false);
            for (int i = 0; i < 32; i += 4) {
                cpu.writeMemoryWord(physAddress + i, 0);
            }
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void icbi(int ra, int rb) {
        // Instruction Cache Block Invalidate (no-op in simple emulator)
        long address = calculateAddress(ra, cpu.getGPRVal(rb));
        try {
            cpu.getMmu().translateAddress(address, true, true); // Instruction access
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ISI);
        }
    }

    public void sync() {
        // Synchronize (no-op in simple emulator)
    }

    public void eieio() {
        // Enforce In-Order Execution of I/O (no-op in simple emulator)
    }

    // --- External Control Operations ---

    public void eciwx(int rt, int ra, int rb) {
        long address = calculateAddress(ra, cpu.getGPRVal(rb));
        if ((address & 0x3) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, false, false);
            cpu.setGPR(rt, cpu.readMemoryWord(physAddress) & 0xFFFFFFFFL);
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    public void ecowx(int rs, int ra, int rb) {
        long address = calculateAddress(ra, cpu.getGPRVal(rb));
        if ((address & 0x3) != 0) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_ALIGNMENT);
            return;
        }
        try {
            long physAddress = cpu.getMmu().translateAddress(address, true, false);
            cpu.writeMemoryWord(physAddress, (int) cpu.getGPRVal(rs));
        } catch (IllegalArgumentException e) {
            cpu.setDar(address);
            cpu.setDsisr(0x40000000);
            cpu.handleInterrupt(ExceptionHandler.EXCEPTION_DSI);
        }
    }

    // --- Helper Methods ---

    private long calculateAddress(int ra, long displacement) {
        return (ra == 0 ? 0 : cpu.getGPRVal(ra)) + displacement;
    }
}