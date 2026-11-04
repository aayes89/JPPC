package pcp.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Auditor de identidad de ELF XeLL. No descifra ni modifica el ejecutable. */
public final class XenonElfAudit {
    private XenonElfAudit() {}

    private static boolean contains(byte[] data, String value) {
        byte[] needle = value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        outer: for (int i = 0; i <= data.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) if (data[i + j] != needle[j]) continue outer;
            return true;
        }
        return false;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Uso: java pcp.utils.XenonElfAudit xenon.elf");
            System.exit(2);
        }
        Path path = Path.of(args[0]);
        byte[] data = Files.readAllBytes(path);
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        System.out.println("ELF=" + path.toAbsolutePath());
        System.out.println("SIZE=" + data.length);
        System.out.println("SHA256=" + sha);
        report(data, "XeLL legacy second stage", "XeLL - Xenon linux loader second stage");
        report(data, "XeLL Reloaded banner", "XeLL Reloaded");
        report(data, "Xenos framebuffer mode marker", "Xenos FB with 148x41 (1280x720) at 0x9e000000 initialized.");
        report(data, "USB EHCI init marker", "Initialising USB EHCI");
        report(data, "XeLL main menu marker", "XeLL Main Menu");
        System.out.println("NOTA=La ausencia de una cadena ASCII no descarta texto rasterizado, comprimido o construido en runtime.");
    }

    private static void report(byte[] data, String label, String needle) {
        System.out.println((contains(data, needle) ? "PRESENT " : "ABSENT  ") + label);
    }
}
