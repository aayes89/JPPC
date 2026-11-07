/*
 * The MIT License
 *
 * Copyright 2026 Slam.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package pcp.xenon;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Primitivas criptográficas utilizadas por la cadena de arranque Xenon.
 *
 * Implementa: - RC4 - SHA-1 - HMAC-SHA1 - Comparación en tiempo constante
 *
 * No implementa derivaciones de claves Xenon no verificadas, RSA ni validación
 * de firmas.
 *
 * @author Slam
 */
public final class XenonBootCrypto {

    private static final int SHA1_BLOCK_SIZE = 64;
    private static final int SHA1_DIGEST_SIZE = 20;

    private XenonBootCrypto() {
    }

    /**
     * Descifra o cifra datos utilizando RC4.
     *
     * RC4 es simétrico: la misma operación cifra y descifra.
     */
    public static byte[] rc4(byte[] key, byte[] input) {

        if (key == null || key.length == 0) {
            throw new IllegalArgumentException("Clave RC4 inválida");
        }

        if (input == null) {
            throw new IllegalArgumentException("Entrada RC4 nula");
        }

        int[] state = new int[256];
        for (int i = 0; i < 256; i++) {
            state[i] = i;
        }

        // Key Scheduling Algorithm (KSA)
        int j = 0;
        for (int i = 0; i < 256; i++) {
            j = (j + state[i] + (key[i % key.length] & 0xFF)) & 0xFF;
            int temp = state[i];
            state[i] = state[j];
            state[j] = temp;
        }

        // Pseudo-Random Generation Algorithm (PRGA)
        byte[] output = new byte[input.length];

        int i = 0;
        j = 0;

        for (int n = 0; n < input.length; n++) {
            i = (i + 1) & 0xFF;
            j = (j + state[i]) & 0xFF;

            int temp = state[i];
            state[i] = state[j];
            state[j] = temp;

            int index = (state[i] + state[j]) & 0xFF;

            int keyByte = state[index];

            output[n] = (byte) (input[n] ^ keyByte);
        }

        Arrays.fill(state, 0);

        return output;
    }

    /**
     * Calcula SHA-1.
     *
     * La implementación utiliza MessageDigest de Java.
     */
    public static byte[] sha1(byte[] input) {
        if (input == null) {
            throw new IllegalArgumentException("Entrada SHA-1 nula");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return digest.digest(input);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 no disponible en la JVM", ex);
        }
    }

    /**
     * Calcula HMAC-SHA1.
     *
     * Implementación RFC 2104.
     */
    public static byte[] hmacSha1(byte[] key, byte[] message) {
        if (key == null || message == null) {
            throw new IllegalArgumentException("Clave o mensaje HMAC nulo");
        }

        byte[] normalizedKey = key.clone();
        if (normalizedKey.length > SHA1_BLOCK_SIZE) {
            normalizedKey = sha1(normalizedKey);
        }

        byte[] paddedKey = new byte[SHA1_BLOCK_SIZE];
        System.arraycopy(normalizedKey, 0, paddedKey, 0, normalizedKey.length);
        byte[] innerPad = new byte[SHA1_BLOCK_SIZE];
        byte[] outerPad = new byte[SHA1_BLOCK_SIZE];
        for (int i = 0; i < SHA1_BLOCK_SIZE; i++) {
            innerPad[i] = (byte) (paddedKey[i] ^ 0x36);
            outerPad[i] = (byte) (paddedKey[i] ^ 0x5C);
        }

        byte[] innerData = new byte[innerPad.length + message.length];
        System.arraycopy(innerPad, 0, innerData, 0, innerPad.length);
        System.arraycopy(message, 0, innerData, innerPad.length, message.length);

        byte[] innerHash = sha1(innerData);
        byte[] outerData = new byte[outerPad.length + innerHash.length];
        System.arraycopy(outerPad, 0, outerData, 0, outerPad.length);
        System.arraycopy(innerHash, 0, outerData, outerPad.length, innerHash.length);

        byte[] result = sha1(outerData);

        Arrays.fill(normalizedKey, (byte) 0);
        Arrays.fill(paddedKey, (byte) 0);
        Arrays.fill(innerPad, (byte) 0);
        Arrays.fill(outerPad, (byte) 0);
        Arrays.fill(innerData, (byte) 0);
        Arrays.fill(outerData, (byte) 0);
        Arrays.fill(innerHash, (byte) 0);

        return result;
    }

    /**
     * Compara dos arrays sin terminar anticipadamente cuando encuentra una
     * diferencia.
     */
    public static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null) {
            return false;
        }

        int difference = a.length ^ b.length;
        int maxLength = Math.max(a.length, b.length);
        for (int i = 0; i < maxLength; i++) {
            int valueA = i < a.length ? a[i] & 0xFF : 0;
            int valueB = i < b.length ? b[i] & 0xFF : 0;
            difference |= valueA ^ valueB;
        }

        return difference == 0;
    }

    /**
     * Convierte bytes a hexadecimal.
     */
    public static String toHex(byte[] data) {
        if (data == null) {
            return "null";
        }

        StringBuilder result = new StringBuilder(data.length * 2);
        for (byte value : data) {
            result.append(String.format("%02X", value & 0xFF));
        }

        return result.toString();
    }

    /**
     * Convierte hexadecimal a bytes.
     */
    public static byte[] fromHex(String hex) {
        if (hex == null || (hex.length() & 1) != 0) {
            throw new IllegalArgumentException("Cadena hexadecimal inválida");
        }

        byte[] result = new byte[hex.length() / 2];

        for (int i = 0; i < result.length; i++) {
            int high = Character.digit(hex.charAt(i * 2), 16);
            int low = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("Carácter hexadecimal inválido");
            }

            result[i] = (byte) ((high << 4) | low);
        }

        return result;
    }
}
