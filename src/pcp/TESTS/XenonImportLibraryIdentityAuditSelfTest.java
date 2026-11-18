package pcp.TESTS;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import pcp.system.Memory;
import pcp.xenon.XenonFlashFs;
import pcp.xenon.XenonFlashModuleLoader;
import pcp.xenon.XenonNandParser;
import pcp.xenon.XexFile;


/** H03.10: conserva identidad de registros de import duplicados al decodificar XEX. */
public final class XenonImportLibraryIdentityAuditSelfTest {
    private XenonImportLibraryIdentityAuditSelfTest() { }

    public static void main(String[] args) throws Exception {
        args = new String[]{"./src/pcp/TESTS/nand.bin","./src/pcp/keys.txt"};
        if (args.length != 2) throw new IllegalArgumentException(
                "Uso: XenonImportLibraryIdentityAuditSelfTest <nand.bin> <keys.txt>");
        List<byte[]> keys = XexFile.loadKeys(Path.of(args[1]));
        XenonFlashFs fs = XenonFlashFs.open(XenonNandParser.load(Path.of(args[0])));
        XenonFlashModuleLoader loader = new XenonFlashModuleLoader(fs,
                new Memory(128 * 1024 * 1024), keys);
        audit(loader, keys, "xam.xex", new int[]{906});
        audit(loader, keys, "dash.xex", new int[]{623, 421});
        System.out.println("IMPORT-LIBRARY-IDENTITY PASS (record identity preserved)");
    }


    private static void audit(XenonFlashModuleLoader loader, List<byte[]> keys,
            String moduleName, int[] expectedCounts) throws Exception {
        XenonFlashModuleLoader.Module module = loader.load(moduleName);
        byte[] image = module.getXex().image(keys);
        List<XexFile.ImportLibrary> libraries = module.getXex().importLibraries();
        List<XexFile.ImportEntry> entries = module.getXex().importEntries(image);
        if (libraries.size() != expectedCounts.length)
            throw new AssertionError(moduleName + " library-record count=" + libraries.size());
        for (int index = 0; index < libraries.size(); index++) {
            XexFile.ImportLibrary lib = libraries.get(index);
            TreeMap<Integer, Integer> types = new TreeMap<>();
            TreeSet<Integer> ordinals = new TreeSet<>();
            int count = 0;
            for (XexFile.ImportEntry entry : entries) {
                if (entry.libraryIndex != index) continue;
                if (!entry.library.equals(lib.name) || entry.libraryId != lib.libraryId
                        || !Arrays.equals(entry.libraryDigest, lib.digest))
                    throw new AssertionError(moduleName + " record identity mismatch at index=" + index);
                types.merge(entry.typeFlag, 1, Integer::sum);
                ordinals.add(entry.ordinal);
                count++;
            }
            if (count != expectedCounts[index] || count != lib.thunkAddresses.length)
                throw new AssertionError(moduleName + " record " + index + " count=" + count
                        + " expected=" + expectedCounts[index]);
            if (types.keySet().stream().anyMatch(t -> t != 0 && t != 1))
                throw new AssertionError(moduleName + " unsupported thunk type flags=" + types);
            System.out.printf("IMPORT-RECORD %-10s index=%d library=%s id=%08X count=%d types=%s uniqueOrdinals=%d digest=%s%n",
                    moduleName, index, lib.name, lib.libraryId, count, types, ordinals.size(), hex(lib.digest));
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder s = new StringBuilder();
        for (byte b : bytes) s.append(String.format("%02X", b));
        return s.toString();
    }
}
