package com.lotusblight.overlay;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Random;

/**
 * The old set from the endings' menu (the dark brown tube TV with its control strip and the VCR under it: client/VhsTv), drawn for the
 * overlay: the same colours, bevels, knobs and slots, and a glass with scanlines, a tracking band crawling up, snow and the dark rim of the tube.
 * It is the easter egg of the second song: the snakes of errors pour out of its screen.
 */
final class OldTv {
    private static final Color BODY = new Color(0x2A2420), BODY_LIGHT = new Color(0x4A4038), BODY_DARK = new Color(0x14100E);

    /** Where the glass will be for a set of this size and place: {x, y, w, h} (the same arithmetic as {@link #draw}). */
    static double[] glassOf(double cx, double bottom, double tvW) {
        double f = tvW / 900.0, tvH = tvW * 0.56, vcrH = tvW * 0.10;
        double vcrY = bottom - vcrH, tvY = vcrY - 6 * f - tvH, tvX = cx - tvW / 2;
        return new double[]{tvX + 18 * f, tvY + 18 * f, tvW - (36 + 74) * f, tvH - 36 * f};
    }

    /** Where the glass is after a draw: {x, y, w, h}. */
    final double[] glass = new double[4];

    /**
     * @param cx     centre of the set across
     * @param bottom where the bottom of the video deck stands
     * @param tvW    width of the set (the picture tube and the strip)
     * @param power  0..1: how far it is switched on (it opens from a line of light)
     */
    void draw(Graphics2D g0, double cx, double bottom, double tvW, double ms, double power) {
        if (power <= 0) return;
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double f = tvW / 900.0;                                   // the original is laid out for about 900 px of width
        double tvH = tvW * 0.56, vcrW = tvW * 0.60, vcrH = tvW * 0.10;
        double vcrY = bottom - vcrH, tvY = vcrY - 6 * f - tvH, tvX = cx - tvW / 2, vcrX = cx - vcrW / 2;
        double sx = tvX + 18 * f, sy = tvY + 18 * f, sw = tvW - (36 + 74) * f, sh = tvH - 36 * f;
        glass[0] = sx; glass[1] = sy; glass[2] = sw; glass[3] = sh;

        panel(g, tvX, tvY, tvX + tvW, tvY + tvH, BODY, BODY_LIGHT, BODY_DARK, f);
        // the control strip: speaker slots, two knobs, a row of buttons, the power light
        double sc = sx + sw + 18 * f;
        g.setColor(BODY_DARK);
        for (int i = 0; i < 9; i++) g.fill(new Rectangle2D.Double(sc + 4 * f, sy + (6 + i * 6) * f, 42 * f, 2 * f));
        knob(g, sc + 25 * f, sy + 86 * f, 17 * f, Math.toRadians(ms / 40 % 360), f);
        knob(g, sc + 25 * f, sy + 132 * f, 13 * f, Math.toRadians(40), f);
        for (int i = 0; i < 4; i++) panel(g, sc + (2 + i * 12) * f, sy + sh - 38 * f, sc + (12 + i * 12) * f, sy + sh - 26 * f,
                new Color(0x3A322C), new Color(0x5A5046), BODY_DARK, f);
        g.setColor(power > 0.6 ? new Color(0xE03030) : new Color(0x501010));
        g.fill(new Rectangle2D.Double(sc + 36 * f, sy + sh - 16 * f, 8 * f, 6 * f));
        // the video deck
        panel(g, vcrX, vcrY, vcrX + vcrW, vcrY + vcrH, new Color(0x1E1A18), new Color(0x3A322C), new Color(0x0C0A09), f);
        double slotW = vcrW * 0.5, slotX = vcrX + (vcrW - slotW) / 2;
        g.setColor(new Color(0x040302));
        g.fill(new Rectangle2D.Double(slotX, vcrY + vcrH / 2 - 3 * f, slotW, 7 * f));
        g.setColor(new Color(0x5A5046));
        g.fill(new Rectangle2D.Double(slotX, vcrY + vcrH / 2 - 3 * f, slotW, f));
        g.setColor(new Color(0x3A322C));
        for (int i = 0; i < 5; i++) g.fill(new Rectangle2D.Double(vcrX + (12 + i * 10) * f, vcrY + 8 * f, 6 * f, 4 * f));
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, Math.max(8, (int) (11 * f))));
        g.setColor(power > 0.6 ? new Color(0xE0A030) : new Color(0x605040));
        g.drawString("VHS", (float) (vcrX + vcrW - 38 * f), (float) (vcrY + 17 * f));

        glass(g, sx, sy, sw, sh, ms, power, f);
        g.dispose();
    }

    private static void glass(Graphics2D g0, double sx, double sy, double sw, double sh, double ms, double power, double f) {
        Graphics2D g = (Graphics2D) g0.create();
        Shape screen = new RoundRectangle2D.Double(sx, sy, sw, sh, 28 * f, 28 * f);
        g.setColor(Color.BLACK);
        g.fill(screen);
        g.clip(screen);
        // the set switching on: a line of light that opens into the picture
        double open = Math.min(1, power / 0.55);
        double half = sh / 2 * Math.max(0.01, open);
        double midY = sy + sh / 2;
        g.clip(new Rectangle2D.Double(sx, midY - half, sw, half * 2));
        Random snow = new Random((long) (ms / 60));
        g.setColor(new Color(14, 16, 40));
        g.fill(new Rectangle2D.Double(sx, sy, sw, sh));
        for (int i = 0; i < 300; i++) {                                    // the snow of a tape that is not playing
            double x = sx + snow.nextDouble() * sw, y = sy + snow.nextDouble() * sh;
            g.setColor(new Color(255, 255, 255, 40 + snow.nextInt(110)));
            g.fill(new Rectangle2D.Double(x, y, (1 + snow.nextInt(3)) * f * 1.4, f * 1.4));
        }
        // scanlines, a tracking band crawling up, torn lines
        g.setColor(new Color(0, 0, 0, 80));
        for (double y = sy; y < sy + sh; y += 2 * f * 1.6) g.fill(new Rectangle2D.Double(sx, y, sw, f * 1.6));
        double band = sy + sh - ((ms / 14.0 * f) % (sh + 80 * f));
        g.setColor(new Color(255, 255, 255, 40));
        g.fill(new Rectangle2D.Double(sx, band, sw, 5 * f));
        Random rnd = new Random((long) (ms / 90));
        for (int i = 0; i < 14; i++) {
            double y = band + (rnd.nextInt(14) - 4) * f, x0 = sx + rnd.nextDouble() * sw;
            g.setColor(new Color(255, 255, 255, 90));
            g.fill(new Rectangle2D.Double(x0, y, Math.min(sx + sw - x0, (20 + rnd.nextInt(120)) * f), f));
        }
        if (open >= 1) {
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, Math.max(9, (int) (13 * f))));
            g.setColor(new Color(224, 224, 224));
            g.drawString("▶ PLAY", (float) (sx + sw - 62 * f), (float) (sy + 22 * f));
            g.drawString("SP", (float) (sx + 12 * f), (float) (sy + 22 * f));
        }
        // the tube: dark edges and corners
        g.setClip(screen);
        int steps = 22;
        for (int i = 0; i < steps; i++) {
            int a = (int) (110 * Math.pow(1 - i / (double) steps, 2.2));
            g.setColor(new Color(0, 0, 0, a));
            double k = i * f;
            g.fill(new Rectangle2D.Double(sx, sy + k, sw, f));
            g.fill(new Rectangle2D.Double(sx, sy + sh - k - f, sw, f));
            g.fill(new Rectangle2D.Double(sx + k, sy, f, sh));
            g.fill(new Rectangle2D.Double(sx + sw - k - f, sy, f, sh));
        }
        g.dispose();
    }

    private static void panel(Graphics2D g, double x0, double y0, double x1, double y1, Color base, Color light, Color dark, double f) {
        double c = 3 * f;
        g.setColor(base);
        g.fill(new Rectangle2D.Double(x0 + c, y0, x1 - x0 - 2 * c, y1 - y0));
        g.fill(new Rectangle2D.Double(x0, y0 + c, x1 - x0, y1 - y0 - 2 * c));
        g.setColor(light);
        g.fill(new Rectangle2D.Double(x0 + c, y0, x1 - x0 - 2 * c, 2 * f));
        g.fill(new Rectangle2D.Double(x0, y0 + c, 2 * f, y1 - y0 - 2 * c));
        g.setColor(dark);
        g.fill(new Rectangle2D.Double(x0 + c, y1 - 2 * f, x1 - x0 - 2 * c, 2 * f));
        g.fill(new Rectangle2D.Double(x1 - 2 * f, y0 + c, 2 * f, y1 - y0 - 2 * c));
    }

    private static void knob(Graphics2D g, double cx, double cy, double r, double a, double f) {
        g.setColor(BODY_DARK);
        g.fill(new Ellipse2D.Double(cx - r - 2 * f, cy - r - 2 * f, (r + 2 * f) * 2, (r + 2 * f) * 2));
        g.setColor(new Color(0x3A322C));
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        g.setColor(new Color(0x4A4038));
        g.fill(new Ellipse2D.Double(cx - r + 3 * f, cy - r + 3 * f, (r - 3 * f) * 2, (r - 3 * f) * 2));
        g.setColor(new Color(0xD0C8B0));
        g.setStroke(new java.awt.BasicStroke((float) (2 * f)));
        g.draw(new java.awt.geom.Line2D.Double(cx, cy, cx + Math.cos(a) * (r - 2 * f), cy + Math.sin(a) * (r - 2 * f)));
    }
}
