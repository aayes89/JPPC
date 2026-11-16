package pcp.xenon;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Valida y separa la imagen HV + xboxkrnl.exe expandida desde CE. */
public final class XenonBaseImageInspector {

    public static final int HYPERVISOR_REGION_SIZE = 0x40000;
    public static final int KERNEL_PE_OFFSET = 0x40000;
    private static final int PE_MACHINE_POWERPC = 0x01F2;
    private static final int PE32_MAGIC = 0x010B;
    private static final int PE_SECTION_HEADER_SIZE = 40;

    private XenonBaseImageInspector() { }

    public static final class SectionInfo {
        public final String name;
        public final long virtualAddress;
        public final long virtualSize;
        public final long rawOffset;
        public final long rawSize;

        private SectionInfo(String name, long virtualAddress, long virtualSize,
                long rawOffset, long rawSize) {
            this.name = name;
            this.virtualAddress = virtualAddress;
            this.virtualSize = virtualSize;
            this.rawOffset = rawOffset;
            this.rawSize = rawSize;
        }
    }

    public static final class Result {
        public final int hypervisorVersion;
        public final long hypervisorEntryOffset;
        public final long hypervisorHeaderSize;
        public final long kernelImageBase;
        public final long kernelEntryRva;
        public final long kernelEntryPoint;
        public final long kernelSizeOfImage;
        public final long kernelSizeOfHeaders;
        public final int kernelMachine;
        public final int kernelSectionCount;
        public final List<SectionInfo> sections;

        private Result(int hypervisorVersion, long hypervisorEntryOffset,
                long hypervisorHeaderSize, long kernelImageBase,
                long kernelEntryRva, long kernelEntryPoint,
                long kernelSizeOfImage, long kernelSizeOfHeaders,
                int kernelMachine, List<SectionInfo> sections) {
            this.hypervisorVersion = hypervisorVersion;
            this.hypervisorEntryOffset = hypervisorEntryOffset;
            this.hypervisorHeaderSize = hypervisorHeaderSize;
            this.kernelImageBase = kernelImageBase;
            this.kernelEntryRva = kernelEntryRva;
            this.kernelEntryPoint = kernelEntryPoint;
            this.kernelSizeOfImage = kernelSizeOfImage;
            this.kernelSizeOfHeaders = kernelSizeOfHeaders;
            this.kernelMachine = kernelMachine;
            this.kernelSectionCount = sections.size();
            this.sections = Collections.unmodifiableList(new ArrayList<>(sections));
        }
    }

    public static Result inspect(byte[] baseImage) throws IOException {
        if (baseImage == null || baseImage.length < KERNEL_PE_OFFSET + 0x100) {
            throw new IOException("Imagen base HV/kernel demasiado pequeña");
        }

        if (baseImage[0] != 'N' || baseImage[1] != 'N') {
            throw new IOException("Magic NN de la imagen base no válido");
        }
        int hvVersion = u16be(baseImage, 2);
        long hvEntry = u32be(baseImage, 8);
        long hvHeaderSize = u32be(baseImage, 12);
        if (hvEntry >= HYPERVISOR_REGION_SIZE || hvHeaderSize == 0
                || hvHeaderSize > HYPERVISOR_REGION_SIZE) {
            throw new IOException(String.format(
                    "Cabecera HV NN inconsistente: entry=0x%X size=0x%X",
                    hvEntry, hvHeaderSize));
        }

        int kernelOffset = KERNEL_PE_OFFSET;
        if (u16le(baseImage, kernelOffset) != 0x5A4D) {
            throw new IOException("No se encontró MZ en el inicio de xboxkrnl.exe");
        }
        long peOffsetLong = u32le(baseImage, kernelOffset + 0x3C);
        if (peOffsetLong > Integer.MAX_VALUE) throw new IOException("e_lfanew fuera de rango");
        int peOffset = kernelOffset + (int) peOffsetLong;
        requireRange(baseImage, peOffset, 24, "PE/COFF");
        if (baseImage[peOffset] != 'P' || baseImage[peOffset + 1] != 'E'
                || baseImage[peOffset + 2] != 0 || baseImage[peOffset + 3] != 0) {
            throw new IOException("Firma PE de xboxkrnl.exe inválida");
        }

        int coff = peOffset + 4;
        int machine = u16le(baseImage, coff);
        int sectionCount = u16le(baseImage, coff + 2);
        int optionalSize = u16le(baseImage, coff + 16);
        if (machine != PE_MACHINE_POWERPC) {
            throw new IOException(String.format("Machine PE no Xenon: 0x%04X", machine));
        }
        if (sectionCount <= 0 || sectionCount > 96 || optionalSize < 96) {
            throw new IOException("COFF/Optional Header de xboxkrnl.exe inválido");
        }

        int optional = coff + 20;
        requireRange(baseImage, optional, optionalSize, "Optional Header");
        if (u16le(baseImage, optional) != PE32_MAGIC) {
            throw new IOException("xboxkrnl.exe no es PE32");
        }

        long entryRva = u32le(baseImage, optional + 16);
        long imageBase = u32le(baseImage, optional + 28);
        long sizeOfImage = u32le(baseImage, optional + 56);
        long sizeOfHeaders = u32le(baseImage, optional + 60);
        long kernelFileSize = baseImage.length - (long) KERNEL_PE_OFFSET;
        if (imageBase != 0x80040000L || sizeOfImage == 0
                || sizeOfImage != kernelFileSize || sizeOfHeaders == 0
                || sizeOfHeaders > sizeOfImage || sizeOfHeaders > kernelFileSize
                || entryRva == 0 || entryRva >= sizeOfImage) {
            throw new IOException(String.format(
                    "PE base kernel inconsistente: base=0x%X entry=0x%X image=0x%X file=0x%X",
                    imageBase, entryRva, sizeOfImage, kernelFileSize));
        }

        long sectionTable = optional + optionalSize;
        requireRange(baseImage, sectionTable,
                (long) sectionCount * PE_SECTION_HEADER_SIZE, "Section Table");
        List<SectionInfo> sections = new ArrayList<>();
        for (int i = 0; i < sectionCount; i++) {
            int p = (int) sectionTable + i * PE_SECTION_HEADER_SIZE;
            int nameEnd = p;
            while (nameEnd < p + 8 && baseImage[nameEnd] != 0) nameEnd++;
            String name = new String(baseImage, p, nameEnd - p,
                    java.nio.charset.StandardCharsets.US_ASCII);
            long virtualSize = u32le(baseImage, p + 8);
            long virtualAddress = u32le(baseImage, p + 12);
            long rawSize = u32le(baseImage, p + 16);
            long rawOffset = u32le(baseImage, p + 20);
            if (virtualAddress > sizeOfImage || virtualSize > sizeOfImage - virtualAddress
                    || rawOffset > kernelFileSize || rawSize > kernelFileSize - rawOffset) {
                throw new IOException("Sección PE fuera de límites: " + name);
            }
            sections.add(new SectionInfo(name, virtualAddress, virtualSize,
                    rawOffset, rawSize));
        }

        return new Result(hvVersion, hvEntry, hvHeaderSize, imageBase,
                entryRva, imageBase + entryRva, sizeOfImage, sizeOfHeaders,
                machine, sections);
    }

    private static void requireRange(byte[] data, long offset, long length, String name)
            throws IOException {
        if (offset < 0 || length < 0 || offset > data.length || length > data.length - offset) {
            throw new IOException(name + " fuera de límites");
        }
    }

    private static int u16be(byte[] b, int o) {
        return ((b[o] & 0xFF) << 8) | (b[o + 1] & 0xFF);
    }

    private static int u16le(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8);
    }

    private static long u32be(byte[] b, int o) {
        return ((long) (b[o] & 0xFF) << 24) | ((long) (b[o + 1] & 0xFF) << 16)
                | ((long) (b[o + 2] & 0xFF) << 8) | (b[o + 3] & 0xFFL);
    }

    private static long u32le(byte[] b, int o) {
        return (b[o] & 0xFFL) | ((b[o + 1] & 0xFFL) << 8)
                | ((b[o + 2] & 0xFFL) << 16) | ((b[o + 3] & 0xFFL) << 24);
    }
}
