/*
 * The MIT License
 *
 * Copyright 2025 Slam.
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
package pcp.utils;

/**
 *
 * @author Slam
 */
public class ELFSection {
    // Atributos requeridos
    public long address;       // Dirección virtual (VMA)
    public long size;          // Tamaño de la sección
    public long offset;        // Offset en el archivo
    public byte[] data;        // Datos de la sección
    public String name;        // Nombre de la sección
    public int nameOffset;     // Offset en la tabla de strings
    public int type;           // Tipo de sección (SHT_*)
    public long flags;         // Flags de la sección (SHF_*)
    public int link;           // Índice a sección relacionada
    public int info;           // Información adicional específica
    public long addralign;     // Alineación de la sección
    public long entsize;       // Tamaño de entrada (para tablas)
    
    // Constructor para inicializar valores por defecto
    public ELFSection() {
        address = 0;
        size = 0;
        offset = 0;
        data = null;
        name = "";
        nameOffset = 0;
        type = 0;
        flags = 0;
        link = 0;
        info = 0;
        addralign = 1;
        entsize = 0;
    }
}