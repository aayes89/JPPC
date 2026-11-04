package pcp.utils;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

/** Auditor read-only de identidad y recursos de XeLL dentro de un ELF. */
public final class XenonElfAudit {
    private static final Charset CP437 = Charset.forName("IBM437");
    private static final String LOGO_MARKER = "Free60.org XeLL - Xenon Linux Loader -git-";

    private XenonElfAudit() {}

    private static int indexOf(byte[] data, String value) {
        byte[] needle = value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        outer: for (int i = 0; i <= data.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static void report(byte[] data, String label, String needle) {
        int offset = indexOf(data, needle);
        System.out.printf("%s %s%s%n", offset >= 0 ? "PRESENT" : "ABSENT ", label,
                offset >= 0 ? " @ file+0x" + Integer.toHexString(offset) : "");
    }

    /** Extrae el bloque CP437 contiguo anterior a la atribución Free60.org. */
    private static byte[] extractLogo(byte[] data) {
        int marker = indexOf(data, LOGO_MARKER);
        if (marker < 0) return null;
        int start = marker - 1;
        while (start >= 0 && data[start] != 0) start--;
        start++;
        if (start >= marker) return null;
        return Arrays.copyOfRange(data, start, marker + LOGO_MARKER.length());
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1 && args.length != 3) {
            System.err.println("Uso: java pcp.utils.XenonElfAudit xenon.elf [--extract-logo salida.txt]");
            System.exit(2);
        }
        Path path = Path.of(args[0]);
        byte[] data = Files.readAllBytes(path);
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        System.out.println("ELF=" + path.toAbsolutePath());
        System.out.println("SIZE=" + data.length);
        System.out.println("SHA256=" + sha);
        report(data, "XeLL legacy second stage", "XeLL - Xenon linux loader second stage");
        report(data, "XeLL Reloaded literal banner", "XeLL Reloaded");
        report(data, "XeLL CP437 ASCII logo attribution", LOGO_MARKER);
        report(data, "Xenos framebuffer mode marker", "Xenos FB with 148x41 (1280x720) at 0x9e000000 initialized.");
        report(data, "USB EHCI init marker", "Initialising USB EHCI");
        report(data, "XeLL main menu marker", "XeLL Main Menu");
        if (args.length == 3) {
            if (!"--extract-logo".equals(args[1])) throw new IllegalArgumentException("Opción desconocida: " + args[1]);
            byte[] logo = extractLogo(data);
            if (logo == null) throw new IllegalStateException("No se encontró el bloque CP437 del logo");
            Path output = Path.of(args[2]);
            Files.writeString(output, new String(logo, CP437), CP437);
            System.out.println("LOGO_EXTRACTED=" + output.toAbsolutePath());
            System.out.println("LOGO_SIZE=" + logo.length);
        }
        System.out.println("NOTA=Las cadenas ausentes pueden generarse en runtime; la presencia de recursos no prueba su ejecución.");
    }
}
