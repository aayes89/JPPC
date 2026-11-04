# Demo de framebuffer: código PPC que dibuja en 0x10000000 (1280x720, 0x00RRGGBB).
# Ensamblar:  python3 ppcasm.py fb_demo.s fb_demo.bin
start:
        lis     r1, 0x20                # pila
        lis     r29, 0x0FFF             # consola
        lis     r30, 0x1000             # framebuffer
        la      r3, banner
        bl      puts

# --- degradado: R = x*327>>10, G = y*436>>10, B = 0x80 ---
        mr      r9, r30
        li      r5, 0                   # y
yloop:  li      r4, 0                   # x
        mulli   r8, r5, 364
        srwi    r8, r8, 10
        slwi    r8, r8, 8
        ori     r8, r8, 0x80
xloop:  mulli   r6, r4, 204
        srwi    r6, r6, 10
        slwi    r6, r6, 16
        or      r7, r6, r8
        stw     r7, 0(r9)
        addi    r9, r9, 4
        addi    r4, r4, 1
        cmpwi   r4, 1280
        blt     xloop
        addi    r5, r5, 1
        cmpwi   r5, 720
        blt     yloop

# --- panel oscuro y marco blanco ---
        li      r3, 16
        li      r4, 16
        li      r5, 720
        li      r6, 64
        lis     r7, 0x0010
        ori     r7, r7, 0x1030
        bl      fill_rect
        li      r7, -1                  # 0xFFFFFFFF
        li      r3, 0
        li      r4, 0
        li      r5, 1280
        li      r6, 3
        bl      fill_rect               # arriba
        li      r3, 0
        li      r4, 717
        li      r5, 1280
        li      r6, 3
        bl      fill_rect               # abajo
        li      r3, 0
        li      r4, 0
        li      r5, 3
        li      r6, 600
        bl      fill_rect               # izquierda
        li      r3, 1277
        li      r4, 0
        li      r5, 3
        li      r6, 600
        bl      fill_rect               # derecha

# --- texto ---
        la      r3, txt1
        li      r4, 24
        li      r5, 24
        li      r6, -1
        bl      draw_text
        la      r3, txt2
        li      r4, 24
        li      r5, 40
        lis     r6, 0x00FF
        ori     r6, r6, 0xFF00          # amarillo
        bl      draw_text
        la      r3, txt3
        li      r4, 24
        li      r5, 56
        lis     r6, 0x0000
        ori     r6, r6, 0xFFFF          # cian
        bl      draw_text

        lis     r10, 0xC800             # registro 'present'
        li      r6, 1
        stw     r6, 0x0C(r10)
        la      r3, done_msg
        bl      puts
halt:   b       halt

# fill_rect(r3=x, r4=y, r5=w, r6=h, r7=color)
fill_rect:
        mulli   r8, r4, 1280
        add     r8, r8, r3
        slwi    r8, r8, 2
        add     r8, r8, r30
        mr      r9, r6
fr_row: mr      r10, r5
        mr      r11, r8
fr_col: stw     r7, 0(r11)
        addi    r11, r11, 4
        addic.  r10, r10, -1
        bne     fr_col
        addi    r8, r8, 5120
        addic.  r9, r9, -1
        bne     fr_row
        blr

# draw_text(r3=cadena, r4=x, r5=y, r6=color) con la fuente 8x8 (bit0 = píxel izquierdo)
draw_text:
        mr      r10, r3
        mr      r11, r4
dt_char:
        lbz     r3, 0(r10)
        cmpwi   r3, 0
        beq     dt_end
        addi    r3, r3, -32
        slwi    r3, r3, 3
        la      r13, font
        add     r13, r13, r3
        li      r14, 0
dt_row: lbzx    r15, r13, r14
        add     r17, r5, r14
        mulli   r17, r17, 1280
        add     r17, r17, r11
        slwi    r17, r17, 2
        add     r17, r17, r30
        li      r16, 0
dt_col: li      r18, 1
        slw     r18, r18, r16
        and.    r18, r18, r15
        beq     dt_skip
        stw     r6, 0(r17)
dt_skip:
        addi    r17, r17, 4
        addi    r16, r16, 1
        cmpwi   r16, 8
        blt     dt_col
        addi    r14, r14, 1
        cmpwi   r14, 8
        blt     dt_row
        addi    r11, r11, 8
        addi    r10, r10, 1
        b       dt_char
dt_end: blr

puts:   lbz     r4, 0(r3)
        cmpwi   r4, 0
        beq     puts_end
        stb     r4, 0(r29)
        addi    r3, r3, 1
        b       puts
puts_end:
        blr

banner:   .asciz "JPPC fb_demo: dibujando...\n"
done_msg: .asciz "JPPC fb_demo: listo\n"
txt1:     .asciz "HOLA MUNDO - JPPC"
txt2:     .asciz "PowerPC en Java: render por MMIO"
txt3:     .asciz "stw -> 0x10000000"
        .align  2
font:
        .fontdata
