package pcp.TESTS;
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

import pcp.gpu.XenonSata;

/**
 * Pruebas ATA/ATAPI: canal listo, identidad ATAPI, INQUIRY y medio ausente.
 */
public final class XenonSataSelfTest {

    private static void write(XenonSata d, int off, int value) {
        d.write(off, (byte) value);
    }

    private static int read(XenonSata d, int off) {
        return d.read(off) & 0xFF;
    }

    private static void packet(XenonSata d, int opcode, int allocation) {
        write(d, 7, 0xA0);
        write(d, 0, opcode);
        write(d, 0, 0);
        write(d, 0, 0);
        write(d, 0, 0);
        write(d, 0, allocation);
        for (int i = 0; i < 7; i++) {
            write(d, 0, 0);
        }
    }

    public static void main(String[] args) {
        XenonSata d = new XenonSata();
        if (read(d, 7) != 0x40) {
            throw new AssertionError("ATA status inicial");
        }
        write(d, 7, 0xEC); // HDD ausente: no DRQ, no datos inventados
        if (read(d, 7) != 0x40) {
            throw new AssertionError("IDENTIFY DEVICE sin HDD");
        }
        write(d, 7, 0xA1);
        if (read(d, 7) != 0x48) {
            throw new AssertionError("IDENTIFY PACKET status");
        }
        int id0 = d.readWord(0) & 0xFFFF;
        if (id0 != 0x8580) {
            throw new AssertionError(String.format("IDENTIFY PACKET word0=%04X", id0));
        }
        for (int i = 0; i < 255; i++) {
            d.readWord(0);
        }
        if (read(d, 7) != 0x40) {
            throw new AssertionError("IDENTIFY PACKET completion");
        }
        packet(d, 0x12, 36);
        if (read(d, 7) != 0x48) {
            throw new AssertionError("INQUIRY DRQ");
        }
        int[] inquiry = new int[36];
        for (int i = 0; i < inquiry.length; i++) {
            inquiry[i] = read(d, 0);
        }
        if (inquiry[0] != 0x05 || inquiry[1] != 0x80) {
            throw new AssertionError("INQUIRY device type/removable");
        }
        String vendor = new String(new byte[]{(byte) inquiry[8], (byte) inquiry[9], (byte) inquiry[10], (byte) inquiry[11]}, java.nio.charset.StandardCharsets.US_ASCII);
        if (!"JPPC".equals(vendor)) {
            throw new AssertionError("INQUIRY vendor=" + vendor);
        }
        packet(d, 0x00, 0);
        if (read(d, 7) != 0x41 || read(d, 1) != 0x20) {
            throw new AssertionError("TEST UNIT READY must report NOT READY");
        }
        packet(d, 0x03, 18); // REQUEST SENSE
        if (read(d, 7) != 0x48) {
            throw new AssertionError("REQUEST SENSE DRQ");
        }
        int[] sense = new int[18];
        for (int i = 0; i < sense.length; i++) {
            sense[i] = read(d, 0);
        }
        if (sense[2] != 0x02 || sense[12] != 0x3A) {
            throw new AssertionError("REQUEST SENSE medium absent");
        }
        packet(d, 0x28, 0);
        if (read(d, 7) != 0x41 || read(d, 1) != 0x20) {
            throw new AssertionError("READ(10) must report NOT READY");
        }
        System.out.println("Xenon SATA/ATAPI: ALL OK (status, identify, inquiry, no medium)");
    }
}
