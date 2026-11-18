
from pathlib import Path
import sys

if len(sys.argv) != 4:
    print("Uso: py extract_asm.py archivo.asm inicio fin")
    print("Ejemplo: py extract_asm.py 1bl.asm 0x01000138 0x01000200")
    sys.exit(1)

source = Path(sys.argv[1])
start = int(sys.argv[2], 16)
end = int(sys.argv[3], 16)

for line in source.read_text(encoding="utf-8").splitlines():
    try:
        address = int(line.split()[0], 16)
    except (ValueError, IndexError):
        continue

    if start <= address <= end:
        print(line)
