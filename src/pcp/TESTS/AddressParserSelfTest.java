package pcp.TESTS;

import pcp.PCP;

/** Regression for unsigned 64-bit Xenon physical addresses in CLI options. */
public final class AddressParserSelfTest {
    private AddressParserSelfTest() {}
    public static void main(String[] args) {
        check(PCP.parseAddress("0x8000020000000000"), 0x8000020000000000L);
        check(PCP.parseAddress("0xFFFFFFFF80000000"), 0xFFFFFFFF80000000L);
        check(PCP.parseAddress("0x80000000"), 0x80000000L);
        check(PCP.parseAddress("4096"), 4096L);
        System.out.println("Unsigned 64-bit address parser: ALL OK");
    }
    private static void check(long actual, long expected) {
        if (actual != expected) throw new AssertionError(
                "expected 0x" + Long.toUnsignedString(expected, 16)
                + " got 0x" + Long.toUnsignedString(actual, 16));
    }
}
