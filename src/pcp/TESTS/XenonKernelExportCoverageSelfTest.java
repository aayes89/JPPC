package pcp.TESTS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import pcp.system.Memory;
import pcp.xenon.XenonBootChainRuntime;
import pcp.xenon.XenonBootCredentials;
import pcp.xenon.XenonFlashFs;
import pcp.xenon.XenonFlashModuleLoader;
import pcp.xenon.XenonKernelExportTable;
import pcp.xenon.XenonNandParser;
import pcp.xenon.XexFile;

/** Cruza imports reales de NAND con exports de xboxkrnl reconstruido por boot chain. */
public final class XenonKernelExportCoverageSelfTest {
    private XenonKernelExportCoverageSelfTest() { }

    public static void main(String[] args) throws Exception {
        args = new String[]{"./src/pcp/TESTS/nand.bin","./src/pcp/keys.txt","./src/pcp/fuses.txt","./src/pcp/boot_creds.txt","./src/pcp/TESTS/1bl.bin"};
        if (args.length != 5) throw new IllegalArgumentException(
                "Uso: XenonKernelExportCoverageSelfTest <nand.bin> <keys.txt> <fuses.txt> <boot_creds.txt> <1bl.bin>");
        Path nandPath = Path.of(args[0]).toAbsolutePath().normalize();
        XenonNandParser nand = XenonNandParser.load(nandPath);
        XenonFlashFs fs = XenonFlashFs.open(nand);
        Path keys = Path.of(args[1]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(keys)) throw new IllegalArgumentException("Falta keys.txt");
        List<byte[]> xexKeys = XexFile.loadKeys(keys);
        XenonFlashModuleLoader modules = new XenonFlashModuleLoader(fs,
                new Memory(128 * 1024 * 1024), xexKeys);

        try (XenonBootCredentials credentials = XenonBootCredentials.load(Path.of(args[3]))) {
            XenonBootChainRuntime.KernelStageResult boot = XenonBootChainRuntime.bootToKernelText(
                    nandPath, Path.of(args[2]), Path.of(args[4]), credentials);
            byte[] combined = boot.patchedKernel.getImage();
            byte[] kernelPe = java.util.Arrays.copyOfRange(combined, 0x40000, combined.length);
            XenonKernelExportTable exports = XenonKernelExportTable.parse(kernelPe);
            if (exports.exportCount() < 1) throw new AssertionError("Kernel export table vacía");
            audit(modules, xexKeys, exports, "xam.xex", 906);
            audit(modules, xexKeys, exports, "bootanim.xex", 218);
            System.out.printf("KERNEL-EXPORT-COVERAGE PASS exports=%d ordinalBase=%d kernel=%08X%n",
                    exports.exportCount(), exports.ordinalBase(), boot.patchedKernel.imageInfo.kernelImageBase);
        }
    }

    private static void audit(XenonFlashModuleLoader modules, List<byte[]> keys,
            XenonKernelExportTable exports, String name, int expected) throws Exception {
        XenonFlashModuleLoader.Module module = modules.load(name);
        byte[] pe = module.getXex().image(keys);
        List<XexFile.ImportEntry> imports = module.getXex().importEntries(pe);
        int resolved = 0;
        int missing = 0;
        int executable = 0;
        int data = 0;
        java.util.TreeSet<Integer> missingOrdinals = new java.util.TreeSet<>();
        for (XexFile.ImportEntry entry : imports) {
            if (entry.library.equalsIgnoreCase("xboxkrnl.exe")) {
                if (exports.containsOrdinal(entry.ordinal)) resolved++;
                else { missing++; missingOrdinals.add(entry.ordinal); }
                if (entry.typeFlag == 1) executable++;
                else if (entry.typeFlag == 0) data++;
                else throw new AssertionError("Tipo import no soportado: " + entry.typeFlag);
            }
        }
        if (imports.size() != expected || resolved + missing != expected) {
            throw new AssertionError(name + " conteo imports inesperado=" + imports.size());
        }
        System.out.printf("KERNEL-IMPORT-COVERAGE %-12s total=%d resolved=%d missing=%d code=%d data=%d missingOrdinals=%s%n",
                name, imports.size(), resolved, missing, executable, data, missingOrdinals);
        if (missing != 0) throw new AssertionError(name + " contiene ordinals no exportados por kernel: " + missingOrdinals);
    }
}
