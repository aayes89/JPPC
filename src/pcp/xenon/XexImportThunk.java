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

/**
 * Acceso a XEX_THUNK_DATA dentro de una imagen PE cargada.
 *
 * @autor: Slam
 */
public final class XexImportThunk {

    public static final int THUNK_SIZE = 16;

    public final long address;
    public final long rawValue;
    public final int type;
    public final int hint;
    public final int ordinal;

    private XexImportThunk(
            long address,
            long rawValue,
            int type,
            int hint,
            int ordinal) {

        this.address = address;
        this.rawValue = rawValue;
        this.type = type;
        this.hint = hint;
        this.ordinal = ordinal;
    }

    public static XexImportThunk read(
            byte[] image,
            long imageBase,
            long thunkVA) {

        if (image == null) {
            throw new IllegalArgumentException("Imagen nula");
        }

        long offset = thunkVA - imageBase;

        if (offset < 0
                || offset > image.length - THUNK_SIZE) {

            throw new IllegalArgumentException(
                    "Thunk fuera de imagen: 0x"
                    + Long.toHexString(thunkVA));
        }

        int p = (int) offset;

        long raw
                = ((long) (image[p] & 0xFF) << 24)
                | ((long) (image[p + 1] & 0xFF) << 16)
                | ((long) (image[p + 2] & 0xFF) << 8)
                | (long) (image[p + 3] & 0xFF);

        int type = (int) ((raw >>> 24) & 0xFF);
        // XEX thunk: TYPE ocupa el byte alto y ORDINAL los 24 bits bajos.
        // El byte 16..23 no es un hint independiente.
        int hint = (int) ((raw >>> 16) & 0xFF);
        int ordinal = (int) (raw & 0x00FFFFFF);

        return new XexImportThunk(
                thunkVA,
                raw,
                type,
                hint,
                ordinal);
    }

    public boolean isFunction() {
        return type == 1;
    }

    public boolean isVariable() {
        return type == 0;
    }

    public String describe() {
        return String.format(
                "THUNK VA=%08X RAW=%08X TYPE=%02X ORD=%06X BYTE16=%02X",
                address,
                rawValue,
                type,
                ordinal,
                hint);
    }
}
