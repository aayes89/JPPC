# Lee el sector 16 (descriptor ISO9660) por el CD-ROM virtual y comprueba "CD001". "OK" si coincide.
start:
        lis     r29, 0x0FFF
        lis     r10, 0xEA00
        ori     r10, r10, 0xF000        # base del CD-ROM virtual
        li      r4, 16
        stw     r4, 0(r10)              # LBA = 16
        li      r4, 1
        stw     r4, 4(r10)              # 1 sector
        lis     r4, 0x30                # destino 0x00300000
        stw     r4, 8(r10)
        li      r4, 1
        stw     r4, 12(r10)             # leer
        lwz     r5, 16(r10)             # estado
        cmpwi   r5, 0
        bne     fail
        lis     r6, 0x30
        lbz     r3, 1(r6)               # 'C'
        cmpwi   r3, 'C'
        bne     fail
        lbz     r3, 5(r6)               # '1'
        cmpwi   r3, '1'
        bne     fail
        li      r3, 'O'
        stb     r3, 0(r29)
        li      r3, 'K'
        stb     r3, 0(r29)
        b       done
fail:   li      r3, 'N'
        stb     r3, 0(r29)
done:   li      r3, 10
        stb     r3, 0(r29)
halt:   b       halt
