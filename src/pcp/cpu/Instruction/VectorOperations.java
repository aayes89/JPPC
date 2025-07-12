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
package pcp.cpu.Instruction;

import pcp.cpu.CPU;
import pcp.utils.DecodingInstr;

/**
 *
 * @author Slam
 */
public class VectorOperations {

    private final CPU cpu;

    public VectorOperations(CPU cpu) {
        this.cpu = cpu;
    }

    public void execute(DecodingInstr.InstructionFields fields) {
        
        int shb = (fields.instruction() >> 6) & 0xF;

        switch (fields.xo()) {
            case 0:
                break; // nop
            case 2: // vand
                vand(fields.rt(), fields.ra(), fields.rb());
                break;
            case 4:
                vaddubm(fields.rt(), fields.ra(), fields.rb());
                break;
            case 8:
                vaddsbm(fields.rt(), fields.ra(), fields.rb());
                break;
            case 10:
                vperm(fields.rt(), fields.ra(), fields.rb(), fields.rc());
                break;
            case 12:
                vsububm(fields.rt(), fields.ra(), fields.rb());
                break;
            case 16:
                // not implemented yet
                System.out.println("Opcode 62, subOpcode 16: not implemented yet");
                break;
            case 38:
                vxor(fields.rt(), fields.ra(), fields.rb());
                break;
            case 44:
                vspltisb(fields.rt(), fields.si()<<16);
                break;
            case 64:
                vmulubm(fields.rt(), fields.ra(), fields.rb());
                break;
            case 74: // vaddfp
                vaddfp(fields.rt(), fields.ra(), fields.rb());
                break;
            case 76:
                vsldoi(fields.rt(), fields.ra(), fields.rb(), shb);
                break;
            case 80:
                vspltish(fields.rt(), fields.si()<<16);
                break;
            case 86:
                vceqb(fields.rt(), fields.ra(), fields.rb());
                break;
            case 104:
                vadduhm(fields.rt(), fields.ra(), fields.rb());
                break;
            case 106: // vmaddfp
                vmaddfp(fields.rt(), fields.ra(), fields.rb(), fields.rc());
                break;
            case 518: // lvx
                lvx(fields.rt(), fields.ra(), fields.rb());
                break;
            case 774: // stvx
                stvx(fields.rt(), fields.ra(), fields.rb());
                break;
            case 1284: // vnor
                vnor(fields.rt(), fields.ra(), fields.rb());
                break;
            default:
                throw new UnsupportedOperationException("Vector opcode not implemented: " + fields.xo());
        }
    }

    // vaddubm - Vector Add Unsigned Byte Modulo
    public void vaddubm(int VRT, int VRA, int VRB) {
        int[] va  = cpu.getVRVal(VRA);
        int[] vb = cpu.getVRVal(VRB);
        int[] result = new int[4];

        for (int i = 0; i < 16; i++) {
            int byteA = (va[i                                  / 4] >> (24 - 8 * (i % 4))) & 0xFF;
            int byteB = (vb[i / 4] >> (24 - 8 * (i % 4))) & 0xFF;
            int sum = (byteA + byteB) & 0xFF;
            result[i / 4] |= sum << (24 - 8 * (i % 4));
        }

        cpu.setVR(VRT, result);
    }

    public void lvx(int vrt, int ra, int rb) {
        long addr = cpu.getGPRVal(ra) + cpu.getGPRVal(rb);
        int[] data = new int[4];
        for (int i = 0; i < 4; i++) {
            data[i] = (int)cpu.readMemoryWord(addr + i * 4);
        }
        cpu.setVR(vrt, data);
    }

    // vperm - Vector Permute
    public void vperm(int VRT, int VRA, int VRB, int VRC) {
        // Obtener los vectores
        int[] va  = cpu.getVRVal(VRA);
        int[] vb = cpu.getVRVal(VRB);
        int[] vc = cpu.getVRVal(VRC);

        // Validar entradas
        if (va  == null || vb == null || vc == null || va.length != 4 || vb.length != 4 || vc.length != 4) {
            throw new IllegalArgumentException("Invalid vector register values or lengths");
        }

        int[] result = new int[4]; // Resultado de 4 palabras de 32 bits

        for (int i = 0; i < 16; i++) {
            // Extraer el byte de control
            int selector = (vc[i / 4] >> (24 - 8 * (i % 4))) & 0xFF;
            int source = (selector & 0x10) != 0 ? 1 : 0; // 0 = va, 1 = vb
            int byteIndex = selector & 0x0F; // Limitar a 0-15

            // Seleccionar la fuente correcta
            int[] sourceArray = (source == 0) ? va  : vb;
            int wordIndex = byteIndex / 4; // Palabra que contiene el byte
            int byteOffset = (byteIndex % 4) * 8; // Desplazamiento dentro de la palabra

            // Extraer el byte
            int word = sourceArray[wordIndex];
            int byteVal = (word >> (24 - byteOffset)) & 0xFF;

            // Insertar el byte en el resultado
            int resultIndex = i / 4;
            int resultOffset = (24 - 8 * (i % 4));
            result[resultIndex] |= byteVal << resultOffset;
        }

        // Establecer el resultado
        cpu.setVR(VRT, result);
    }

    public void stvx(int VRS, int RA, int RB) {
        long addr = cpu.getGPRVal(RA) + cpu.getGPRVal(RB);
        int[] vec = cpu.getVRVal(VRS);
        for (int i = 0; i < 4; i++) {
            cpu.writeMemoryWord(addr + i * 4, vec[i]);
        }
    }

    public void vaddsbm(int VRT, int VRA, int VRB) {
        int[] va  = cpu.getVRVal(VRA);
        int[] vb = cpu.getVRVal(VRB);
        int[] res = new int[4];
        for (int i = 0; i < 16; i++) {
            int off = 24 - 8 * (i % 4);
            byte a = (byte) ((va[i                              / 4] >> off) & 0xFF);
            byte b = (byte) ((vb[i / 4] >> off) & 0xFF);
            int sum = (a + b) & 0xFF;             // se envuelve
            res[i / 4] |= (sum << off);
        }
        cpu.setVR(VRT, res);
    }

    public void vsububm(int VRT, int VRA, int VRB) {
        int[] va  = cpu.getVRVal(VRA);
        int[] vb = cpu.getVRVal(VRB);
        int[] res = new int[4];
        for (int i = 0; i < 16; i++) {
            int off = 24 - 8 * (i % 4);
            int a = (va[i                             / 4] >> off) & 0xFF;
            int b = (vb[i / 4] >> off) & 0xFF;
            int diff = (a - b) & 0xFF;
            res[i / 4] |= (diff << off);
        }
        cpu.setVR(VRT, res);
    }

    public void vxor(int VRT, int VRA, int VRB) {
        int[] va  = cpu.getVRVal(VRA);
        int[] vb = cpu.getVRVal(VRB);
        int[] res = new int[4];
        for (int i = 0; i < 4; i++) {
            res[i] = va[i] ^ vb[i];
        }
        cpu.setVR(VRT, res);
    }

    public void vmulubm(int VRT, int VRA, int VRB) {
        int[] va  = cpu.getVRVal(VRA);
        int[] vb = cpu.getVRVal(VRB);
        int[] res = new int[4];
        for (int i = 0; i < 16; i++) {
            int off = 24 - 8 * (i % 4);
            int a = (va[i                             / 4] >> off) & 0xFF;
            int b = (vb[i / 4] >> off) & 0xFF;
            int prod = (a * b) & 0xFF;
            res[i / 4] |= (prod << off);
        }
        cpu.setVR(VRT, res);
    }

    public void vceqb(int VRT, int VRA, int VRB) {
        int[] va  = cpu.getVRVal(VRA);
        int[] vb = cpu.getVRVal(VRB);
        int[] res = new int[4];
        for (int i = 0; i < 16; i++) {
            int off = 24 - 8 * (i % 4);
            int a = (va[i                           / 4] >> off) & 0xFF;
            int b = (vb[i / 4] >> off) & 0xFF;
            int eq = (a == b) ? 0xFF : 0x00;
            res[i / 4] |= (eq << off);
        }
        cpu.setVR(VRT, res);
    }

    /*public void vadduhm(int VRT, int VRA, int VRB) {
        int[] va  = cpu.getVRVal(VRA);
        int[] vb = cpu.getVRVal(VRB);
        int[] res = new int[4];
        for (int i = 0; i < 8; i++) {
            int wordIndex = i / 2;
            int shift = (i % 2 == 0) ? 16 : 0;
            int a = (va[wordIndex] >> shift) & 0xFFFF;
            int b = (vb[wordIndex] >> shift) & 0xFFFF;
            int sum = (a + b) & 0xFFFF;
            res[wordIndex] |= (sum << shift);
        }
        cpu.setVR(VRT, res);
    }*/
    public void vadduhm(int VRT, int VRA, int VRB) {
        int[] va  = cpu.getVRVal(VRA);
        int[] vb = cpu.getVRVal(VRB);
        int[] res = new int[4];

        for (int i = 0; i < 8; i++) {
            int wordIndex = i / 2;
            int shift = (i % 2 == 0) ? 16 : 0;
            int a = (va[wordIndex] >> shift) & 0xFFFF;
            int b = (vb[wordIndex] >> shift) & 0xFFFF;
            int sum = (a + b) & 0xFFFF;
            res[wordIndex] |= sum << shift;
        }
        cpu.setVR(VRT, res);
    }

    public void vspltisb(int VRT, int imm) {
        int value = (imm << 27) >> 27;
        int b = value & 0xFF;
        int packed = (b << 24) | (b << 16) | (b << 8) | b;
        cpu.setVR(VRT, new int[]{packed, packed, packed, packed});
    }

    public void vspltish(int VRT, int imm) {
        int value = (imm << 27) >> 27;
        int h = value & 0xFFFF;
        int packed = (h << 16) | h;
        cpu.setVR(VRT, new int[]{packed, packed, packed, packed});
    }

    public void vsldoi(int VRT, int VRA, int VRB, int shb) {
        // shb = número de bytes a rotar (0–15)
        int[] va  = cpu.getVRVal(VRA);
        int[] vb = cpu.getVRVal(VRB);
        int[] res = new int[4];
        byte[] tmp = new byte[32];
        for (int i = 0; i < 4; i++) {
            for (int b = 0; b < 4; b++) {
                tmp[i * 4 + b] = (byte) ((va[i] >> (24 - 8 * b)) & 0xFF);
                tmp[i * 4 + b + 16] = (byte) ((vb[i] >> (24 - 8 * b)) & 0xFF);
            }
        }
        // concatenar [va||vb], rotar shb bytes y reempaquetar
        for (int i = 0; i < 16; i++) {
            byte val = tmp[(i + shb) & 0x1F];
            int wordIndex = i / 4;
            int byteOffset = 24 - 8 * (i % 4);
            res[wordIndex] |= (val & 0xFF) << byteOffset;
        }
        cpu.setVR(VRT, res);
    }

    // Vector AND
    public void vand(int VRT, int VRA, int VRB) {
        int[] va  = cpu.getVRVal(VRA);
        int[] vb = cpu.getVRVal(VRB);
        int[] res = new int[4];
        for (int i = 0; i < 4; i++) {
            res[i] = va[i] & vb[i];
        }
        cpu.setVR(VRT, res);
    }

// Vector Add Floating-Point
    public void vaddfp(int VRT, int VRA, int VRB) {
        int[] va  = cpu.getVRVal(VRA);
        int[] vb = cpu.getVRVal(VRB);
        int[] res = new int[4];
        for (int i = 0; i < 4; i++) {
            float a = Float.intBitsToFloat(va[i]);
            float b = Float.intBitsToFloat(vb[i]);
            float sum = a + b;
            res[i] = Float.floatToIntBits(sum);
        }
        cpu.setVR(VRT, res);
    }

// Vector Multiply-Add Floating-Point (VMX128)
    public void vmaddfp(int VRT, int VRA, int VRB, int VRC) {
        int[] va  = cpu.getVRVal(VRA);
        int[] vb = cpu.getVRVal(VRB);
        int[] vc = cpu.getVRVal(VRC);
        int[] res = new int[4];
        for (int i = 0; i < 4; i++) {
            float a = Float.intBitsToFloat(va[i]);
            float b = Float.intBitsToFloat(vb[i]);
            float c = Float.intBitsToFloat(vc[i]);
            float result = a * b + c;
            res[i] = Float.floatToIntBits(result);
        }
        cpu.setVR(VRT, res);
    }

    // Vector NOR
    public void vnor(int vrt, int vra, int vrb) {
        int[] va  = cpu.getVRVal(vra);
        int[] vb = cpu.getVRVal(vrb);
        int[] res = new int[4];
        for (int i = 0; i < 4; i++) {
            res[i] = ~(va[i] | vb[i]);
        }
        cpu.setVR(vrt, res);
    }
}
