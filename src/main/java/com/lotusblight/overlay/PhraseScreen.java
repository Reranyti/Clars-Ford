package com.lotusblight.overlay;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The sung phrases of the second song (phrases.txt, "seconds<TAB>text"): while one is being sung the whole screen goes black and
 * the words stand alone on it. Nothing is to be hit meanwhile - the circles of those moments are taken out of the game
 * ({@link RhythmGame#skip}), so there is no damage and they do not count in the results.
 *
 * <p>A phrase lasts until the next one starts, but as long as it is sung (about 0.28 s a character, at most {@link #MAX_HOLD_MS}); a third column in phrases.txt sets the end exactly.
 * The look of the words is provisional until the original's is known.
 */
final class PhraseScreen {
    private record Phrase(double from, double to, String text) {}

    private static final double MAX_HOLD_MS = 1_600;
    private static final double PER_CHAR_MS = 280;
    private static final double OUT_MS = 140;           // how long the black takes to clear after the last words

    private List<Phrase> phrases = new ArrayList<>();
    private final List<Phrase> fromFile;
    private long seen = Long.MIN_VALUE;

    PhraseScreen() {
        List<double[]> starts = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        List<Double> ends = new ArrayList<>();
        try (InputStream in = PhraseScreen.class.getResourceAsStream("/assets/lotusblight/overlay/phrases.txt")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] p = line.split("\\t");
                    if (p.length >= 2) {
                        starts.add(new double[]{Double.parseDouble(p[0].trim()) * 1000});
                        texts.add(p[1].trim());
                        ends.add(p.length >= 3 ? Double.parseDouble(p[2].trim()) * 1000 : 0.0);          // an optional third column: where it ends, s
                    }
                }
            }
        } catch (Exception ignored) {
            // no phrases: no black screens
        }
        List<Phrase> loaded = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            double from = starts.get(i)[0];
            String text = texts.get(i);
            double sung = Math.min(MAX_HOLD_MS, 200 + PER_CHAR_MS * text.replace("…", "").length());      // roughly as long as it is sung
            double hold = i + 1 < starts.size() ? Math.min(starts.get(i + 1)[0] - from, sung) : sung;
            if (ends.get(i) > from) hold = ends.get(i) - from;                                              // the author's own end, if given
            loaded.add(new Phrase(from, from + Math.max(200, hold), text));
        }
        fromFile = loaded;
        phrases = loaded;
    }

    /** The phrases of the timeline (events named "Phrase: words" on the text track) take the place of the file's when there are any. */
    private void refresh() {
        long v = SceneData.version();
        if (v == seen) return;
        seen = v;
        SceneData.Cut cut = Layers.cut();
        List<Phrase> mine = new ArrayList<>();
        if (cut != null) {
            for (SceneData.Event e : cut.events) {
                if (e.track() == SceneLabels.TEXT && e.label().startsWith(SceneLabels.PHRASE)) {
                    mine.add(new Phrase(e.from() * 1000, e.to() * 1000, e.label().substring(SceneLabels.PHRASE.length())));
                }
            }
        }
        mine.sort((a, b) -> Double.compare(a.from, b.from));
        phrases = mine.isEmpty() ? fromFile : mine;
    }

    /** The phrases of the file (the author's own), whatever the timeline says: {from ms, to ms, words}. */
    List<Object[]> defaults() {
        List<Object[]> out = new ArrayList<>();
        for (Phrase p : fromFile) out.add(new Object[]{p.from, p.to, p.text});
        return out;
    }

    /** The phrases as they are now: {from ms, to ms} and the words. */
    List<Object[]> items() {
        refresh();
        List<Object[]> out = new ArrayList<>();
        for (Phrase p : phrases) out.add(new Object[]{p.from, p.to, p.text});
        return out;
    }

    /** The stretches of the song, in ms, that are black with words (for taking their circles out of the game). */
    List<double[]> intervals() {
        refresh();
        List<double[]> out = new ArrayList<>();
        for (Phrase p : phrases) out.add(new double[]{p.from, p.to});
        return out;
    }

    /** The family of the plain sans Chinese face, for other things drawn in the same look. */
    static String family() {
        return wordsFont();
    }

    private static String wordsFamily;

    /** The plain sans-serif Chinese face the original uses (Microsoft YaHei on Windows), else whatever the system has for it. */
    private static String wordsFont() {
        if (wordsFamily == null) {
            java.util.Set<String> have = new java.util.HashSet<>(java.util.Arrays.asList(
                    java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            wordsFamily = Font.DIALOG;
            for (String f : new String[]{"Microsoft YaHei", "Microsoft YaHei UI", "Noto Sans CJK SC", "Noto Sans SC", "SimHei", "Arial Unicode MS"}) {
                if (have.contains(f)) {
                    wordsFamily = f;
                    break;
                }
            }
        }
        return wordsFamily;
    }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    /** Draws over everything else: the black and the words. */
    void render(Graphics2D g, int w, int h, double ms) {
        refresh();
        Phrase cur = null;
        double black = 0;
        for (Phrase p : phrases) {
            if (ms >= p.from && ms < p.to) {
                cur = p;
                black = 1;
            } else if (ms >= p.to && ms < p.to + OUT_MS) {
                black = Math.max(black, 1 - (ms - p.to) / OUT_MS);
            }
        }
        if (black <= 0) return;
        Graphics2D b = (Graphics2D) g.create();
        b.setComposite(AlphaComposite.SrcOver);
        b.setColor(new Color(2, 2, 8, (int) Math.round(255 * black)));       // the original's near-black, a breath of blue
        b.fillRect(0, 0, w, h);
        if (cur != null) {
            b.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            double age = ms - cur.from;
            double alpha = Math.min(1, age / 90.0) * (1 - smooth((ms - (cur.to - 120)) / 120.0));
            float size = (float) (h * 0.215);
            b.setFont(new Font(wordsFont(), Font.PLAIN, 12).deriveFont(size));
            FontMetrics fm = b.getFontMetrics();
            while (fm.stringWidth(cur.text) > w * 0.86 && size > 12) {
                size *= 0.92f;
                b.setFont(b.getFont().deriveFont(size));
                fm = b.getFontMetrics();
            }
            // The original draws its "……" as six big round dots, not as the font's small ones.
            boolean dots = cur.text.endsWith("……");
            String text = dots ? cur.text.substring(0, cur.text.length() - 2) : cur.text;
            double dotD = h * 0.034, pitch = h * 0.052, dotGap = h * 0.02;
            double dotsW = dots ? dotGap + pitch * 5 + dotD : 0;
            double total = fm.stringWidth(text) + dotsW;
            int x = (int) ((w - total) / 2), y = h / 2 + fm.getAscent() / 3;
            boolean noSignal = cur.text.equals("\u65e0\u4fe1\u53f7");         // 无信号: red, and tipped over
            if (noSignal) {
                b.rotate(Math.toRadians(11), w / 2.0, h / 2.0);
                b.setColor(new Color(255, 32, 40, (int) (255 * alpha)));
            } else {
                b.setColor(new Color(255, 255, 255, (int) (255 * alpha)));
            }
            // the strokes of the original are a little heavier than the plain face: drawn twice, half a pixel apart
            b.drawString(text, x - 0.45f, (float) y);
            b.drawString(text, x + 0.45f, (float) y);
            if (dots) {
                double dx = x + fm.stringWidth(text) + dotGap, dy = y - h * 0.047;
                for (int i = 0; i < 6; i++) b.fill(new java.awt.geom.Ellipse2D.Double(dx + i * pitch, dy - dotD / 2, dotD, dotD));
            }
        }
        b.dispose();
    }
}
