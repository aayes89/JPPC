#!/bin/sh
# Regresión JPPC. Requiere compilación previa (./run.sh test compila primero).
cd "$(dirname "$0")"
CP=out
T=src/pcp/TESTS
fail=0
mkdir -p "$CP"

check() {
    if echo "$2" | grep -q "$3"; then
        echo "OK    $1"
    else
        echo "FALLA $1"
        fail=1
    fi
}
check_file() {
    if [ -s "$1" ]; then
        echo "OK    $2"
    else
        echo "FALLA $2"
        fail=1
    fi
}
run() {
    java -Xmx1g -cp "$CP" pcp.PCP "$@" 2>&1
}

check "NAND Xenon RAW (528 bytes, OOB, geometry)" \
    "$(java -Xmx1g -cp "$CP" pcp.TESTS.NandImageSelfTest 2>&1)" "NAND RAW Xenon: ALL OK"

check "autotest CPU 32 bits (23 tests)" \
    "$(run "$T/cpu_selftest.bin" --headless --max 1000000 --png "$CP/a.png")" "ALL OK"
check "autotest CPU 64 bits + FP (22 tests)" \
    "$(run "$T/cpu_selftest64.bin" --headless --max 1000000 --png "$CP/b.png")" "ALL OK (64)"
check "autotest AltiVec/VMX (9 tests)" \
    "$(run "$T/cpu_selftest_vmx.bin" --headless --max 1000000 --png "$CP/c.png")" "ALL OK (VMX)"
check "fb_demo (render por MMIO)" \
    "$(run "$T/fb_demo.bin" --headless --max 1000000 --png "$CP/fb_demo.png")" "JPPC fb_demo: dibujando"
check "triangle_fixed (GPU de juguete)" \
    "$(run "$T/triangle_fixed.bin" --size 640x480 --headless --png "$CP/triangle_fixed.png")" "halted=true"

out=$(run "$T/lk.elf" --elf --size 1280x720 --headless --max 3000000 --input 'help\n' --png "$CP/lk.png")
check "lk.elf arranca (PPC64 + UART)" "$out" "entering main console loop"
check "lk.elf: el shell responde a 'help'" "$out" "command list by block"
check "lk.elf: sin instrucciones ilegales" "$(echo "$out" | grep -ci 'instrucción ilegal')" "^0$"

out=$(run "$T/lk.elf" --elf --size 1280x720 --headless --max 400000000 --input 'gfx rgb_bars\n' --png "$CP/lk_bars.png")
check "lk.elf: gfx rgb_bars (tiling Xenos)" "$out" "retile_framebuffer"

out=$(run "$T/xenon.elf" --elf --size 1024x768 --tb 1000 --headless --max 300000000 \
    --png "$CP/xell.png" --console-png "$CP/xell_uart.png")
check "xenon.elf: XeLL + AVPACK VGA" "$out" "AVPACK detected: 5b"
check "xenon.elf: Xenos video mode setup" "$out" ". f2"
check "xenon.elf: NAND virtual + USB EHCI/OHCI" "$out" "Initialising USB OHCI"
check "xenon.elf: dos hubs virtuales enumerados" "$out" "USB bus 1 device 1"
check "xenon.elf: framebuffer Xenos escrito" "$out" "VRAM palabras no nulas: 786432"
check_file "$CP/xell.png" "xenon.elf: captura framebuffer"
check_file "$CP/xell_uart.png" "xenon.elf: captura UART"

exit "$fail"
