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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parser de XEX_HEADER_IMPORT_LIBRARIES.
 *
 * Todos los campos numéricos del contenedor XEX son big-endian.
 *
 * @autor: Slam
 *
 */
public final class XexImportLibraryParser {

    private static final int HEADER_SIZE = 0x0C;
    private static final int LIBRARY_SIZE = 0x28;
    private static final int DESCRIPTOR_SIZE = 4;

    private XexImportLibraryParser() {
    }

    public static final class ImportDescriptor {

        public final long firstThunk;

        ImportDescriptor(long firstThunk) {
            this.firstThunk = firstThunk;
        }
    }

    public static final class ImportLibrary {

        public final long size;
        public final long id;
        public final long version;
        public final long minVersion;
        public final int nameIndex;
        public final String name;
        public final List<ImportDescriptor> descriptors;

        ImportLibrary(
                long size,
                long id,
                long version,
                long minVersion,
                int nameIndex,
                String name,
                List<ImportDescriptor> descriptors) {

            this.size = size;
            this.id = id;
            this.version = version;
            this.minVersion = minVersion;
            this.nameIndex = nameIndex;
            this.name = name;

            this.descriptors = Collections.unmodifiableList(
                    new ArrayList<>(descriptors));
        }
    }

    public static List<ImportLibrary> parse(byte[] data) {

        if (data == null || data.length < HEADER_SIZE) {
            throw new IllegalArgumentException(
                    "XEX import header incompleto");
        }

        ByteBuffer b = ByteBuffer.wrap(data)
                .order(ByteOrder.BIG_ENDIAN);

        long sizeOfHeader = u32(b, 0);
        long stringTableSize = u32(b, 4);
        long numberOfLibraries = u32(b, 8);

        if (sizeOfHeader < HEADER_SIZE
                || sizeOfHeader > data.length) {
            throw new IllegalArgumentException(
                    "sizeOfHeader inválido: " + sizeOfHeader);
        }

        if (stringTableSize > sizeOfHeader - HEADER_SIZE) {
            throw new IllegalArgumentException(
                    "String table excede el bloque XEX");
        }

        if (numberOfLibraries > 4096) {
            throw new IllegalArgumentException(
                    "Número de bibliotecas sospechoso: "
                    + numberOfLibraries);
        }

        int stringsStart = HEADER_SIZE;
        int stringsEnd = stringsStart + (int) stringTableSize;

        List<String> names = parseNames(
                data,
                stringsStart,
                stringsEnd,
                (int) numberOfLibraries);

        int libraryOffset = stringsEnd;

        List<ImportLibrary> result = new ArrayList<>();

        for (int i = 0; i < numberOfLibraries; i++) {

            checkRange(
                    libraryOffset,
                    LIBRARY_SIZE,
                    (int) sizeOfHeader);

            long librarySize = u32(b, libraryOffset);

            long id = u32(b, libraryOffset + 0x18);
            long version = u32(b, libraryOffset + 0x1C);
            long minVersion = u32(b, libraryOffset + 0x20);

            int nameIndex = u16(b, libraryOffset + 0x24);
            int importCount = u16(b, libraryOffset + 0x26);

            if (nameIndex >= names.size()) {
                throw new IllegalArgumentException(
                        "Índice de nombre inválido: " + nameIndex);
            }

            long descriptorBytes
                    = (long) importCount * DESCRIPTOR_SIZE;

            long recordBytes = LIBRARY_SIZE + descriptorBytes;

            if (recordBytes > sizeOfHeader - libraryOffset) {
                throw new IllegalArgumentException(
                        "Tabla de descriptores fuera de rango: "
                        + names.get(nameIndex));
            }

            if (librarySize != 0 && librarySize < LIBRARY_SIZE) {
                throw new IllegalArgumentException(
                        "Tamaño de biblioteca inválido: "
                        + librarySize);
            }

            List<ImportDescriptor> descriptors
                    = new ArrayList<>(importCount);

            int descriptorOffset
                    = libraryOffset + LIBRARY_SIZE;

            for (int j = 0; j < importCount; j++) {

                long firstThunk = u32(b, descriptorOffset);

                descriptors.add(
                        new ImportDescriptor(firstThunk));

                descriptorOffset += DESCRIPTOR_SIZE;
            }

            result.add(new ImportLibrary(
                    librarySize,
                    id,
                    version,
                    minVersion,
                    nameIndex,
                    names.get(nameIndex),
                    descriptors));

            // El registro siguiente empieza después de los
            // descriptores de esta biblioteca.
            if (librarySize < recordBytes || librarySize > sizeOfHeader - libraryOffset) {
                throw new IllegalArgumentException("Tamaño de biblioteca inconsistente: " + librarySize);
            }

            libraryOffset += (int) librarySize;
        }

        return Collections.unmodifiableList(result);
    }

    private static List<String> parseNames(
            byte[] data,
            int start,
            int end,
            int count) {

        List<String> names = new ArrayList<>(count);
        int offset = start;

        for (int i = 0; i < count; i++) {

            if (offset >= end) {
                throw new IllegalArgumentException(
                        "String table truncada");
            }

            int terminator = offset;

            while (terminator < end
                    && data[terminator] != 0) {
                terminator++;
            }

            if (terminator >= end) {
                throw new IllegalArgumentException(
                        "Nombre sin terminador NUL");
            }

            names.add(new String(
                    data,
                    offset,
                    terminator - offset,
                    StandardCharsets.US_ASCII));

            // Alineación individual a 4 bytes.
            int length = terminator - offset + 1;
            offset += (length + 3) & ~3;
        }

        if (offset > end) {
            throw new IllegalArgumentException(
                    "Alineación de string table inválida");
        }

        return names;
    }

    private static long u32(ByteBuffer b, int offset) {
        return Integer.toUnsignedLong(b.getInt(offset));
    }

    private static int u16(ByteBuffer b, int offset) {
        return Short.toUnsignedInt(b.getShort(offset));
    }

    private static void checkRange(
            int offset,
            int length,
            int limit) {

        if (offset < 0
                || length < 0
                || offset > limit - length) {

            throw new IllegalArgumentException(
                    "Lectura fuera de rango: 0x"
                    + Integer.toHexString(offset));
        }
    }
}
