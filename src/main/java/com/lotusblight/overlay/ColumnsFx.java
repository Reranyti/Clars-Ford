package com.lotusblight.overlay;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The repeating columns of the second song (columns.txt: the moments, in seconds, each next sign appears; a blank line = the columns reset).
 * "有" stacks down the edge of the screen and "线" next to it, mirrored on the other side; with every sign the whole picture is knocked over
 * to one side, sharply, alternately left and right (the camera, see {@link #camera}). It draws no background of its own.
 *
 * <p>The camera moves only the picture (backdrop and columns), not the circles: their clicks are judged where they are drawn.
 */
final class ColumnsFx {
    private static final double TILT = Math.toRadians(5.5);   // how far a hit knocks the picture over
    private static final double TILT_DECAY_MS = 120;           // and how fast it falls back
    private static final double FADE_OUT_MS = 350;

    private final List<double[]> series = new ArrayList<>();   // each: the times (ms) at which the next sign appears

    ColumnsFx() {
        try (InputStream in = ColumnsFx.class.getResourceAsStream("/assets/lotusblight/overlay/columns.txt")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                List<Double> cur = new ArrayList<>();
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith("#")) continue;
                    if (line.isBlank()) {
                        flush(cur);
                        continue;
                    }
                    cur.add(Double.parseDouble(line.trim()) * 1000);
                }
                flush(cur);
            }
        } catch (Exception ignored) {
            // no columns: nothing is drawn
        }
    }

    private void flush(List<Double> cur) {
        if (cur.isEmpty()) return;
        double[] a = new double[cur.size()];
        for (int i = 0; i < a.length; i++) a[i] = cur.get(i);
        series.add(a);
        cur.clear();
    }

    /** The series the moment belongs to (or -1), and how many signs are up in it. */
    private int[] state(double ms) {
        for (int s = 0; s < series.size(); s++) {
            double[] a = series.get(s);
            double end = a[a.length - 1] + FADE_OUT_MS;
            if (ms >= a[0] && ms < end) {
                int n = 0;
                while (n < a.length && a[n] <= ms) n++;
                return new int[]{s, n};
            }
        }
        return null;
    }

    /** {roll in radians, zoom}: the camera knocked over by the last sign, alternately to the left and to the right. */
    double[] camera(double ms) {
        int[] st = state(ms);
        if (st == null || st[1] == 0) return new double[]{0, 1};
        double[] a = series.get(st[0]);
        int k = st[1] - 1;
        double age = ms - a[k];
        double kick = Math.exp(-age / TILT_DECAY_MS);
        double sign = k % 2 == 0 ? 1 : -1;
        double roll = sign * TILT * kick;
        double zoom = 1 + 0.045 * kick + 0.05 * Math.abs(Math.sin(roll)) * 4;      // enough past the frame that the corners stay covered
        return new double[]{roll, zoom};
    }

    private static Shape glyph(String ch, double size) {
        Font f = new Font(PhraseScreen.family(), Font.BOLD, 12).deriveFont((float) size);
        GlyphVector gv = f.createGlyphVector(new java.awt.font.FontRenderContext(null, true, true), ch);
        Rectangle2D b = gv.getVisualBounds();
        return AffineTransform.getTranslateInstance(-b.getCenterX(), -b.getCenterY()).createTransformedShape(gv.getOutline());
    }

    private double fade(double ms, double[] a) {
        double end = a[a.length - 1];
        return ms > end ? 1 - (ms - end) / FADE_OUT_MS : 1;
    }

    /** The columns, and the white bars. Call with the graphics already carrying the camera. */
    void render(Graphics2D g, int w, int h, double ms) {
        int[] st = state(ms);
        if (st == null || st[1] == 0) return;
        double[] a = series.get(st[0]);
        int count = st[1];
        double fade = fade(ms, a);
        Graphics2D b = (Graphics2D) g.create();
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (fade < 1) b.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, fade)));
        double size = h * 0.15, pitch = h * 0.118;
        int visible = (int) Math.floor(h / pitch) + 1;
        // once the column is full it moves up by a sign at a time, snapping on the beat
        double scrollSigns = Math.max(0, count - (visible - 1));
        double scrollKick = scrollSigns > 0 ? Math.min(1, (ms - a[count - 1]) / 70.0) : 1;
        double scroll = (Math.max(0, scrollSigns - 1) + scrollKick) * pitch;
        Shape you = glyph("有", size), xian = glyph("线", size * 0.94);
        String[] sides = {"L", "R"};
        for (String side : sides) {
            boolean left = side.equals("L");
            Shape outer = left ? you : xian, inner = left ? xian : you;
            double outerX = left ? w * 0.052 : w * 0.948, innerX = left ? w * 0.142 : w * 0.858;
            double lean = left ? -0.16 : 0.16;
            drawColumn(b, outer, outerX, size, pitch, count, a, ms, scroll, lean, 0, h);
            drawColumn(b, inner, innerX, size, pitch, count, a, ms, scroll, lean, 1, h);
        }
        b.dispose();
    }

    private void drawColumn(Graphics2D b, Shape glyph, double x, double size, double pitch, int count, double[] times, double ms,
                            double scroll, double lean, int second, int h) {
        for (int i = 0; i < count; i++) {
            double y = i * pitch + pitch * 0.55 - scroll + second * pitch * 0.5;
            if (y < -pitch || y > h + pitch) continue;
            double age = ms - times[i];
            double pop = age < 70 ? 1 + 0.5 * (1 - age / 70.0) : 1;           // a sign arrives big and settles
            AffineTransform keep = b.getTransform();
            b.translate(x + y * Math.sin(-lean) * 0.35, y);
            b.rotate(lean);
            b.scale(pop, pop);
            b.setColor(Color.BLACK);
            b.setStroke(new BasicStroke((float) (size * 0.13), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            b.draw(glyph);
            b.setColor(Color.WHITE);
            b.fill(glyph);
            b.setTransform(keep);
        }
    }
}
