package pcp.xenon;

import java.nio.file.Path;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFormat;

/** Verifica el WAV PCM decodificado desde el recurso XMA real de bootanim.xex. */
public final class BootAnimationAudioSmoke {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Uso: BootAnimationAudioSmoke audio-pcm.wav");
        try (AudioInputStream in = AudioSystem.getAudioInputStream(Path.of(args[0]).toFile())) {
            AudioFormat f = in.getFormat();
            if (!AudioFormat.Encoding.PCM_SIGNED.equals(f.getEncoding())
                    || f.getSampleRate() != 48000f || f.getSampleSizeInBits() != 16
                    || f.getChannels() != 2 || f.getFrameSize() != 4) {
                throw new AssertionError("Formato PCM inesperado: " + f);
            }
            long frames = in.getFrameLength();
            double seconds = frames / f.getFrameRate();
            if (seconds < 8.0 || seconds > 8.3) throw new AssertionError("Duración inesperada: " + seconds);
            long bytes = 0;
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) >= 0) bytes += n;
            if (bytes < 1_000_000) throw new AssertionError("PCM truncado: " + bytes);
            System.out.printf("BOOTANIM_REAL_AUDIO_OK encoding=%s rate=%.0f channels=%d bits=%d duration=%.3fs pcmBytes=%d%n",
                    f.getEncoding(), f.getSampleRate(), f.getChannels(), f.getSampleSizeInBits(), seconds, bytes);
        }
    }
}
