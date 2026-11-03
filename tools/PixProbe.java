import pcp.PPCSimulator;
public class PixProbe {
    public static void main(String[] a) throws Exception {
        PPCSimulator s = new PPCSimulator(640, 480);
        s.loadELF("src/pcp/TESTS/xenon.elf");
        s.runBlocking(300_000_000);
        int[] v = s.getFrameBuffer().getGpuTiledBuffer();
        java.util.Map<Integer,Integer> h = new java.util.TreeMap<>();
        for (int x : v) if (x != 0) h.merge(x, 1, Integer::sum);
        h.forEach((k, c) -> System.out.printf("palabra VRAM 0x%08x  x%d%n", k, c));
    }
}
