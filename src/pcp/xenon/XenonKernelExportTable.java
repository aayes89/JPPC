package pcp.xenon;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Parser PE de la tabla de exports ordinales de xboxkrnl.exe. */
public final class XenonKernelExportTable {
    private final long imageBase;
    private final int ordinalBase;
    private final Map<Integer, Long> ordinalToRva;
    private final Map<Integer, String> ordinalToName;

    private XenonKernelExportTable(long imageBase, int ordinalBase,
                                   Map<Integer, Long> ordinalToRva,
                                   Map<Integer, String> ordinalToName) {
        this.imageBase = imageBase;
        this.ordinalBase = ordinalBase;
        this.ordinalToRva = Collections.unmodifiableMap(new LinkedHashMap<>(ordinalToRva));
        this.ordinalToName = Collections.unmodifiableMap(new LinkedHashMap<>(ordinalToName));
    }

    public static XenonKernelExportTable parse(byte[] peFile) throws IOException {
        if (peFile == null || peFile.length < 0x40 || peFile[0] != 'M' || peFile[1] != 'Z') {
            throw new IOException("Imagen xboxkrnl PE/MZ inválida");
        }
        int pe = readLe32(peFile, 0x3C);
        if (pe < 0 || pe + 24L > peFile.length || !asciiAt(peFile, pe, "PE\0\0")) {
            throw new IOException("Firma PE de xboxkrnl inválida");
        }
        int sectionCount = readLe16(peFile, pe + 6);
        int optionalSize = readLe16(peFile, pe + 20);
        int optional = pe + 24;
        if (sectionCount <= 0 || sectionCount > 0x100 || optional + optionalSize > peFile.length) {
            throw new IOException("Cabeceras PE de xboxkrnl fuera de rango");
        }
        int magic = readLe16(peFile, optional);
        int directoryOffset;
        long imageBase;
        if (magic == 0x10B) {
            directoryOffset = 96;
            imageBase = Integer.toUnsignedLong(readLe32(peFile, optional + 28));
        } else if (magic == 0x20B) {
            directoryOffset = 112;
            imageBase = readLe64(peFile, optional + 24);
        } else {
            throw new IOException(String.format("OptionalHeader PE no soportado: 0x%X", magic));
        }
        if (directoryOffset + 8 > optionalSize) throw new IOException("DataDirectory PE truncado");
        long exportRva = Integer.toUnsignedLong(readLe32(peFile, optional + directoryOffset));
        long exportSize = Integer.toUnsignedLong(readLe32(peFile, optional + directoryOffset + 4));
        if (exportRva == 0 || exportSize < 40) throw new IOException("xboxkrnl no contiene export directory válido");

        Section[] sections = new Section[sectionCount];
        long sectionTable = (long) optional + optionalSize;
        if (sectionTable + (long) sectionCount * 40 > peFile.length) {
            throw new IOException("Tabla de secciones PE de xboxkrnl truncada");
        }
        for (int i = 0; i < sectionCount; i++) {
            int p = (int) sectionTable + i * 40;
            sections[i] = new Section(
                    Integer.toUnsignedLong(readLe32(peFile, p + 8)),
                    Integer.toUnsignedLong(readLe32(peFile, p + 12)),
                    Integer.toUnsignedLong(readLe32(peFile, p + 16)),
                    Integer.toUnsignedLong(readLe32(peFile, p + 20)));
        }
        long sizeOfHeaders = Integer.toUnsignedLong(readLe32(peFile, optional + 60));
        int exportOffset = rvaToOffset(peFile, sections, sizeOfHeaders, exportRva, 40);
        int ordinalBase = readLe32(peFile, exportOffset + 16);
        long functionCountLong = Integer.toUnsignedLong(readLe32(peFile, exportOffset + 20));
        long nameCountLong = Integer.toUnsignedLong(readLe32(peFile, exportOffset + 24));
        long functionsRva = Integer.toUnsignedLong(readLe32(peFile, exportOffset + 28));
        long namesRva = Integer.toUnsignedLong(readLe32(peFile, exportOffset + 32));
        long ordinalsRva = Integer.toUnsignedLong(readLe32(peFile, exportOffset + 36));
        if (functionCountLong > 0x10000 || nameCountLong > functionCountLong) {
            throw new IOException("Conteos de exports PE de xboxkrnl inválidos");
        }
        int functionCount = (int) functionCountLong;
        int nameCount = (int) nameCountLong;
        int functionsOffset = rvaToOffset(peFile, sections, sizeOfHeaders, functionsRva, functionCount * 4L);
        int namesOffset = nameCount == 0 ? 0
                : rvaToOffset(peFile, sections, sizeOfHeaders, namesRva, nameCount * 4L);
        int ordinalsOffset = nameCount == 0 ? 0
                : rvaToOffset(peFile, sections, sizeOfHeaders, ordinalsRva, nameCount * 2L);

        Map<Integer, Long> ordinalToRva = new LinkedHashMap<>();
        for (int i = 0; i < functionCount; i++) {
            long rva = Integer.toUnsignedLong(readLe32(peFile, functionsOffset + i * 4));
            if (rva != 0) ordinalToRva.put(ordinalBase + i, rva);
        }
        Map<Integer, String> ordinalToName = new LinkedHashMap<>();
        for (int i = 0; i < nameCount; i++) {
            long nameRva = Integer.toUnsignedLong(readLe32(peFile, namesOffset + i * 4));
            int ordinalIndex = readLe16(peFile, ordinalsOffset + i * 2);
            if (ordinalIndex >= functionCount) throw new IOException("Ordinal name PE fuera de rango");
            int nameOffset = rvaToOffset(peFile, sections, sizeOfHeaders, nameRva, 1);
            ordinalToName.put(ordinalBase + ordinalIndex, readAsciiZ(peFile, nameOffset));
        }
        return new XenonKernelExportTable(imageBase, ordinalBase, ordinalToRva, ordinalToName);
    }

    private static int rvaToOffset(byte[] image, Section[] sections, long sizeOfHeaders,
                                   long rva, long length) throws IOException {
        if (rva < sizeOfHeaders && rva + length <= image.length) return (int) rva;
        for (Section section : sections) {
            long span = Math.max(section.virtualSize, section.rawSize);
            if (rva >= section.virtualAddress && rva + length <= section.virtualAddress + span) {
                long offset = section.rawPointer + (rva - section.virtualAddress);
                if (offset >= 0 && offset + length <= image.length) return (int) offset;
            }
        }
        throw new IOException(String.format("RVA PE no mapeable: 0x%X (+0x%X)", rva, length));
    }

    private static String readAsciiZ(byte[] data, int offset) throws IOException {
        int end = offset;
        while (end < data.length && data[end] != 0) end++;
        if (end == data.length) throw new IOException("Nombre export PE sin terminador");
        return new String(data, offset, end - offset, StandardCharsets.US_ASCII);
    }

    private static boolean asciiAt(byte[] data, int offset, String value) {
        if (offset < 0 || offset + value.length() > data.length) return false;
        for (int i = 0; i < value.length(); i++) {
            if ((data[offset + i] & 0xFF) != value.charAt(i)) return false;
        }
        return true;
    }

    private static int readLe16(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static int readLe32(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16) | (data[offset + 3] << 24);
    }

    private static long readLe64(byte[] data, int offset) {
        return Integer.toUnsignedLong(readLe32(data, offset))
                | (Integer.toUnsignedLong(readLe32(data, offset + 4)) << 32);
    }

    public boolean containsOrdinal(int ordinal) { return ordinalToRva.containsKey(ordinal); }
    public long resolveAddress(int ordinal) {
        Long rva = ordinalToRva.get(ordinal);
        return rva == null ? 0 : imageBase + rva;
    }
    public long resolveRva(int ordinal) { return ordinalToRva.getOrDefault(ordinal, 0L); }
    public long imageBase() { return imageBase; }
    public int ordinalBase() { return ordinalBase; }
    public int exportCount() { return ordinalToRva.size(); }
    public Map<Integer, Long> ordinalRvas() { return ordinalToRva; }
    public Map<Integer, String> ordinalNames() { return ordinalToName; }

    private static final class Section {
        final long virtualSize;
        final long virtualAddress;
        final long rawSize;
        final long rawPointer;
        Section(long virtualSize, long virtualAddress, long rawSize, long rawPointer) {
            this.virtualSize = virtualSize;
            this.virtualAddress = virtualAddress;
            this.rawSize = rawSize;
            this.rawPointer = rawPointer;
        }
    }
}
