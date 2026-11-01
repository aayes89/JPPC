#!/usr/bin/env python3
"""Mini ensamblador PowerPC (32 bits, big-endian) para probar JPPC sin toolchain.
Uso: python3 ppcasm.py entrada.s salida.bin [--base 0x0]
Soporta las instrucciones usadas por los tests (ver tabla) y directivas
.long .byte .ascii .asciz .align .fontdata (tabla 8x8 de BitmapFont.java)."""
import re, struct, sys, os

def reg(t):
    t = t.strip().lower()
    m = re.fullmatch(r'r(\d+)', t)
    if not m or not 0 <= int(m.group(1)) < 32:
        raise ValueError('registro inválido: ' + t)
    return int(m.group(1))

def freg(t):
    m = re.fullmatch(r'f(\d+)', t.strip().lower())
    if not m or not 0 <= int(m.group(1)) < 32:
        raise ValueError('registro FP inválido: ' + t)
    return int(m.group(1))

def crf(t):
    m = re.fullmatch(r'cr(\d)', t.strip().lower())
    return int(m.group(1)) if m else None

def s16(v): return v & 0xFFFF
def D(op, rt, ra, imm): return (op << 26) | (rt << 21) | (ra << 16) | s16(imm)
def X(op, rt, ra, rb, xo, rc=0): return (op << 26) | (rt << 21) | (ra << 16) | (rb << 11) | (xo << 1) | rc
def M(op, rs, ra, sh, mb, me, rc=0): return (op << 26) | (rs << 21) | (ra << 16) | (sh << 11) | (mb << 6) | (me << 1) | rc

D_ARITH = {'addi': 14, 'addis': 15, 'mulli': 7, 'subfic': 8, 'addic': 12, 'addic.': 13}
D_LOGIC = {'ori': 24, 'oris': 25, 'xori': 26, 'xoris': 27, 'andi.': 28, 'andis.': 29}
D_MEM = {'lwz': 32, 'lwzu': 33, 'lbz': 34, 'lbzu': 35, 'stw': 36, 'stwu': 37, 'stb': 38, 'stbu': 39,
         'lhz': 40, 'lhzu': 41, 'lha': 42, 'lhau': 43, 'sth': 44, 'sthu': 45}
X_MEM = {'lwzx': 23, 'lbzx': 87, 'lhzx': 279, 'lhax': 343, 'stwx': 151, 'stbx': 215, 'sthx': 407,
         'lwzux': 55, 'lbzux': 119, 'stwux': 183, 'stbux': 247}
# rt, ra, rb (rt=rD)
XO3 = {'add': 266, 'subf': 40, 'mullw': 235, 'divw': 491, 'divwu': 459, 'addc': 10, 'subfc': 8,
       'adde': 138, 'subfe': 136, 'mulhw': 75, 'mulhwu': 11}
# ra, rs, rb (rs en campo rt)
LOG3 = {'and': 28, 'or': 444, 'xor': 316, 'nand': 476, 'nor': 124, 'andc': 60, 'orc': 412, 'eqv': 284,
        'slw': 24, 'srw': 536, 'sraw': 792}
DS_MEM = {'ld': (58, 0), 'ldu': (58, 1), 'lwa': (58, 2), 'std': (62, 0), 'stdu': (62, 1)}
XO3.update({'mulld': 233, 'divd': 489, 'divdu': 457, 'mulhd': 73, 'mulhdu': 9})
LOG3.update({'sld': 27, 'srd': 539, 'srad': 794})
FP_D = {'lfs': 48, 'lfsu': 49, 'lfd': 50, 'lfdu': 51, 'stfs': 52, 'stfsu': 53, 'stfd': 54, 'stfdu': 55}
FP_A = {'fadd': 21, 'fsub': 20, 'fdiv': 18, 'fadds': 21, 'fsubs': 20, 'fdivs': 18}
FP_X = {'fmr': 72, 'fneg': 40, 'fabs': 264, 'frsp': 12, 'fctiwz': 15, 'fctiw': 14, 'fcfid': 846, 'fctidz': 815}
BCOND = {'blt': (12, 0), 'bgt': (12, 1), 'beq': (12, 2), 'bso': (12, 3),
         'bge': (4, 0), 'ble': (4, 1), 'bne': (4, 2), 'bns': (4, 3)}
SPR = {'xer': 1, 'lr': 8, 'ctr': 9, 'srr0': 26, 'srr1': 27, 'dec': 22, 'sprg0': 272}

def parse_imm(t, labels, pc=None):
    t = t.strip()
    if len(t) >= 3 and t[0] == "'" and t[-1] == "'":
        return ord(t[1:-1].encode().decode('unicode_escape'))
    try:
        return int(t, 0)
    except ValueError:
        pass
    m = re.fullmatch(r'(-?)([A-Za-z_.][\w.]*)', t)
    if m and m.group(2) in labels:
        v = labels[m.group(2)]
        return -v if m.group(1) else v
    raise ValueError('inmediato/etiqueta desconocido: ' + t)

def split_ops(s):
    out, cur, depth, q = [], '', 0, False
    for ch in s:
        if ch == "'" : q = not q
        if ch == '(' and not q: depth += 1
        if ch == ')' and not q: depth -= 1
        if ch == ',' and depth == 0 and not q:
            out.append(cur.strip()); cur = ''
        else:
            cur += ch
    if cur.strip(): out.append(cur.strip())
    return out

def mem_operand(t, labels):
    m = re.fullmatch(r'(.*)\((r\d+)\)', t.strip())
    if not m: raise ValueError('operando memoria inválido: ' + t)
    return parse_imm(m.group(1) or '0', labels), reg(m.group(2))

def li_size(ops):
    try:
        v = int(ops[1], 0)
    except ValueError:
        return 4
    return 4 if -32768 <= v <= 32767 else (4 if v & 0xFFFF == 0 else 8)

def size_of(mn, ops):
    if mn == 'li': return li_size(ops)
    if mn == 'la': return 8
    return 4

def font_bytes():
    here = os.path.dirname(os.path.abspath(__file__))
    src = open(os.path.join(here, '..', 'gpu', 'BitmapFont.java'), encoding='utf-8').read()
    rows = re.findall(r'\{([^{}]*)\}', src[src.index('font8x8_basic = {'):])
    data = bytearray()
    for r in rows:
        data += bytes(int(x, 16) for x in re.findall(r'0x([0-9A-Fa-f]{2})', r))
    assert len(data) == 95 * 8, len(data)
    return bytes(data)

def assemble(text, base=0):
    lines = []
    for raw in text.splitlines():
        line = re.split(r'(?<!\')[#;](?!\')', raw)[0].strip() if "'" not in raw else raw.split('#')[0].strip()
        while True:
            m = re.match(r'^([A-Za-z_.][\w.]*):\s*(.*)$', line)
            if not m: break
            lines.append(('label', m.group(1)))
            line = m.group(2)
        if line:
            lines.append(('ins', line))
    # pasada 1: tamaños y etiquetas
    labels, pc, items = {}, base, []
    for kind, v in lines:
        if kind == 'label':
            labels[v] = pc; continue
        parts = v.split(None, 1)
        mn = parts[0].lower(); rest = parts[1] if len(parts) > 1 else ''
        if mn.startswith('.'):
            if mn == '.align':
                a = 1 << int(rest, 0); pad = (-pc) % a
                items.append((pc, 'bytes', bytes(pad))); pc += pad
            elif mn == '.org':
                pad = int(rest, 0) - pc
                assert pad >= 0, '.org hacia atrás'
                items.append((pc, 'bytes', bytes(pad))); pc += pad
            elif mn in ('.long', '.word'):
                ops = split_ops(rest); items.append((pc, 'long', ops)); pc += 4 * len(ops)
            elif mn == '.byte':
                ops = split_ops(rest); items.append((pc, 'byte', ops)); pc += len(ops)
            elif mn in ('.ascii', '.asciz'):
                s = re.search(r'"(.*)"', rest).group(1).encode().decode('unicode_escape').encode('latin1')
                if mn == '.asciz': s += b'\0'
                items.append((pc, 'bytes', s)); pc += len(s)
            elif mn == '.fontdata':
                b = font_bytes(); items.append((pc, 'bytes', b)); pc += len(b)
            else:
                raise ValueError('directiva desconocida: ' + mn)
            continue
        ops = split_ops(rest)
        n = size_of(mn, ops)
        items.append((pc, 'ins', (mn, ops))); pc += n
    # pasada 2
    out = bytearray()
    def emit(w): out.extend(struct.pack('>I', w & 0xFFFFFFFF))
    for addr, kind, v in items:
        assert len(out) == addr - base, 'desalineado en 0x%x' % addr
        if kind == 'bytes': out.extend(v); continue
        if kind == 'long':
            for o in v: emit(parse_imm(o, labels))
            continue
        if kind == 'byte':
            for o in v: out.append(parse_imm(o, labels) & 0xFF)
            continue
        mn, ops = v
        try:
            for w in encode(mn, ops, labels, addr): emit(w)
        except Exception as e:
            raise ValueError('%s %s (0x%x): %s' % (mn, ', '.join(ops), addr, e))
    return bytes(out), labels

def encode(mn, ops, L, pc):
    rc = 0
    base_mn = mn
    if mn.endswith('.') and mn not in ('addic.', 'andi.', 'andis.'):
        rc = 1; base_mn = mn[:-1]
    P = lambda i: parse_imm(ops[i], L)
    if mn == 'nop': return [0x60000000]
    if mn == 'blr': return [0x4E800020]
    if mn == 'blrl': return [0x4E800021]
    if mn == 'bctr': return [0x4E800420]
    if mn == 'bctrl': return [0x4E800421]
    if mn == 'rfi': return [0x4C000064]
    if mn == 'sc': return [0x44000002]
    if mn == 'trap': return [0x7FE00008]
    if mn == 'sync': return [0x7C0004AC]
    if mn == 'li':
        rd, v = reg(ops[0]), P(1)
        if -32768 <= v <= 32767: return [D(14, rd, 0, v)]
        hi = (v >> 16) & 0xFFFF
        w = [D(15, rd, 0, hi)]
        if v & 0xFFFF: w.append(D(24, rd, rd, v & 0xFFFF))  # ori rd,rd,lo
        return w
    if mn == 'la':
        rd, v = reg(ops[0]), P(1)
        return [D(15, rd, 0, (v >> 16) & 0xFFFF), D(24, rd, rd, v & 0xFFFF)]
    if mn == 'lis': return [D(15, reg(ops[0]), 0, P(1))]
    if mn == 'mr': r = reg(ops[1]); return [X(31, r, reg(ops[0]), r, 444)]
    if mn == 'mr.': r = reg(ops[1]); return [X(31, r, reg(ops[0]), r, 444, 1)]
    if mn == 'subi': return [D(14, reg(ops[0]), reg(ops[1]), -P(2))]
    if mn in D_ARITH: return [D(D_ARITH[mn], reg(ops[0]), reg(ops[1]), P(2))]
    if mn in D_LOGIC: return [D(D_LOGIC[mn], reg(ops[1]), reg(ops[0]), P(2))]  # ra,rs,uimm
    if mn in ('cmpwi', 'cmplwi', 'cmpw', 'cmplw'):
        c = 0
        if crf(ops[0]) is not None: c = crf(ops[0]); ops = ops[1:]
        if mn in ('cmpwi', 'cmplwi'):
            return [(11 if mn == 'cmpwi' else 10) << 26 | c << 23 | reg(ops[0]) << 16 | s16(P(1))]
        return [X(31, c << 2, reg(ops[0]), reg(ops[1]), 0 if mn == 'cmpw' else 32)]
    if mn in D_MEM:
        d, ra = mem_operand(ops[1], L); return [D(D_MEM[mn], reg(ops[0]), ra, d)]
    if mn in X_MEM: return [X(31, reg(ops[0]), reg(ops[1]), reg(ops[2]), X_MEM[mn])]
    if base_mn in XO3: return [X(31, reg(ops[0]), reg(ops[1]), reg(ops[2]), XO3[base_mn], rc)]
    if base_mn == 'sub': return [X(31, reg(ops[0]), reg(ops[2]), reg(ops[1]), 40, rc)]
    if base_mn in LOG3: return [X(31, reg(ops[1]), reg(ops[0]), reg(ops[2]), LOG3[base_mn], rc)]
    if base_mn == 'neg': return [X(31, reg(ops[0]), reg(ops[1]), 0, 104, rc)]
    if base_mn in ('addze', 'subfze', 'addme', 'subfme'):
        xo = {'addze': 202, 'subfze': 200, 'addme': 234, 'subfme': 232}[base_mn]
        return [X(31, reg(ops[0]), reg(ops[1]), 0, xo, rc)]
    if base_mn in ('extsb', 'extsh', 'cntlzw'):
        xo = {'extsb': 954, 'extsh': 922, 'cntlzw': 26}[base_mn]
        return [X(31, reg(ops[1]), reg(ops[0]), 0, xo, rc)]
    if base_mn == 'srawi': return [X(31, reg(ops[1]), reg(ops[0]), P(2), 824, rc)]
    if base_mn == 'rlwinm': return [M(21, reg(ops[1]), reg(ops[0]), P(2), P(3), P(4), rc)]
    if base_mn == 'rlwimi': return [M(20, reg(ops[1]), reg(ops[0]), P(2), P(3), P(4), rc)]
    if base_mn == 'rlwnm': return [M(23, reg(ops[1]), reg(ops[0]), reg(ops[2]), P(3), P(4), rc)]
    if base_mn == 'slwi': n = P(2); return [M(21, reg(ops[1]), reg(ops[0]), n, 0, 31 - n, rc)]
    if base_mn == 'srwi': n = P(2); return [M(21, reg(ops[1]), reg(ops[0]), 32 - n, n, 31, rc)]
    if base_mn == 'clrlwi': n = P(2); return [M(21, reg(ops[1]), reg(ops[0]), 0, n, 31, rc)]
    if base_mn == 'rotlwi': return [M(21, reg(ops[1]), reg(ops[0]), P(2), 0, 31, rc)]
    if mn in ('mflr', 'mfctr', 'mfxer'):
        s = SPR[mn[2:]]; return [X(31, reg(ops[0]), s & 31, s >> 5, 339)]
    if mn in ('mtlr', 'mtctr', 'mtxer'):
        s = SPR[mn[2:]]; return [X(31, reg(ops[0]), s & 31, s >> 5, 467)]
    if mn == 'mfspr': s = P(1); return [X(31, reg(ops[0]), s & 31, s >> 5, 339)]
    if mn == 'mtspr': s = P(0); return [X(31, reg(ops[1]), s & 31, s >> 5, 467)]
    if mn == 'mfcr': return [X(31, reg(ops[0]), 0, 0, 19)]
    if mn == 'mtcrf': return [(31 << 26) | (reg(ops[1]) << 21) | ((P(0) & 0xFF) << 12) | (144 << 1)]
    if mn == 'mfmsr': return [X(31, reg(ops[0]), 0, 0, 83)]
    if mn == 'mtmsr': return [X(31, reg(ops[0]), 0, 0, 146)]
    if mn in DS_MEM:
        op, xo = DS_MEM[mn]
        d, ra = mem_operand(ops[1], L)
        return [(op << 26) | (reg(ops[0]) << 21) | (ra << 16) | (d & 0xFFFC) | xo]
    if base_mn in ('extsw', 'cntlzd'):
        xo = {'extsw': 986, 'cntlzd': 58}[base_mn]
        return [X(31, reg(ops[1]), reg(ops[0]), 0, xo, rc)]
    if base_mn == 'sradi':
        sh = P(2)
        return [(31 << 26) | (reg(ops[1]) << 21) | (reg(ops[0]) << 16) | ((sh & 31) << 11) | (413 << 2) | (((sh >> 5) & 1) << 1) | rc]
    if base_mn in ('rldicl', 'rldicr', 'rldic', 'rldimi', 'clrldi', 'srdi', 'sldi'):
        rs_, ra_ = reg(ops[1]), reg(ops[0])
        if base_mn == 'clrldi': name, sh, mb = 'rldicl', 0, P(2)
        elif base_mn == 'srdi': n = P(2); name, sh, mb = 'rldicl', (64 - n) & 63, n
        elif base_mn == 'sldi': n = P(2); name, sh, mb = 'rldicr', n, 63 - n
        else: name, sh, mb = base_mn, P(2), P(3)
        xo = {'rldicl': 0, 'rldicr': 1, 'rldic': 2, 'rldimi': 3}[name]
        return [(30 << 26) | (rs_ << 21) | (ra_ << 16) | ((sh & 31) << 11) | ((mb & 31) << 6) | (((mb >> 5) & 1) << 5) | (xo << 2) | (((sh >> 5) & 1) << 1) | rc]
    if mn in ('cmpd', 'cmpld', 'cmpdi', 'cmpldi'):
        c = 0
        if crf(ops[0]) is not None: c = crf(ops[0]); ops = ops[1:]
        if mn in ('cmpdi', 'cmpldi'):
            return [(11 if mn == 'cmpdi' else 10) << 26 | c << 23 | 1 << 21 | reg(ops[0]) << 16 | s16(P(1))]
        return [X(31, (c << 2) | 1, reg(ops[0]), reg(ops[1]), 0 if mn == 'cmpd' else 32)]
    if mn in FP_D:
        d, ra = mem_operand(ops[1], L); return [D(FP_D[mn], freg(ops[0]), ra, d)]
    if base_mn in FP_A:
        op = 59 if base_mn.endswith('s') else 63
        return [(op << 26) | (freg(ops[0]) << 21) | (freg(ops[1]) << 16) | (freg(ops[2]) << 11) | (FP_A[base_mn] << 1) | rc]
    if base_mn in ('fmul', 'fmuls'):
        op = 59 if base_mn.endswith('s') else 63
        return [(op << 26) | (freg(ops[0]) << 21) | (freg(ops[1]) << 16) | (freg(ops[2]) << 6) | (25 << 1) | rc]
    if base_mn in FP_X:
        return [(63 << 26) | (freg(ops[0]) << 21) | (freg(ops[1]) << 11) | (FP_X[base_mn] << 1) | rc]
    if mn == 'fcmpu':
        c = 0
        if crf(ops[0]) is not None: c = crf(ops[0]); ops = ops[1:]
        return [(63 << 26) | (c << 23) | (freg(ops[0]) << 16) | (freg(ops[1]) << 11)]
    # saltos
    def target(t): return parse_imm(t, L)
    if mn in ('b', 'bl', 'ba', 'bla'):
        aa = 1 if mn.endswith('a') and mn != 'bla' or mn == 'bla' else 0
        lk = 1 if mn in ('bl', 'bla') else 0
        off = target(ops[0]) if aa else target(ops[0]) - pc
        return [(18 << 26) | (off & 0x03FFFFFC) | (aa << 1) | lk]
    if mn in BCOND or (mn.endswith('l') and mn[:-1] in BCOND):
        lk = 0
        if mn not in BCOND: lk = 1; mn = mn[:-1]
        bo, bi = BCOND[mn]
        c = 0
        if crf(ops[0]) is not None: c = crf(ops[0]); ops = ops[1:]
        off = target(ops[0]) - pc
        return [(16 << 26) | (bo << 21) | ((4 * c + bi) << 16) | (off & 0xFFFC) | lk]
    if mn in ('bdnz', 'bdz'):
        bo = 16 if mn == 'bdnz' else 18
        return [(16 << 26) | (bo << 21) | ((target(ops[0]) - pc) & 0xFFFC)]
    raise ValueError('instrucción no soportada por el mini-ensamblador')

if __name__ == '__main__':
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    base = 0
    if '--base' in sys.argv: base = int(sys.argv[sys.argv.index('--base') + 1], 0); args = [a for a in args if a != sys.argv[sys.argv.index('--base') + 1]]
    data, labels = assemble(open(args[0], encoding='utf-8').read(), base)
    open(args[1], 'wb').write(data)
    print('%s: %d bytes, %d etiquetas' % (args[1], len(data), len(labels)))
