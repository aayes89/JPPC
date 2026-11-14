package pcp.xenon;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Carga y valida el módulo real de animación de arranque almacenado en FlashFS.
 * No lo ejecuta: bootanim.xex es un módulo DLL sin entry point autónomo y
 * depende del kernel, del dispositivo xbmovie, de Xenos y del subsistema XMA.
 */
public final class XenonBootAnimation {

    public static final String FILE_NAME = "bootanim.xex";
    public static final int WAVE_FORMAT_XMA = 0x0165;

    public static final class PeSection {

        public final String name;
        public final long virtualAddress;
        public final long virtualSize;
        public final long rawPointer;
        public final long rawSize;
        private final byte[] mappedData;

        private PeSection(String name, long virtualAddress, long virtualSize, long rawPointer, long rawSize, byte[] mappedData) {
            this.name = name;
            this.virtualAddress = virtualAddress;
            this.virtualSize = virtualSize;
            this.rawPointer = rawPointer;
            this.rawSize = rawSize;
            this.mappedData = mappedData;
        }

        public byte[] data() {
            return mappedData.clone();
        }
    }

    private final XenonFlashFs.Entry fileEntry;
    private final byte[] rawXex;
    private final XexFile xex;
    private final byte[] image;
    private final long crc32;
    private final long peEntryPoint;
    private final List<XexFile.ImportLibrary> importLibraries;
    private final List<XexFile.ImportEntry> importEntries;
    private final byte[] audio;
    private final int audioFormat;
    private final int audioChannels;
    private final long audioFormatRateField;
    private final PeSection xbMovieSection;
    private final int movieDevicePathOffset;
    private final int startupTitleOffset;

    private XenonBootAnimation(XenonFlashFs.Entry fileEntry, byte[] rawXex,
            XexFile xex, byte[] image, long crc32,
            long peEntryPoint,
            List<XexFile.ImportLibrary> importLibraries,
            List<XexFile.ImportEntry> importEntries,
            byte[] audio, int audioFormat, int audioChannels,
            long audioFormatRateField, PeSection xbMovieSection,
            int movieDevicePathOffset, int startupTitleOffset) {
        this.fileEntry = fileEntry;
        this.rawXex = rawXex;
        this.xex = xex;
        this.image = image;
        this.crc32 = crc32;
        this.peEntryPoint = peEntryPoint;
        this.importLibraries = Collections.unmodifiableList(new ArrayList<>(importLibraries));
        this.importEntries = Collections.unmodifiableList(new ArrayList<>(importEntries));
        this.audio = audio;
        this.audioFormat = audioFormat;
        this.audioChannels = audioChannels;
        this.audioFormatRateField = audioFormatRateField;
        this.xbMovieSection = xbMovieSection;
        this.movieDevicePathOffset = movieDevicePathOffset;
        this.startupTitleOffset = startupTitleOffset;
    }

    public static XenonBootAnimation load(XenonFlashFs flashFs) throws IOException {
        if (flashFs == null) {
            throw new IllegalArgumentException("FlashFS nulo");
        }
        XenonFlashFs.Entry entry = flashFs.find(FILE_NAME);
        if (entry == null) {
            throw new IOException("FlashFS no contiene " + FILE_NAME);
        }

        byte[] raw = flashFs.read(entry);
        XexFile xex = XexFile.parse(raw);
        if (xex.encryption() != 0) {
            throw new IOException("bootanim.xex cifrado: requiere claves XEX antes de analizarlo");
        }
        byte[] image = xex.image(Collections.<byte[]>emptyList());
        if (image.length != xex.imageSize()) {
            throw new IOException("Tamaño expandido de bootanim.xex no coincide con XEX.imageSize");
        }
        if (image.length < 0x40 || image[0] != 'M' || image[1] != 'Z') {
            throw new IOException("La imagen expandida de bootanim.xex no contiene PE/MZ");
        }

        long crc = crc32(raw);
        long peEntry = readPeEntryPoint(image);
        List<XexFile.ImportLibrary> libraries = xex.importLibraries();
        List<XexFile.ImportEntry> imports = xex.importEntries(image);

        byte[] audio = null;
        int audioFormat = -1;
        int audioChannels = 0;
        long audioRate = 0;
        for (XexFile.Resource resource : xex.resources()) {
            if (!"audio".equalsIgnoreCase(resource.name)) {
                continue;
            }
            long offset = resource.address - xex.loadAddress();
            if (offset < 0 || resource.size <= 0 || offset + resource.size > image.length
                    || resource.size > Integer.MAX_VALUE) {
                throw new IOException("Recurso audio de bootanim.xex fuera de imagen");
            }
            audio = Arrays.copyOfRange(image, (int) offset, (int) (offset + resource.size));
            int[] wave = parseWaveFormat(audio);
            audioFormat = wave[0];
            audioChannels = wave[1];
            audioRate = Integer.toUnsignedLong(wave[2]);
            break;
        }
        if (audio == null) {
            throw new IOException("bootanim.xex no contiene recurso XEX 'audio'");
        }

        PeSection movie = findSection(image, ".XBMOVIE");
        int devicePath = findAscii(image, "\\??\\xbmovie:");
        if (devicePath < 0) {
            devicePath = findUtf16Le(image, "\\??\\xbmovie:");
        }
        int title = findAscii(image, "Xbox360 Startup Animation");

        return new XenonBootAnimation(entry, raw, xex, image, crc, peEntry,
                libraries, imports, audio, audioFormat, audioChannels, audioRate,
                movie, devicePath, title);
    }

    private static long crc32(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        return crc.getValue();
    }

    private static long readPeEntryPoint(byte[] image) throws IOException {
        int pe = readLe32(image, 0x3C);
        if (pe < 0 || pe + 24L > image.length
                || image[pe] != 'P' || image[pe + 1] != 'E'
                || image[pe + 2] != 0 || image[pe + 3] != 0) {
            throw new IOException("Cabecera PE de bootanim.xex inválida");
        }
        int optionalSize = readLe16(image, pe + 20);
        int optional = pe + 24;
        if (optionalSize < 20 || optional + 20L > image.length) {
            throw new IOException("OptionalHeader PE de bootanim.xex truncado");
        }
        return Integer.toUnsignedLong(readLe32(image, optional + 16));
    }

    private static PeSection findSection(byte[] image, String wanted) throws IOException {
        int pe = readLe32(image, 0x3C);
        int count = readLe16(image, pe + 6);
        int optionalSize = readLe16(image, pe + 20);
        long table = (long) pe + 24 + optionalSize;
        if (count > 0x100 || table + (long) count * 40 > image.length) {
            throw new IOException("Tabla de secciones PE de bootanim.xex fuera de rango");
        }
        for (int i = 0; i < count; i++) {
            int p = (int) table + i * 40;
            int nameLength = 0;
            while (nameLength < 8 && image[p + nameLength] != 0) {
                nameLength++;
            }
            String name = new String(image, p, nameLength, StandardCharsets.US_ASCII);
            if (!wanted.equalsIgnoreCase(name)) {
                continue;
            }
            long virtualSize = Integer.toUnsignedLong(readLe32(image, p + 8));
            long virtualAddress = Integer.toUnsignedLong(readLe32(image, p + 12));
            long rawSize = Integer.toUnsignedLong(readLe32(image, p + 16));
            long rawPointer = Integer.toUnsignedLong(readLe32(image, p + 20));
            if (virtualAddress + virtualSize > image.length || virtualSize > Integer.MAX_VALUE) {
                throw new IOException("Sección " + wanted + " fuera de imagen expandida");
            }
            byte[] mapped = Arrays.copyOfRange(image, (int) virtualAddress,
                    (int) (virtualAddress + virtualSize));
            return new PeSection(name, virtualAddress, virtualSize, rawPointer, rawSize, mapped);
        }
        return null;
    }

    private static int[] parseWaveFormat(byte[] wave) throws IOException {
        if (wave.length < 12 || !asciiAt(wave, 0, "RIFF") || !asciiAt(wave, 8, "WAVE")) {
            throw new IOException("Recurso audio no es RIFF/WAVE");
        }
        int p = 12;
        while (p + 8 <= wave.length) {
            int size = readLe32(wave, p + 4);
            long end = (long) p + 8 + Integer.toUnsignedLong(size);
            if (end > wave.length) {
                throw new IOException("Chunk WAV fuera de recurso audio");
            }
            if (asciiAt(wave, p, "fmt ")) {
                if (size < 16) {
                    throw new IOException("Chunk fmt WAV demasiado corto");
                }
                int data = p + 8;
                return new int[]{readLe16(wave, data), readLe16(wave, data + 2),
                    readLe32(wave, data + 4)};
            }
            p = (int) end + (size & 1);
        }
        throw new IOException("Recurso audio sin chunk fmt");
    }

    private static boolean asciiAt(byte[] data, int offset, String value) {
        if (offset < 0 || offset + value.length() > data.length) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if ((data[offset + i] & 0xFF) != value.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static int findAscii(byte[] data, String value) {
        byte[] needle = value.getBytes(StandardCharsets.US_ASCII);
        outer:
        for (int i = 0; i <= data.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static int findUtf16Le(byte[] data, String value) {
        byte[] needle = new byte[value.length() * 2];
        for (int i = 0; i < value.length(); i++) {
            needle[i * 2] = (byte) value.charAt(i);
            needle[i * 2 + 1] = 0;
        }
        outer:
        for (int i = 0; i <= data.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static int readLe16(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static int readLe32(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16) | (data[offset + 3] << 24);
    }

    public XenonFlashFs.Entry fileEntry() {
        return fileEntry;
    }

    public byte[] rawXex() {
        return rawXex.clone();
    }

    public XexFile xex() {
        return xex;
    }

    public byte[] image() {
        return image.clone();
    }

    public long crc32() {
        return crc32;
    }

    public long peEntryPoint() {
        return peEntryPoint;
    }

    public List<XexFile.ImportLibrary> importLibraries() {
        return importLibraries;
    }

    public List<XexFile.ImportEntry> importEntries() {
        return importEntries;
    }

    public boolean hasKernelImportOrdinal(int ordinal) {
        for (XexFile.ImportEntry imported : importEntries) {
            if ("xboxkrnl.exe".equalsIgnoreCase(imported.library) && imported.ordinal == ordinal) {
                return true;
            }
        }
        return false;
    }

    public byte[] audio() {
        return audio.clone();
    }

    public int audioFormat() {
        return audioFormat;
    }

    public int audioChannels() {
        return audioChannels;
    }

    public long audioFormatRateField() {
        return audioFormatRateField;
    }

    public PeSection xbMovieSection() {
        return xbMovieSection;
    }

    public boolean hasMovieDevicePath() {
        return movieDevicePathOffset >= 0;
    }

    public int movieDevicePathOffset() {
        return movieDevicePathOffset;
    }

    public boolean hasStartupTitle() {
        return startupTitleOffset >= 0;
    }

    public int startupTitleOffset() {
        return startupTitleOffset;
    }

    public boolean hasStandaloneEntryPoint() {
        return xex.hasEntryPoint() || xex.entryPoint() != 0 || peEntryPoint != 0;
    }

    public boolean requiresKernelBootServices() {
        return !hasStandaloneEntryPoint();
    }
}
