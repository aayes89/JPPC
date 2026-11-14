package pcp.xenon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

/** Reproduce el audio XMA original de bootanim, previamente decodificado a PCM WAV. */
public final class BootAnimationAudio implements AutoCloseable {
    private final Path pcmFile;
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile Clip clip;

    public BootAnimationAudio(Path pcmFile) {
        if (pcmFile == null) throw new IllegalArgumentException("Ruta PCM nula");
        this.pcmFile = pcmFile;
    }

    public static BootAnimationAudio fromDefaultNand() {
        return new BootAnimationAudio(Paths.get("nandDump/extracted/resources/bootanim.xex/audio-pcm.wav"));
    }

    /** Se llama al observar la transición guest; nunca inicia dos veces el mismo clip. */
    public void startOnce() {
        if (!started.compareAndSet(false, true)) return;
        try {
            if (!Files.isRegularFile(pcmFile)) throw new IOException("No existe audio PCM real: " + pcmFile);
            try (AudioInputStream in = AudioSystem.getAudioInputStream(pcmFile.toFile())) {
                Clip opened = AudioSystem.getClip();
                opened.open(in);
                clip = opened;
                opened.start();
                System.out.printf("[BOOTANIM-AUDIO] PLAY real-XMA-decoded PCM: %s, %.3f s, %d Hz, %d canales%n",
                        pcmFile, opened.getMicrosecondLength() / 1_000_000.0,
                        opened.getFormat().getSampleRate(), opened.getFormat().getChannels());
            }
        } catch (IOException | UnsupportedAudioFileException | LineUnavailableException | IllegalArgumentException ex) {
            System.err.println("[BOOTANIM-AUDIO] No se pudo reproducir el audio real: " + ex.getMessage());
        }
    }

    @Override public void close() {
        Clip current = clip;
        if (current != null) {
            current.stop();
            current.close();
            clip = null;
        }
    }
}
