# triangle_fixed: igual que triangle.asm pero con el vertex buffer en una dirección válida.
# (triangle.asm apunta el VB a 0x80020000, donde no hay datos; sus vértices están en su propio .data.)
# Ensamblar:  python3 ppcasm.py triangle_fixed.s triangle_fixed.bin
start:
        lis     r3, 0xC800              # base de registros de la GPU
        li      r4, 0
        stw     r4, 0x1000(r3)          # render target = pantalla
        li      r4, 640
        stw     r4, 0x1004(r3)
        li      r4, 480
        stw     r4, 0x1008(r3)
        li      r4, 1
        stw     r4, 0x100C(r3)
        li      r4, 0x0404
        stw     r4, 0x1010(r3)
        li      r4, 2560
        stw     r4, 0x1014(r3)
        lis     r4, 0x0020              # clear a azul oscuro 0x00202060
        ori     r4, r4, 0x2060
        stw     r4, 0x1020(r3)
        li      r4, 1
        stw     r4, 0x1024(r3)          # clear
        la      r4, vertex_buffer
        stw     r4, 0x1080(r3)
        li      r4, 12
        stw     r4, 0x1084(r3)
        li      r4, 3
        stw     r4, 0x1088(r3)
        la      r4, vertex_shader
        stw     r4, 0x1100(r3)
        li      r4, 12
        stw     r4, 0x1104(r3)
        la      r4, pixel_shader
        stw     r4, 0x1200(r3)
        li      r4, 24
        stw     r4, 0x1204(r3)
        li      r4, 3
        stw     r4, 0x1300(r3)          # TRIANGLE_LIST
        li      r4, 1
        stw     r4, 0x1304(r3)          # draw
        stw     r4, 0x1400(r3)          # flush
loop:   b       loop

vertex_buffer:
        .long   0x00000000, 0x3F000000, 0x00000000     # ( 0.0,  0.5, 0)
        .long   0xBF000000, 0xBF000000, 0x00000000     # (-0.5, -0.5, 0)
        .long   0x3F000000, 0xBF000000, 0x00000000     # ( 0.5, -0.5, 0)
vertex_shader:
        .long   0x102A1100, 0, 0
pixel_shader:
        .long   0x102A2100, 0, 0x3F800000, 0x3F000000, 0x00000000, 0x3F800000   # color (1.0, 0.5, 0.0, 1.0)
