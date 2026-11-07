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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import pcp.gpu.Device;

/**
 * Emulación de los registros Fusebox del Xenon.
 *
 * Cada fuseset contiene 64 bits: +0: palabra alta (32 bits). +4: palabra baja
 * (32 bits).
 *
 * Los registros son de solo lectura.
 *
 * @author Slam
 */
public final class XenonFuses implements Device {

    public static final long BASE = 0xEA002000L;
    public static final long SIZE = 0x1000L;

    private static final int FUSESET_COUNT = 12;
    private static final int FUSESET_BASE_OFFSET = 0x200;
    private static final int FUSESET_STRIDE = 0x10;

    /*
     * Única fuente de datos:
     * lines[n] = (palabra alta << 32) | palabra baja.
     */
    private final long[] lines = new long[FUSESET_COUNT];

    private static final Pattern FUSESET_PATTERN = Pattern.compile(
            "(?i)^fuseset\\s+(\\d{2})\\s*:\\s*([0-9a-f]{16})$"
    );

    public XenonFuses() {
    }

    @Override
    public boolean containsAddress(long address) {
        return address >= 0 && address < SIZE;
    }

    @Override
    public byte read(long offset) {
        int word = readWord(offset & ~3L);
        int shift = 24 - (int) ((offset & 3L) * 8);
        return (byte) (word >>> shift);
    }

    @Override
    public void write(long offset, byte value) {
        // Fusebox de solo lectura.
    }

    @Override
    public synchronized int readWord(long offset) {
        long relative = offset - FUSESET_BASE_OFFSET;

        if (relative < 0) {
            return 0;
        }

        long line = relative / FUSESET_STRIDE;
        long within = relative % FUSESET_STRIDE;

        if (line >= FUSESET_COUNT) {
            return 0;
        }

        long value = lines[(int) line];

        if (within == 0) {
            return (int) (value >>> 32);
        }

        if (within == 4) {
            return (int) value;
        }

        return 0;
    }

    /**
     * Carga atómica de los 12 fusesets.
     *
     * Formato: fuseset 00: c0ffffffffffffff
     */
    public synchronized void load(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("La ruta de fuses no puede ser null");
        }

        if (!Files.isRegularFile(path)) {
            throw new IOException("Archivo de fuses no encontrado: " + path.toAbsolutePath());
        }

        List<String> fileLines = Files.readAllLines(path, StandardCharsets.UTF_8);

        long[] newLines = new long[FUSESET_COUNT];
        boolean[] loaded = new boolean[FUSESET_COUNT];

        for (int lineNumber = 0; lineNumber < fileLines.size(); lineNumber++) {
            String line = fileLines.get(lineNumber).trim();

            if (lineNumber == 0 && line.startsWith("\uFEFF")) {
                line = line.substring(1).trim();
            }

            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            Matcher matcher = FUSESET_PATTERN.matcher(line);

            if (!matcher.matches()) {
                throw new IOException("Formato inválido en línea " + (lineNumber + 1) + ": " + line);
            }

            int index = Integer.parseInt(matcher.group(1));

            if (index < 0 || index >= FUSESET_COUNT) {
                throw new IOException("Fuseset fuera de rango en línea " + (lineNumber + 1) + ": " + index);
            }

            if (loaded[index]) {
                throw new IOException("Fuseset duplicado: " + String.format("%02d", index));
            }

            String hex = matcher.group(2);

            try {
                newLines[index] = Long.parseUnsignedLong(hex, 16);
            } catch (NumberFormatException ex) {
                throw new IOException("Valor hexadecimal inválido en fuseset " + index, ex);
            }

            loaded[index] = true;
        }

        for (int i = 0; i < FUSESET_COUNT; i++) {
            if (!loaded[i]) {
                throw new IOException("Falta el fuseset " + String.format("%02d", i));
            }
        }

        // Aplicación atómica: solo se modifica el estado tras validar todo.
        System.arraycopy(newLines, 0, lines, 0, FUSESET_COUNT);

        System.out.printf("[FUSES] Cargados %d fusesets desde %s%n", FUSESET_COUNT, path.toAbsolutePath());
    }

    /**
     * Vista SoC compartida con la vista Fusebox.
     *
     * Dirección: 0x8000020000020000 + n * 0x200
     *
     * +0: palabra alta. +4: palabra baja.
     */
    public static final long SOC_BASE = 0x8000020000020000L;
    public static final int SOC_STRIDE = 0x200;
    public static final long SOC_SIZE = (long) FUSESET_COUNT * SOC_STRIDE;

    public Device socView() {
        return new Device() {

            @Override
            public boolean containsAddress(long address) {
                return address >= 0 && address < SOC_SIZE;
            }

            @Override
            public byte read(long offset) {
                int word = readWord(offset & ~3L);
                int shift = 24 - (int) ((offset & 3L) * 8);
                return (byte) (word >>> shift);
            }

            @Override
            public void write(long offset, byte value) {
                // Fusebox de solo lectura.
            }

            @Override
            public int readWord(long offset) {
                if (offset < 0 || offset >= SOC_SIZE) {
                    return 0;
                }

                int line = (int) (offset / SOC_STRIDE);
                long within = offset % SOC_STRIDE;

                synchronized (XenonFuses.this) {
                    long value = lines[line];

                    if (within == 0) {
                        return (int) (value >>> 32);
                    }

                    if (within == 4) {
                        return (int) value;
                    }
                }

                return 0;
            }
        };
    }
}
