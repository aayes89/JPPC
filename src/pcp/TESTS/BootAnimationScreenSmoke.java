package pcp.xenon;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

public final class BootAnimationScreenSmoke {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("logo.png output.png");
        BootAnimationOverlay overlay = new BootAnimationOverlay(Path.of(args[0]));
        BufferedImage first = overlay.renderAt(1280, 720, 0.75);
        BufferedImage second = overlay.renderAt(1280, 720, 1.25);
        int visible = 0;
        int changed = 0;
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                int a = first.getRGB(x, y) & 0xFFFFFF;
                int b = second.getRGB(x, y) & 0xFFFFFF;
                if (a != 0) visible++;
                if (a != b) changed++;
            }
        }
        if (visible < 1000 || changed < 1000) {
            throw new AssertionError("Overlay no visible/animated: visible=" + visible + " changed=" + changed);
        }
        ImageIO.write(first, "PNG", Path.of(args[1]).toFile());
        Path gifPath = Path.of(args[1]).resolveSibling("xenon-bootanim-screen.gif");
        writeGif(overlay, gifPath);
        System.out.printf("BOOTANIM_SCREEN_RENDER_OK visible=%d changed=%d output=%s gif=%s%n",
                visible, changed, args[1], gifPath);
    }

    private static void writeGif(BootAnimationOverlay overlay, Path path) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("gif");
        if (!writers.hasNext()) throw new IllegalStateException("No existe writer GIF");
        ImageWriter writer = writers.next();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(path.toFile())) {
            writer.setOutput(out);
            writer.prepareWriteSequence(null);
            for (int i = 0; i < 90; i++) {
                BufferedImage frame = overlay.renderAt(640, 480, i * 0.03);
                IIOMetadata metadata = writer.getDefaultImageMetadata(
                        ImageTypeSpecifier.createFromRenderedImage(frame), null);
                String format = metadata.getNativeMetadataFormatName();
                IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
                IIOMetadataNode control = child(root, "GraphicControlExtension");
                control.setAttribute("disposalMethod", "none");
                control.setAttribute("userInputFlag", "FALSE");
                control.setAttribute("transparentFlag", "FALSE");
                control.setAttribute("delayTime", "3");
                control.setAttribute("transparentColorIndex", "0");
                if (i == 0) {
                    IIOMetadataNode extensions = child(root, "ApplicationExtensions");
                    IIOMetadataNode loop = new IIOMetadataNode("ApplicationExtension");
                    loop.setAttribute("applicationID", "NETSCAPE");
                    loop.setAttribute("authenticationCode", "2.0");
                    loop.setUserObject(new byte[]{1, 0, 0});
                    extensions.appendChild(loop);
                }
                metadata.setFromTree(format, root);
                writer.writeToSequence(new IIOImage(frame, null, metadata), null);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
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
