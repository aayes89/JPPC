import sys
from pathlib import Path
from capstone import Cs, CS_ARCH_PPC, CS_MODE_64, CS_MODE_BIG_ENDIAN

BASE = 0x01000000
ROM_OFFSET = 0x100

if len(sys.argv) != 4:
    print("Uso: py disasm_1bl.py 1bl.bin INICIO FIN")
    print("Ejemplo: py disasm_1bl.py 1bl.bin 0x01003000 0x01003180")
    sys.exit(1)

data = Path(sys.argv[1]).read_bytes()
start = int(sys.argv[2], 16)
end = int(sys.argv[3], 16)

if int.from_bytes(data[0:2], "big") != 0x0341:
    raise ValueError("Magic 1BL incorrecto")

declared = int.from_bytes(data[12:16], "big")
limit = ROM_OFFSET + declared

if start < BASE + ROM_OFFSET or end < start:
    raise ValueError("Rango inválido")

offset_start = start - BASE
offset_end = end - BASE + 4

if offset_end > limit or offset_start < ROM_OFFSET:
    raise ValueError("Rango fuera del código declarado")

md = Cs(CS_ARCH_PPC, CS_MODE_64 | CS_MODE_BIG_ENDIAN)
md.detail = True

code = data[offset_start:offset_end]

for ins in md.disasm(code, start):
    raw = ins.bytes.hex(" ").upper()
    print(f"{ins.address:08X}  {raw:<11}  {ins.mnemonic:<12} {ins.op_str}")
