package pcp.TESTS;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.ImageWriter;

/**
 * Host-side diagnostic presentation built only from media extracted from this NAND.
 * This is NOT execution of bootanim.xex or a reconstruction of the retail animation.
 */
public final class BootAnimationPreview {
    private static final int WIDTH = 640;
    private static final int HEIGHT = 480;
    private static final int FRAME_COUNT = 90;
    private static final int FRAME_DELAY_CS = 3;

    private BootAnimationPreview() { }

    public static void main(String[] args) throws Exception {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        Path output = args.length > 1 ? Paths.get(args[1]) : Paths.get("build-bootanim-preview");
        Path logoPath = root.resolve("nandDump/extracted/resources/xam.xex/xam/xenonLogo.png");
        Path audioPath = root.resolve("nandDump/extracted/resources/bootanim.xex/audio.wav");
        if (!Files.isRegularFile(logoPath)) throw new IOException("Falta logo NAND: " + logoPath);
        if (!Files.isRegularFile(audioPath)) throw new IOException("Falta audio NAND: " + audioPath);

        BufferedImage logo = ImageIO.read(logoPath.toFile());
        if (logo == null) throw new IOException("No se pudo decodificar: " + logoPath);
        Files.createDirectories(output);
        Path gif = output.resolve("xenon-bootanim-host-preview.gif");
        Path png = output.resolve("xenon-bootanim-first-frame.png");
        Path wav = output.resolve("bootanim-original.wav");

        writeGif(logo, gif);
        ImageIO.write(render(logo, 24), "PNG", png.toFile());
        Files.copy(audioPath, wav, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        System.out.println("[BOOTANIM-PREVIEW] SOURCE logo=" + logoPath
                + " (" + logo.getWidth() + "x" + logo.getHeight() + ")");
        System.out.println("[BOOTANIM-PREVIEW] SOURCE audio=" + audioPath
                + " (" + Files.size(audioPath) + " bytes; copied unchanged)");
        System.out.println("[BOOTANIM-PREVIEW] OUTPUT gif=" + gif.toAbsolutePath());
        System.out.println("[BOOTANIM-PREVIEW] OUTPUT png=" + png.toAbsolutePath());
        System.out.println("[BOOTANIM-PREVIEW] OUTPUT wav=" + wav.toAbsolutePath());
        System.out.println("[BOOTANIM-PREVIEW] MODE=HOST_MEDIA_PREVIEW; bootanim.xex guest code NOT executed");
    }

    private static BufferedImage render(BufferedImage logo, int frame) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, WIDTH, HEIGHT);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double t = frame / (double) (FRAME_COUNT - 1);
            double fadeIn = Math.min(1.0, t / 0.18);
            double fadeOut = Math.min(1.0, (1.0 - t) / 0.16);
            float alpha = (float) Math.max(0.0, Math.min(fadeIn, fadeOut));
            double pulse = 1.0 + 0.035 * Math.sin(t * Math.PI * 6.0);
            int size = (int) Math.round(270 * pulse);
            int x = (WIDTH - size) / 2;
            int y = (HEIGHT - size) / 2;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g.drawImage(logo, x, y, size, size, null);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static void writeGif(BufferedImage logo, Path output) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("gif");
        if (!writers.hasNext()) throw new IOException("JDK ImageIO no dispone de writer GIF");
        ImageWriter writer = writers.next();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(output.toFile())) {
            writer.setOutput(out);
            writer.prepareWriteSequence(null);
            for (int i = 0; i < FRAME_COUNT; i++) {
                BufferedImage frame = render(logo, i);
                ImageTypeSpecifier type = ImageTypeSpecifier.createFromRenderedImage(frame);
                IIOMetadata metadata = writer.getDefaultImageMetadata(type, null);
                configureFrameMetadata(metadata, i == 0);
                writer.writeToSequence(new IIOImage(frame, null, metadata), null);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
    }

    private static void configureFrameMetadata(IIOMetadata metadata, boolean first) throws IOException {
        String format = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
        IIOMetadataNode control = child(root, "GraphicControlExtension");
        control.setAttribute("disposalMethod", "none");
        control.setAttribute("userInputFlag", "FALSE");
        control.setAttribute("transparentFlag", "FALSE");
        control.setAttribute("delayTime", Integer.toString(FRAME_DELAY_CS));
        control.setAttribute("transparentColorIndex", "0");
        if (first) {
            IIOMetadataNode extensions = child(root, "ApplicationExtensions");
            IIOMetadataNode loop = new IIOMetadataNode("ApplicationExtension");
            loop.setAttribute("applicationID", "NETSCAPE");
            loop.setAttribute("authenticationCode", "2.0");
            loop.setUserObject(new byte[]{1, 0, 0});
            extensions.appendChild(loop);
        }
        metadata.setFromTree(format, root);
    }

    private static IIOMetadataNode child(IIOMetadataNode parent, String name) {
        for (int i = 0; i < parent.getLength(); i++) {
            if (name.equals(parent.item(i).getNodeName())) return (IIOMetadataNode) parent.item(i);
        }
        IIOMetadataNode node = new IIOMetadataNode(name);
        parent.appendChild(node);
        return node;
    }
}
