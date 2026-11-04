# Autotest de la CPU de JPPC. Imprime "<letra>+" si pasa o "<letra>-" si falla.
# Ensamblar:  python3 ppcasm.py cpu_selftest.s cpu_selftest.bin
        b       start
        .org    0xC00                   # vector de 'sc'
        li      r20, 0x55
        mfspr   r21, 26                 # SRR0
        rfi
        .org    0x1000
start:
        lis     r1, 0x20                # pila en 0x00200000
        lis     r29, 0x0FFF             # consola
        lis     r30, 0x1000             # framebuffer
        li      r28, 0                  # contador de fallos

# A: lis con bit alto y construcción alternativa
        lis     r3, 0xC800
        li      r4, 0xC8
        slwi    r4, r4, 24
        li      r5, 'A'
        bl      check
# B: bucle bdnz, suma 1..100
        li      r3, 0
        li      r6, 100
        mtctr   r6
        li      r7, 1
loopB:  add     r3, r3, r7
        addi    r7, r7, 1
        bdnz    loopB
        li      r4, 5050
        li      r5, 'B'
        bl      check
# C: comparaciones con y sin signo
        li      r6, -5
        li      r3, 0
        cmpwi   r6, 3
        bge     skipC1
        addi    r3, r3, 1               # -5 < 3 (signed)
skipC1: cmplwi  r6, 3
        ble     skipC2
        addi    r3, r3, 2               # 0xFFFFFFFB > 3 (unsigned)
skipC2: li      r4, 3
        li      r5, 'C'
        bl      check
# D: cadena de acarreo (suma de 64 bits)
        li      r10, -1
        li      r11, 1
        addc    r12, r10, r11           # 0, CA=1
        li      r13, 0
        li      r14, 0
        adde    r3, r13, r14            # 0+0+CA = 1
        li      r4, 1
        li      r5, 'D'
        bl      check
# E: mullw / divw con signo
        li      r6, -7
        li      r7, 6
        mullw   r8, r6, r7              # -42
        divw    r3, r8, r7              # -7
        li      r4, -7
        li      r5, 'E'
        bl      check
# F: rlwinm
        lis     r6, 0x1234
        ori     r6, r6, 0x5678
        rlwinm  r3, r6, 8, 24, 31       # rotl 8 -> 0x34567812 -> 0x12
        li      r4, 0x12
        li      r5, 'F'
        bl      check
# G: srawi
        li      r6, -16
        srawi   r3, r6, 2
        li      r4, -4
        li      r5, 'G'
        bl      check
# H: sth/lha con extensión de signo
        lis     r10, 0x10               # área de trabajo 0x00100000
        li      r6, -2
        sth     r6, 0(r10)
        lha     r3, 0(r10)
        li      r4, -2
        li      r5, 'H'
        bl      check
# I: lhz sin extensión
        lhz     r3, 0(r10)
        li      r4, 0xFFFE
        li      r5, 'I'
        bl      check
# J: stwu / lwz
        li      r6, 0x1234
        stwu    r6, 16(r10)
        lwz     r3, 0(r10)
        li      r4, 0x1234
        li      r5, 'J'
        bl      check
# K: stwu actualiza el registro base
        lis     r11, 0x10
        subf    r3, r11, r10
        li      r4, 16
        li      r5, 'K'
        bl      check
# L: bl/blr anidado con pila y LR
        li      r3, 5
        bl      f1
        li      r4, 7
        li      r5, 'L'
        bl      check
# M: xor
        lis     r6, 0xF0F0
        ori     r6, r6, 0xF0F0
        lis     r7, 0x0FF0
        ori     r7, r7, 0xFF00
        xor     r3, r6, r7
        lis     r4, 0xFF00
        ori     r4, r4, 0x0FF0
        li      r5, 'M'
        bl      check
# N: nor (check pisa r6, se recargan)
        lis     r6, 0xF0F0
        ori     r6, r6, 0xF0F0
        lis     r7, 0x0FF0
        ori     r7, r7, 0xFF00
        nor     r3, r6, r7
        li      r4, 0x000F
        slwi    r4, r4, 16
        ori     r4, r4, 0x000F
        li      r5, 'N'
        bl      check
# O: extsb y cntlzw
        li      r6, 0x80
        extsb   r3, r6
        li      r4, -128
        li      r5, 'O'
        bl      check
        lis     r6, 1
        cntlzw  r3, r6
        li      r4, 15
        li      r5, 'P'
        bl      check
# Q: rlwimi
        lis     r3, 0xAAAA
        ori     r3, r3, 0xAAAA
        li      r6, 0xCD
        rlwimi  r3, r6, 8, 16, 23
        lis     r4, 0xAAAA
        ori     r4, r4, 0xCDAA
        li      r5, 'Q'
        bl      check
# R: campos del CR (cmpwi cr1) y mfcr
        li      r8, 0
        mtcrf   0xFF, r8
        li      r6, 1
        cmpwi   cr1, r6, 0              # GT -> CR1 = 0b0100
        mfcr    r3
        lis     r4, 0x0400
        li      r5, 'R'
        bl      check
# S: mtctr/mfctr
        li      r6, 77
        mtctr   r6
        mfctr   r3
        li      r4, 77
        li      r5, 'S'
        bl      check
# T: bytes en el framebuffer MMIO (big-endian) leídos como palabra
        li      r6, 0x12
        stb     r6, 0(r30)
        li      r6, 0x34
        stb     r6, 1(r30)
        li      r6, 0x56
        stb     r6, 2(r30)
        li      r6, 0x78
        stb     r6, 3(r30)
        lwz     r3, 0(r30)
        lis     r4, 0x1234
        ori     r4, r4, 0x5678
        li      r5, 'T'
        bl      check
        li      r6, 0
        stw     r6, 0(r30)              # deja el píxel 0 en negro
# U: direcciones >= 0x80000000 espejan la RAM
        li      r6, 0x77
        lis     r10, 0x8010
        stw     r6, 0(r10)
        lis     r11, 0x10
        lwz     r3, 0(r11)
        li      r4, 0x77
        li      r5, 'U'
        bl      check
# V: sc -> vector 0xC00 -> rfi
        li      r20, 0
        sc
        mr      r3, r20
        li      r4, 0x55
        li      r5, 'V'
        bl      check
# W: registros del framebuffer (ancho en 0xC8000004)
        lis     r10, 0xC800
        lwz     r3, 4(r10)
        li      r4, 800
        li      r5, 'W'
        bl      check

        cmpwi   r28, 0
        bne     bad
        la      r3, msg_ok
        bl      puts
        b       end
bad:    la      r3, msg_bad
        bl      puts
end:    b       end

check:  cmpw    r3, r4
        bne     chk_fail
        stb     r5, 0(r29)
        li      r6, '+'
        stb     r6, 0(r29)
        b       chk_nl
chk_fail:
        stb     r5, 0(r29)
        li      r6, '-'
        stb     r6, 0(r29)
        addi    r28, r28, 1
chk_nl: li      r6, 10
        stb     r6, 0(r29)
        blr

puts:   lbz     r4, 0(r3)
        cmpwi   r4, 0
        beq     puts_end
        stb     r4, 0(r29)
        addi    r3, r3, 1
        b       puts
puts_end:
        blr

f1:     mflr    r0
        stw     r0, 4(r1)
        stwu    r1, -16(r1)
        bl      f2
        addi    r3, r3, 1
        addi    r1, r1, 16
        lwz     r0, 4(r1)
        mtlr    r0
        blr
f2:     addi    r3, r3, 1
        blr

msg_ok:  .asciz "ALL OK\n"
msg_bad: .asciz "FAILURES\n"
