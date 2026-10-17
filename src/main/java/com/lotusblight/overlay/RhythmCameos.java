package com.lotusblight.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Four short cameos of rhythm games, a few seconds each, drawn in code in the look of the games they nod to (nothing of theirs is copied: no
 * pictures, no sounds, no names): the circles and shrinking rings of a clicking game, a path of tiles with two planets going round it, notes
 * falling down lanes, and a row of seven beats with a pulse. {@code t} is the time in seconds from the start of the cameo.
 */
final class RhythmCameos {
    static final double LENGTH = 4.6;

    static String name(int kind) {
        return switch (kind) {
            case 0 -> "circles";
            case 1 -> "tiles";
            case 2 -> "lanes";
            default -> "seventh";
        };
    }

    static void render(Graphics2D g, int w, int h, int kind, double t) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        switch (kind) {
            case 0 -> circles(g, w, h, t);
            case 1 -> tiles(g, w, h, t);
            case 2 -> lanes(g, w, h, t);
            default -> seventh(g, w, h, t);
        }
    }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    // ---- 1. circles: hit them as the ring closes in -----------------------------------------------------------------------------------

    private static void circles(Graphics2D g, int w, int h, double t) {
        g.setPaint(new GradientPaint(0, 0, new Color(24, 10, 40), 0, h, new Color(60, 16, 70)));
        g.fillRect(0, 0, w, h);
        // dim, pulsing background rays, as behind a beatmap
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + t * 0.2;
            g.setColor(new Color(255, 120, 200, 14));
            Path2D.Double p = new Path2D.Double();
            p.moveTo(w / 2.0, h / 2.0);
            p.lineTo(w / 2.0 + Math.cos(a - 0.12) * w, h / 2.0 + Math.sin(a - 0.12) * w);
            p.lineTo(w / 2.0 + Math.cos(a + 0.12) * w, h / 2.0 + Math.sin(a + 0.12) * w);
            p.closePath();
            g.fill(p);
        }
        double r = h * 0.085;
        double[][] spots = {{0.22, 0.62}, {0.40, 0.34}, {0.58, 0.64}, {0.76, 0.38}, {0.50, 0.50}, {0.30, 0.50}, {0.70, 0.55}, {0.60, 0.30}};
        Color[] combo = {new Color(255, 96, 160), new Color(96, 190, 255), new Color(255, 200, 80), new Color(150, 255, 170)};
        double beat = 0.52, appear = 0.8;
        for (int i = 0; i < spots.length; i++) {
            double hit = 0.7 + i * beat;
            double age = t - (hit - appear);
            if (age < 0) continue;
            double x = spots[i][0] * w, y = spots[i][1] * h;
            Color c = combo[i % combo.length];
            double toHit = hit - t;
            if (toHit > -0.18) {
                double fade = Math.min(1, age / 0.18);
                if (toHit > 0) {                                                     // the circle, and the ring still closing in on it
                    double ring = r * (1 + 3.2 * Math.min(1, toHit / appear));
                    g.setColor(new Color(255, 255, 255, (int) (200 * fade)));
                    g.setStroke(new BasicStroke((float) (h * 0.006)));
                    g.draw(new Ellipse2D.Double(x - ring, y - ring, ring * 2, ring * 2));
                    g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (225 * fade)));
                    g.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
                    g.setColor(new Color(255, 255, 255, (int) (255 * fade)));
                    g.setStroke(new BasicStroke((float) (h * 0.008)));
                    g.draw(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
                    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) (h * 0.07)));
                    String n = String.valueOf(i % 4 + 1);
                    FontMetrics fm = g.getFontMetrics();
                    g.setColor(new Color(255, 255, 255, (int) (255 * fade)));
                    g.drawString(n, (float) (x - fm.stringWidth(n) / 2.0), (float) (y + fm.getAscent() * 0.36));
                } else {                                                             // hit: a burst that fades
                    double u = (-toHit) / 0.18;
                    double br = r * (1 + 0.7 * u);
                    g.setColor(new Color(255, 255, 255, (int) (200 * (1 - u))));
                    g.setStroke(new BasicStroke((float) (h * 0.01)));
                    g.draw(new Ellipse2D.Double(x - br, y - br, br * 2, br * 2));
                }
            }
        }
        // the cursor, running from circle to circle, with a short trail
        double ct = (t - 0.7) / beat;
        int k = (int) Math.floor(Math.max(0, ct));
        double u = smooth(ct - k);
        int a = Math.min(spots.length - 1, k), b = Math.min(spots.length - 1, k + 1);
        double cx = (spots[a][0] + (spots[b][0] - spots[a][0]) * u) * w, cy = (spots[a][1] + (spots[b][1] - spots[a][1]) * u) * h;
        for (int s = 8; s >= 0; s--) {
            double tt = ct - s * 0.05;
            int ka = (int) Math.floor(Math.max(0, tt)), kb = Math.min(spots.length - 1, ka + 1);
            ka = Math.min(spots.length - 1, ka);
            double uu = smooth(tt - Math.floor(Math.max(0, tt)));
            double px = (spots[ka][0] + (spots[kb][0] - spots[ka][0]) * uu) * w, py = (spots[ka][1] + (spots[kb][1] - spots[ka][1]) * uu) * h;
            g.setColor(new Color(255, 255, 255, 18 + (8 - s) * 8));
            double cr = h * 0.012 * (1 - s * 0.07);
            g.fill(new Ellipse2D.Double(px - cr, py - cr, cr * 2, cr * 2));
        }
        g.setColor(new Color(255, 255, 255, 230));
        g.fill(new Ellipse2D.Double(cx - h * 0.016, cy - h * 0.016, h * 0.032, h * 0.032));
        hud(g, w, h, (int) (t * 4200), "x" + Math.max(0, (int) ((t - 0.7) / beat) + 1));
    }

    private static void hud(Graphics2D g, int w, int h, int score, String combo) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) (h * 0.07)));
        g.setColor(new Color(255, 255, 255, 230));
        String s = String.format("%07d", score);
        g.drawString(s, w - g.getFontMetrics().stringWidth(s) - h * 0.04f, h * 0.10f);
        g.drawString(combo, h * 0.04f, h * 0.95f);
        g.setColor(new Color(255, 255, 255, 40));
        g.fillRoundRect((int) (w * 0.3), (int) (h * 0.03), (int) (w * 0.4), (int) (h * 0.018), 8, 8);
        g.setColor(new Color(255, 120, 190));
        g.fillRoundRect((int) (w * 0.3), (int) (h * 0.03), (int) (w * 0.4 * 0.8), (int) (h * 0.018), 8, 8);
    }

    // ---- 2. tiles: two planets going round and round a path --------------------------------------------------------------------------

    private static void tiles(Graphics2D g, int w, int h, double t) {
        g.setPaint(new GradientPaint(0, 0, new Color(14, 10, 34), 0, h, new Color(36, 18, 70)));
        g.fillRect(0, 0, w, h);
        double step = h * 0.15;
        // the path: a winding line of tiles; turns make the planets go round faster or slower
        int[][] dir = {{1, 0}, {1, 0}, {1, 0}, {0, -1}, {1, 0}, {1, 0}, {0, 1}, {0, 1}, {1, 0}, {1, 0}, {0, -1}, {1, 0}, {1, 0}, {1, 0}};
        int n = dir.length + 1;
        double[] px = new double[n], py = new double[n];
        px[0] = 0;
        py[0] = 0;
        for (int i = 1; i < n; i++) {
            px[i] = px[i - 1] + dir[i - 1][0] * step;
            py[i] = py[i - 1] + dir[i - 1][1] * step;
        }
        double beat = 0.34;
        double pos = t / beat;                                                      // which tile the pivot is on
        int cur = Math.min(n - 2, (int) Math.floor(pos));
        double frac = pos - Math.floor(pos);
        // the camera follows the pivot tile
        double camX = px[cur] + (px[Math.min(n - 1, cur + 1)] - px[cur]) * smooth(frac), camY = py[cur] + (py[Math.min(n - 1, cur + 1)] - py[cur]) * smooth(frac);
        g.translate(w * 0.42 - camX, h * 0.5 - camY);
        for (int i = 0; i < n; i++) {
            boolean passed = i <= cur;
            Color c = passed ? new Color(255, 214, 120) : new Color(150, 110, 255);
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 40));
            g.fill(new RoundRectangle2D.Double(px[i] - step * 0.5 - 6, py[i] - step * 0.5 - 6, step + 12, step + 12, 22, 22));
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), passed ? 235 : 215));
            g.fill(new RoundRectangle2D.Double(px[i] - step * 0.46, py[i] - step * 0.46, step * 0.92, step * 0.92, 14, 14));
        }
        // the two planets: one stands on the tile, the other goes round it half a turn at a time
        double angle = frac * Math.PI + cur * Math.PI;
        double cx = px[cur], cy = py[cur], rad = step * 0.9;
        double ox = cx + Math.cos(angle) * rad, oy = cy + Math.sin(angle) * rad;
        for (int s = 10; s >= 1; s--) {                                              // the trail of the moving planet
            double a2 = angle - s * 0.11;
            double tx = cx + Math.cos(a2) * rad, ty = cy + Math.sin(a2) * rad;
            g.setColor(new Color(255, 90, 100, 14 + (10 - s) * 6));
            double pr = step * 0.2 * (1 - s * 0.06);
            g.fill(new Ellipse2D.Double(tx - pr, ty - pr, pr * 2, pr * 2));
        }
        planet(g, cx, cy, step * 0.24, new Color(80, 170, 255));
        planet(g, ox, oy, step * 0.24, new Color(255, 80, 96));
        g.translate(-(w * 0.42 - camX), -(h * 0.5 - camY));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) (h * 0.05)));
        g.setColor(new Color(255, 255, 255, 190));
        g.drawString("Perfect!", (float) (w * 0.42 - h * 0.1), (float) (h * 0.22));
    }

    private static void planet(Graphics2D g, double x, double y, double r, Color c) {
        g.setPaint(new RadialGradientPaint((float) x, (float) y, (float) (r * 2.6), new float[]{0f, 1f},
                new Color[]{new Color(c.getRed(), c.getGreen(), c.getBlue(), 120), new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
        g.fill(new Ellipse2D.Double(x - r * 2.6, y - r * 2.6, r * 5.2, r * 5.2));
        g.setColor(c);
        g.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
        g.setColor(new Color(255, 255, 255, 190));
        g.fill(new Ellipse2D.Double(x - r * 0.45, y - r * 0.55, r * 0.5, r * 0.5));
    }

    // ---- 3. lanes: notes falling down four lanes --------------------------------------------------------------------------------------

    private static void lanes(Graphics2D g, int w, int h, double t) {
        g.setColor(new Color(10, 12, 24));
        g.fillRect(0, 0, w, h);
        int lanes = 4;
        double laneW = h * 0.17, left = (w - laneW * lanes) / 2, hitY = h * 0.84;
        g.setColor(new Color(20, 24, 46));
        g.fillRect((int) left, 0, (int) (laneW * lanes), h);
        g.setStroke(new BasicStroke(2f));
        for (int i = 0; i <= lanes; i++) {
            g.setColor(new Color(255, 255, 255, 38));
            g.drawLine((int) (left + i * laneW), 0, (int) (left + i * laneW), h);
        }
        double speed = h * 0.62;                                                      // the notes fall this fast (px a second)
        Color[] c = {new Color(255, 96, 140), new Color(96, 200, 255), new Color(96, 200, 255), new Color(255, 96, 140)};
        // a pattern of (time of the hit, lane)
        double[][] notes = {{0.8, 0}, {1.1, 1}, {1.4, 2}, {1.7, 3}, {2.0, 1}, {2.0, 2}, {2.3, 0}, {2.6, 3}, {2.9, 1}, {3.2, 2}, {3.5, 0}, {3.5, 3}, {3.8, 1}, {4.1, 2}};
        for (double[] nt : notes) {
            double y = hitY - (nt[0] - t) * speed;
            if (y < -laneW || y > hitY + laneW * 0.6) continue;
            int lane = (int) nt[1];
            double x = left + lane * laneW;
            if (y > hitY) continue;                                                   // hit: it is gone
            g.setColor(c[lane]);
            g.fill(new RoundRectangle2D.Double(x + laneW * 0.07, y - laneW * 0.22, laneW * 0.86, laneW * 0.44, 12, 12));
            g.setColor(new Color(255, 255, 255, 120));
            g.fill(new RoundRectangle2D.Double(x + laneW * 0.07, y - laneW * 0.22, laneW * 0.86, laneW * 0.12, 8, 8));
        }
        for (int i = 0; i < lanes; i++) {                                              // the receptors, flashing on a hit
            double flash = 0;
            for (double[] nt : notes) if ((int) nt[1] == i && t >= nt[0] && t < nt[0] + 0.14) flash = 1 - (t - nt[0]) / 0.14;
            double x = left + i * laneW;
            g.setColor(new Color(255, 255, 255, (int) (50 + 160 * flash)));
            g.setStroke(new BasicStroke((float) (h * 0.008)));
            g.draw(new RoundRectangle2D.Double(x + laneW * 0.08, hitY - laneW * 0.22, laneW * 0.84, laneW * 0.44, 12, 12));
            if (flash > 0) {
                g.setColor(new Color(c[i].getRed(), c[i].getGreen(), c[i].getBlue(), (int) (90 * flash)));
                g.fillRect((int) x, 0, (int) laneW, (int) hitY);
            }
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) (h * 0.06)));
        g.setColor(new Color(255, 230, 120, 230));
        String j = "PERFECT";
        g.drawString(j, (float) ((w - g.getFontMetrics().stringWidth(j)) / 2.0), (float) (h * 0.55));
        g.setColor(new Color(255, 255, 255, 220));
        String s = String.format("%d  x%d", (int) (t * 9000), Math.max(0, (int) ((t - 0.8) / 0.3)));
        g.drawString(s, (float) (w - g.getFontMetrics().stringWidth(s) - h * 0.04), (float) (h * 0.1));
    }

    // ---- 4. a row of seven beats: press on the seventh --------------------------------------------------------------------------------

    private static void seventh(Graphics2D g, int w, int h, double t) {
        g.setPaint(new GradientPaint(0, 0, new Color(250, 214, 224), 0, h, new Color(190, 226, 250)));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(255, 255, 255, 120));
        for (int i = 0; i < 9; i++) g.fillRect(0, (int) (h * 0.12 * i), w, 2);
        double beat = 0.45;
        double rowY = h * 0.5, left = w * 0.14, gap = (w * 0.72) / 6.0;
        // the ECG line running under the beats
        g.setStroke(new BasicStroke((float) (h * 0.012), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(40, 50, 90));
        Path2D.Double ecg = new Path2D.Double();
        ecg.moveTo(0, h * 0.78);
        for (int x = 0; x < w; x += 4) {
            double tt = t - (w - x) / (w * 0.9) * 0.0 + x / (double) w * 2.2;
            double ph = (tt % beat) / beat;
            double dy = ph > 0.45 && ph < 0.5 ? -(ph - 0.45) / 0.05 : ph >= 0.5 && ph < 0.56 ? -1 + (ph - 0.5) / 0.06 * 1.6 : ph >= 0.56 && ph < 0.6 ? 0.6 - (ph - 0.56) / 0.04 * 0.6 : 0;
            ecg.lineTo(x, h * 0.78 + dy * h * 0.09);
        }
        g.draw(ecg);
        // seven beats, the seventh is the one to press; the others pass in turn
        double bt = t / beat;
        for (int i = 0; i < 7; i++) {
            double x = left + i * gap;
            boolean seventh = i == 6;
            double active = Math.max(0, 1 - Math.abs(bt % 7 - i) * 2.2);
            double rr = h * (seventh ? 0.075 : 0.05) * (1 + 0.3 * active);
            g.setColor(seventh ? new Color(255, 70, 100) : new Color(40, 50, 90));
            if (seventh) {
                g.setStroke(new BasicStroke((float) (h * 0.012)));
                g.draw(new Ellipse2D.Double(x - rr * 1.5, rowY - rr * 1.5, rr * 3, rr * 3));
            }
            g.fill(new Ellipse2D.Double(x - rr, rowY - rr, rr * 2, rr * 2));
            if (active > 0.1) {
                g.setColor(new Color(255, 255, 255, (int) (200 * active)));
                g.fill(new Ellipse2D.Double(x - rr * 0.4, rowY - rr * 0.4, rr * 0.8, rr * 0.8));
            }
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) (h * 0.07)));
        g.setColor(new Color(40, 50, 90));
        String s = "1 2 3 4 5 6 7";
        g.drawString(s, (float) ((w - g.getFontMetrics().stringWidth(s)) / 2.0), (float) (h * 0.28));
        g.setColor(new Color(255, 70, 100));
        String hint = "press";
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) (h * 0.05)));
        g.drawString(hint, (float) (left + 6 * gap - g.getFontMetrics().stringWidth(hint) / 2.0), (float) (rowY + h * 0.2));
    }
}
