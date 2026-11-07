package pcp.xenon;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Extractor para contenedores XUIZ versión 3 (directorio de nombres ASCII). */
public final class XuiZExtractor {

    private static final int HEADER_SIZE = 22;
    private static final int MAX_DEPTH = 6;

    private XuiZExtractor() { }

    /** Extrae un XUIZ y devuelve el número de archivos escritos. */
    public static int extract(byte[] data, Path destination) throws IOException {
        return extract(data, destination, destination);
    }

    /**
     * Extrae permitiendo rutas relativas como ..\dash\archivo.xur, pero
     * nunca permite salir del directorio raíz autorizado del módulo.
     */
    public static int extract(byte[] data, Path destination, Path allowedRoot) throws IOException {
        Path root = allowedRoot.toAbsolutePath().normalize();
        Path dest = destination.toAbsolutePath().normalize();
        if (!dest.startsWith(root)) throw new IOException("Destino XUIZ fuera de su raíz");
        return extract(data, dest, root, 0);
    }

    private static int extract(byte[] data, Path destination, Path allowedRoot, int depth) throws IOException {
        if (data == null || data.length < HEADER_SIZE || !hasMagic(data, "XUIZ")) {
            throw new IOException("No es un contenedor XUIZ válido");
        }
        if (depth > MAX_DEPTH) throw new IOException("Profundidad XUIZ excedida");

        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        int flags = bb.getInt(4);
        long totalLength = bb.getInt(8) & 0xFFFFFFFFL;
        long dataPointer = bb.getInt(16) & 0xFFFFFFFFL;
        int entryCount = bb.getShort(20) & 0xFFFF;

        if (totalLength != data.length) {
            throw new IOException("XUIZ length no coincide: header=" + totalLength
                    + " real=" + data.length);
        }
        if (flags != 3) {
            throw new IOException("Variante XUIZ no soportada: flags=" + flags);
        }
        if (dataPointer > data.length - HEADER_SIZE) {
            throw new IOException("Directorio XUIZ fuera de rango");
        }

        int directoryStart = HEADER_SIZE;
        int directoryEnd = directoryStart + (int) dataPointer;
        int dataStart = directoryEnd;
        int p = directoryStart;
        int written = 0;

        Files.createDirectories(destination);
        for (int i = 0; i < entryCount; i++) {
            if (p + 9 > directoryEnd) {
                throw new IOException("Entrada XUIZ truncada: " + i);
            }
            long fileLength = bb.getInt(p) & 0xFFFFFFFFL;
            long fileOffset = bb.getInt(p + 4) & 0xFFFFFFFFL;
            int nameLength = data[p + 8] & 0xFF;
            p += 9;

            if (nameLength == 0 || p + nameLength > directoryEnd) {
                throw new IOException("Nombre XUIZ inválido en entrada " + i);
            }
            String name = new String(data, p, nameLength, StandardCharsets.UTF_8);
            p += nameLength;

            long start = (long) dataStart + fileOffset;
            long end = start + fileLength;
            if (start < dataStart || end > data.length || fileLength > Integer.MAX_VALUE) {
                throw new IOException("Datos XUIZ fuera de rango: " + name);
            }

            Path output = safeResolve(destination, allowedRoot, name);
            Files.createDirectories(output.getParent());
            byte[] contents = new byte[(int) fileLength];
            System.arraycopy(data, (int) start, contents, 0, contents.length);
            Files.write(output, contents);
            written++;

            if (hasMagic(contents, "XUIZ") && depth < MAX_DEPTH) {
                Path nested = output.resolveSibling(output.getFileName().toString() + ".contents");
                try {
                    written += extract(contents, nested, allowedRoot, depth + 1);
                } catch (IOException ignored) {
                    // Un XUIZ anidado de otra variante se conserva como archivo sin expandir.
                }
            }
        }
        return written;
    }

    private static Path safeResolve(Path destination, Path allowedRoot, String name) throws IOException {
        String normalized = name.replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        Path result = destination.resolve(normalized).normalize();
        if (!result.startsWith(allowedRoot.normalize())) {
            throw new IOException("Ruta XUIZ fuera de la raíz autorizada: " + name);
        }
        return result;
    }

    private static boolean hasMagic(byte[] data, String magic) {
        if (data == null || data.length < magic.length()) return false;
        for (int i = 0; i < magic.length(); i++) {
            if (data[i] != (byte) magic.charAt(i)) return false;
        }
        return true;
    }
}
