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

import java.io.IOException;
import java.util.Arrays;

/**
 * Representación de una imagen de arranque Xenon.
 *
 * Gestiona los datos extraídos de NAND y su estado de procesamiento.
 *
 * No implementa descifrado, validación HMAC ni verificación RSA.
 *
 * Los campos de cabecera se asignan únicamente cuando sus offsets y
 * significados hayan sido confirmados.
 *
 * @author Slam
 */
public final class XenonBootImage {

    public enum Type {
        UNKNOWN,
        CB_A,
        CB_B,
        CD,
        CF,
        HV,
        KERNEL
    }

    public enum State {
        EXTRACTED,
        DECRYPTED,
        INTEGRITY_VERIFIED,
        SIGNATURE_VERIFIED,
        READY
    }

    private final Type type;

    private final byte[] originalData;

    private byte[] decryptedData;

    private int version;
    private int declaredSize;

    private long loadAddress;
    private long entryPoint;

    private boolean loadAddressKnown;
    private boolean entryPointKnown;

    private State state;

    public XenonBootImage(Type type, byte[] data) throws IOException {

        if (type == null) {
            throw new IOException("Tipo de imagen no especificado");
        }

        if (data == null || data.length == 0) {
            throw new IOException("Imagen de arranque vacía");
        }

        this.type = type;
        this.originalData = data.clone();

        this.decryptedData = null;

        this.version = -1;
        this.declaredSize = data.length;

        this.loadAddress = 0;
        this.entryPoint = 0;

        this.loadAddressKnown = false;
        this.entryPointKnown = false;

        this.state = State.EXTRACTED;
    }

    public Type getType() {
        return type;
    }

    public synchronized State getState() {
        return state;
    }

    public synchronized int getVersion() {
        return version;
    }

    public synchronized void setVersion(int version) {
        if (version < 0 || version > 0xFFFF) {
            throw new IllegalArgumentException("Versión fuera de rango");
        }

        this.version = version;
    }

    public synchronized int getDeclaredSize() {
        return declaredSize;
    }

    public synchronized void setDeclaredSize(int size) {

        if (size <= 0 || size > originalData.length) {
            throw new IllegalArgumentException("Tamaño declarado inválido");
        }

        this.declaredSize = size;
    }

    public synchronized long getLoadAddress() {
        return loadAddress;
    }

    public synchronized void setLoadAddress(long address) {
        this.loadAddress = address;
        this.loadAddressKnown = true;
    }

    public synchronized boolean hasLoadAddress() {
        return loadAddressKnown;
    }

    public synchronized long getEntryPoint() {
        return entryPoint;
    }

    public synchronized void setEntryPoint(long address) {
        this.entryPoint = address;
        this.entryPointKnown = true;
    }

    public synchronized boolean hasEntryPoint() {
        return entryPointKnown;
    }

    public synchronized boolean isDecrypted() {
        return decryptedData != null;
    }

    /**
     * Datos originales extraídos de NAND.
     */
    public synchronized byte[] getOriginalData() {
        return originalData.clone();
    }

    /**
     * Datos actualmente disponibles para procesamiento.
     *
     * Devuelve los datos descifrados si existen. En caso contrario, devuelve
     * los originales.
     */
    public synchronized byte[] getData() {

        if (decryptedData != null) {
            return decryptedData.clone();
        }

        return originalData.clone();
    }

    public synchronized byte[] getDecryptedData() {

        if (decryptedData == null) {
            return null;
        }

        return decryptedData.clone();
    }

    /**
     * Establece el resultado de un descifrado.
     */
    public synchronized void setDecryptedData(byte[] data) throws IOException {

        if (data == null || data.length == 0) {
            throw new IOException("Resultado de descifrado vacío");
        }

        if (data.length != originalData.length) {
            throw new IOException("El tamaño descifrado no coincide con la imagen original");
        }

        decryptedData = data.clone();

        state = State.DECRYPTED;
    }

    /**
     * Avanza el estado de procesamiento.
     *
     * No permite retroceder ni saltar etapas.
     */
    public synchronized void advanceState(State next) throws IOException {
        if (next == null) {
            throw new IOException("Estado de imagen no especificado");
        }

        if (next.ordinal() != state.ordinal() + 1) {
            throw new IOException("Transición de estado inválida: " + state + " -> " + next);
        }

        if (next == State.DECRYPTED && !isDecrypted()) {
            throw new IOException("No existen datos descifrados");
        }

        state = next;
    }

    /**
     * Lee un byte del buffer activo.
     */
    public synchronized int readU8(int offset) throws IOException {
        byte[] data = activeData();
        checkRange(data, offset, 1);
        return data[offset] & 0xFF;
    }

    /**
     * Lee un entero de 16 bits Big Endian.
     */
    public synchronized int readU16BE(int offset) throws IOException {
        byte[] data = activeData();
        checkRange(data, offset, 2);
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    /**
     * Lee un entero de 32 bits Big Endian.
     */
    public synchronized long readU32BE(int offset) throws IOException {
        byte[] data = activeData();
        checkRange(data, offset, 4);
        return ((long) (data[offset] & 0xFF) << 24)
                | ((long) (data[offset + 1] & 0xFF) << 16)
                | ((long) (data[offset + 2] & 0xFF) << 8)
                | ((long) (data[offset + 3] & 0xFF));
    }

    /**
     * Extrae una región del buffer activo.
     */
    public synchronized byte[] readRange(int offset, int length) throws IOException {
        byte[] data = activeData();
        checkRange(data, offset, length);
        return Arrays.copyOfRange(data, offset, offset + length);
    }

    /**
     * Devuelve los primeros bytes del componente.
     */
    public synchronized byte[] getHeader(int length) throws IOException {
        if (length <= 0) {
            throw new IOException("Tamaño de cabecera inválido");
        }

        return readRange(0, length);
    }

    public synchronized int getImageSize() {
        return originalData.length;
    }

    public synchronized int getActiveSize() {
        return activeData().length;
    }

    public synchronized boolean isReady() {
        return state == State.READY;
    }

    /**
     * Comprueba que la imagen contiene un rango válido.
     */
    private void checkRange(byte[] data, int offset, int length) throws IOException {
        if (offset < 0 || length < 0 || offset > data.length || length > data.length - offset) {
            throw new IOException("Acceso fuera de límites: offset=0x" + Integer.toHexString(offset) + ", length=0x" + Integer.toHexString(length));
        }
    }

    private byte[] activeData() {
        return decryptedData != null ? decryptedData : originalData;
    }
}
