package pcp.gpu;

/**
 * Host boot display used by --xam until the guest presents its first frame.
 * This is not a replacement for the original bootanim.xex.
 */
public final class BootSplash {
/*
    private static final int BG = 0xFF05090D, WHITE = 0xFFF2F5F7, MUTED = 0xFF8B969F, GREEN = 0xFF79C143;

    private BootSplash() {
    }

    public static void render(FrameBuffer fb, int frame) {
        if (fb == null) {
            return;
        }
        int w = fb.getWidth(), h = fb.getHeight(), cx = w / 2, cy = h / 2 - 42;
        int r = Math.max(34, Math.min(w, h) / 11);
        fb.clear(BG);
        for (int y = 0; y < h; y += 4) {
            int s = 5 + (int) (8.0 * y / Math.max(1, h));
            fb.drawLine(0, y, w - 1, y, 0xFF000000 | (s << 16) | ((s + 4) << 8) | (s + 7));
        }
        fb.drawCircle(cx, cy, r, 0xFFBFC9CE);
        fb.drawCircle(cx, cy, r - 2, 0xFF56636A);
        int phase = Math.floorMod(frame, 48);
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians((phase * 7 + i * 90) % 360);
            int x1 = cx + (int) (Math.cos(a) * (r + 5)), y1 = cy + (int) (Math.sin(a) * (r + 5));
            int x2 = cx + (int) (Math.cos(a) * (r + 15)), y2 = cy + (int) (Math.sin(a) * (r + 15));
            fb.drawLine(x1, y1, x2, y2, i == 0 ? WHITE : GREEN);
        }
        int d = r / 2;
        for (int i = -3; i <= 3; i++) {
            fb.drawLine(cx - d + i, cy - d, cx + d + i, cy + d, GREEN);
            fb.drawLine(cx + d + i, cy - d, cx - d + i, cy + d, GREEN);
        }
        String title = "XBOX 360", sub = "XENON SYSTEM STARTUP";
        fb.printText(cx - title.length() * 4, cy + r + 30, title, WHITE);
        fb.printText(cx - sub.length() * 4, cy + r + 52, sub, MUTED);
        int bw = Math.min(300, w - 80), bx = (w - bw) / 2, by = Math.min(h - 72, cy + r + 88);
        fb.drawRect(bx, by, bw, 8, 0xFF657078);
        int fill = Math.max(8, (int) (bw * ((frame % 90) / 90.0)));
        fb.fillRect(bx + 1, by + 1, Math.min(bw - 2, fill), 6, GREEN);
        fb.printText(bx, by + 19, "INITIALIZING XAM / XENON RUNTIME", MUTED);
        fb.present();
    }

    /**
     * True once the guest has written enough framebuffer words to own the
     * display.
     *//*
    public static boolean guestHasPresentedPixels(FrameBuffer fb) {
        return fb.getGuestWriteCount() >= 64;
    }
    */
}
