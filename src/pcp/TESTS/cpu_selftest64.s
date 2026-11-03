# Autotest de las instrucciones de 64 bits y punto flotante. "<letra>+" = pasa, "<letra>-" = falla.
# Ensamblar:  python3 ppcasm.py cpu_selftest64.s cpu_selftest64.bin
start:
        lis     r1, 0x20
        lis     r29, 0x0FFF
        li      r28, 0
        lis     r10, 0x10               # área de trabajo 0x00100000

# constante 0x1122334455667788 en r6
        lis     r8, 0x1122
        ori     r8, r8, 0x3344
        sldi    r8, r8, 32
        lis     r9, 0x5566
        ori     r9, r9, 0x7788
        or      r8, r8, r9              # (check64 pisa r3-r6; la constante vive en r8)

# A: std/ld de 64 bits
        std     r8, 0(r10)
        ld      r3, 0(r10)
        mr      r4, r8
        li      r5, 'A'
        bl      check64
# B: rldicl (rotar y limpiar a la izquierda)
        rldicl  r3, r8, 16, 48          # rotl 16 -> 0x3344556677881122 -> bajos 16 bits
        li      r4, 0x1122
        li      r5, 'B'
        bl      check64
# C: sldi + srdi
        sldi    r3, r8, 8               # 0x2233445566778800
        srdi    r3, r3, 60
        li      r4, 2
        li      r5, 'C'
        bl      check64
# D: sradi con negativo
        li      r6, -4096
        sradi   r3, r6, 4
        li      r4, -256
        li      r5, 'D'
        bl      check64
# E: extsw
        lis     r6, 0x8000
        clrldi  r6, r6, 32              # 0x0000000080000000
        extsw   r3, r6
        lis     r4, 0x8000              # 0xFFFFFFFF80000000
        li      r5, 'E'
        bl      check64
# F: mulld
        li      r6, 1
        sldi    r6, r6, 32
        li      r7, 3
        mulld   r3, r6, r7              # 3 << 32
        li      r4, 3
        sldi    r4, r4, 32
        li      r5, 'F'
        bl      check64
# G: divd con signo
        li      r6, -100
        li      r7, 7
        divd    r3, r6, r7
        li      r4, -14
        li      r5, 'G'
        bl      check64
# H: cntlzd
        li      r6, 1
        sldi    r6, r6, 40
        cntlzd  r3, r6
        li      r4, 23
        li      r5, 'H'
        bl      check64
# I: lwa (con signo) y lwz (sin signo) de 0x80000000
        lis     r6, 0x8000
        stw     r6, 8(r10)
        lwa     r3, 8(r10)
        lis     r4, 0x8000
        li      r5, 'I'
        bl      check64
        lwz     r3, 8(r10)
        li      r4, 1
        sldi    r4, r4, 31
        li      r5, 'J'
        bl      check64
# K: stdu / ldu actualizan el registro base
        mr      r11, r10
        li      r6, 99
        stdu    r6, 16(r11)
        subf    r3, r10, r11
        li      r4, 16
        li      r5, 'K'
        bl      check64
        li      r12, 0
        ldu     r12, 0(r11)
        mr      r3, r12
        li      r4, 99
        li      r5, 'L'
        bl      check64
# M: sld / srd / srad
        li      r6, 5
        li      r7, 33
        sld     r3, r6, r7              # 5 << 33
        li      r4, 5
        sldi    r4, r4, 33
        li      r5, 'M'
        bl      check64
        li      r6, -64
        li      r7, 3
        srad    r3, r6, r7
        li      r4, -8
        li      r5, 'N'
        bl      check64

# --- punto flotante ---
        la      r11, fconsts
        lfs     f1, 0(r11)              # 1.5
        lfs     f2, 4(r11)              # 2.25
# O: fadd -> 3.75 = 0x400E000000000000
        fadd    f3, f1, f2
        stfd    f3, 32(r10)
        ld      r3, 32(r10)
        li      r4, 0x400E
        sldi    r4, r4, 48
        li      r5, 'O'
        bl      check64
# P: fmul -> 3.375 = 0x400B000000000000
        fmul    f3, f1, f2
        stfd    f3, 32(r10)
        ld      r3, 32(r10)
        li      r4, 0x400B
        sldi    r4, r4, 48
        li      r5, 'P'
        bl      check64
# Q: fdiv 3.75/1.5 = 2.5 = 0x4004000000000000
        fadd    f3, f1, f2
        fdiv    f4, f3, f1
        stfd    f4, 32(r10)
        ld      r3, 32(r10)
        li      r4, 0x4004
        sldi    r4, r4, 48
        li      r5, 'Q'
        bl      check64
# R: fcfid + fctiwz
        li      r6, 100
        std     r6, 40(r10)
        lfd     f5, 40(r10)
        fcfid   f6, f5                  # 100.0 = 0x4059000000000000
        stfd    f6, 48(r10)
        ld      r3, 48(r10)
        li      r4, 0x4059
        sldi    r4, r4, 48
        li      r5, 'R'
        bl      check64
        fctiwz  f7, f6
        stfd    f7, 56(r10)
        lwz     r3, 60(r10)             # palabra baja (big-endian)
        li      r4, 100
        li      r5, 'S'
        bl      check64
# T: fcmpu 1.5 < 2.25 -> LT en CR0
        fcmpu   f1, f2
        mfcr    r3
        srwi    r3, r3, 28
        li      r4, 8
        li      r5, 'T'
        bl      check64
# U: stfs / lwz redondeo a simple: 1.5f = 0x3FC00000
        stfs    f1, 64(r10)
        lwz     r3, 64(r10)
        lis     r4, 0x3FC0
        li      r5, 'U'
        bl      check64
# V: fneg / fabs
        fneg    f8, f1
        fabs    f9, f8
        stfd    f9, 72(r10)
        ld      r3, 72(r10)
        li      r4, 0x3FF8
        sldi    r4, r4, 48              # 1.5 = 0x3FF8000000000000
        li      r5, 'V'
        bl      check64

        cmpwi   r28, 0
        bne     bad
        la      r3, msg_ok
        bl      puts
        b       end
bad:    la      r3, msg_bad
        bl      puts
end:    b       end

check64:
        cmpd    r3, r4
        bne     c_fail
        stb     r5, 0(r29)
        li      r6, '+'
        stb     r6, 0(r29)
        b       c_nl
c_fail: stb     r5, 0(r29)
        li      r6, '-'
        stb     r6, 0(r29)
        addi    r28, r28, 1
c_nl:   li      r6, 10
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

fconsts: .long  0x3FC00000, 0x40100000
msg_ok:  .asciz "ALL OK (64)\n"
msg_bad: .asciz "FAILURES (64)\n"
