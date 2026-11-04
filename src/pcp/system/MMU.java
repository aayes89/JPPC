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
package pcp.system;

import java.util.Arrays;
import pcp.cpu.CPU;

/**
 *
 * @author Slam
 */

public class MMU {
    private final CPU cpu;
    private final int[] batRegisters = new int[16]; // IBAT0-3, DBAT0-3 (8 pairs)
    private final int[] segmentRegisters = new int[16]; // SR0-15

    private static class TLBEntry {
        int vpn; // Virtual Page Number
        int ppn; // Physical Page Number
        boolean valid;
        int vsid; // Virtual Segment ID
    }

    private final TLBEntry[] tlb = new TLBEntry[64]; // 64-entry TLB

    public MMU(CPU cpu) {
        this.cpu = cpu;
        Arrays.fill(batRegisters, 0);
        Arrays.fill(segmentRegisters, 0);
        for (int i = 0; i < tlb.length; i++) {
            tlb[i] = new TLBEntry();
            tlb[i].valid = false;
        }
    }

    public long translateAddress(long virtualAddress, boolean isWrite, boolean isInstruction) {
        long msrBit = isInstruction ? CPU.MSR_IR : CPU.MSR_DR;
        if ((cpu.getMsr() & msrBit) == 0) {
            return virtualAddress; // Real mode
        }
        long segment = (virtualAddress >>> 28) & 0xF;
        int sr = segmentRegisters[(int) segment];
        if ((sr & 0x80000000) != 0) { // Direct-Store segment: unsupported by this MMU model.
            raiseTranslationException(isInstruction, virtualAddress);
            return 0;
        }
        int vsid = sr & 0x00FFFFFF;

        // BATs are global effective-address ranges, not indexed by segment number.
        // Layout: IBAT0U/L..IBAT3U/L at [0..7], DBAT0U/L..DBAT3U/L at [8..15].
        int first = isInstruction ? 0 : 8;
        boolean problemState = (cpu.getMsr() & CPU.MSR_PR) != 0;
        for (int i = 0; i < 4; i++) {
            int batu = batRegisters[first + i * 2];
            int batl = batRegisters[first + i * 2 + 1];
            int validBit = problemState ? 1 : 2; // BATU[Vp] / BATU[Vs]
            if ((batu & validBit) == 0) {
                continue;
            }

            // BATU[BL] encodes a contiguous run of low-order block-mask bits.
            long blockMask = ((((long) batu >>> 2) & 0x1FFL) << 17) | 0x1FFFFL;
            long effectiveBase = (batu & 0xFFFE0000L) & ~blockMask;
            long effective = virtualAddress & 0xFFFFFFFFL;
            if ((effective & ~blockMask) != effectiveBase) {
                continue;
            }

            // BATL[BRPN] supplies the physical block base; preserve the offset.
            long physicalBase = (batl & 0xFFFE0000L) & ~blockMask;
            return (physicalBase | (effective & blockMask)) & 0xFFFFFFFFL;
        }
        // TLB translation
        long vpn = (virtualAddress >>> 12) & 0xFFFF;
        for (TLBEntry entry : tlb) {
            if (entry.valid && entry.vsid == vsid && entry.vpn == vpn) {
                return (entry.ppn << 12) | (virtualAddress & 0xFFF);
            }
        }
        // Translation failure
        raiseTranslationException(isInstruction, virtualAddress);
        return 0;
    }

    private void raiseTranslationException(boolean isInstruction, long address) {
        cpu.getExceptionHandler().handleException(
            isInstruction ? ExceptionHandler.EXCEPTION_ISI : ExceptionHandler.EXCEPTION_DSI,
            cpu.getPc(), address
        );
    }

    public void setBAT(int index, int value) {
        if (index >= 0 && index < batRegisters.length) {
            batRegisters[index] = value;
        }
    }

    public int getBAT(int index) {
        if (index >= 0 && index < batRegisters.length) {
            return batRegisters[index];
        }
        return 0;
    }

    public void setSegmentRegister(int index, int value) {
        if (index >= 0 && index < segmentRegisters.length) {
            segmentRegisters[index] = value;
        }
    }

    public int getSegmentRegister(int index) {
        if (index >= 0 && index < segmentRegisters.length) {
            return segmentRegisters[index];
        }
        return 0;
    }

    public void setTLBEntry(int index, int vpn, int ppn, int vsid) {
        if (index >= 0 && index < tlb.length) {
            tlb[index].vpn = vpn;
            tlb[index].ppn = ppn;
            tlb[index].vsid = vsid;
            tlb[index].valid = true;
        }
    }

    public void invalidateTLB() {
        for (TLBEntry entry : tlb) {
            entry.valid = false;
        }
    }

    public long loadWord(long virtualAddress, boolean isInstruction) {
        long physAddress = translateAddress(virtualAddress, false, isInstruction);
        return cpu.readMemoryWord(physAddress);
    }

    public void storeWord(long virtualAddress, long value, boolean isInstruction) {
        long physAddress = translateAddress(virtualAddress, true, isInstruction);
        cpu.writeMemoryWord(physAddress, value);
    }

    public void initializeBATs() {
        Arrays.fill(batRegisters, 0);
        Arrays.fill(segmentRegisters, 0);
        invalidateTLB();
    }
}