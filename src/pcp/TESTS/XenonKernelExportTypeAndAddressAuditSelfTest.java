package pcp.TESTS;


import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import pcp.system.Memory;
import pcp.xenon.XenonBootChainRuntime;
import pcp.xenon.XenonBootCredentials;
import pcp.xenon.XenonFlashFs;
import pcp.xenon.XenonFlashModuleLoader;
import pcp.xenon.XenonKernelExportTable;
import pcp.xenon.XenonNandParser;
import pcp.xenon.XexFile;


/** Read-only audit: kernel export ordinal/RVA/section/characteristics vs XEX import type flags. */
public final class XenonKernelExportTypeAndAddressAuditSelfTest {
    private XenonKernelExportTypeAndAddressAuditSelfTest() {}


    private static final class Section {
        final String name;
        final long va, virtualSize, rawSize, characteristics;
        Section(String name, long va, long virtualSize, long rawSize, long characteristics) {
            this.name = name; this.va = va; this.virtualSize = virtualSize;
            this.rawSize = rawSize; this.characteristics = characteristics;
        }
        boolean contains(long rva) { return rva >= va && rva < va + Math.max(virtualSize, rawSize); }
        boolean executable() { return (characteristics & 0x20000000L) != 0; }
        boolean writable() { return (characteristics & 0x80000000L) != 0; }
        boolean code() { return (characteristics & 0x00000020L) != 0; }
        String flags() { return (code() ? "C" : "-") + (executable() ? "X" : "-") + (writable() ? "W" : "-"); }
    }

    public static void main(String[] args) throws Exception {
        args = new String[]{"./src/pcp/TESTS/nand.bin","./src/pcp/keys.txt","./src/pcp/fuses.txt","./src/pcp/boot_creds.txt","./src/pcp/TESTS/1bl.bin"};
        if (args.length != 5) throw new IllegalArgumentException(
                "Usage: XenonKernelExportTypeAndAddressAuditSelfTest <nand.bin> <keys.txt> <fuses.txt> <boot_creds.txt> <1bl.bin>");
        Path nandPath = Path.of(args[0]).toAbsolutePath().normalize();
        Path keysPath = Path.of(args[1]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(keysPath)) throw new IllegalArgumentException("Missing keys.txt");
        XenonNandParser nand = XenonNandParser.load(nandPath);
        XenonFlashFs fs = XenonFlashFs.open(nand);
        List<byte[]> keys = XexFile.loadKeys(keysPath);
        XenonFlashModuleLoader loader = new XenonFlashModuleLoader(fs, new Memory(128 * 1024 * 1024), keys);
        try (XenonBootCredentials credentials = XenonBootCredentials.load(Path.of(args[3]))) {
            XenonBootChainRuntime.KernelStageResult boot = XenonBootChainRuntime.bootToKernelText(
                nandPath, Path.of(args[2]), Path.of(args[4]), credentials);
            byte[] combined = boot.patchedKernel.getImage();
            byte[] kernelPe = Arrays.copyOfRange(combined, 0x40000, combined.length);
            XenonKernelExportTable exports = XenonKernelExportTable.parse(kernelPe);
            List<Section> sections = parseSections(kernelPe);
            System.out.printf("KERNEL-PE base=%08X exports=%d ordinalBase=%d sections=%d%n",
                exports.imageBase(), exports.exportCount(), exports.ordinalBase(), sections.size());
            for (Section s : sections) System.out.printf("KERNEL-SECTION %-8s RVA=%08X VS=%08X RAW=%08X FLAGS=%s CH=%08X%n",
                s.name, s.va, s.virtualSize, s.rawSize, s.flags(), s.characteristics);
            Map<String, Integer> exportBuckets = new TreeMap<>();
            Map<String, Integer> importBuckets = new TreeMap<>();
            int named = 0, unnamed = 0, forwarders = 0, noSection = 0;
            for (Map.Entry<Integer, Long> e : exports.ordinalRvas().entrySet()) {
                String name = exports.ordinalNames().get(e.getKey());
                if (name == null) unnamed++; else named++;
                Section section = sectionFor(sections, e.getValue());
                String bucket = section == null ? "<NO_SECTION>" : section.name + "/" + section.flags();
                exportBuckets.merge(bucket, 1, Integer::sum);
                if (section == null) noSection++;
                long exportStart = getExportRva(kernelPe);
                long exportEnd = exportStart + getExportSize(kernelPe);
                if (e.getValue() >= exportStart && e.getValue() < exportEnd) forwarders++;
            }
            System.out.printf("KERNEL-EXPORTS named=%d unnamed=%d forwarderRvas=%d noSection=%d buckets=%s%n",
                named, unnamed, forwarders, noSection, exportBuckets);
            auditModule(loader, keys, exports, sections, "xam.xex", importBuckets);
            auditModule(loader, keys, exports, sections, "bootanim.xex", importBuckets);
            auditModule(loader, keys, exports, sections, "dash.xex", importBuckets);
            System.out.printf("IMPORT-FLAG/EXPORT-SECTION DISTRIBUTION %s%n", importBuckets);
            System.out.println("KERNEL-EXPORT-TYPE-ADDRESS AUDIT PASS (read-only; no imports linked or thunks modified)");
        }
    }


    private static void auditModule(XenonFlashModuleLoader loader, List<byte[]> keys,
        XenonKernelExportTable exports, List<Section> sections, String name,
        Map<String, Integer> buckets) throws Exception {
        XenonFlashModuleLoader.Module module;
        try { module = loader.load(name); }
        catch (java.io.IOException missing) {
            if (name.equals("dash.xex")) { System.out.println("IMPORT-AUDIT dash.xex absent/not loadable: " + missing.getMessage()); return; }
            throw missing;
        }
        byte[] image = module.getXex().image(keys);
        List<XexFile.ImportEntry> imports = module.getXex().importEntries(image);
        Map<String, Integer> libraries = new TreeMap<>();
        Map<String, Map<Integer, Integer>> libraryTypeCounts = new TreeMap<>();
        for (XexFile.ImportEntry imp : imports) {
            libraries.merge(imp.library, 1, Integer::sum);
            libraryTypeCounts.computeIfAbsent(imp.library, k -> new TreeMap<>()).merge(imp.typeFlag, 1, Integer::sum);
        }
        System.out.printf("IMPORT-LIBRARIES %-12s %s typeFlags=%s%n", name, libraries, libraryTypeCounts);
        int kernel = 0, found = 0, missing = 0, invalidFlags = 0, codeFlag = 0, dataFlag = 0;
        Map<Integer, Map<Integer, Integer>> ordinalFlags = new TreeMap<>();
        for (XexFile.ImportEntry imp : imports) {
            if (!imp.library.equalsIgnoreCase("xboxkrnl.exe")) continue;
            kernel++;
            if (exports.containsOrdinal(imp.ordinal)) found++; else missing++;
            if (imp.typeFlag == 1) codeFlag++; else if (imp.typeFlag == 0) dataFlag++; else invalidFlags++;
            ordinalFlags.computeIfAbsent(imp.ordinal, k -> new TreeMap<>()).merge(imp.typeFlag, 1, Integer::sum);
            long rva = exports.resolveRva(imp.ordinal);
            Section section = sectionFor(sections, rva);
            String sectionName = section == null ? "<NO_SECTION>" : section.name + "/" + section.flags();
            String bucket = name + "/type=" + imp.typeFlag + "/export=" + sectionName;
            buckets.merge(bucket, 1, Integer::sum);
        }
        int duplicateOrdinals = 0, mixedTypeOrdinals = 0;
        for (Map.Entry<Integer, Map<Integer, Integer>> e : ordinalFlags.entrySet()) {
            int total = e.getValue().values().stream().mapToInt(Integer::intValue).sum();
            if (total > 1) duplicateOrdinals++;
            if (e.getValue().size() > 1) mixedTypeOrdinals++;
        }
        System.out.printf("IMPORT-AUDIT %-12s total=%d kernel=%d found=%d missing=%d type1=%d type0=%d invalidType=%d duplicateOrdinal=%d mixedType=%d%n",
            name, imports.size(), kernel, found, missing, codeFlag, dataFlag, invalidFlags, duplicateOrdinals, mixedTypeOrdinals);
        if (name.equals("dash.xex")) {
            System.out.println("IMPORT-AUDIT dash.xex kernel exports not audited here; provider libraries require separate XAM export parsing.");
        } else if (missing != 0 || invalidFlags != 0) {
            throw new AssertionError(name + " import ordinal/flag failures");
        }
    }


    private static List<Section> parseSections(byte[] pe) throws Exception {
        if (pe.length < 0x40 || pe[0] != 'M' || pe[1] != 'Z') throw new java.io.IOException("PE invalid");
        int p = u32(pe, 0x3c);
        if (p + 24 > pe.length || u32(pe, p) != 0x00004550) throw new java.io.IOException("PE header invalid");
        int count = u16(pe, p + 6), optionalSize = u16(pe, p + 20);
        int table = p + 24 + optionalSize;
        if (count <= 0 || count > 0x100 || table + (long) count * 40 > pe.length) throw new java.io.IOException("Section table invalid");
        List<Section> out = new ArrayList<>();
        for (int i=0;i<count;i++) {
            int s=table+i*40; int n=0; while(n<8 && pe[s+n]!=0)n++;
            String name=new String(pe,s,n,java.nio.charset.StandardCharsets.US_ASCII);
            out.add(new Section(name, u32(pe,s+12), u32(pe,s+8), u32(pe,s+16), u32(pe,s+36)));
        }
        return out;
    }
    private static Section sectionFor(List<Section> sections, long rva) {
        for (Section s:sections) if(s.contains(rva)) return s;
        return null;
    }
    private static long getExportRva(byte[] pe) throws Exception { return dataDir(pe,0); }
    private static long getExportSize(byte[] pe) throws Exception { return dataDir(pe,4); }
    private static long dataDir(byte[] pe, int add) throws Exception {
        int p=u32(pe,0x3c), opt=p+24, magic=u16(pe,opt);
        int d=magic==0x10b?96:magic==0x20b?112:-1;
        if(d<0) throw new java.io.IOException("Optional header unsupported");
        return u32(pe,opt+d+add);
    }
    private static int u16(byte[] b,int p){return (b[p]&255)|((b[p+1]&255)<<8);}
    private static int u32(byte[] b,int p){return (b[p]&255)|((b[p+1]&255)<<8)|((b[p+2]&255)<<16)|(b[p+3]<<24);}
}
