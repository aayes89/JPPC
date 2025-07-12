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
import pcp.system.ExceptionHandler;

/**
 *
 * @author Slam
 */
public class SpecialInstr {

    private final CPU cpu;
    private final long[] sr = new long[16]; // Segment Registers

    public SpecialInstr(CPU cpu) {
        this.cpu = cpu;
    }

    // mfmsr - Move from Machine State Register
    public void mfmsr(int RT) {
        cpu.setGPR(RT, cpu.getMsr());
    }

    // mtmsr - Move to Machine State Register
    public void mtmsr(int RS) {
        cpu.setMsr(cpu.getGPRVal(RS));
    }

    // mftb - Move from Time Base
    public void mftb(int RT, int TBR) {
        long timeBase = cpu.getTimeBase(); // Assumes CPU tracks a 64-bit time base
        switch (TBR) {
            case 268: // TBL (Time Base Lower)
                cpu.setGPR(RT, (int) (timeBase & 0xFFFFFFFF));
                break;
            case 269: // TBU (Time Base Upper)
                cpu.setGPR(RT, (int) (timeBase >>> 32));
                break;
            default:
                throw new UnsupportedOperationException("Invalid TBR: " + TBR);
        }
    }

    // dcbf - Data Cache Block Flush
    public void dcbf(int RA, int RB) {
        long addr =  cpu.getGPRVal(RA) + cpu.getGPRVal(RB);
        // Placeholder for cache flush; invalidate line if cache is implemented
        System.out.println("dcbf: Flushing data cache block at 0x" + Long.toHexString(addr));
    }

    // icbi - Instruction Cache Block Invalidate
    public void icbi(int RA, int RB) {
        long addr =  cpu.getGPRVal(RA) + cpu.getGPRVal(RB);
        // Placeholder for instruction cache invalidation
        System.out.println("icbi: Invalidating instruction cache block at 0x" + Long.toHexString(addr));
    }

    // sync - Synchronize
    public void sync() {
        // For single-core, no-op is fine; for multi-core, add memory barrier
        System.out.println("sync: Memory barrier executed");
    }

    // mtspr (move to special register)
    public void mtspr(int SPR, int RS) {
        long value =  cpu.getGPRVal(RS);
        // Los SPRs se codifican en 10 bits: (SPR[5:9] << 5) | SPR[0:4]
        int spr = ((SPR & 0x1F) << 5) | ((SPR >> 5) & 0x1F);

        switch (spr) {
            case 1:   // XER
                cpu.setXer(value);
                break;
            case 5: // RTCL
                long currentTime = System.currentTimeMillis() * 1000L; // microsegundos
                long rtcl = currentTime & 0xFFFFFFFFL;
                cpu.setRTCL((int) rtcl);
                break;
            case 8:   // LR
                cpu.setLr(value);
                break;
            case 9:   // CTR
                cpu.setCtr(value);
                break;
            case 22:  // DEC (Decrementer)
                // En un sistema real esto configuraría el decrementer
                cpu.setDec((int)value);
                break;
            case 25:  // SDR1 (Storage Description Register 1)
                cpu.setSDR1((int)value); // Store for MMU use
                break;
            case 26:  // SRR0 (Save/Restore Register 0)
                cpu.setSrr0(value);
                break;
            case 27:  // SRR1 (Save/Restore Register 1)
                cpu.setSrr1(value);
                break;
            case 272: // SPRG0 (Special Purpose Register General 0)
                cpu.setSprg0((int)value);
                break;
            case 273: // SPRG1
                cpu.setSprg1((int)value);
                break;
            case 274: // SPRG2
                cpu.setSprg2((int)value);
                break;
            case 3:
            case 275: // SPRG3
                cpu.setSprg3((int)value);
                break;
            case 18:
                cpu.setDar(value);
                break;
            case 19:
                cpu.setDsisr((int)value);
                break;
            case 287:
                cpu.setPvr((int)value);
                break;
            case 995: // SPR no estándar, provisionalmente no implementado
                //System.out.println("mtspr: SPR 995 no implementado, valor guardado ignorado: 0x" + Integer.toHexString(value));
                break;
            default:
                throw new UnsupportedOperationException("mtspr to SPR " + spr + " not implemented");
        }
    }

    // mfspr (move from special register)
    public void mfspr(int RT, int SPR) {
        int spr = ((SPR & 0x1F) << 5) | ((SPR >> 5) & 0x1F);
        long value = 0;

        switch (spr) {
            case 0: //MQ
                value = cpu.getMQ();
                break;
            case 1:   // XER
                value = cpu.getXer();
                break;
            case 4: // RTCU
                value = cpu.getRTCU();
                break;
            case 5: // RTCL
                value = cpu.getRTCL();
                break;
            case 8:   // LR
                value = cpu.getLr();
                break;
            case 9:   // CTR
                value = cpu.getCtr();
                break;
            case 18:
                value = cpu.getDsisr();
                break;
            case 19:
                value = cpu.getDar();
                break;
            case 6:   // DEC not priv
            case 22:  // DEC priv                
                value = cpu.getDec();
                break;
            case 25:   //  SDR1
                value = cpu.getSDR1();
                break;
            case 26:  // SRR0
                value = cpu.getSrr0();
                break;
            case 27:  // SRR1
                value = cpu.getSrr1();
                break;
            case 272: // SPRG0
                value = cpu.getSprg0();
                break;
            case 273: // SPRG1
                value = cpu.getSprg1();
                break;
            case 274: // SPRG2
                value = cpu.getSprg2();
                break;
            case 275: // SPRG3
                value = cpu.getSprg3();
                break;

            case 287:
                value = cpu.getPvr();
                break;
            /*new*/
            case 282: // 01000 11010 EAR Yes
                value = cpu.getEAR();
                break;
            case 284: // 01000 11100 TBL Yes
                value = cpu.getTBL();
                break;
            case 285: // 01000 11101 TBU Yes
                value = cpu.getTBU();
                break;
            case 528: // 10000 10000 IBAT0U Yes
                value = cpu.getIBAT0U();
                break;
            case 529: // 10000 10001 IBAT0L Yes
                value = cpu.getIBAT0L();
                break;
            case 530: // 10000 10010 IBAT1U Yes
                value = cpu.getIBAT1U();
                break;
            case 531: // 10000 10011 IBAT1L Yes
                value = cpu.getIBAT1L();
                break;
            case 532: // 10000 10100 IBAT2U Yes
                value = cpu.getIBAT2U();
                break;
            case 533: // 10000 10101 IBAT2L Yes
                value = cpu.getIBAT2L();
                break;
            case 534: // 10000 10110 IBAT3U Yes
                value = cpu.getIBAT3U();
                break;
            case 535: // 10000 10111 IBAT3L Yes
                value = cpu.getIBAT3L();
                break;
            case 536: // 10000 11000 DBAT0U Yes
                value = cpu.getDBAT0U();
                break;
            case 537: // 10000 11001 DBAT0L Yes
                value = cpu.getDBAT0L();
                break;
            case 538: // 10000 11010 DBAT1U Yes
                value = cpu.getDBAT1U();
                break;
            case 539: // 10000 11011 DBAT1L Yes
                value = cpu.getDBAT1L();
                break;
            case 540: // 10000 11100 DBAT2U Yes
                value = cpu.getDBAT2U();
                break;
            case 541: // 10000 11101 DBAT2L Yes
                value = cpu.getDBAT2L();
                break;
            case 542: // 10000 11110 DBAT3U Yes
                value = cpu.getDBAT3U();
                break;
            case 543: // 10000 11111 DBAT3L Yes
                value = cpu.getDBAT3L();
                break;
            default:
                throw new UnsupportedOperationException("[SpecialInstr] mfspr from SPR " + spr + " not implemented");
        }
        cpu.setGPR(RT, value);
    }

    // mtcrf (move to condition register fields)
    public void mtcrf(int FXM, int RS) {
        long value =  cpu.getGPRVal(RS);
        // FXM es una máscara de 8 bits que indica qué campos actualizar
        for (int i = 0; i < 8; i++) {
            if ((FXM & (0x80 >> i)) != 0) {
                // Extraer el campo de 4 bits
                long crField = (value >> (28 - i * 4)) & 0xF;
                // Actualizar el campo en el CR
                cpu.setCrField(i, crField);
            }
        }
    }

    // mfcr (move from condition register)
    public void mfcr(int RT) {
        cpu.setGPR(RT, cpu.getCr());
    }

    // mcrf (move condition register field)
    public void mcrf(int BF, int BFA) {
        // Extraer campo del CR
        long crField = cpu.getCrField(BFA);
        // Escribir en otro campo del CR
        cpu.setCrField(BF, crField);
    }

    // isync (instruction synchronize)
    public void isync() {
        // En un cpuulador, esto podría limpiar el pipeline
        // No necesita implementación funcional en este nivel
    }

    // rfi (return from interrupt)
    public void rfi() {
        cpu.setMsr(cpu.getSrr1()); // Restore MSR
        cpu.setPc(cpu.getSrr0());  // Restore PC
        cpu.setInterruptMode(false); // Exit interrupt mode (assumes CPU has this method)
    }

    // sc (system call)
    public void sc() {
        cpu.setSrr0(cpu.getPc() + 4); // Save return address
        cpu.setSrr1(cpu.getMsr());    // Save MSR
        cpu.setPc(0x00000C00);        // Jump to system call handler (verify for Xbox 360)
        cpu.setInterruptMode(true);   // Enter interrupt mode
    }

    public void isel(int RT, int RA, int RB, int crBit) {
        long crValue = cpu.getCr();
        long bit = (crValue >> (31 - crBit)) & 1; // CR bits are 0-31, leftmost is 31
        cpu.setGPR(RT, bit != 0 ? cpu.getGPRVal(RA) : cpu.getGPRVal(RB));
    }

    // twi (trap word immediate)
    public void twi(int TO, int RA, int SI) {
        long raValue = cpu.getGPRVal(RA);
        boolean trap = false;

        // Evaluar condiciones de trap
        switch (TO) {
            case 4:
                trap = (raValue < SI);
                break;
            case 8:
                trap = (raValue > SI);
                break;
            case 12:
                trap = (raValue < SI) || (raValue > SI);
                break;
            case 16:
                trap = (raValue == SI);
                break;
            case 20:
                trap = (raValue >= SI);
                break;
            case 24:
                trap = (raValue <= SI);
                break;
            case 28:
                trap = (raValue != SI);
                break;
            default: // Otras condiciones no implementadas
        }

        if (trap) {
            // Similar a una system call pero para traps
            cpu.setSrr0(cpu.getPc() + 4);
            cpu.setSrr1(cpu.getMsr());
            cpu.setPc(0x00000700); // Dirección del trap handler
            cpu.setInterrupt(true);
        }
    }

    // tdi (trap doubleword immediate - 64-bit, implementado para completitud)
    public void tdi(int TO, int RA, int SI) {
        // Implementación cpuilar a twi pero para 64 bits
        // En arquitectura de 32 bits, puede tratarse igual
        twi(TO, RA, SI);
    }

    // mtsr - Move to Segment Register
    public void mtsr(int SR, int RS) {
        sr[SR] = cpu.getGPRVal(RS);
    }

    // mfsr - Move from Segment Register
    public void mfsr(int RT, int SR) {
        cpu.setGPR(RT, sr[SR]);
    }

    // mtsrin - Move to Segment Register Indirect
    public void mtsrin(int RS, int RB) {
        long value =  cpu.getGPRVal(RS);
        int srIndex = (int)(cpu.getGPRVal(RB) >> 28) & 0xF;
        sr[srIndex] = value;
    }

    public void executePrimary(int opcode, int instruction) {
        // Instrucciones con opcode primario 19
        int extendedOpcode = instruction & 0x7FF;
        if (opcode == 19) {
            int extOpcode = (instruction >> 1) & 0x3FF; // Bits 21-30
            int BT = (instruction >> 21) & 0x1F;
            int BA = (instruction >> 16) & 0x1F;
            int BB = (instruction >> 11) & 0x1F;
            switch (extOpcode) {
                case 150: // isync
                    isync();
                    break;
                case 257: // crand
                    crand(BT, BA, BB);
                    break;
                case 449: // cror                    
                    cror(BT, BA, BB);
                    break;
                case 596: // sync
                case 1196: // sync
                    sync();
                    break;
                default:
                    throw new UnsupportedOperationException("Special primary opcode not implemented: " + extendedOpcode);
            }
        }
        switch (extendedOpcode) {
            case 0: // mcrf
                int BF = (instruction >> 23) & 0x7;
                int BFA = (instruction >> 18) & 0x7;
                mcrf(BF, BFA);
                break;
            case 16: // bclr
                // Implementación en clase Branchs
                break;
            case 33: // crnor (Condition Register NOR)
                // Implementación lógica de CR
                break;
            case 150: // isync
                isync();
                break;
            case 596:   // sync (0x4AC)
            case 1196: // sync
                sync();
                break;

            default:
                throw new UnsupportedOperationException("[Special primary] opcode not implemented: " + extendedOpcode);
        }
    }

    public void executeExtended(int extendedOpcode, int instruction) {
        int RT = (instruction >> 21) & 0x1F;
        int RA = (instruction >> 16) & 0x1F;
        int RB = (instruction >> 11) & 0x1F;
        int FXM = (instruction >> 12) & 0xFF; // Para mtcrf
        int SPR = ((instruction >> 11) & 0x1F) | ((instruction >> 16) & 0x1F0); // Para mtspr/mfspr
        // Assume CRb (condition bit) is fixed or derived
        int crBit = 0; // Adjust based on context

        switch (extendedOpcode) {
            case 4:   // tw
                int TO = (instruction >> 21) & 0x1F;
                tw(TO, RA, RB, instruction);
                break;
            case 19:  // mfcr
                mfcr(RT);
                break;
            case 50:  // rfi
                rfi();
                break;
            case 68:  // tdi
                int TO_di = (instruction >> 21) & 0x1F;
                tdi(TO_di, RA, (short) (instruction & 0xFFFF));
                break;
            case 86: // dcbf
                dcbf(RA, RB);
                break;
            case 87: // lbzx
                long addr =  cpu.getGPRVal(RA) + cpu.getGPRVal(RB);
                DCACHE_Flush(addr);
                break;
            case 128: // mtcrf                
                mtcrf(FXM, RT);
                break;
            case 144: // mtcrf
                mtcrf(FXM, RT);
                break;
            case 146: // mtmsr
                mtmsr(RT);
                break;
            case 150: // isync
                isync();
                break;
            case 210: // mtsr
                int SR = (instruction >> 16) & 0xF;  // Campo SR (bits 16-19)
                mtsr(SR, RT);
                break;
            case 300: // Nuevo caso para 300
                mtxer(RT); // Ejemplo: mover valor al registro XER
                break;
            case 339: // mfspr
                mfspr(RT, SPR);
                break;
            case 402:
                //nop
                System.out.println("XO 402");
                break;
            case 467: // mtspr
                mtspr(SPR, RT);
                break;
            case 596:
            case 1196: // sync (0x4AC)
                System.out.println("Executing sync at PC=0x" + Long.toHexString(cpu.getPc()));
                sync();
                break;
            case 498:
            case 996: // slbia
                slbia();
                break;
            case 512: // mfsr
                int SR_mfsr = (instruction >> 16) & 0xF;
                mfsr(RT, SR_mfsr);
                break;
            case 566: // tlbsync
                tlbsync();
                break;
            case 678: // isel RT, RA, RB, CRb
                isel(RT, RA, RB, crBit);
                break;
            case 934: // mtxer
                mtxer(RT);
                break;
            case 1010: // tlbie
                tlbie(RB);
                break;
            default:
                cpu.getExceptionHandler().handleException(
                        ExceptionHandler.EXCEPTION_PROGRAM,
                        cpu.getPc(),
                        instruction
                );

                break;
        }
    }

// Implementación de tw (trap word)
    public void tw(int TO, int RA, int RB, int instruction) {
        long raValue = cpu.getGPRVal(RA);
        long rbValue = cpu.getGPRVal(RB);
        boolean trap = false;

        switch (TO) {
            case 4:
                trap = (raValue < rbValue);
                break;
            case 8:
                trap = (raValue > rbValue);
                break;
            case 12:
                trap = (raValue < rbValue) || (raValue > rbValue);
                break;
            case 16:
                trap = (raValue == rbValue);
                break;
            case 20:
                trap = (raValue >= rbValue);
                break;
            case 24:
                trap = (raValue <= rbValue);
                break;
            case 28:
                trap = (raValue != rbValue);
                break;
            default: // Otras condiciones
        }

        if (trap) {
            cpu.setSrr0(cpu.getPc() + 4);
            cpu.setSrr1(cpu.getMsr());
            cpu.setPc(0x00000700); // Trap handler address
            cpu.setInterrupt(true);
        }
    }

    // Condition Register AND
    public void crand(int BT, int BA, int BB) {
        long cr = cpu.getCr();
        long bitA = (cr >> (31 - BA)) & 1;
        long bitB = (cr >> (31 - BB)) & 1;
        long result = bitA & bitB;
        cpu.setCrField(BT, result);
    }

    // Condition Register OR
    public void cror(int bt, int ba, int bb) {
        long cr = cpu.getCr();
        long bitA = (cr >> (31 - ba)) & 1;
        long bitB = (cr >> (31 - bb)) & 1;
        long result = bitA | bitB;
        cpu.setCrField(bt, result);
    }

    // TLB Invalidate Entry
    public void tlbie(int RB) {
        long address = cpu.getGPRVal(RB);
        System.out.println("tlbie: Invalidating TLB entry for address 0x" + Long.toHexString(address));
        // In a real implementation, invalidate TLB entry for the effective address        
        // cpu.getTLB().invalidate(address);
    }

    // Método para la instrucción mtxer (ejemplo)
    private void mtxer(int RT) {
        long value =  cpu.getGPRVal(RT); // Obtener valor del registro general RT
        cpu.setXer(value); // Establecer valor en el registro XER
    }

    // slbia - Segment Lookaside Buffer Invalidate All
    public void slbia() {
        // En un simulador básico, esto puede ser un no-op
        // En una implementación completa, invalidar todas las entradas del SLB
        System.out.println("slbia: Invalidating all SLB entries");        
        cpu.getMmu().invalidateTLB();
    }

    // tlbsync - TLB Synchronize
    public void tlbsync() {
        // En un simulador de un solo procesador, esto puede ser un no-op
        // En un simulador multiprocesador, implementar sincronización
        System.out.println("tlbsync: Synchronizing TLB operations");
        // Ejemplo: cpu.synchronizeTLB();
    }

    private void DCACHE_Flush(long addr) {
        cpu.setReserveAddress(addr);
    }
}
