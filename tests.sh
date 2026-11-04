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

check "Bus RAM alias + MMIO big-endian" \
    "$(java -cp "$CP" pcp.TESTS.BusMappingSelfTest 2>&1)" "Bus mapping/alias/MMIO endian: ALL OK"
check "Xenon SPR TBL/TBU + Time Base + DEC underflow" \
    "$(java -cp "$CP" pcp.TESTS.XenonTimebaseSelfTest 2>&1)" "Xenon Time Base/Decrementer: ALL OK"
check "Xenon MMU IBAT/DBAT effective-address translation" \
    "$(java -cp "$CP" pcp.TESTS.XenonMmuSelfTest 2>&1)" "Xenon MMU BAT: ALL OK"
check "Xenon DSI/ISI exception state" \
    "$(java -cp "$CP" pcp.TESTS.XenonExceptionSelfTest 2>&1)" "Xenon DSI/ISI exception state: ALL OK"
check "SATA/ATAPI virtual DVD sin medio" \
    "$(java -Xmx1g -cp "$CP" pcp.TESTS.XenonSataSelfTest 2>&1)" "Xenon SATA/ATAPI: ALL OK"
if [ -n "${JPPC_TEST_NAND:-}" ]; then
    check "SFCX NAND real offset 0x4200 (read-only)" \
        "$(java -Xmx1g -cp "$CP" pcp.TESTS.NandSfcxImageSelfTest "$JPPC_TEST_NAND" 2>&1)" "SFCX NAND image: read-only transfer at logical offset 0x4200 OK"
fi
check "NAND Xenon RAW (528 bytes, OOB, geometry)" \
    "$(java -Xmx1g -cp "$CP" pcp.TESTS.NandImageSelfTest 2>&1)" "NAND RAW Xenon: ALL OK"
check "Xenos GPU register endian + surface address (LK/XeLL)" \
    "$(java -cp "$CP" pcp.TESTS.XenosGpuSelfTest 2>&1)" "Xenos GPU endian/surface: ALL OK"

audit=$(java -cp "$CP" pcp.utils.XenonElfAudit "$T/xenon.elf" 2>&1)
check "XeLL ELF audit identifies legacy second stage" "$audit" "PRESENT XeLL legacy second stage"
check "XeLL ELF audit detects CP437 ASCII logo" "$audit" "PRESENT XeLL CP437 ASCII logo attribution"
check "XeLL ELF audit reports missing literal Xenos marker" "$audit" "ABSENT  Xenos framebuffer mode marker"
logo=$(java -cp "$CP" pcp.utils.XenonElfAudit "$T/xenon.elf" --extract-logo "$CP/xell-logo-cp437.txt" 2>&1)
check "XeLL CP437 logo extraction" "$logo" "LOGO_EXTRACTED="
check_file "$CP/xell-logo-cp437.txt" "XeLL CP437 logo artifact"
check "XeLL logo artifact contains attribution" "$(cat "$CP/xell-logo-cp437.txt")" "Free60.org XeLL"

check "autotest CPU 32 bits (23 tests)" \
    "$(run "$T/cpu_selftest.bin" --size 800x600 --headless --max 1000000 --png "$CP/a.png")" "ALL OK"
check "autotest CPU 64 bits + FP (22 tests)" \
    "$(run "$T/cpu_selftest64.bin" --headless --max 1000000 --png "$CP/b.png")" "ALL OK (64)"
check "autotest AltiVec/VMX (9 tests)" \
    "$(run "$T/cpu_selftest_vmx.bin" --headless --max 1000000 --png "$CP/c.png")" "ALL OK (VMX)"
# Las pruebas de arranque usan el ELF real de XeLL; no se ejecutan demos de framebuffer.

out=$(run "$T/lk.elf" --elf --size 1024x768 --headless --max 3000000 --input 'help\n' --png "$CP/lk.png")
check "lk.elf arranca (PPC64 + UART)" "$out" "entering main console loop"
check "lk.elf: el shell responde a 'help'" "$out" "command list by block"
check "lk.elf: sin instrucciones ilegales" "$(echo "$out" | grep -ci 'instrucción ilegal')" "^0$"

out=$(run "$T/lk.elf" --elf --size 1024x768 --headless --max 400000000 --input 'gfx rgb_bars\n' --png "$CP/lk_bars.png")
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
