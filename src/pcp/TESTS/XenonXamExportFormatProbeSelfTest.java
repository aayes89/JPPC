package pcp.TESTS;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import pcp.system.Memory;
import pcp.xenon.XenonFlashFs;
import pcp.xenon.XenonFlashModuleLoader;
import pcp.xenon.XenonNandParser;
import pcp.xenon.XexFile;

/**
 * Probes the XAM PE export data directory without assuming PE export semantics.
 * H03.9 validates that the XUIZ payload is localization/resource metadata.
 */
public final class XenonXamExportFormatProbeSelfTest {
    private XenonXamExportFormatProbeSelfTest() { }


    public static void main(String[] args) throws Exception {
        args = new String[]{"./src/pcp/TESTS/nand.bin","./src/pcp/keys.txt"};
        if (args.length != 2) throw new IllegalArgumentException(
                "Uso: XenonXamExportFormatProbeSelfTest <nand.bin> <keys.txt>");
        List<byte[]> keys = XexFile.loadKeys(Path.of(args[1]));
        XenonFlashFs fs = XenonFlashFs.open(XenonNandParser.load(Path.of(args[0])));
        XenonFlashModuleLoader loader = new XenonFlashModuleLoader(
                fs, new Memory(128 * 1024 * 1024), keys);

        byte[] xamPayload = probe(loader, keys, "xam.xex", 0x580000, 0x2D6B, "XUIZ", 906);
        byte[] dashPayload = probe(loader, keys, "dash.xex", 0xA7D000, 0x1B66, "XDBF", 1044);
        int stringCount = inspectXuiZStrings(xamPayload);
        if (stringCount < 10) throw new AssertionError("XUIZ localization metadata not found; strings=" + stringCount);
        System.out.printf("XUIZ-PAYLOAD PASS signature=XUIZ printableStrings=%d localizationPaths=%d%n",
                stringCount, countLocalizationPaths(xamPayload));
        System.out.println("XAM-EXPORT-FORMAT-PROBE PASS (PE export directory is not a standard export table)");
    }

    private static byte[] probe(XenonFlashModuleLoader loader, List<byte[]> keys, String name,
            int expectedRva, int expectedSize, String expectedMagic, int expectedImports) throws Exception {
        XenonFlashModuleLoader.Module module = loader.load(name);
        byte[] image = module.getXex().image(keys);
        int pe = readLe32(image, 0x3C);
        if (pe < 0 || pe + 24 > image.length || readLe32(image, pe) != 0x00004550)
            throw new AssertionError(name + " no contiene PE válido");
        int optional = pe + 24;
        if (readLe16(image, optional) != 0x10B) throw new AssertionError(name + " no es PE32");
        int exportDirectory = optional + 96;
        int exportRva = readLe32(image, exportDirectory);
        int exportSize = readLe32(image, exportDirectory + 4);
        if (exportRva != expectedRva || exportSize != expectedSize || exportRva < 0
                || exportRva + 4 > image.length || exportRva + exportSize > image.length)
            throw new AssertionError(name + " DataDirectory[EXPORT] inesperado o fuera de rango");
        String magic = new String(image, exportRva, 4, StandardCharsets.US_ASCII);
        if (!expectedMagic.equals(magic) || "IMAGE_EXPORT_DIRECTORY".equals(magic))
            throw new AssertionError(name + " export payload signature=" + magic);
        int imports = module.getXex().importEntries(image).size();
        if (imports != expectedImports) throw new AssertionError(name + " imports=" + imports);
        System.out.printf("EXPORT-PROBE %-10s dir0RVA=%08X size=%08X magic=%s imports=%d%n",
                name, exportRva, exportSize, magic, imports);
        return java.util.Arrays.copyOfRange(image, exportRva, exportRva + exportSize);
    }


    private static int inspectXuiZStrings(byte[] payload) {
        int count = 0;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < payload.length; i++) {
            int c = payload[i] & 0xFF;
            if (c >= 0x20 && c <= 0x7E) current.append((char)c);
            else {
                if (current.length() >= 4) {
                    String s = current.toString();
                    count++;
                    if (s.endsWith("strings.xus") || s.contains("\\strings.xus")) {
                        System.out.printf("XUIZ-STRING offset=%04X value=%s%n", i - current.length(), s);
                    }
                }
                current.setLength(0);
            }
        }
        if (current.length() >= 4) count++;
        return count;
    }


    private static int countLocalizationPaths(byte[] payload) {
        String ascii = new String(payload, StandardCharsets.ISO_8859_1);
        int count = 0;
        for (String language : new String[]{"ja-jp", "zh-tw", "de-de", "es-es", "fr-fr", "it-it", "ko-kr", "pt-br", "pl-pl", "ru-ru"})
            if (ascii.contains(language + "\\strings.xus")) count++;
        return count;
    }


    private static int readLe16(byte[] b, int p) {
        return (b[p] & 0xFF) | ((b[p + 1] & 0xFF) << 8);
    }
    private static int readLe32(byte[] b, int p) {
        return (b[p] & 0xFF) | ((b[p + 1] & 0xFF) << 8)
                | ((b[p + 2] & 0xFF) << 16) | (b[p + 3] << 24);
    }
}
