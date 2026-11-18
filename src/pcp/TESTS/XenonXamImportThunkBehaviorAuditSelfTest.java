package pcp.TESTS;


import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import pcp.system.Memory;
import pcp.xenon.XenonFlashFs;
import pcp.xenon.XenonFlashModuleLoader;
import pcp.xenon.XenonNandParser;
import pcp.xenon.XexFile;

/** H03.11: audit raw import thunk patterns before enabling XAM linking. */
public final class XenonXamImportThunkBehaviorAuditSelfTest {
    private XenonXamImportThunkBehaviorAuditSelfTest() { }

    public static void main(String[] args) throws Exception {
        args = new String[]{"./src/pcp/TESTS/nand.bin","./src/pcp/keys.txt"};
        if (args.length != 2) throw new IllegalArgumentException(
                "Usage: XenonXamImportThunkBehaviorAuditSelfTest <nand.bin> <keys.txt>");
        List<byte[]> keys = XexFile.loadKeys(Path.of(args[1]));
        XenonFlashFs fs = XenonFlashFs.open(XenonNandParser.load(Path.of(args[0])));
        XenonFlashModuleLoader loader = new XenonFlashModuleLoader(fs,
                new Memory(128 * 1024 * 1024), keys);
        audit(loader, keys, "xam.xex", new int[]{906});
        audit(loader, keys, "dash.xex", new int[]{623, 421});
        System.out.println("IMPORT-THUNK-BEHAVIOR AUDIT PASS (no thunks modified)");
    }


    private static void audit(XenonFlashModuleLoader loader, List<byte[]> keys,
            String moduleName, int[] expectedCounts) throws Exception {
        XenonFlashModuleLoader.Module module = loader.load(moduleName);
        byte[] image = module.getXex().image(keys);
        List<XexFile.ImportLibrary> libs = module.getXex().importLibraries();
        List<XexFile.ImportEntry> entries = module.getXex().importEntries(image);
        if (libs.size() != expectedCounts.length) throw new AssertionError("library count: " + moduleName);
        for (int li = 0; li < libs.size(); li++) {
            TreeMap<Integer, Integer> typeCounts = new TreeMap<>();
            TreeMap<Integer, TreeSet<Integer>> ordinalTypes = new TreeMap<>();
            TreeMap<Integer, Integer> ordinalFrequency = new TreeMap<>();
            long minRva = Long.MAX_VALUE, maxRva = Long.MIN_VALUE;
            int recordCount = 0, aligned = 0, unaligned = 0;
            for (XexFile.ImportEntry e : entries) {
                if (e.libraryIndex != li) continue;
                recordCount++;
                typeCounts.merge(e.typeFlag, 1, Integer::sum);
                ordinalTypes.computeIfAbsent(e.ordinal, k -> new TreeSet<>()).add(e.typeFlag);
                ordinalFrequency.merge(e.ordinal, 1, Integer::sum);
                minRva = Math.min(minRva, e.thunkRva);
                maxRva = Math.max(maxRva, e.thunkRva);
                if ((e.thunkAddress & 3L) == 0) aligned++; else unaligned++;
            }
            if (recordCount != expectedCounts[li] || recordCount != libs.get(li).thunkAddresses.length)
                throw new AssertionError(moduleName + " record " + li + " count=" + recordCount);
            int ordinalsWithMixedTypes = 0, repeatedOrdinalCount = 0;
            for (Map.Entry<Integer, TreeSet<Integer>> item : ordinalTypes.entrySet()) {
                if (item.getValue().size() > 1) ordinalsWithMixedTypes++;
                if (ordinalFrequency.get(item.getKey()) > 1) repeatedOrdinalCount++;
            }
            if (typeCounts.keySet().stream().anyMatch(t -> t != 0 && t != 1))
                throw new AssertionError(moduleName + " unsupported types " + typeCounts);
            System.out.printf("THUNK-AUDIT module=%s record=%d count=%d types=%s uniqueOrdinals=%d repeatedOrdinals=%d mixedTypeOrdinals=%d aligned=%d unaligned=%d RVA=[%08X,%08X]%n",
                    moduleName, li, recordCount, typeCounts, ordinalTypes.size(),
                    repeatedOrdinalCount, ordinalsWithMixedTypes, aligned, unaligned,
                    minRva, maxRva);
        }
    }
}
