
import sys
from pathlib import Path
from capstone import (
    Cs,
    CS_ARCH_PPC,
    CS_MODE_64,
    CS_MODE_BIG_ENDIAN
)

BASE = 0x01000000
ROM_OFFSET = 0x100

# Rangos ejecutables expresados como offsets dentro del archivo.
# Inicialmente solo se conoce el punto de entrada.
# Añadir otros rangos después de identificarlos mediante referencias.
CODE_RANGES = [
    (0x100, None)
]

def main():
    if len(sys.argv) < 3:
        print("Uso:")
        print("  py disasm_1bl.py 1bl.bin raw")
        print("  py disasm_1bl.py 1bl.bin code")
        sys.exit(1)

    file_path = Path(sys.argv[1])
    mode = sys.argv[2].lower()

    if not file_path.exists():
        raise FileNotFoundError(file_path)

    data = file_path.read_bytes()

    md = Cs(
        CS_ARCH_PPC,
        CS_MODE_64 | CS_MODE_BIG_ENDIAN
    )

    md.detail = True

    output = []

    def disassemble_range(start, end):
        offset = start
        end = min(end, len(data))

        while offset + 4 <= end:
            address = BASE + offset
            chunk = data[offset:offset + 4]

            instructions = list(md.disasm(chunk, address))

            if instructions:
                ins = instructions[0]

                output.append(
                    f"{ins.address:016X}  "
                    f"{ins.bytes.hex(' ').upper():<11}  "
                    f"{ins.mnemonic:<12} "
                    f"{ins.op_str}"
                )
            else:
                output.append(
                    f"{address:016X}  "
                    f"{chunk.hex(' ').upper():<11}  "
                    f".long        "
                    f"0x{int.from_bytes(chunk, 'big'):08X}"
                )

            offset += 4

    if mode == "raw":
        disassemble_range(ROM_OFFSET, len(data))

    elif mode == "code":
        for start, end in CODE_RANGES:
            if end is None:
                end = len(data)

            disassemble_range(start, end)

    else:
        print("Modo desconocido:", mode)
        sys.exit(1)

    output_path = Path("1bl_completo.asm")
    output_path.write_text(
        "\n".join(output),
        encoding="utf-8"
    )

    print(f"[OK] Desensamblado: {output_path}")
    print(f"[INFO] Tamaño ROM: {len(data):#x}")
    print(f"[INFO] Modo: {mode.upper()}")
    print(f"[INFO] Base ejecución: {BASE:#x}")
    print(f"[INFO] Offset inicial: {ROM_OFFSET:#x}")


if __name__ == "__main__":
    main()
