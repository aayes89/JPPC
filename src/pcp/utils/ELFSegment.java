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
public class ELFSegment {
    // Atributos requeridos
    public long virtualAddress;  // Dirección virtual (VMA)
    public long physicalAddress; // Dirección física (LMA)
    public long fileOffset;      // Offset en el archivo
    public long fileSize;        // Tamaño en el archivo
    public long memorySize;      // Tamaño en memoria
    public int flags;            // Flags del segmento (PF_*)
    public long align;           // Alineación del segmento
    
    // Constructor para inicializar valores por defecto
    public ELFSegment() {
        virtualAddress = 0;
        physicalAddress = 0;
        fileOffset = 0;
        fileSize = 0;
        memorySize = 0;
        flags = 0;
        align = 4096; // Alineación por defecto de 4K
    }
}
