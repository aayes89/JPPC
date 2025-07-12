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

import pcp.system.ELFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Slam
 */
public class ELFFile {

    public static final int ELFCLASS32 = 1;
    public static final int ELFCLASS64 = 2;
    public static final int ELFDATA2LSB = 1;
    public static final int ELFDATA2MSB = 2;
    public static final int EM_PPC = 20;
    public static final int EM_PPC64 = 21;
    public static final int PT_LOAD = 1;

    private final List<ELFSection> sections = new ArrayList<>();
    private final List<ELFSegment> segments = new ArrayList<>();
    private long entryPoint;
    private boolean is64Bit;
    private boolean bigEndian;
    private int machine;
    public byte[] elfData;

    public ELFFile(String filename) throws ELFException {
        try {
            elfData = Files.readAllBytes(Paths.get(filename));
            parseELFHeader();
            parseSections();
            parseSegments();
        } catch (IOException e) {
            throw new ELFException("Error reading ELF file", e);
        }
    }

    private void parseELFHeader() throws ELFException {
        if (elfData[0] != 0x7F || elfData[1] != 'E' || elfData[2] != 'L' || elfData[3] != 'F') {
            throw new ELFException("Invalid ELF magic number");
        }

        is64Bit = (elfData[4] == ELFCLASS64);
        bigEndian = (elfData[5] == ELFDATA2MSB);

        if (elfData[6] != 1) {
            throw new ELFException("Unsupported ELF version");
        }

        ByteOrder byteOrder = bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
        ByteBuffer buffer = ByteBuffer.wrap(elfData).order(byteOrder);

        machine = buffer.getShort(18) & 0xFFFF;
        entryPoint = is64Bit ? buffer.getLong(24) : buffer.getInt(24) & 0xFFFFFFFFL;

        if (machine != EM_PPC && machine != EM_PPC64) {
            throw new ELFException("Unsupported architecture: " + machine);
        }

        if ((machine == EM_PPC64 && !is64Bit) || (machine == EM_PPC && is64Bit)) {
            throw new ELFException("Architecture/class mismatch");
        }
    }

    private void parseSections() throws ELFException {
        ByteOrder byteOrder = bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
        ByteBuffer buffer = ByteBuffer.wrap(elfData).order(byteOrder);

        int e_shoff = is64Bit ? (int) buffer.getLong(40) : buffer.getInt(32);
        int e_shentsize = buffer.getShort(46) & 0xFFFF;
        int e_shnum = buffer.getShort(48) & 0xFFFF;
        int e_shstrndx = buffer.getShort(50) & 0xFFFF;

        if (e_shstrndx >= e_shnum) {
            throw new ELFException("Invalid section string table index: " + e_shstrndx);
        }

        int strtabOffset = e_shoff + e_shstrndx * e_shentsize;
        ELFSection strtab = readSectionHeader(buffer, strtabOffset);

        for (int i = 0; i < e_shnum; i++) {
            int headerOffset = e_shoff + i * e_shentsize;
            ELFSection section = readSectionHeader(buffer, headerOffset);

            if (strtab.data != null && section.nameOffset < strtab.data.length) {
                int end = (int) section.nameOffset;
                while (end < strtab.data.length && strtab.data[end] != 0) {
                    end++;
                }
                section.name = new String(strtab.data, (int) section.nameOffset,
                        end - (int) section.nameOffset, StandardCharsets.UTF_8);
            } else {
                section.name = "section_" + i;
            }

            sections.add(section);
        }
    }

    private ELFSection readSectionHeader(ByteBuffer buffer, int offset) {
        ELFSection section = new ELFSection();
        section.nameOffset = buffer.getInt(offset);

        if (is64Bit) {
            section.type = buffer.getInt(offset + 4);
            section.flags = buffer.getLong(offset + 8);
            section.address = buffer.getLong(offset + 16);
            section.offset = buffer.getLong(offset + 24);
            section.size = buffer.getLong(offset + 32);
            section.link = buffer.getInt(offset + 40);
            section.info = buffer.getInt(offset + 44);
            section.addralign = buffer.getLong(offset + 48);
            section.entsize = buffer.getLong(offset + 56);
        } else {
            section.type = buffer.getInt(offset + 4);
            section.flags = buffer.getInt(offset + 8) & 0xFFFFFFFFL;
            section.address = buffer.getInt(offset + 12) & 0xFFFFFFFFL;
            section.offset = buffer.getInt(offset + 16) & 0xFFFFFFFFL;
            section.size = buffer.getInt(offset + 20) & 0xFFFFFFFFL;
            section.link = buffer.getInt(offset + 24);
            section.info = buffer.getInt(offset + 28);
            section.addralign = buffer.getInt(offset + 32) & 0xFFFFFFFFL;
            section.entsize = buffer.getInt(offset + 36) & 0xFFFFFFFFL;
        }

        if (section.type != 8 && section.size > 0 && section.offset > 0
                && section.offset + section.size <= elfData.length) {
            section.data = new byte[(int) section.size];
            System.arraycopy(elfData, (int) section.offset, section.data, 0, (int) section.size);
        }

        return section;
    }

    private void parseSegments() throws ELFException {
        ByteOrder byteOrder = bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
        ByteBuffer buffer = ByteBuffer.wrap(elfData).order(byteOrder);

        int e_phoff = is64Bit ? (int) buffer.getLong(32) : buffer.getInt(28);
        int e_phentsize = buffer.getShort(42) & 0xFFFF;
        int e_phnum = buffer.getShort(44) & 0xFFFF;

        if (e_phoff < 0 || e_phoff + e_phnum * e_phentsize > elfData.length) {
            throw new ELFException("Invalid program header table: offset=0x"
                    + Integer.toHexString(e_phoff) + ", num=" + e_phnum);
        }

        for (int i = 0; i < e_phnum; i++) {
            int headerOffset = e_phoff + i * e_phentsize;
            ELFSegment segment = new ELFSegment();

            int p_type = buffer.getInt(headerOffset);
            if (p_type != PT_LOAD) {
                System.out.println("Skipping non-loadable segment: type=0x" + Integer.toHexString(p_type));
                continue;
            }

            if (is64Bit) {
                segment.flags = buffer.getInt(headerOffset + 4);
                segment.fileOffset = buffer.getLong(headerOffset + 8);
                segment.virtualAddress = buffer.getLong(headerOffset + 16);
                segment.physicalAddress = buffer.getLong(headerOffset + 24);
                segment.fileSize = buffer.getLong(headerOffset + 32);
                segment.memorySize = buffer.getLong(headerOffset + 40);
                segment.align = buffer.getLong(headerOffset + 48);
            } else {
                segment.flags = buffer.getInt(headerOffset + 24);
                segment.fileOffset = buffer.getInt(headerOffset + 4) & 0xFFFFFFFFL;
                segment.virtualAddress = buffer.getInt(headerOffset + 8) & 0xFFFFFFFFL;
                segment.physicalAddress = buffer.getInt(headerOffset + 12) & 0xFFFFFFFFL;
                segment.fileSize = buffer.getInt(headerOffset + 16) & 0xFFFFFFFFL;
                segment.memorySize = buffer.getInt(headerOffset + 20) & 0xFFFFFFFFL;
                segment.align = buffer.getInt(headerOffset + 28) & 0xFFFFFFFFL;
            }

            if (segment.fileOffset < 0 || segment.fileOffset + segment.fileSize > elfData.length) {
                throw new ELFException("Invalid segment offset/size: offset=0x"
                        + Long.toHexString(segment.fileOffset) + ", size=" + segment.fileSize);
            }

            segments.add(segment);
        }
    }

    public List<ELFSection> getSections() {
        return sections;
    }

    public List<ELFSegment> getSegments() {
        return segments;
    }

    public long getEntryPoint() {
        return entryPoint;
    }

    public boolean is64Bit() {
        return is64Bit;
    }

    public boolean isBigEndian() {
        return bigEndian;
    }

    public int getMachine() {
        return machine;
    }

    public ELFSection getSectionByName(String name) {
        for (ELFSection section : sections) {
            if (name.equals(section.name)) {
                return section;
            }
        }
        return null;
    }
}
