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
        long sr = segmentRegisters[(int)segment];
        if ((sr & 0x80000000) != 0) { // Direct Store Segment
            cpu.getExceptionHandler().handleException(
                isInstruction ? ExceptionHandler.EXCEPTION_ISI : ExceptionHandler.EXCEPTION_DSI,
                cpu.getPc(), virtualAddress
            );
            return 0;
        }
        long vsid = sr & 0xFFFFFF;
        // BAT translation
        long batIndex = segment * 2 + (isInstruction ? 0 : 8);
        if (batIndex < batRegisters.length - 1) {
            int batu = batRegisters[(int)batIndex];
            int batl = batRegisters[(int)batIndex + 1];
            if ((batu & 0x1) != 0) { // Valid BAT
                int blockSize = ((batu >>> 2) & 0x1FF) | ((batu >>> 21) & 0x3FE00);
                int mask = ~(blockSize << 17);
                int basePhysical = batl & 0xFFFFF000;
                long offset = virtualAddress & 0x0FFFFFFF;
                return basePhysical | (offset & mask);
            }
        }
        // TLB translation
        long vpn = (virtualAddress >>> 12) & 0xFFFF;
        for (TLBEntry entry : tlb) {
            if (entry.valid && entry.vsid == vsid && entry.vpn == vpn) {
                return (entry.ppn << 12) | (virtualAddress & 0xFFF);
            }
        }
        // Translation failure
        cpu.getExceptionHandler().handleException(
            isInstruction ? ExceptionHandler.EXCEPTION_ISI : ExceptionHandler.EXCEPTION_DSI,
            cpu.getPc(), virtualAddress
        );
        return 0;
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