package pcp.TESTS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import pcp.system.Memory;
import pcp.xenon.XenonFlashFs;
import pcp.xenon.XenonFlashModuleLoader;
import pcp.xenon.XenonNandParser;
import pcp.xenon.XexFile;
import pcp.xenon.XexImportThunk;

/** Verifica que XEX_THUNK_DATA conserve los 24 bits del ordinal. */
public final class XenonXexThunkOrdinal24SelfTest {
    private XenonXexThunkOrdinal24SelfTest() {}

    public static void main(String[] args) throws Exception {
        args = new String[]{"./src/pcp/TESTS/nand.bin","./src/pcp/keys.txt"};
        if (args.length != 2) throw new IllegalArgumentException(
                "Usage: XenonXexThunkOrdinal24SelfTest <nand.bin> <keys.txt>");
        Path nandPath = Path.of(args[0]);
        List<byte[]> keys = XexFile.loadKeys(Path.of(args[1]));
        XenonFlashFs fs = XenonFlashFs.open(XenonNandParser.load(nandPath));
        XenonFlashModuleLoader loader = new XenonFlashModuleLoader(
                fs, new Memory(128 * 1024 * 1024), keys);
        int checked = 0, highByteOrdinals = 0;
        for (String name : new String[]{"xam.xex", "bootanim.xex", "dash.xex"}) {
            XenonFlashModuleLoader.Module module = loader.load(name);
            XexFile xex = module.getXex();
            byte[] image = xex.image(keys);
            long base = xex.imageBase() & 0xFFFFFFFFL;
            for (XexFile.ImportEntry entry : xex.importEntries(image)) {
                XexImportThunk thunk = XexImportThunk.read(
                        image, base, entry.thunkAddress);
                if (thunk.ordinal != entry.ordinal || thunk.type != entry.typeFlag) {
                    throw new AssertionError(String.format(
                            "%s thunk mismatch VA=%08X raw=%08X parserOrdinal=%06X legacyOrdinal=%06X type=%02X/%02X",
                            name, entry.thunkAddress, thunk.rawValue,
                            entry.ordinal, thunk.ordinal, entry.typeFlag, thunk.type));
                }
                if ((entry.ordinal & 0xFF0000) != 0) highByteOrdinals++;
                checked++;
            }
            System.out.printf("THUNK-ORDINAL24 %-12s imports=%d base=%08X%n",
                    name, xex.importEntries(image).size(), base);
        }
        if (checked == 0) throw new AssertionError("No imports audited");
        System.out.printf("XEX-THUNK-ORDINAL24 PASS checked=%d ordinalsAbove16Bits=%d%n",
                checked, highByteOrdinals);
    }
}
