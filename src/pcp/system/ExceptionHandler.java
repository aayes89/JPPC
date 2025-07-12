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

import pcp.cpu.CPU;
import pcp.utils.DecodingInstr;

/**
 *
 * @author Slam
 */
public class ExceptionHandler {

    

    private final CPU cpu;
    private boolean existException;
    private final DecodingInstr decodingInstr;

    public ExceptionHandler(CPU cpu) {
        this.cpu = cpu;
        this.existException = false;
        this.decodingInstr = new DecodingInstr();
    }

    // Tipos de excepciones
    public static final int EXCEPTION_RESET = 0x000;
    public static final int EXCEPTION_TWI = 0x700;
    public static final int EXCEPTION_MACHINE_CHECK = 0x100;
    public static final int EXCEPTION_DATA_STORAGE = 0x200;
    public static final int EXCEPTION_INSTRUCTION_STORAGE = 0x300;
    public static final int EXCEPTION_EXTERNAL_INTERRUPT = 0x400;
    public static final int EXCEPTION_DSI = 0x300;
    public static final int EXCEPTION_ISI = 0x400;
    public static final int EXCEPTION_ALIGNMENT = 0x500;
    public static final int EXCEPTION_PROGRAM = 0x600;
    public static final int EXCEPTION_FP = 0x700;
    public static final int EXCEPTION_DECREMENTER = 0x800;
    public static final int EXCEPTION_SYSTEM_CALL = 0x900;
    public static final int EXCEPTION_TRACE = 0xA00;
    public static final int EXCEPTION_PERFORMANCE_MONITOR = 0xB00;
    public static final int EXCEPTION_INST_DEBUG = 0xC00;
    public static final int EXCEPTION_DATA_DEBUG = 0xD00;
    public static final int EXCEPTION_APU_UNAVAILABLE = 0xE00;
    public static final int EXCEPTION_INVALID = 0x0;

    // Manejar una excepción
    public void handleException(int exceptionType, long pc, long instruction) {
        switch (exceptionType) {
            case EXCEPTION_PROGRAM:
                System.err.println("[Exception Handler] Program exception at PC=0x" + Long.toHexString(pc)
                        + ": Unimplemented instruction 0x" + Long.toHexString(instruction));                

                // Guardar estado para depuración
                cpu.setSrr1(cpu.getMsr());
                cpu.setSrr0(pc);

                // Configurar nuevo estado
                cpu.setMsr(cpu.getMsr() & 0x0000FFFF); // Limpiar bits superiores
                cpu.setPc(exceptionType);
                cpu.setInterrupt(true);
                existException = true;
                // Saltar al manejador de excepciones
                cpu.handleInterrupt(0x700);
                //cpu.setPc(0x700);
                break;
            // ... otros casos ...
        }
    }

    // Manejar una interrupción
    public void handleInterrupt() {
        // Implementación específica para cada tipo de interrupción
    }

    // Verificar excepciones pendientes
    public void checkPendingExceptions() {
        // Lógica para verificar y manejar excepciones pendientes
        if (existException) {
            existException = false;
        }
    }
}
