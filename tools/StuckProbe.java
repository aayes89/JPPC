import pcp.PPCSimulator;
public class StuckProbe {
    public static void main(String[] a) throws Exception {
        PPCSimulator s = new PPCSimulator(640, 480);
        s.loadELF("src/pcp/TESTS/xenon.elf");
        s.runBlocking(200_000_000);
        var c = s.getCpu();
        long r9 = c.getGPRVal(9), r3 = c.getGPRVal(3);
        System.out.printf("PC=0x%x r3=0x%x r9=0x%x LR=0x%x%n", c.getPc(), r3, r9, c.getLr());
        System.out.printf("byte en r9 = 0x%02x ; palabra [r9-7]=0x%08x [r9-3]=0x%08x%n",
            s.getBus().read(r9) & 0xFF, s.getBus().readWord(r9 - 7), s.getBus().readWord(r9 - 3));
        System.out.println("pila:" + c.backtrace());
    }
}
