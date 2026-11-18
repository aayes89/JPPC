# triangle.asm
# PowerPC assembly program to draw a red triangle on Xbox 360 in tiled mode

.section .text
.global _start

_start:
    # Initialize stack pointer (r1) to a safe memory region (e.g., 0x80010000)
    lis     r1, 0x8001      # r1 = 0x80010000
    ori     r1, r1, 0x0000
    stwu    r1, -0x100(r1)  # Reserve stack space

    # Disable interrupts (set MSR[EE] = 0)
    mfmsr   r3
    lis     r4, 0xFFFF      # Load 0xFFFF7FFF
    ori     r4, r4, 0x7FFF  # Clear MSR[EE] (bit 15)
    and     r3, r3, r4
    mtmsr   r3

    # Initialize GPU base address (0xC8000000)
    lis     r3, 0xC800      # r3 = 0xC8000000 (GPU registers)
    ori     r3, r3, 0x0000

    # Set up tiled render target (640x480, RGBA8888, 4x4 tiles)
    lis     r5, 0x1000      # Framebuffer base address (0x10000000)
    li      r4, 0x0000
    stw     r4, 0x1000(r3)  # Set render target base address
    li      r4, 640         # Width
    stw     r4, 0x1004(r3)  # Set width
    li      r4, 480         # Height
    stw     r4, 0x1008(r3)  # Set height
    li      r4, 0x0001      # Format: RGBA8888 (1 = 32-bit)
    stw     r4, 0x100C(r3)  # Set format
    li      r4, 0x0404      # Tile dimensions: 4x4 tiles
    stw     r4, 0x1010(r3)  # Set tiling mode
    li      r4, 2560        # Pitch (640 * 4 bytes)
    stw     r4, 0x1014(r3)  # Set pitch

    # Clear render target to black (0x00000000)
    li      r4, 0x0000
    stw     r4, 0x1020(r3)  # Set clear color
    li      r4, 0x0001      # Clear command
    stw     r4, 0x1024(r3)  # Issue clear

    # Set up vertex buffer pointer (0x80020000)
    lis     r4, 0x8002      # r4 = 0x80020000 (vertex buffer)
    ori     r4, r4, 0x0000
    stw     r4, 0x1080(r3)  # Set vertex buffer base
    li      r4, 12          # Stride: 12 bytes per vertex (3 floats)
    stw     r4, 0x1084(r3)  # Set vertex stride
    li      r4, 3           # Vertex count: 3
    stw     r4, 0x1088(r3)  # Set vertex count

    # Load simple vertex shader (identity transform)
    lis     r4, vertex_shader@h
    ori     r4, r4, vertex_shader@l
    stw     r4, 0x1100(r3)  # Set vertex shader address
    li      r4, 12          # Vertex shader size
    stw     r4, 0x1104(r3)  # Set vertex shader size

    # Load pixel shader (outputs red)
    lis     r4, pixel_shader@h
    ori     r4, r4, pixel_shader@l
    stw     r4, 0x1200(r3)  # Set pixel shader address
    li      r4, 24          # Pixel shader size
    stw     r4, 0x1204(r3)  # Set pixel shader size

    # Set primitive type to triangle list
    li      r4, 3           # Primitive type: TRIANGLE_LIST
    stw     r4, 0x1300(r3)  # Set primitive type

    # Issue draw command
    li      r4, 1           # Draw command: 1 primitive (1 triangle)
    stw     r4, 0x1304(r3)  # Issue draw

    # Flush command buffer
    li      r4, 1
    stw     r4, 0x1400(r3)  # Flush command buffer

    # Infinite loop to keep triangle on screen
loop:
    b       loop

.section .data
# Vertex Buffer (3 vertices at 0x80020000)
vertex_buffer:
    # Vertex 1: (0.0, 0.5, 0.0) -> Top center
    .long   0x00000000      # X = 0.0
    .long   0x3F000000      # Y = 0.5
    .long   0x00000000      # Z = 0.0
    # Vertex 2: (-0.5, -0.5, 0.0) -> Bottom left
    .long   0xBF000000      # X = -0.5
    .long   0xBF000000      # Y = -0.5
    .long   0x00000000      # Z = 0.0
    # Vertex 3: (0.5, -0.5, 0.0) -> Bottom right
    .long   0x3F000000      # X = 0.5
    .long   0xBF000000      # Y = -0.5
    .long   0x00000000      # Z = 0.0

# Vertex Shader (minimal, identity transform)
vertex_shader:
    .long   0x102A1100      # Shader header
    .long   0x00000000      # mov o0, v0
    .long   0x00000000      # nop
vertex_shader_size:
    .long   12              # Size in bytes

# Pixel Shader (outputs red: RGB = 1.0, 0.0, 0.0)
pixel_shader:
    .long   0x102A2100      # Shader header
    .long   0x00000000      # mov oC0, c0
    .long   0x3F800000      # c0 = (1.0, 0.0, 0.0, 1.0)
    .long   0x00000000
    .long   0x00000000
    .long   0x3F800000
pixel_shader_size:
    .long   24              # Size in bytes

# Framebuffer base address
framebuffer:
    .long   0x10000000

# Ensure file ends with a newline
