import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

/** Trims the Iowa MIS mf piano notes (mono float32 raw at 44.1 kHz) into the app's set: onset-aligned, level-matched, faded, 16-bit WAV. */
public class PianoPrep {
    static final int RATE = 44100;
    static final String[] NAMES = {"C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"};

    static int midiOf(String n) {
        String letter = n.replaceAll("[0-9]", "");
        int oct = Integer.parseInt(n.replaceAll("[^0-9]", ""));
        return 12 * (oct + 1) + Arrays.asList(NAMES).indexOf(letter);
    }

    static double seconds(int midi) {
        if (midi <= 40) return 7.0;
        if (midi <= 60) return 5.5;
        if (midi <= 76) return 4.0;
        return 2.5;
    }

    public static void main(String[] a) throws Exception {
        Path in = Paths.get(a[0]), out = Paths.get(a[1]);
        Files.createDirectories(out);
        long total = 0;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(in, "*.f32")) {
            for (Path p : ds) {
                String note = p.getFileName().toString().replace("Piano.mf.", "").replace(".f32", "");
                int midi = midiOf(note);
                byte[] b = Files.readAllBytes(p);
                FloatBuffer fb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
                float[] x = new float[fb.remaining()];
                fb.get(x);
                float peak = 0;
                for (float v : x) peak = Math.max(peak, Math.abs(v));
                int onset = 0;
                while (onset < x.length && Math.abs(x[onset]) < peak * 0.05f) onset++;
                onset = Math.max(0, onset - RATE * 3 / 1000);
                int len = Math.min(x.length - onset, (int) (seconds(midi) * RATE));
                // Level: RMS over 50-300 ms after the onset, matched across notes; never over 0.95 peak.
                double sum = 0; int n = 0;
                for (int i = onset + RATE / 20; i < Math.min(onset + len, onset + RATE * 3 / 10); i++) { sum += x[i] * x[i]; n++; }
                double rms = Math.sqrt(sum / Math.max(1, n));
                float pk = 0;
                for (int i = onset; i < onset + len; i++) pk = Math.max(pk, Math.abs(x[i]));
                double target = 0.12 * (midi > 84 ? Math.pow(10, -6.0 / 20 * (midi - 84) / 24.0) : 1.0);
                double g = Math.min(target / rms, 0.95 / pk);
                double ns = 0; int nn = 0;
                for (int i = Math.max(0, onset - RATE / 10); i < onset; i++) { ns += x[i] * x[i]; nn++; }
                double noiseDb = 20 * Math.log10(Math.sqrt(ns / Math.max(1, nn)) * g + 1e-9);
                int fade = (int) (0.3 * RATE);
                short[] s = new short[len];
                double tail = 0;
                for (int i = 0; i < len; i++) {
                    double v = x[onset + i] * g;
                    if (i < RATE * 3 / 1000) v *= i / (RATE * 3 / 1000.0);
                    if (i >= len - fade) v *= 0.5 + 0.5 * Math.cos(Math.PI * (i - (len - fade)) / fade);
                    if (i >= len - fade - RATE / 10 && i < len - fade) tail = Math.max(tail, Math.abs(v));
                    s[i] = (short) Math.max(-32767, Math.min(32767, Math.round(v * 32767)));
                }
                Path f = out.resolve("mf-" + midi + ".wav");
                write(f, s);
                total += Files.size(f);
                System.out.printf("%-4s midi %3d onset %5.3fs len %.1fs gain %5.2f tail %5.1f dB noise %5.1f dB%n", note, midi, onset / (double) RATE, len / (double) RATE, g, 20 * Math.log10(tail + 1e-9), noiseDb);
            }
        }
        System.out.printf("total %.1f MB%n", total / 1e6);
    }

    static void write(Path f, short[] s) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(44 + s.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        bb.put("RIFF".getBytes()).putInt(36 + s.length * 2).put("WAVE".getBytes());
        bb.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1).putInt(RATE).putInt(RATE * 2).putShort((short) 2).putShort((short) 16);
        bb.put("data".getBytes()).putInt(s.length * 2);
        for (short v : s) bb.putShort(v);
        Files.write(f, bb.array());
    }
}
