# bouncer: "HOLA X360" rebota por la pantalla (640x480) y cambia de color al chocar.
# Es lo que describe bouncing.py, que sólo genera un esqueleto vacío.
# Ejecutar:  java -cp out pcp.PCP src/pcp/TESTS/bouncer.bin --size 640x480
start:
        lis     r1, 0x20
        lis     r29, 0x0FFF
        lis     r30, 0x1000
        li      r3, 0
        li      r4, 0
        li      r5, 640
        li      r6, 480
        li      r7, 0
        bl      fill_rect               # pantalla negra
        li      r20, 100                # x
        li      r21, 60                 # y
        li      r22, 3                  # dx
        li      r23, 2                  # dy
        li      r24, 0                  # índice de color
main:
        mr      r3, r20
        mr      r4, r21
        li      r5, 72
        li      r6, 8
        li      r7, 0
        bl      fill_rect               # borra el texto anterior
        add     r20, r20, r22
        add     r21, r21, r23
        cmpwi   r20, 1
        bge     xl_ok
        li      r20, 1
        neg     r22, r22
        addi    r24, r24, 1
xl_ok:  cmpwi   r20, 567
        ble     xr_ok
        li      r20, 567
        neg     r22, r22
        addi    r24, r24, 1
xr_ok:  cmpwi   r21, 1
        bge     yt_ok
        li      r21, 1
        neg     r23, r23
        addi    r24, r24, 1
yt_ok:  cmpwi   r21, 471
        ble     yb_ok
        li      r21, 471
        neg     r23, r23
        addi    r24, r24, 1
yb_ok:  andi.   r10, r24, 3
        slwi    r10, r10, 2
        la      r11, colors
        lwzx    r6, r11, r10
        la      r3, msg
        mr      r4, r20
        mr      r5, r21
        bl      draw_text
        lis     r10, 0xC800
        li      r9, 1
        stw     r9, 0x0C(r10)           # present
        lis     r9, 0x10                # retardo ~1M iteraciones
        mtctr   r9
delay:  bdnz    delay
        b       main

fill_rect:                              # r3=x r4=y r5=w r6=h r7=color
        mulli   r8, r4, 640
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
        addi    r8, r8, 2560
        addic.  r9, r9, -1
        bne     fr_row
        blr

draw_text:                              # r3=cadena r4=x r5=y r6=color
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
        mulli   r17, r17, 640
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

msg:    .asciz "HOLA X360"
        .align  2
colors: .long   0x00FFFFFF, 0x00FFFF00, 0x0000FF00, 0x0000FFFF
font:
        .fontdata
