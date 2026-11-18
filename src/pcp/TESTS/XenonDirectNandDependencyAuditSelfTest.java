package pcp.TESTS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import pcp.system.Memory;
import pcp.xenon.XenonFlashFs;
import pcp.xenon.XenonFlashModuleLoader;
import pcp.xenon.XenonNandParser;
import pcp.xenon.XexFile;

/**
 * Audita los proveedores declarados por XEX sin enlazar imports.
 * Demuestra qué dependencias pueden satisfacerse desde FlashFS y cuáles
 * requieren el kernel cargado por la boot chain.
 */
public final class XenonDirectNandDependencyAuditSelfTest {
    private XenonDirectNandDependencyAuditSelfTest() { }

    public static void main(String[] args) throws Exception {
        args = new String[]{"./src/pcp/TESTS/nand.bin","./src/pcp/keys.txt"};
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Uso: XenonDirectNandDependencyAuditSelfTest <nand.bin> <keys.txt>");
        }
        Path nandPath = Paths.get(args[0]).toAbsolutePath().normalize();
        Path keysPath = Paths.get(args[1]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(nandPath) || !Files.isRegularFile(keysPath)) {
            throw new IllegalArgumentException("NAND o keys.txt no encontrado");
        }

        XenonFlashFs fs = XenonFlashFs.open(XenonNandParser.load(nandPath));
        if (fs.find("xboxkrnl.exe") != null) {
            throw new AssertionError("La NAND contiene xboxkrnl.exe en FlashFS; revisar supuesto del proveedor");
        }
        List<byte[]> keys = XexFile.loadKeys(keysPath);
        XenonFlashModuleLoader loader = new XenonFlashModuleLoader(
                fs, new Memory(128 * 1024 * 1024), keys);

        audit(loader, "xam.xex", new String[]{"xboxkrnl.exe"},
                new int[]{0x45DC17E0}, new int[]{1}, new int[]{906});
        audit(loader, "bootanim.xex", new String[]{"xboxkrnl.exe"},
                new int[]{0x45DC17E0}, new int[]{1}, new int[]{218});
        audit(loader, "dash.xex", new String[]{"xam.xex", "xam.xex"},
                new int[]{0xFCA15C76, 0xFCA15C76}, new int[]{1, 1}, new int[]{623, 421});

        System.out.printf("DEPENDENCY-AUDIT PASS root=%06X entries=%d kernelProvider=BOOTCHAIN_ONLY%n",
                fs.rootOffset(), fs.entries().size());
    }

    private static void audit(XenonFlashModuleLoader loader, String moduleName,
            String[] names, int[] ids, int[] libraryCounts, int[] importCounts)
            throws Exception {
        XenonFlashModuleLoader.Module module = loader.load(moduleName);
        List<XexFile.ImportLibrary> libraries = module.getImports();
        if (libraries.size() != names.length) {
            throw new AssertionError(moduleName + " library records=" + libraries.size()
                    + " expected=" + names.length);
        }
        for (int i = 0; i < libraries.size(); i++) {
            XexFile.ImportLibrary lib = libraries.get(i);
            if (!names[i].equalsIgnoreCase(lib.name)
                    || lib.libraryId != ids[i]
                    || lib.thunkAddresses.length != importCounts[i]
                    || libraryCounts[i] != 1) {
                throw new AssertionError(moduleName + " dependency record " + i
                        + " mismatch: name=" + lib.name + " id="
                        + String.format("%08X", lib.libraryId) + " imports="
                        + lib.thunkAddresses.length);
            }
            System.out.printf("DEPENDENCY %-12s provider=%-12s id=%08X version=%08X min=%08X imports=%d digest=%s%n",
                    moduleName, lib.name, lib.libraryId, lib.version,
                    lib.minVersion, lib.thunkAddresses.length, hex(lib.digest));
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(String.format("%02X", value & 0xFF));
        return out.toString();
    }
}
