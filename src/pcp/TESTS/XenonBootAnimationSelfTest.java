package pcp.TESTS;

import java.nio.file.Path;
import pcp.xenon.XenonBootAnimation;
import pcp.xenon.XenonFlashFs;
import pcp.xenon.XenonNandParser;

/**
 * Prueba la extracción y el análisis del módulo real bootanim.xex desde NAND.
 */
public final class XenonBootAnimationSelfTest {

    private XenonBootAnimationSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        String nandPath = "./src/pcp/TESTS/nand.bin";
        /*if (args.length != 1) {
            throw new IllegalArgumentException("Uso: XenonBootAnimationSelfTest <nand.bin>");
        }*/

        XenonNandParser nand = XenonNandParser.load(Path.of(nandPath));
        XenonFlashFs flashFs = XenonFlashFs.open(nand);
        XenonBootAnimation animation = XenonBootAnimation.load(flashFs);

        require(animation.fileEntry().size == animation.rawXex().length, "Tamaño extraído de bootanim.xex inconsistente");
        require(animation.xex().encryption() == 0, "bootanim.xex no está sin cifrar");
        require(animation.xex().compression() == 2, "Compresión XEX inesperada para fixture");
        require(animation.image().length == animation.xex().imageSize(), "Tamaño de imagen XEX inconsistente");
        require(animation.audioFormat() == XenonBootAnimation.WAVE_FORMAT_XMA, "El recurso audio no es XMA");
        require(animation.audioChannels() > 0, "Metadatos de audio inválidos");
        require(animation.xbMovieSection() != null, "Falta la sección .XBMOVIE");
        require(animation.hasMovieDevicePath(), "No se encontró el dispositivo \\??\\xbmovie:");
        require(animation.hasStartupTitle(), "No se encontró el identificador Xbox360 Startup Animation");
        require(!animation.hasStandaloneEntryPoint(), "bootanim.xex no debe ejecutarse como un XEX autónomo sin entry point");
        require(animation.requiresKernelBootServices(), "No se detectaron dependencias del kernel");
        require(!animation.importLibraries().isEmpty() && !animation.importEntries().isEmpty(), "Tabla de imports XEX vacía");
        int[] requiredImports = {0x1F3, 0x1F4, 0x1F5, 0x224, 0x226, 0x25B, 0x269, 0x26A};
        for (int ordinal : requiredImports) {
            require(animation.hasKernelImportOrdinal(ordinal),
                    String.format("Falta import crítico xboxkrnl ordinal 0x%X", ordinal));
        }

        System.out.printf("FlashFS %s%n", flashFs.rootInfo());
        System.out.printf("bootanim.xex block=0x%03X size=0x%X CRC32=%08X%n", animation.fileEntry().block, animation.fileEntry().size, animation.crc32());
        System.out.printf("XEX module=%s flags=0x%X enc=%d comp=%d image=0x%X load=0x%08X entry=0x%X PEentry=0x%X%n", animation.xex().originalPeName(), animation.xex().moduleFlags(), animation.xex().encryption(), animation.xex().compression(), animation.xex().imageSize(), animation.xex().loadAddress(), animation.xex().entryPoint(), animation.peEntryPoint());
        System.out.printf("imports=%d libraries=%d audio=XMA(0x%04X) channels=%d fmtRateField=0x%X bytes=0x%X%n", animation.importEntries().size(), animation.importLibraries().size(), animation.audioFormat(), animation.audioChannels(), animation.audioFormatRateField(), animation.audio().length);
        System.out.printf(".XBMOVIE VA=0x%X size=0x%X xbmoviePathOffset=0x%X titleOffset=0x%X%n", animation.xbMovieSection().virtualAddress, animation.xbMovieSection().virtualSize, animation.movieDevicePathOffset(), animation.startupTitleOffset());
        System.out.println("Boot animation asset extracted and validated; kernel/video/XMA execution remains required.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
