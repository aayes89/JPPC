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
     * Descifra CB_A para la familia de cabeceras observada (CB 0x0788).
     *
     * La clave RC4 se deriva como los primeros 16 bytes de
     * HMAC-SHA1(1BL_KEY, header[0x10..0x1F]). El campo 0x10..0x1F se sustituye
     * por esa clave derivada y el flujo RC4 comienza en 0x20. No se interpreta
     * esta operación como verificación de firma o integridad de CB_A.
     */
    public static byte[] decryptCBA(byte[] oneBlKey, byte[] encrypted) {
        if (oneBlKey == null || oneBlKey.length != 16) {
            throw new IllegalArgumentException("La clave 1BL debe tener 16 bytes");
        }
        if (encrypted == null || encrypted.length < 0x20) {
            throw new IllegalArgumentException("CB_A truncado: se requieren al menos 0x20 bytes");
        }
        if (encrypted[0] != 0x43 || encrypted[1] != 0x42) {
            throw new IllegalArgumentException("Magic CB inválido");
        }

        int declaredSize = readU32BE(encrypted, 0x0C);
        if (declaredSize < 0x20 || declaredSize != encrypted.length) {
            throw new IllegalArgumentException(String.format(
                    "Tamaño CB_A inconsistente: cabecera=0x%X buffer=0x%X",
                    declaredSize, encrypted.length));
        }
        int alignedSize = (declaredSize + 0x0F) & ~0x0F;
        if (alignedSize != declaredSize) {
            throw new IllegalArgumentException(String.format(
                    "CB_A no está alineado a 16 bytes: 0x%X", declaredSize));
        }

        byte[] salt = Arrays.copyOfRange(encrypted, 0x10, 0x20);
        byte[] digest = hmacSha1(oneBlKey, salt);
        byte[] rc4Key = Arrays.copyOf(digest, 16);
        byte[] output = encrypted.clone();
        try {
            System.arraycopy(rc4Key, 0, output, 0x10, rc4Key.length);
            byte[] ciphertext = Arrays.copyOfRange(output, 0x20, alignedSize);
            byte[] plaintext = rc4(rc4Key, ciphertext);
            System.arraycopy(plaintext, 0, output, 0x20, plaintext.length);
            Arrays.fill(ciphertext, (byte) 0);
            Arrays.fill(plaintext, (byte) 0);
            return output;
        } finally {
            Arrays.fill(salt, (byte) 0);
            Arrays.fill(digest, (byte) 0);
            Arrays.fill(rc4Key, (byte) 0);
        }
    }

    private static int readU32BE(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
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
        return hmacSha1(key, new byte[][]{message});
    }

    /** HMAC-SHA1 sobre varios segmentos concatenados, sin alterar sus buffers. */
    public static byte[] hmacSha1(byte[] key, byte[]... messages) {
        if (key == null || messages == null) {
            throw new IllegalArgumentException("Clave o mensaje HMAC nulo");
        }
        int totalLength = 0;
        for (byte[] message : messages) {
            if (message == null || message.length > Integer.MAX_VALUE - totalLength) {
                throw new IllegalArgumentException("Segmento HMAC nulo o demasiado grande");
            }
            totalLength += message.length;
        }
        byte[] message = new byte[totalLength];
        int messageOffset = 0;
        for (byte[] part : messages) {
            System.arraycopy(part, 0, message, messageOffset, part.length);
            messageOffset += part.length;
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
        Arrays.fill(message, (byte) 0);

        return result;
    }

    /**
     * Descifra CB_B usando el esquema v1/v2 seleccionado por la versión de CB_A.
     * Para CB_A 1928 (0x0788) se aplica v2: HMAC(CB_A key, CB_B salt,
     * CPU key, CB_A header[0..0x0F] con flags a cero).
     */
    public static byte[] decryptCBB(byte[] cbADecrypted, byte[] cbbEncrypted,
            byte[] cpuKey, int cbAVersion) {
        if (cbADecrypted == null || cbADecrypted.length < 0x3B0) {
            throw new IllegalArgumentException("CB_A descifrado no contiene su cabecera/hash CB_B");
        }
        if (cbbEncrypted == null || cbbEncrypted.length < 0x140) {
            throw new IllegalArgumentException("CB_B truncado");
        }
        if (cpuKey == null || cpuKey.length != 16) {
            throw new IllegalArgumentException("La CPU key debe tener 16 bytes");
        }
        if (cbbEncrypted[0] != 0x43 || cbbEncrypted[1] != 0x42) {
            throw new IllegalArgumentException("Magic CB_B inválido");
        }
        int declaredSize = readU32BE(cbbEncrypted, 0x0C);
        if (declaredSize != cbbEncrypted.length || declaredSize < 0x140
                || (declaredSize & 0x0F) != 0) {
            throw new IllegalArgumentException(String.format(
                    "Tamaño CB_B inconsistente/no alineado: cabecera=0x%X buffer=0x%X",
                    declaredSize, cbbEncrypted.length));
        }

        byte[] cbAKey = Arrays.copyOfRange(cbADecrypted, 0x10, 0x20);
        byte[] salt = Arrays.copyOfRange(cbbEncrypted, 0x10, 0x20);
        byte[] cbAHeader = Arrays.copyOfRange(cbADecrypted, 0, 0x10);
        byte[] digest = null;
        byte[] rc4Key = null;
        try {
            if (usesCBBKeyV2(cbAVersion)) {
                Arrays.fill(cbAHeader, 4, 8, (byte) 0);
                digest = hmacSha1(cbAKey, salt, cpuKey, cbAHeader);
            } else {
                digest = hmacSha1(cbAKey, salt, cpuKey);
            }
            rc4Key = Arrays.copyOf(digest, 16);
            byte[] output = cbbEncrypted.clone();
            System.arraycopy(rc4Key, 0, output, 0x10, 16);
            byte[] ciphertext = Arrays.copyOfRange(output, 0x20, output.length);
            byte[] plaintext = rc4(rc4Key, ciphertext);
            System.arraycopy(plaintext, 0, output, 0x20, plaintext.length);
            Arrays.fill(ciphertext, (byte) 0);
            Arrays.fill(plaintext, (byte) 0);
            return output;
        } finally {
            Arrays.fill(cbAKey, (byte) 0);
            Arrays.fill(salt, (byte) 0);
            Arrays.fill(cbAHeader, (byte) 0);
            if (digest != null) Arrays.fill(digest, (byte) 0);
            if (rc4Key != null) Arrays.fill(rc4Key, (byte) 0);
        }
    }

    /** CB_A 1928 y las revisiones listadas usan la derivación CB_B v2. */
    public static boolean usesCBBKeyV2(int cbAVersion) {
        switch (cbAVersion) {
            case 1942:
            case 1927:
            case 1928:
            case 4561:
            case 4574:
            case 4562:
            case 4569:
            case 5774:
            case 6754:
            case 7378:
            case 9231:
            case 13181:
            case 13182:
            case 16128:
                return true;
            default:
                return false;
        }
    }

    /**
     * XeCryptRotSumSha sobre la concatenación de segmentos. Requiere longitud
     * múltiplo de 8; se utiliza para comprobar el hash CB_B almacenado en CB_A.
     */
    public static byte[] rotSumSha1(byte[]... segments) {
        if (segments == null) throw new IllegalArgumentException("Segmentos nulos");
        int total = 0;
        for (byte[] segment : segments) {
            if (segment == null || segment.length > Integer.MAX_VALUE - total) {
                throw new IllegalArgumentException("Segmento RotSumSha nulo o demasiado grande");
            }
            total += segment.length;
        }
        if ((total & 7) != 0) {
            throw new IllegalArgumentException("RotSumSha requiere longitud múltiplo de 8");
        }
        byte[] data = new byte[total];
        int offset = 0;
        for (byte[] segment : segments) {
            System.arraycopy(segment, 0, data, offset, segment.length);
            offset += segment.length;
        }

        long sumCarry = 0;
        long sumForward = 0;
        long borrow = 0;
        long sumReverse = 0;
        for (int i = 0; i < data.length; i += 8) {
            long word = readU64BE(data, i);
            sumForward += word;
            sumReverse -= word;
            if (Long.compareUnsigned(sumForward, word) < 0) sumCarry++;
            sumForward = Long.rotateLeft(sumForward, 29);
            if (Long.compareUnsigned(word, sumReverse) < 0) borrow--;
            sumReverse = Long.rotateLeft(sumReverse, 31);
        }

        byte[] rotSum = new byte[0x20];
        writeU64BE(rotSum, 0, sumCarry);
        writeU64BE(rotSum, 8, sumForward);
        writeU64BE(rotSum, 16, borrow);
        writeU64BE(rotSum, 24, sumReverse);
        byte[] inverse = new byte[rotSum.length];
        for (int i = 0; i < rotSum.length; i++) inverse[i] = (byte) ~rotSum[i];
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(rotSum);
            digest.update(rotSum);
            digest.update(data);
            digest.update(inverse);
            digest.update(inverse);
            return digest.digest();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 no disponible en la JVM", ex);
        } finally {
            Arrays.fill(data, (byte) 0);
            Arrays.fill(rotSum, (byte) 0);
            Arrays.fill(inverse, (byte) 0);
        }
    }

    /**
     * Descifra CD cuando la cadena contiene CB_A/CB_B: HMAC-SHA1(CB_B key,
     * CD salt), seguido de RC4 desde el offset 0x20. En esta variante retail
     * no se aplica una segunda HMAC con CPU key.
     */
    public static byte[] decryptCD(byte[] cbBDecrypted, byte[] cdEncrypted) {
        if (cbBDecrypted == null || cbBDecrypted.length < 0x3B0) {
            throw new IllegalArgumentException("CB_B descifrado inválido");
        }
        if (cdEncrypted == null || cdEncrypted.length < 0x260
                || cdEncrypted[0] != 0x43 || cdEncrypted[1] != 0x44) {
            throw new IllegalArgumentException("Imagen CD inválida o truncada");
        }
        int declaredSize = readU32BE(cdEncrypted, 0x0C);
        if (declaredSize != cdEncrypted.length || (declaredSize & 0x0F) != 0) {
            throw new IllegalArgumentException(String.format(
                    "Tamaño CD inconsistente/no alineado: cabecera=0x%X buffer=0x%X",
                    declaredSize, cdEncrypted.length));
        }
        byte[] cbbKey = Arrays.copyOfRange(cbBDecrypted, 0x10, 0x20);
        byte[] salt = Arrays.copyOfRange(cdEncrypted, 0x10, 0x20);
        byte[] digest = null;
        byte[] rc4Key = null;
        try {
            digest = hmacSha1(cbbKey, salt);
            rc4Key = Arrays.copyOf(digest, 16);
            byte[] output = cdEncrypted.clone();
            System.arraycopy(rc4Key, 0, output, 0x10, 16);
            byte[] ciphertext = Arrays.copyOfRange(output, 0x20, output.length);
            byte[] plaintext = rc4(rc4Key, ciphertext);
            System.arraycopy(plaintext, 0, output, 0x20, plaintext.length);
            Arrays.fill(ciphertext, (byte) 0);
            Arrays.fill(plaintext, (byte) 0);
            return output;
        } finally {
            Arrays.fill(cbbKey, (byte) 0);
            Arrays.fill(salt, (byte) 0);
            if (digest != null) Arrays.fill(digest, (byte) 0);
            if (rc4Key != null) Arrays.fill(rc4Key, (byte) 0);
        }
    }

    /** Verifica CD contra el RotSumSha almacenado en CB_B + 0x39C. */
    public static boolean verifyCDIntegrity(byte[] cbBDecrypted, byte[] cdDecrypted) {
        if (cbBDecrypted == null || cbBDecrypted.length < 0x3B0
                || cdDecrypted == null || cdDecrypted.length < 0x260
                || (cdDecrypted.length & 0x0F) != 0 || cdDecrypted[0x120] != 0) {
            return false;
        }
        byte[] expected = Arrays.copyOfRange(cbBDecrypted, 0x39C, 0x3B0);
        byte[] first = Arrays.copyOfRange(cdDecrypted, 0, 0x10);
        byte[] body = Arrays.copyOfRange(cdDecrypted, 0x120, cdDecrypted.length);
        byte[] actual = null;
        try {
            actual = rotSumSha1(first, body);
            return constantTimeEquals(expected, actual);
        } finally {
            Arrays.fill(expected, (byte) 0);
            Arrays.fill(first, (byte) 0);
            Arrays.fill(body, (byte) 0);
            if (actual != null) Arrays.fill(actual, (byte) 0);
        }
    }

    /** Descifra CE con la clave que se utilizó para descifrar CD. */
    public static byte[] decryptCE(byte[] cdDecrypted, byte[] ceEncrypted) {
        if (cdDecrypted == null || cdDecrypted.length < 0x260) {
            throw new IllegalArgumentException("CD descifrado inválido");
        }
        if (ceEncrypted == null || ceEncrypted.length < 0x30
                || ceEncrypted[0] != 0x43 || ceEncrypted[1] != 0x45) {
            throw new IllegalArgumentException("Imagen CE inválida o truncada");
        }
        int declaredSize = readU32BE(ceEncrypted, 0x0C);
        int alignedSize = (declaredSize + 15) & ~15;
        if (declaredSize < 0x30 || alignedSize != ceEncrypted.length) {
            throw new IllegalArgumentException(String.format(
                    "Tamaño CE inconsistente: declarado=0x%X almacenado=0x%X",
                    declaredSize, ceEncrypted.length));
        }
        byte[] cdKey = Arrays.copyOfRange(cdDecrypted, 0x10, 0x20);
        byte[] salt = Arrays.copyOfRange(ceEncrypted, 0x10, 0x20);
        byte[] digest = null;
        byte[] rc4Key = null;
        try {
            digest = hmacSha1(cdKey, salt);
            rc4Key = Arrays.copyOf(digest, 16);
            byte[] output = ceEncrypted.clone();
            System.arraycopy(rc4Key, 0, output, 0x10, 16);
            byte[] ciphertext = Arrays.copyOfRange(output, 0x20, output.length);
            byte[] plaintext = rc4(rc4Key, ciphertext);
            System.arraycopy(plaintext, 0, output, 0x20, plaintext.length);
            Arrays.fill(ciphertext, (byte) 0);
            Arrays.fill(plaintext, (byte) 0);
            return output;
        } finally {
            Arrays.fill(cdKey, (byte) 0);
            Arrays.fill(salt, (byte) 0);
            if (digest != null) Arrays.fill(digest, (byte) 0);
            if (rc4Key != null) Arrays.fill(rc4Key, (byte) 0);
        }
    }

    /** Verifica CE contra el RotSumSha almacenado en CD + 0x24C. */
    public static boolean verifyCEIntegrity(byte[] cdDecrypted, byte[] ceDecrypted) {
        if (cdDecrypted == null || cdDecrypted.length < 0x260
                || ceDecrypted == null || ceDecrypted.length < 0x30
                || (ceDecrypted.length & 0x0F) != 0) {
            return false;
        }
        byte[] expected = Arrays.copyOfRange(cdDecrypted, 0x24C, 0x260);
        byte[] first = Arrays.copyOfRange(ceDecrypted, 0, 0x10);
        byte[] body = Arrays.copyOfRange(ceDecrypted, 0x20, ceDecrypted.length);
        byte[] actual = null;
        try {
            actual = rotSumSha1(first, body);
            return constantTimeEquals(expected, actual);
        } finally {
            Arrays.fill(expected, (byte) 0);
            Arrays.fill(first, (byte) 0);
            Arrays.fill(body, (byte) 0);
            if (actual != null) Arrays.fill(actual, (byte) 0);
        }
    }

    /**
     * Descifra y valida un KeyVault retail de 0x4000 bytes. El digest inicial
     * deriva la clave RC4; después se verifica HMAC-SHA1(CPU key, payload,
     * versión 0x0712) contra los primeros 16 bytes.
     */
    public static byte[] decryptKeyVault(byte[] cpuKey, byte[] encryptedKeyVault) {
        if (cpuKey == null || cpuKey.length != 16) {
            throw new IllegalArgumentException("La CPU key debe tener 16 bytes");
        }
        if (encryptedKeyVault == null || encryptedKeyVault.length != 0x4000) {
            throw new IllegalArgumentException("El KeyVault debe contener exactamente 0x4000 bytes");
        }
        byte[] initialHash = Arrays.copyOfRange(encryptedKeyVault, 0, 0x10);
        byte[] rc4Digest = null;
        byte[] rc4Key = null;
        byte[] output = null;
        byte[] payload = null;
        byte[] version = new byte[]{0x07, 0x12};
        byte[] expected = null;
        byte[] expected16 = null;
        byte[] hashPayload = null;
        byte[] encryptedPayload = null;
        try {
            rc4Digest = hmacSha1(cpuKey, initialHash);
            rc4Key = Arrays.copyOf(rc4Digest, 16);
            output = encryptedKeyVault.clone();
            encryptedPayload = Arrays.copyOfRange(encryptedKeyVault, 0x10, encryptedKeyVault.length);
            payload = rc4(rc4Key, encryptedPayload);
            System.arraycopy(payload, 0, output, 0x10, payload.length);
            hashPayload = Arrays.copyOfRange(output, 0x10, output.length);
            expected = hmacSha1(cpuKey, hashPayload, version);
            expected16 = Arrays.copyOf(expected, 16);
            if (!constantTimeEquals(initialHash, expected16)) {
                Arrays.fill(output, (byte) 0);
                throw new IllegalArgumentException("KeyVault HMAC inválido: CPU key incorrecta o NAND dañada");
            }
            return output;
        } finally {
            Arrays.fill(initialHash, (byte) 0);
            Arrays.fill(version, (byte) 0);
            if (rc4Digest != null) Arrays.fill(rc4Digest, (byte) 0);
            if (rc4Key != null) Arrays.fill(rc4Key, (byte) 0);
            if (payload != null) Arrays.fill(payload, (byte) 0);
            if (expected != null) Arrays.fill(expected, (byte) 0);
            if (expected16 != null) Arrays.fill(expected16, (byte) 0);
            if (hashPayload != null) Arrays.fill(hashPayload, (byte) 0);
            if (encryptedPayload != null) Arrays.fill(encryptedPayload, (byte) 0);
        }
    }

    /** Verifica CB_B contra el hash de 20 bytes almacenado en CB_A + 0x39C. */
    public static boolean verifyCBBIntegrity(byte[] cbADecrypted, byte[] cbBDecrypted) {
        if (cbADecrypted == null || cbADecrypted.length < 0x3B0
                || cbBDecrypted == null || cbBDecrypted.length < 0x140
                || (cbBDecrypted.length & 0x0F) != 0) {
            return false;
        }
        byte[] expected = Arrays.copyOfRange(cbADecrypted, 0x39C, 0x3B0);
        byte[] first = Arrays.copyOfRange(cbBDecrypted, 0, 0x10);
        byte[] body = Arrays.copyOfRange(cbBDecrypted, 0x140, cbBDecrypted.length);
        byte[] actual = null;
        try {
            actual = rotSumSha1(first, body);
            return constantTimeEquals(expected, actual);
        } finally {
            Arrays.fill(expected, (byte) 0);
            Arrays.fill(first, (byte) 0);
            Arrays.fill(body, (byte) 0);
            if (actual != null) Arrays.fill(actual, (byte) 0);
        }
    }

    private static long readU64BE(byte[] data, int offset) {
        long value = 0;
        for (int i = 0; i < 8; i++) value = (value << 8) | (data[offset + i] & 0xFFL);
        return value;
    }

    private static void writeU64BE(byte[] data, int offset, long value) {
        for (int i = 7; i >= 0; i--) {
            data[offset + i] = (byte) value;
            value >>>= 8;
        }
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
