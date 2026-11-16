/*
 * The MIT License
 *
 * Copyright 2026 Slam.
 */
package pcp.xenon;

import java.io.Console;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.awt.GraphicsEnvironment;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;

/**
 * Credenciales de arranque proporcionadas por el propietario de la NAND.
 *
 * Las claves nunca se almacenan en el proyecto ni se imprimen. El modo
 * interactivo utiliza Console.readPassword() para evitar eco en terminal.
 * También puede cargarse un archivo externo elegido explícitamente por el
 * usuario; ese archivo permanece fuera del proyecto.
 */
public final class XenonBootCredentials implements AutoCloseable {

    private byte[] oneBlKey;
    private byte[] cpuKey;

    private XenonBootCredentials(byte[] oneBlKey, byte[] cpuKey) {
        validateKey(oneBlKey, "1BL");
        validateKey(cpuKey, "CPU");
        this.oneBlKey = oneBlKey.clone();
        this.cpuKey = cpuKey.clone();
    }

    /**
     * Solicita las claves sin eco: usa la terminal cuando existe y, en caso
     * contrario, muestra diálogos de contraseña para ejecuciones desde IDE/UI.
     */
    public static XenonBootCredentials readFromUser() throws IOException {
        if (System.console() != null) {
            return readFromConsole();
        }
        if (GraphicsEnvironment.isHeadless()) {
            throw new IOException("No hay entrada segura disponible. Use --boot-credentials <archivo> o ejecute con terminal/UI.");
        }

        char[] oneBl = null;
        char[] cpu = null;
        byte[] oneBlBytes = null;
        byte[] cpuBytes = null;
        try {
            oneBl = promptPassword("Introduzca la clave 1BL (32 hex)");
            cpu = promptPassword("Introduzca la CPU key (32 hex)");
            oneBlBytes = parseHexKey(oneBl, "1BL");
            cpuBytes = parseHexKey(cpu, "CPU");
            return new XenonBootCredentials(oneBlBytes, cpuBytes);
        } finally {
            wipe(oneBl);
            wipe(cpu);
            wipe(oneBlBytes);
            wipe(cpuBytes);
        }
    }

    private static char[] promptPassword(String message) throws IOException {
        JPasswordField field = new JPasswordField(34);
        int result = JOptionPane.showConfirmDialog(null, field, message,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            throw new IOException("Entrada de credenciales cancelada por el usuario");
        }
        return field.getPassword();
    }

    /** Solicita ambas claves sin mostrarlas en pantalla. */
    public static XenonBootCredentials readFromConsole() throws IOException {
        Console console = System.console();
        if (console == null) {
            throw new IOException("No hay consola segura disponible. Inicie desde una terminal o seleccione un archivo externo de credenciales.");
        }

        char[] oneBl = null;
        char[] cpu = null;
        byte[] oneBlBytes = null;
        byte[] cpuBytes = null;
        try {
            oneBl = console.readPassword("1BL key (32 hex): ");
            cpu = console.readPassword("CPU key (32 hex): ");
            oneBlBytes = parseHexKey(oneBl, "1BL");
            cpuBytes = parseHexKey(cpu, "CPU");
            return new XenonBootCredentials(oneBlBytes, cpuBytes);
        } finally {
            wipe(oneBl);
            wipe(cpu);
            wipe(oneBlBytes);
            wipe(cpuBytes);
        }
    }

    /**
     * Carga claves desde un archivo externo elegido por el usuario.
     * Formatos admitidos: "1BL: <32 hex>" y "CPU: <32 hex>".
     * También reconoce "Your CPU key :". Las líneas fuseset se ignoran para
     * permitir reutilizar un volcado de configuración personal.
     */
    public static XenonBootCredentials load(Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IOException("Archivo de credenciales inexistente: " + path);
        }

        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        byte[] oneBl = null;
        byte[] cpu = null;
        try {
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (i == 0 && line.startsWith("\uFEFF")) {
                    line = line.substring(1).trim();
                }
                if (line.isEmpty() || line.startsWith("#")
                        || line.toLowerCase(Locale.ROOT).startsWith("fuseset ")) {
                    continue;
                }

                int separator = line.indexOf(':');
                if (separator < 0) {
                    continue;
                }
                String label = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(separator + 1).trim();
                if (label.equals("1bl")) {
                    if (oneBl != null) {
                        throw new IOException("Clave 1BL duplicada en línea " + (i + 1));
                    }
                    byte[] parsed = parseHexString(value, "1BL");
                    if (oneBl != null) {
                        boolean same = XenonBootCrypto.constantTimeEquals(oneBl, parsed);
                        wipe(parsed);
                        if (!same) {
                            throw new IOException("Claves 1BL contradictorias en el archivo");
                        }
                    } else {
                        oneBl = parsed;
                    }
                } else if (label.equals("cpu") || label.equals("your cpu key")) {
                    byte[] parsed = parseHexString(value, "CPU");
                    if (cpu != null) {
                        boolean same = XenonBootCrypto.constantTimeEquals(cpu, parsed);
                        wipe(parsed);
                        if (!same) {
                            throw new IOException("Claves CPU contradictorias en el archivo");
                        }
                    } else {
                        cpu = parsed;
                    }
                }
            }

            if (oneBl == null || cpu == null) {
                throw new IOException("El archivo debe contener las entradas 1BL y CPU");
            }
            return new XenonBootCredentials(oneBl, cpu);
        } finally {
            wipe(oneBl);
            wipe(cpu);
        }
    }

    public synchronized byte[] getOneBlKey() {
        ensureOpen();
        return oneBlKey.clone();
    }

    public synchronized byte[] getCpuKey() {
        ensureOpen();
        return cpuKey.clone();
    }

    private static byte[] parseHexString(String value, String name) throws IOException {
        char[] chars = value.toCharArray();
        try {
            return parseHexKey(chars, name);
        } finally {
            wipe(chars);
        }
    }

    private static byte[] parseHexKey(char[] value, String name) throws IOException {
        if (value == null || value.length != 32) {
            throw new IOException("La clave " + name + " debe contener exactamente 32 dígitos hexadecimales");
        }
        byte[] result = new byte[16];
        for (int i = 0; i < result.length; i++) {
            int high = Character.digit(value[i * 2], 16);
            int low = Character.digit(value[i * 2 + 1], 16);
            if (high < 0 || low < 0) {
                wipe(result);
                throw new IOException("La clave " + name + " contiene caracteres no hexadecimales");
            }
            result[i] = (byte) ((high << 4) | low);
        }
        return result;
    }

    private static void validateKey(byte[] key, String name) {
        if (key == null || key.length != 16) {
            throw new IllegalArgumentException("La clave " + name + " debe tener 16 bytes");
        }
    }

    private void ensureOpen() {
        if (oneBlKey == null || cpuKey == null) {
            throw new IllegalStateException("Las credenciales ya fueron liberadas");
        }
    }

    private static void wipe(char[] value) {
        if (value != null) Arrays.fill(value, '\0');
    }

    private static void wipe(byte[] value) {
        if (value != null) Arrays.fill(value, (byte) 0);
    }

    @Override
    public synchronized void close() {
        wipe(oneBlKey);
        wipe(cpuKey);
        oneBlKey = null;
        cpuKey = null;
    }
}
