#!/usr/bin/env python3
"""Crea una ISO9660 mínima de prueba (con Rock Ridge NM y El Torito plataforma PowerPC) para probar --iso.
Uso: python3 mkiso.py salida.iso archivo_arranque.bin"""
import struct, sys

SECTOR = 2048
def both32(v): return struct.pack('<I', v) + struct.pack('>I', v)
def both16(v): return struct.pack('<H', v) + struct.pack('>H', v)

def dirrec(name_bytes, lba, size, is_dir, su=b''):
    n = len(name_bytes)
    ln = 33 + n + (1 if n % 2 == 0 else 0) + len(su)
    if ln % 2: ln += 1
    r = bytearray(ln)
    r[0] = ln; r[2:10] = both32(lba); r[10:18] = both32(size)
    r[18:25] = bytes([126, 1, 1, 0, 0, 0, 0]); r[25] = 2 if is_dir else 0
    r[28:32] = both16(1); r[32] = n; r[33:33+n] = name_bytes
    off = 33 + n + (1 if n % 2 == 0 else 0)
    r[off:off+len(su)] = su
    return bytes(r)

def nm(name):
    b = name.encode()
    return b'NM' + bytes([5 + len(b), 1, 0]) + b

def build(out, boot_path):
    boot = open(boot_path, 'rb').read()
    boot_sectors = (len(boot) + SECTOR - 1) // SECTOR
    # Mapa: 16 PVD, 17 El Torito BRVD, 18 terminador, 19 catálogo, 20 raíz, 21 dir BOOT, 22.. datos
    LBA_CAT, LBA_ROOT, LBA_BOOT, LBA_DATA = 19, 20, 21, 22
    def dir_sector(entries, self_lba, parent_lba):
        d = dirrec(b'\x00', self_lba, SECTOR, True) + dirrec(b'\x01', parent_lba, SECTOR, True) + b''.join(entries)
        return d.ljust(SECTOR, b'\0')
    root = dir_sector([dirrec(b'BOOT', LBA_BOOT, SECTOR, True)], LBA_ROOT, LBA_ROOT)
    bootdir = dir_sector([dirrec(b'FB_DEMO.BIN;1', LBA_DATA, len(boot), False, nm('kernel-ppc.bin'))], LBA_BOOT, LBA_ROOT)
    total = LBA_DATA + boot_sectors
    pvd = bytearray(SECTOR)
    pvd[0] = 1; pvd[1:6] = b'CD001'; pvd[6] = 1
    pvd[8:40] = b'JPPC'.ljust(32); pvd[40:72] = b'JPPC_TEST'.ljust(32)
    pvd[80:88] = both32(total); pvd[120:124] = both16(1); pvd[124:128] = both16(1); pvd[128:132] = both16(SECTOR)
    pvd[156:156+34] = dirrec(b'\x00', LBA_ROOT, SECTOR, True)[:34]
    term = bytearray(SECTOR); term[0] = 255; term[1:6] = b'CD001'; term[6] = 1
    brvd = bytearray(SECTOR); brvd[1:6] = b'CD001'; brvd[6] = 1; brvd[7:30] = b'EL TORITO SPECIFICATION'; brvd[71:75] = struct.pack('<I', LBA_CAT)
    cat = bytearray(SECTOR)
    cat[0] = 1; cat[1] = 1; cat[30] = 0x55; cat[31] = 0xAA          # validación, plataforma 1 = PowerPC
    cat[32] = 0x88; cat[34:36] = struct.pack('<H', 0); cat[38:40] = struct.pack('<H', boot_sectors * 4)
    cat[40:44] = struct.pack('<I', LBA_DATA)
    img = bytearray(16 * SECTOR)
    for s in (pvd, brvd, term, cat, root, bootdir):
        img += s
    img += boot.ljust(boot_sectors * SECTOR, b'\0')
    open(out, 'wb').write(img)
    print('%s: %d sectores' % (out, len(img) // SECTOR))

if __name__ == '__main__':
    build(sys.argv[1], sys.argv[2])
