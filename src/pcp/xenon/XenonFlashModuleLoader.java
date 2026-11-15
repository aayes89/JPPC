/*
 * The MIT License
 * Copyright 2026 Slam.
 */
package pcp.xenon;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import pcp.system.Memory;

/**
 * Carga un módulo XEX directamente desde FlashFS hasta su imagen PE32 PPC.
 * Mantiene separados el contenedor XEX, la imagen PE reconstruida y el
 * mapeo de secciones en RAM para que el gestor de módulos pueda resolver
 * imports y crear el hilo sólo después de validar toda la imagen.
 */
public final class XenonFlashModuleLoader {

    public static final class Module {
        private final XenonFlashFs.Entry file;
        private final XexFile xex;
        private final XenonPeLoader.LoadedImage image;
        private final List<XexFile.ImportLibrary> imports;

        private Module(XenonFlashFs.Entry file, XexFile xex,
                XenonPeLoader.LoadedImage image,
                List<XexFile.ImportLibrary> imports) {
            this.file = file;
            this.xex = xex;
            this.image = image;
            this.imports = Collections.unmodifiableList(imports);
        }

        public String getName() { return file.name; }
        public long getFileSize() { return file.size; }
        public XexFile getXex() { return xex; }
        public XenonPeLoader.LoadedImage getImage() { return image; }
        public List<XexFile.ImportLibrary> getImports() { return imports; }
        public boolean hasEntryPoint() { return image.hasEntryPoint(); }
        public long getEntryPoint() { return image.getEntryPoint(); }
    }

    private final XenonFlashFs flashFs;
    private final Memory memory;
    private final XenonPeLoader peLoader;
    private final List<byte[]> xexKeys;

    public XenonFlashModuleLoader(XenonFlashFs flashFs, Memory memory,
            List<byte[]> xexKeys) {
        if (flashFs == null || memory == null) {
            throw new IllegalArgumentException("FlashFS y Memory son obligatorios");
        }
        this.flashFs = flashFs;
        this.memory = memory;
        this.xexKeys = xexKeys == null ? Collections.emptyList() : xexKeys;
        this.peLoader = new XenonPeLoader();
    }

    /**
     * Lee el XEX desde la cadena FAT, descifra/descomprime su payload y mapea
     * el PE. No inicia CPU ni modifica PC/LR: la transición de hilo pertenece
     * al gestor de ejecución y debe ocurrir tras resolver los imports.
     */
    public Module load(String path) throws IOException {
        if (path == null || path.trim().isEmpty()) {
            throw new IOException("Ruta XEX vacía");
        }
        XenonFlashFs.Entry entry = find(path);
        if (entry == null) throw new IOException("Módulo no encontrado en NAND: " + path);
        if (entry.size > Integer.MAX_VALUE) {
            throw new IOException("XEX demasiado grande: " + entry.name + " size=" + entry.size);
        }

        byte[] container = flashFs.read(entry, 0, (int) entry.size);
        XexFile xex = XexFile.parse(container);
        byte[] pe = xex.image(xexKeys);
        if (pe.length < 0x40 || pe[0] != 'M' || pe[1] != 'Z') {
            throw new IOException("Payload XEX no contiene una imagen PE MZ: " + entry.name);
        }

        XenonPeLoader.LoadedImage loaded = peLoader.load(pe, memory);
        long declaredBase = xex.imageBase() & 0xFFFFFFFFL;
        if (declaredBase != 0 && declaredBase != loaded.getImageBase()) {
            throw new IOException(String.format(
                    "ImageBase XEX/PE inconsistente en %s: XEX=%08X PE=%08X",
                    entry.name, declaredBase, loaded.getImageBase()));
        }

        List<XexFile.ImportLibrary> imports = xex.importLibraries();
        System.out.printf("[XEX-MODULE] MAPPED name=%s file=%08X base=%08X size=%08X "
                        + "entry=%08X imports=%d resources=%d%n",
                entry.name, entry.size, loaded.getImageBase(),
                loaded.getSizeOfImage(), loaded.getEntryPoint(), imports.size(),
                xex.resources().size());
        return new Module(entry, xex, loaded, imports);
    }

    private XenonFlashFs.Entry find(String path) {
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        XenonFlashFs.Entry entry = flashFs.find(normalized);
        if (entry != null) return entry;
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) return flashFs.find(normalized.substring(slash + 1));
        return null;
    }
}
