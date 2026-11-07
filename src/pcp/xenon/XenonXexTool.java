package pcp.xenon;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

/**
 * Extrae el filesystem Xenon, decodifica XEX2 y vuelca la tabla de imports.
 */
public final class XenonXexTool {

    private XenonXexTool() {
    }

    public static void main(String[] args) throws Exception {
        /*if (args.length < 2) {
            System.out.println("Uso: XenonXexTool nand.bin directorio_salida [claves.txt]");
            return;
        }*/
        String nandP = "./src/pcp/TESTS/nand.bin", extDir = "./nandDump", keyText = "./keys.txt";

        Path nandPath = Paths.get(nandP);
        Path out = Paths.get(extDir);
        Path keysPath = Paths.get(keyText);
        Files.createDirectories(out.resolve("fs"));
        Files.createDirectories(out.resolve("image"));
        Files.createDirectories(out.resolve("resources"));

        

        List<byte[]> keys = keysPath != null && Files.isRegularFile(keysPath)
                ? XexFile.loadKeys(keysPath) : Collections.<byte[]>emptyList();

        XenonNandParser nand = XenonNandParser.load(nandPath);
        XenonFlashFs fs = XenonFlashFs.open(nand);
        System.out.println("Raíz seleccionada: " + fs.rootInfo());
        System.out.println("Entradas: " + fs.entries().size());

        int decoded = 0, delta = 0, nonXex = 0, failed = 0;
        int resources = 0, unpacked = 0;

        Path importsPath = out.resolve("imports.csv");
        try (BufferedWriter importsWriter = Files.newBufferedWriter(importsPath, StandardCharsets.UTF_8)) {
            importsWriter.write("module,library,library_id,ordinal,type_flag,thunk_va,thunk_rva,raw_word\n");

            for (XenonFlashFs.Entry e : fs.entries()) {
                byte[] raw = fs.read(e);
                Files.write(out.resolve("fs").resolve(e.name), raw);

                if (!hasMagic(raw, "XEX2")) {
                    nonXex++;
                    System.out.printf("%-26s size=%8d magic=%s (archivo de datos)%n",
                            e.name, raw.length, hexPreview(raw, 4));
                    continue;
                }

                try {
                    XexFile x = XexFile.parse(raw);
                    if (x.compression() == 3) {
                        delta++;
                        System.out.printf("%-26s XEXP delta: enc=%d image=0x%X (pendiente de aplicar)%n",
                                e.name, x.encryption(), x.imageSize());
                        continue;
                    }

                    byte[] image = x.image(keys);
                    String ext = imageExtension(image);
                    Files.write(out.resolve("image").resolve(e.name + ext), image);
                    decoded++;

                    Path resourceDir = out.resolve("resources").resolve(safeName(e.name));
                    for (XexFile.Resource resource : x.resources()) {
                        if (resource.size > Integer.MAX_VALUE || resource.address < x.loadAddress()) {
                            continue;
                        }
                        long resourceOffset = resource.address - x.loadAddress();
                        if (resourceOffset + resource.size > image.length) {
                            continue;
                        }
                        byte[] resourceData = java.util.Arrays.copyOfRange(image,
                                (int) resourceOffset, (int) (resourceOffset + resource.size));
                        String resourceExt = hasMagic(resourceData, "RIFF") ? ".wav"
                                : (hasMagic(resourceData, "XUIZ") ? ".xuiz" : ".bin");
                        Path resourceFile = resourceDir.resolve(safeName(resource.name) + resourceExt);
                        Files.createDirectories(resourceFile.getParent());
                        Files.write(resourceFile, resourceData);
                        resources++;
                        if (hasMagic(resourceData, "XUIZ")) {
                            try {
                                unpacked += XuiZExtractor.extract(resourceData,
                                        resourceDir.resolve(safeName(resource.name)), resourceDir);
                            } catch (Exception ignored) {
                                // Se conserva el contenedor original si pertenece a otra variante.
                            }
                        }
                    }

                    StringBuilder deps = new StringBuilder();
                    for (XexFile.ImportLibrary lib : x.importLibraries()) {
                        if (deps.length() > 0) {
                            deps.append(';');
                        }
                        deps.append(lib.name).append('(').append(lib.thunkAddresses.length).append(')');
                    }
                    for (XexFile.ImportEntry imp : x.importEntries(image)) {
                        importsWriter.write(String.format(java.util.Locale.ROOT,
                                "%s,%s,%08X,%06X,%02X,%08X,%08X,%08X%n",
                                e.name, imp.library, imp.libraryId, imp.ordinal,
                                imp.typeFlag, imp.thunkAddress, imp.thunkRva, imp.rawWord));
                    }

                    System.out.printf(
                            "%-26s enc=%d comp=%d image=0x%07X base=0x%08X entry=0x%08X magic=%s PE=%s imports=[%s] resources=%d%n",
                            e.name, x.encryption(), x.compression(), image.length,
                            x.imageBase(), x.entryPoint(), hexPreview(image, 4),
                            x.originalPeName(), deps, x.resources().size());
                } catch (Exception ex) {
                    failed++;
                    System.out.printf("%-26s ERROR: %s%n", e.name, ex.getMessage());
                }
            }
        }

        System.out.printf("Resultado: %d imágenes extraídas, %d parches delta pendientes, "
                + "%d archivos no XEX, %d recursos, %d archivos XUIZ expandidos, %d errores%n",
                decoded, delta, nonXex, resources, unpacked, failed);
    }

    private static boolean hasMagic(byte[] data, String magic) {
        if (data == null || data.length < magic.length()) {
            return false;
        }
        for (int i = 0; i < magic.length(); i++) {
            if (data[i] != (byte) magic.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static String safeName(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String imageExtension(byte[] image) {
        if (hasMagic(image, "MZ")) {
            return ".pe";
        }
        if (hasMagic(image, "XUIZ") || hasMagic(image, "XUIB")) {
            return ".xui";
        }
        return ".bin";
    }

    private static String hexPreview(byte[] data, int length) {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(data.length, length);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(String.format("%02X", data[i] & 0xFF));
        }
        return sb.toString();
    }
}
