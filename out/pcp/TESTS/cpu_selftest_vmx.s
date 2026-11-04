# Autotest de AltiVec/VMX. "<letra>+" pasa, "<letra>-" falla.  (check usa r3-r6; la memoria de trabajo en r10)
start:
        lis     r1, 0x20
        lis     r29, 0x0FFF
        li      r28, 0
        lis     r10, 0x10               # área de trabajo 0x00100000 (alineada a 16)

# A: vspltisw + vadduwm + stvx -> todas las palabras = 8
        vspltisw v1, 5
        vspltisw v2, 3
        vadduwm v3, v1, v2
        stvx    v3, r0, r10
        lwz     r3, 4(r10)
        li      r4, 8
        li      r5, 'A'
        bl      check
# B: vxor consigo mismo = 0
        vxor    v4, v1, v1
        stvx    v4, r0, r10
        lwz     r3, 8(r10)
        li      r4, 0
        li      r5, 'B'
        bl      check
# C: lvx/stvx de un patrón y vperm con lvsl (rotar 4 bytes a la izquierda)
        lis     r7, 0x1122
        ori     r7, r7, 0x3344
        stw     r7, 32(r10)             # w0
        lis     r7, 0x5566
        ori     r7, r7, 0x7788
        stw     r7, 36(r10)             # w1
        lis     r7, 0x99AA
        ori     r7, r7, 0xBBCC
        stw     r7, 40(r10)             # w2
        lis     r7, 0xDDEE
        ori     r7, r7, 0xFF00
        stw     r7, 44(r10)             # w3
        li      r8, 32
        lvx     v5, r10, r8
        addi    r9, r10, 4              # dirección con (EA & 15) = 4
        lvsl    v6, r0, r9
        vperm   v7, v5, v5, v6
        stvx    v7, r0, r10
        lwz     r3, 0(r10)              # = w1
        lis     r4, 0x5566
        ori     r4, r4, 0x7788
        li      r5, 'C'
        bl      check
# D: vsldoi 8 -> palabra 0 = w2
        vsldoi  v8, v5, v5, 8
        stvx    v8, r0, r10
        lwz     r3, 0(r10)
        lis     r4, 0x99AA
        ori     r4, r4, 0xBBCC
        li      r5, 'D'
        bl      check
# E: vaddfp 1.5f + 2.25f = 3.75f = 0x40700000 (splat desde memoria)
        lis     r7, 0x3FC0              # 1.5f
        stw     r7, 64(r10)
        stw     r7, 68(r10)
        stw     r7, 72(r10)
        stw     r7, 76(r10)
        lis     r7, 0x4010              # 2.25f
        stw     r7, 80(r10)
        stw     r7, 84(r10)
        stw     r7, 88(r10)
        stw     r7, 92(r10)
        li      r8, 64
        lvx     v9, r10, r8
        li      r8, 80
        lvx     v10, r10, r8
        vaddfp  v11, v9, v10
        stvx    v11, r0, r10
        lwz     r3, 12(r10)
        lis     r4, 0x4070
        li      r5, 'E'
        bl      check
# F: vsel con máscara total elige vB
        vspltisw v12, -1                # todo unos
        vsel    v13, v1, v2, v12        # = v2 (palabras = 3)
        stvx    v13, r0, r10
        lwz     r3, 0(r10)
        li      r4, 3
        li      r5, 'F'
        bl      check
# G: vmrghw v14 = [a0,b0,a1,b1]; a = v5 (patrón), b = v1 (5)
        vmrghw  v14, v5, v1
        stvx    v14, r0, r10
        lwz     r3, 4(r10)              # = b0 = 5
        li      r4, 5
        li      r5, 'G'
        bl      check
        lwz     r3, 8(r10)              # = a1 = w1
        lis     r4, 0x5566
        ori     r4, r4, 0x7788
        li      r5, 'H'
        bl      check
# I: lvlx / stvlx con EA no alineada (Xenon): lvlx carga desde EA hasta fin de bloque
        addi    r9, r10, 36             # EA = 0x...24 -> offset 4 en el bloque
        lvlx    v15, r0, r9             # 12 bytes: w1,w2,w3 a la izquierda
        stvx    v15, r0, r10
        lwz     r3, 0(r10)              # = w1
        lis     r4, 0x5566
        ori     r4, r4, 0x7788
        li      r5, 'I'
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

msg_ok:  .asciz "ALL OK (VMX)\n"
msg_bad: .asciz "FAILURES (VMX)\n"
