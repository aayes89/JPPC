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

/** Verifica carga XEX desde NAND/FlashFS sin materializar archivos intermedios. */
public final class XenonDirectNandModuleLoadSelfTest {
    private XenonDirectNandModuleLoadSelfTest() { }

    public static void main(String[] args) throws Exception {
        args = new String[]{"./src/pcp/TESTS/nand.bin","./src/pcp/keys.txt"};
        if (args.length != 2) {
            throw new IllegalArgumentException("Uso: XenonDirectNandModuleLoadSelfTest <nand.bin> <keys.txt>");
        }
        Path nandPath = Paths.get(args[0]).toAbsolutePath().normalize();
        Path keysPath = Paths.get(args[1]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(nandPath) || !Files.isRegularFile(keysPath)) {
            throw new IllegalArgumentException("NAND o keys.txt no encontrado");
        }

        XenonNandParser nand = XenonNandParser.load(nandPath);
        XenonFlashFs fs = XenonFlashFs.open(nand);
        List<byte[]> keys = XexFile.loadKeys(keysPath);
        Memory memory = new Memory(128 * 1024 * 1024);
        XenonFlashModuleLoader loader = new XenonFlashModuleLoader(fs, memory, keys);

        check(loader, memory, "xam.xex", 0x815F0000L, true);
        check(loader, memory, "bootanim.xex", 0x98000000L, false);
        check(loader, memory, "dash.xex", 0x92000000L, true);
        System.out.printf("Xenon direct NAND module load: ALL OK (FlashFS entries=%d)\n", fs.entries().size());
    }

    private static void check(XenonFlashModuleLoader loader, Memory memory,
            String name, long expectedBase, boolean hasEntry) throws Exception {
        XenonFlashModuleLoader.Module module = loader.load(name);
        if (module.getImage().getImageBase() != expectedBase) {
            throw new AssertionError(name + " ImageBase inesperado: "
                    + Long.toHexString(module.getImage().getImageBase()));
        }
        if (module.hasEntryPoint() != hasEntry) {
            throw new AssertionError(name + " hasEntryPoint inesperado");
        }
        if (memory.readMemoryWord(expectedBase) != 0x4D5A9000) {
            throw new AssertionError(name + " no quedó mapeado en RAM");
        }
        System.out.printf("DIRECT-NAND PASS %s base=%08X size=%08X entry=%08X\n",
                name, module.getImage().getImageBase(), module.getImage().getSizeOfImage(),
                module.getEntryPoint());
    }
}
