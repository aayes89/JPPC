package pcp.gpu;
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

import java.io.IOException;
import java.io.RandomAccessFile;
import pcp.system.Memory;

/**
 * Unidad de CD-ROM virtual de solo lectura respaldada por un archivo ISO (o cualquier imagen de disco),
 * con DMA directo a RAM. Base para que un SO invitado PPC lea su propio medio de instalación.
 *
 * Registros (palabras de 32 bits, big-endian) en {@link #BASE}:
 *   +0x00 LBA inicial (sectores de 2048 bytes)   +0x04 nº de sectores   +0x08 dirección física destino (RAM)
 *   +0x0C comando: 1 = leer con DMA              +0x10 estado: 0 = listo/ok, 1 = error
 *   +0x14 tamaño total en sectores (solo lectura)
 *
 * @author Slam
 */
public class IsoBlockDevice implements Device {

    public static final long BASE = 0xEA00F000L;
    private final RandomAccessFile file;
    private final Memory memory;
    private final long totalSectors;
    private int lba, count, dest, status;

    public IsoBlockDevice(String path, Memory memory) throws IOException {
        this.file = new RandomAccessFile(path, "r");
        this.memory = memory;
        this.totalSectors = file.length() / 2048;
    }

    @Override
    public boolean containsAddress(long address) {
        return address >= 0 && address < 0x20;
    }

    @Override
    public int readWord(long offs) {
        switch ((int) (offs & ~3L)) {
            case 0x00: return lba;
            case 0x04: return count;
            case 0x08: return dest;
            case 0x10: return status;
            case 0x14: return (int) totalSectors;
            default: return 0;
        }
    }

    @Override
    public synchronized void writeWord(long offs, int v) {
        switch ((int) (offs & ~3L)) {
            case 0x00: lba = v; break;
            case 0x04: count = v; break;
            case 0x08: dest = v; break;
            case 0x0C:
                if (v == 1) {
                    status = 1;
                    try {
                        if (count > 0 && (lba & 0xFFFFFFFFL) + (count & 0xFFFFFFFFL) <= totalSectors) {
                            byte[] buf = new byte[count * 2048];
                            file.seek((lba & 0xFFFFFFFFL) * 2048);
                            file.readFully(buf);
                            memory.writeBlock(dest & 0xFFFFFFFFL, buf);
                            status = 0;
                        }
                    } catch (IOException | RuntimeException e) {
                        status = 1;
                    }
                }
                break;
            default: break;
        }
    }

    @Override
    public byte read(long offs) {
        return (byte) (readWord(offs & ~3L) >>> (24 - 8 * (int) (offs & 3)));
    }

    @Override
    public void write(long offs, byte v) {
        if ((offs & 3) == 3) {
            writeWord(offs & ~3L, v & 0xFF); // escritura byte a byte: no soportada salvo la última
        }
    }
}
