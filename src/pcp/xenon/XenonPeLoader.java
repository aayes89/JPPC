/*
 * The MIT License
 *
 * Copyright 2026 Slam.
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
package pcp.xenon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import pcp.cpu.CPU;
import pcp.system.Memory;

/**
 * Loader PE32 PowerPC para imágenes ejecutables Xenon.
 *
 * Primera etapa: - Carga en ImageBase preferido. - Reconstruye las secciones
 * PE. - No aplica relocations. - No resuelve imports XEX. - No modifica MSR.
 */
public final class XenonPeLoader {

    private static final int DOS_MAGIC = 0x5A4D;
    private static final int PE_MACHINE_POWERPC = 0x01F2;
    private static final int PE32_MAGIC = 0x010B;

    private static final int COFF_HEADER_SIZE = 20;
    private static final int SECTION_HEADER_SIZE = 40;

    public static final class Section {

        private final String name;
        private final long virtualAddress;
        private final long virtualSize;
        private final long rawOffset;
        private final long rawSize;
        private final long characteristics;

        private Section(
                String name,
                long virtualAddress,
                long virtualSize,
                long rawOffset,
                long rawSize,
                long characteristics) {

            this.name = name;
            this.virtualAddress = virtualAddress;
            this.virtualSize = virtualSize;
            this.rawOffset = rawOffset;
            this.rawSize = rawSize;
            this.characteristics = characteristics;
        }

        public String getName() {
            return name;
        }

        public long getVirtualAddress() {
            return virtualAddress;
        }

        public long getVirtualSize() {
            return virtualSize;
        }

        public long getRawOffset() {
            return rawOffset;
        }

        public long getRawSize() {
            return rawSize;
        }

        public long getCharacteristics() {
            return characteristics;
        }
    }

    public static final class LoadedImage {

        private final long imageBase;
        private final long entryPoint;
        private final long entryRva;
        private final long sizeOfImage;
        private final long sizeOfHeaders;
        private final long stackReserve;
        private final long stackCommit;
        private final List<Section> sections;

        private LoadedImage(
                long imageBase,
                long entryPoint,
                long entryRva,
                long sizeOfImage,
                long sizeOfHeaders,
                long stackReserve,
                long stackCommit,
                List<Section> sections) {

            this.imageBase = imageBase;
            this.entryPoint = entryPoint;
            this.entryRva = entryRva;
            this.sizeOfImage = sizeOfImage;
            this.sizeOfHeaders = sizeOfHeaders;
            this.stackReserve = stackReserve;
            this.stackCommit = stackCommit;
            this.sections = Collections.unmodifiableList(
                    new ArrayList<>(sections)
            );
        }

        public long getImageBase() {
            return imageBase;
        }

        public long getEntryPoint() {
            return entryPoint;
        }

        public long getEntryRva() {
            return entryRva;
        }

        public long getSizeOfImage() {
            return sizeOfImage;
        }

        public long getSizeOfHeaders() {
            return sizeOfHeaders;
        }

        public long getStackReserve() {
            return stackReserve;
        }

        public long getStackCommit() {
            return stackCommit;
        }

        public List<Section> getSections() {
            return sections;
        }

        public boolean hasEntryPoint() {
            return entryRva != 0;
        }

        /**
         * Inicializa el contexto mínimo de entrada para una imagen PE32 Xenon.
         * La pila se coloca en el espejo real-mode de la parte alta de RAM:
         * 0x80000000 | dirección física. En RAM de 512 MiB, r1 queda en
         * 0x9FFFFFF0 y los accesos se resuelven en 0x1FFFFFF0.
         *
         * Este contexto permite ejecutar el prólogo PPC del XAM; no sustituye
         * la inicialización del kernel ni la resolución de imports XEX.
         */
        public void start(CPU cpu, int memorySize) {
            if (cpu == null) {
                throw new IllegalArgumentException("CPU nula");
            }
            if (!hasEntryPoint()) {
                throw new IllegalStateException("La imagen no tiene entry point ejecutable");
            }
            if (memorySize < 0x10000) {
                throw new IllegalArgumentException("RAM insuficiente para iniciar XAM: " + memorySize);
            }

            long reserve = stackReserve > 0 ? stackReserve : 0x40000L;
            reserve = (reserve + 15L) & ~15L;
            if (reserve > memorySize - 16L) {
                throw new IllegalStateException(String.format(
                        "Stack PE fuera de RAM: reserve=0x%X RAM=0x%X", reserve, memorySize));
            }

            long physicalTop = ((long) memorySize - 16L) & ~15L;
            long stackTop = 0x80000000L | physicalTop;
            long stackBottom = stackTop - reserve;

            // PE32 PowerPC: SF=0. Mantener el resto de bits de estado que
            // configure el entorno, pero no heredar el modo PPC64 del 1BL.
            cpu.setMsr(cpu.getMsr() & 0x7FFFFFFFFFFFFFFFL);
            // El entry point XAM de esta NAND restaura r14-r31 y LR desde el
            // frame de arranque de hilo. Reproducimos ese frame mínimo y usamos
            // un `b .` como thread-exit trap, reconocido por CPU.haltOnSelfBranch.
            long threadExit = (stackTop - reserve + 0x20L) & ~3L;
            for (int reg = 14; reg <= 31; reg++) {
                long slot = stackTop - 152L + (reg - 14) * 8L;
                cpu.writeMemoryDouble(slot, 0L);
            }
            cpu.writeMemoryWord(stackTop - 8L, threadExit);
            cpu.writeMemoryWord(threadExit, 0x48000000L);
            cpu.setLr(threadExit);
            cpu.setGPR(1, stackTop - 112L);
            cpu.setPc(entryPoint);

            System.out.printf(
                    "[XEX-PE] CONTEXT PC=%08X r1=%08X STACK=[%08X..%08X] RESERVE=%X COMMIT=%X MSR=%016X%n",
                    entryPoint, stackTop - 112L, stackBottom, stackTop, reserve, stackCommit, cpu.getMSR());
        }

        /** Compatibilidad: asume el mapa de RAM Xenon de 512 MiB. */
        public void start(CPU cpu) {
            start(cpu, 0x20000000);
        }

    }

    public LoadedImage load(Path path, Memory memory)
            throws IOException {

        if (path == null) {
            throw new IllegalArgumentException("Path nulo");
        }

        return load(Files.readAllBytes(path), memory);
    }

    public LoadedImage load(byte[] file, Memory memory)
            throws IOException {

        if (file == null || memory == null) {
            throw new IllegalArgumentException(
                    "Archivo o memoria nulos"
            );
        }

        if (file.length < 0x40) {
            throw new IOException("Archivo PE demasiado pequeño");
        }

        if (u16(file, 0) != DOS_MAGIC) {
            throw new IOException("Cabecera DOS MZ inválida");
        }

        long peOffset = u32(file, 0x3C);

        requireRange(
                file,
                peOffset,
                4 + COFF_HEADER_SIZE,
                "Cabecera PE"
        );

        int pe = (int) peOffset;

        if (file[pe] != 'P'
                || file[pe + 1] != 'E'
                || file[pe + 2] != 0
                || file[pe + 3] != 0) {

            throw new IOException("Firma PE inválida");
        }

        int coff = pe + 4;

        int machine = u16(file, coff);
        int sectionCount = u16(file, coff + 2);
        int optionalSize = u16(file, coff + 16);

        if (machine != PE_MACHINE_POWERPC) {
            throw new IOException(
                    "Arquitectura PE no soportada: 0x"
                    + Integer.toHexString(machine)
            );
        }

        if (sectionCount <= 0 || sectionCount > 96) {
            throw new IOException(
                    "Número de secciones inválido: " + sectionCount
            );
        }

        int optional = coff + COFF_HEADER_SIZE;

        requireRange(
                file,
                optional,
                optionalSize,
                "Optional Header"
        );

        if (optionalSize < 96) {
            throw new IOException("Optional Header incompleto");
        }

        if (u16(file, optional) != PE32_MAGIC) {
            throw new IOException("El archivo no es PE32");
        }

        long entryRva = u32(file, optional + 16);
        long imageBase = u32(file, optional + 28);
        long sizeOfImage = u32(file, optional + 56);
        long sizeOfHeaders = u32(file, optional + 60);
        long stackReserve = optionalSize >= 80 ? u32(file, optional + 72) : 0;
        long stackCommit = optionalSize >= 80 ? u32(file, optional + 76) : 0;

        if (sizeOfImage == 0
                || sizeOfImage > Integer.MAX_VALUE) {

            throw new IOException("SizeOfImage inválido");
        }

        if (sizeOfHeaders == 0
                || sizeOfHeaders > sizeOfImage
                || sizeOfHeaders > file.length) {

            throw new IOException("SizeOfHeaders inválido");
        }

        if (entryRva >= sizeOfImage && entryRva != 0) {
            throw new IOException("Entry RVA fuera de la imagen");
        }

        long entryPoint = entryRva == 0
                ? 0
                : imageBase + entryRva;

        if (entryPoint > 0xFFFFFFFFL) {
            throw new IOException("Entry point fuera de PE32");
        }

        /*
         * Reconstrucción de la imagen virtual.
         * El array comienza completamente en cero.
         */
        byte[] mapped = new byte[(int) sizeOfImage];

        System.arraycopy(
                file,
                0,
                mapped,
                0,
                (int) sizeOfHeaders
        );

        long sectionTable = optional + optionalSize;

        requireRange(
                file,
                sectionTable,
                (long) sectionCount * SECTION_HEADER_SIZE,
                "Tabla de secciones"
        );

        List<Section> sections = new ArrayList<>();

        for (int i = 0; i < sectionCount; i++) {

            int p = (int) sectionTable
                    + i * SECTION_HEADER_SIZE;

            int nameEnd = p;

            while (nameEnd < p + 8 && file[nameEnd] != 0) {
                nameEnd++;
            }

            String name = new String(
                    file,
                    p,
                    nameEnd - p,
                    StandardCharsets.US_ASCII
            );

            long virtualSize = u32(file, p + 8);
            long virtualAddress = u32(file, p + 12);
            long rawSize = u32(file, p + 16);
            long rawOffset = u32(file, p + 20);
            long characteristics = u32(file, p + 36);

            long mappedSize = Math.max(
                    virtualSize,
                    rawSize
            );

            if (virtualAddress > sizeOfImage
                    || mappedSize > sizeOfImage - virtualAddress) {

                throw new IOException(
                        "Sección fuera de SizeOfImage: " + name
                );
            }

            if (rawSize > 0) {

                requireRange(
                        file,
                        rawOffset,
                        rawSize,
                        "Datos de sección " + name
                );

                System.arraycopy(
                        file,
                        (int) rawOffset,
                        mapped,
                        (int) virtualAddress,
                        (int) rawSize
                );
            }

            sections.add(new Section(
                    name,
                    virtualAddress,
                    virtualSize,
                    rawOffset,
                    rawSize,
                    characteristics
            ));
        }

        /*
         * Las imágenes se cargan en su ImageBase preferido.
         * No se aplica delta de relocations en esta etapa.
         */
        memory.writeBlock(imageBase, mapped);

        LoadedImage result = new LoadedImage(
                imageBase,
                entryPoint,
                entryRva,
                sizeOfImage,
                sizeOfHeaders,
                stackReserve,
                stackCommit,
                sections
        );

        System.out.printf(
                "[XEX-PE] Loaded BASE=%08X ENTRY=%08X SIZE=%08X SECTIONS=%d%n",
                imageBase,
                entryPoint,
                sizeOfImage,
                sectionCount
        );

        for (Section section : sections) {

            System.out.printf(
                    "  %-8s RVA=%08X VS=%08X RAW=%08X%n",
                    section.name,
                    section.virtualAddress,
                    section.virtualSize,
                    section.rawSize
            );
        }

        return result;
    }

    private static int u16(byte[] data, int offset)
            throws IOException {

        requireRange(data, offset, 2, "u16");

        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8);
    }

    private static long u32(byte[] data, int offset)
            throws IOException {

        requireRange(data, offset, 4, "u32");

        return (data[offset] & 0xFFL)
                | ((data[offset + 1] & 0xFFL) << 8)
                | ((data[offset + 2] & 0xFFL) << 16)
                | ((data[offset + 3] & 0xFFL) << 24);
    }

    private static void requireRange(
            byte[] data,
            long offset,
            long length,
            String description) throws IOException {

        if (offset < 0
                || length < 0
                || offset > data.length
                || length > data.length - offset) {

            throw new IOException(
                    description
                    + " fuera del archivo: offset=0x"
                    + Long.toHexString(offset)
                    + " length=0x"
                    + Long.toHexString(length)
            );
        }
    }
}
