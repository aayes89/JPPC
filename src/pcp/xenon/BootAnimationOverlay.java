package pcp.xenon;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Presentación host de respaldo para AniStartBootAnimation.
 * Usa el logo original extraído de XAM mientras no exista ejecución del
 * renderer retail de bootanim.xex/Xenos. No representa ejecución guest.
 */
public final class BootAnimationOverlay {
    private final BufferedImage logo;
    private volatile long startNanos;

    public BootAnimationOverlay(Path logoPath) throws IOException {
        if (logoPath == null || !java.nio.file.Files.isRegularFile(logoPath)) {
            throw new IOException("No existe el logo Xenon: " + logoPath);
        }
        this.logo = ImageIO.read(logoPath.toFile());
        if (logo == null) throw new IOException("No se pudo decodificar el logo Xenon: " + logoPath);
        this.startNanos = 0L;
    }

    public BufferedImage render(int width, int height) {
        long now = System.nanoTime();
        long start = startNanos;
        if (start == 0L) {
            synchronized (this) {
                if (startNanos == 0L) startNanos = now;
                start = startNanos;
            }
        }
        double seconds = (now - start) / 1_000_000_000.0;
        return renderAt(width, height, seconds);
    }

    /** Render determinista para capturas y pruebas headless. */
    public BufferedImage renderAt(int width, int height, double seconds) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Resolución inválida");
        BufferedImage frame = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = frame.createGraphics();
        try {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, width, height);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            double phase = (seconds % 3.0) / 3.0;
            double fadeIn = Math.min(1.0, phase / 0.18);
            double fadeOut = Math.min(1.0, (1.0 - phase) / 0.16);
            float alpha = (float) Math.max(0.0, Math.min(fadeIn, fadeOut));
            double pulse = 1.0 + 0.035 * Math.sin(phase * Math.PI * 6.0);
            int size = (int) Math.round(Math.min(width, height) * 0.43 * pulse);
            int x = (width - size) / 2;
            int y = (height - size) / 2;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g.drawImage(logo, x, y, size, size, null);
        } finally {
            g.dispose();
        }
        return frame;
    }
}
