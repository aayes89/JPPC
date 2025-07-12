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
package pcp.system;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import pcp.cpu.CPU;
import pcp.utils.DecodingInstr;
import pcp.utils.ELFSection;

/**
 *
 * @author Slam
 */
public class Debugger {

    private final CPU cpu;
    private final Map<Integer, String> symbolTable = new HashMap<>();

    public Debugger(CPU cpu) {
        this.cpu = cpu;
    }

    public void loadSymbols(ELFSection symtab, ELFSection strtab) {
        if (symtab == null || strtab == null) {
            return;
        }

        ByteOrder byteOrder = cpu.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
        ByteBuffer symBuffer = ByteBuffer.wrap(symtab.data).order(byteOrder);
        String strings = new String(strtab.data, StandardCharsets.UTF_8);

        int entrySize = cpu.is64Bit() ? 24 : 16;
        int entries = symtab.data.length / entrySize;

        for (int i = 0; i < entries; i++) {
            int offset = i * entrySize;

            long value;
            long size;
            int nameOffset;
            int info;

            if (cpu.is64Bit()) {
                nameOffset = symBuffer.getInt(offset);
                info = symBuffer.get(offset + 4) & 0xFF;
                // Ignorar otros campos por simplicidad
                value = symBuffer.getLong(offset + 8);
                size = symBuffer.getLong(offset + 16);
            } else {
                nameOffset = symBuffer.getInt(offset);
                value = symBuffer.getInt(offset + 4) & 0xFFFFFFFFL;
                size = symBuffer.getInt(offset + 8) & 0xFFFFFFFFL;
                info = symBuffer.get(offset + 12) & 0xFF;
            }

            // Solo funciones y objetos globales
            if ((info & 0x0F) == 2 /*STT_FUNC*/ || (info & 0x0F) == 1 /*STT_OBJECT*/) {
                int end = nameOffset;
                while (end < strtab.data.length && strtab.data[end] != 0) {
                    end++;
                }
                String name = new String(strtab.data, nameOffset, end - nameOffset, StandardCharsets.UTF_8);
                symbolTable.put((int) value, name);
            }
        }
    }

    public void disassembleInstruction(long address) {
        long instruction = cpu.readMemoryWord(address);

        // Verificar si es una dirección de símbolo
        String symbol = symbolTable.get(address);
        if (symbol != null) {
            System.out.println("\n" + symbol + ":");
        }

        System.out.printf("0x%08X: 0x%08X  ", address, instruction);
        disassemble(instruction);
    }

    public void step() {
        printRegisters();
        disassembleInstruction(cpu.getPc());
        waitForUserInput();

        // Ejecutar una instrucción
        int instruction = cpu.fetchInstruction();
        DecodingInstr.InstructionFields fields = DecodingInstr.decode(instruction);
        cpu.execute(instruction, fields);
    }

    private void printRegisters() {
        //System.out.println("PC: 0x" + Integer.toHexString(cpu.getPc()));
        //System.out.println("CR: 0x" + Integer.toHexString(cpu.getCr()));        
        System.out.println(cpu.toString());
    }

    private void disassemble(long instruction) {
        int opcode = (int)instruction >>> 26;
        int extendedOpcode = (int)instruction & 0x7FF;

        switch (opcode) {
            case 31: // Instrucciones extendidas
                switch (extendedOpcode) {
                    case 266:
                        System.out.println("add");
                        break;
                    case 40:
                        System.out.println("subf");
                        break;
                    case 28:
                        System.out.println("and");
                        break;
                    // ... [otros casos] ...
                    default:
                        System.out.println("unknown extended: " + extendedOpcode);
                }
                break;
            case 14:
                System.out.println("addi");
                break;
            case 15:
                System.out.println("addis");
                break;
            case 32:
                System.out.println("lwz");
                break;
            // ... [otros opcodes] ...
            default:
                System.out.println("unknown: " + opcode);
        }
    }

    private void waitForUserInput() {
        System.out.println("Presione Enter para continuar...");
        new java.util.Scanner(System.in).nextLine();
    }
}
