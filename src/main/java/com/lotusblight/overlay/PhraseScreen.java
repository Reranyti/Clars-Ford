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
 * <p>A phrase lasts until the next one starts, but at most {@link #MAX_HOLD_MS}; the last one lasts a second.
 * The look of the words is provisional until the original's is known.
 */
final class PhraseScreen {
    private record Phrase(double from, double to, String text) {}

    private static final double MAX_HOLD_MS = 1_600;
    private static final double LAST_HOLD_MS = 1_000;
    private static final double OUT_MS = 140;           // how long the black takes to clear after the last words

    private final List<Phrase> phrases = new ArrayList<>();

    PhraseScreen() {
        List<double[]> starts = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        try (InputStream in = PhraseScreen.class.getResourceAsStream("/assets/lotusblight/overlay/phrases.txt")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] p = line.split("\\t", 2);
                    if (p.length == 2) {
                        starts.add(new double[]{Double.parseDouble(p[0].trim()) * 1000});
                        texts.add(p[1].trim());
                    }
                }
            }
        } catch (Exception ignored) {
            // no phrases: no black screens
        }
        for (int i = 0; i < starts.size(); i++) {
            double from = starts.get(i)[0];
            double hold = i + 1 < starts.size() ? Math.min(starts.get(i + 1)[0] - from, MAX_HOLD_MS) : LAST_HOLD_MS;
            phrases.add(new Phrase(from, from + Math.max(200, hold), texts.get(i)));
        }
    }

    /** The stretches of the song, in ms, that are black with words (for taking their circles out of the game). */
    List<double[]> intervals() {
        List<double[]> out = new ArrayList<>();
        for (Phrase p : phrases) out.add(new double[]{p.from, p.to});
        return out;
    }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    /** Draws over everything else: the black and the words. */
    void render(Graphics2D g, int w, int h, double ms) {
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
        b.setColor(new Color(0, 0, 0, (int) Math.round(255 * black)));
        b.fillRect(0, 0, w, h);
        if (cur != null) {
            b.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            double age = ms - cur.from;
            double alpha = Math.min(1, age / 90.0) * (1 - smooth((ms - (cur.to - 120)) / 120.0));
            float size = (float) (h * 0.14);
            b.setFont(new Font(Font.DIALOG, Font.BOLD, 12).deriveFont(size));
            FontMetrics fm = b.getFontMetrics();
            while (fm.stringWidth(cur.text) > w * 0.86 && size > 12) {
                size *= 0.92f;
                b.setFont(b.getFont().deriveFont(size));
                fm = b.getFontMetrics();
            }
            int x = (w - fm.stringWidth(cur.text)) / 2, y = h / 2 + fm.getAscent() / 3;
            int split = Math.max(2, (int) (h * 0.004));
            b.setColor(new Color(255, 40, 80, (int) (110 * alpha)));
            b.drawString(cur.text, x - split, y);
            b.setColor(new Color(40, 220, 255, (int) (110 * alpha)));
            b.drawString(cur.text, x + split, y);
            b.setColor(new Color(255, 255, 255, (int) (255 * alpha)));
            b.drawString(cur.text, x, y);
        }
        b.dispose();
    }
}
