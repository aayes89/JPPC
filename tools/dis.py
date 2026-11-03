import struct,sys
def s16(x): return x-65536 if x&0x8000 else x
def dis(w,pc):
    op=w>>26; rt=(w>>21)&31; ra=(w>>16)&31; rb=(w>>11)&31; si=s16(w&0xFFFF)
    if op==14: return 'li r%d,%d'%(rt,si) if ra==0 else 'addi r%d,r%d,%d'%(rt,ra,si)
    if op==15: return 'lis r%d,0x%x'%(rt,w&0xFFFF) if ra==0 else 'addis r%d,r%d,0x%x'%(rt,ra,w&0xFFFF)
    if op==24: return 'ori r%d,r%d,0x%x'%(ra,rt,w&0xFFFF)
    if op==11: return 'cmpwi r%d,%d'%(ra,si)
    if op==10: return 'cmplwi r%d,0x%x'%(ra,w&0xFFFF)
    if op==18:
        li=w&0x03FFFFFC; li=li-(1<<26) if li&(1<<25) else li
        return ('bl' if w&1 else 'b')+' 0x%x'%((pc+li)&0xFFFFFFFF)
    if op==16: return 'bc %d,%d,0x%x'%(rt,ra,(pc+s16(w&0xFFFC))&0xFFFFFFFF)
    if op==19: return {16:'blr',528:'bctr'}.get((w>>1)&0x3FF,'op19 xo%d'%((w>>1)&0x3FF))
    if op==32: return 'lwz r%d,%d(r%d)'%(rt,si,ra)
    if op==36: return 'stw r%d,%d(r%d)'%(rt,si,ra)
    if op==37: return 'stwu r%d,%d(r%d)'%(rt,si,ra)
    if op==34: return 'lbz r%d,%d(r%d)'%(rt,si,ra)
    if op==21: return 'rlwinm r%d,r%d,%d,%d,%d'%(ra,rt,rb,(w>>6)&31,(w>>1)&31)
    if op==31:
        xo=(w>>1)&0x3FF
        names={444:'or',266:'add',40:'subf',28:'and',316:'xor',534:'lwbrx',662:'stwbrx',339:'mfspr',467:'mtspr',23:'lwzx',151:'stwx',0:'cmpw',32:'cmplw',24:'slw',536:'srw',824:'srawi'}
        n=names.get(xo,'op31 xo%d'%xo)
        return '%s r%d,r%d,r%d'%(n,rt,ra,rb)
    return 'op%d 0x%08x'%(op,w)
if __name__=='__main__':
    d=open(sys.argv[1],'rb').read(); lo=int(sys.argv[2],16); hi=int(sys.argv[3],16)
    for va in range(lo,hi,4):
        w=struct.unpack('>I',d[0x100+va:0x100+va+4])[0]
        print('%08x: %08x  %s'%(va,w,dis(w,va)))
