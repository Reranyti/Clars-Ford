package com.lotusblight.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * The Glitcher puts on faces: he holds a cardboard card with a character on it in front of his face with one hand and the next card at the middle of
 * his body with the other, and changes them over: the upper hand brings its card down, the lower one lifts the next up to the face, and so on. The
 * card is drawn in code (cardboard, a folded corner, a shadow); what is printed on it is a picture the author supplies (here a coloured placeholder
 * with a name). Everything is a function of time, so that it can be scrubbed.
 */
final class MaskCards {
    static final String[] NAMES = {"DOORS", "FORSAKEN", "ENA", "FRISK", "ISAAC", "???"};
    private static final Color[] HUES = {new Color(230, 190, 90), new Color(120, 20, 30), new Color(250, 250, 250), new Color(250, 120, 160), new Color(150, 90, 60), new Color(60, 60, 70)};

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    /** How far through the changing over of the cards it is at the time: the card number that is up at the face, and 0..1 of the swap (0 outside it). */
    static double[] swap(double t, double period, double swapTime) {
        int n = (int) Math.floor(Math.max(0, t) / period);
        double ph = t - n * period;
        double u = ph < period - swapTime ? 0 : (ph - (period - swapTime)) / swapTime;
        return new double[]{n, smooth(u)};
    }

    /**
     * Draws both cards: the one at the face and the one at the middle of the body, with the hands that hold them where they are on the screen.
     * {@code face} is {x, y, u}; {@code upper} and {@code lower} are the screen points of the two fists ({x, y}).
     */
    static void draw(Graphics2D g, double[] face, double[] handUp, double[] handDown, double t, double period, double swapTime) {
        if (t < 0) return;
        double[] sw = swap(t, period, swapTime);
        int n = (int) sw[0];
        double s = sw[1];
        double u = face[2];
        // the card in the lower hand is held by its bottom edge, so its middle is above the fist
        double dx = handDown[0], dy = handDown[1] - u * 4.5;
        // the one that is going down, and the one coming up, cross in the middle of the swap
        double upX = face[0] + (dx - face[0]) * s, upY = face[1] + (dy - face[1]) * s;
        double loX = dx + (face[0] - dx) * s, loY = dy + (face[1] - dy) * s;
        double upRot = Math.toRadians(-6) + s * Math.toRadians(14), loRot = Math.toRadians(9) - s * Math.toRadians(15);
        // the lower one first (it is behind), the one in front of the face last
        boolean frontIsUp = s < 0.5;
        if (frontIsUp) {
            card(g, loX, loY, u * (0.8 + 0.2 * s), n + 1, loRot, 0.9);
            card(g, upX, upY, u, n, upRot, 1);
        } else {
            card(g, upX, upY, u, n, upRot, 1);
            card(g, loX, loY, u * (0.8 + 0.2 * s), n + 1, loRot, 1);
        }
    }

    private static final java.util.Map<Integer, java.awt.image.BufferedImage> prints = new java.util.HashMap<>();

    /** The picture printed on card number {@code i} (cards/card_i.png in the jar, supplied by the author), or null: then the card shows a placeholder. */
    private static synchronized java.awt.image.BufferedImage print(int i) {
        if (!prints.containsKey(i)) {
            java.awt.image.BufferedImage img = null;
            try (java.io.InputStream in = MaskCards.class.getResourceAsStream("/assets/lotusblight/overlay/cards/card_" + i + ".png")) {
                if (in != null) img = pencil(javax.imageio.ImageIO.read(in));
            } catch (java.io.IOException ignored) {
                // no picture: the placeholder
            }
            prints.put(i, img);
        }
        return prints.get(i);
    }

    /**
     * Turns a flat picture into something drawn with coloured pencils on cardboard: the white background goes (it is the paper), the colours are
     * washed out and laid in as uneven hatching, and the outlines are graphite strokes. The result has transparency, so the cardboard shows through.
     */
    static java.awt.image.BufferedImage pencil(java.awt.image.BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        int[] px = src.getRGB(0, 0, w, h, null, 0, w);
        boolean[] bg = new boolean[w * h];
        java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
        for (int x = 0; x < w; x++) { q.add(x); q.add((h - 1) * w + x); }
        for (int y = 0; y < h; y++) { q.add(y * w); q.add(y * w + w - 1); }
        while (!q.isEmpty()) {                                                                  // the background: near-white, reached from the edge
            int i = q.poll();
            if (bg[i]) continue;
            int c = px[i];
            if (((c >> 24) & 255) > 20 && ((c >> 16) & 255) + ((c >> 8) & 255) + (c & 255) < 3 * 232) continue;
            bg[i] = true;
            int x = i % w, y = i / w;
            if (x > 0) q.add(i - 1);
            if (x < w - 1) q.add(i + 1);
            if (y > 0) q.add(i - w);
            if (y < h - 1) q.add(i + w);
        }
        double[] lum = new double[w * h];
        for (int i = 0; i < lum.length; i++) {
            int c = px[i];
            lum[i] = (0.3 * ((c >> 16) & 255) + 0.59 * ((c >> 8) & 255) + 0.11 * (c & 255)) / 255.0;
        }
        java.util.Random rnd = new java.util.Random(7);
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (bg[i]) continue;
                double gx = 0, gy = 0;
                if (x > 0 && x < w - 1 && y > 0 && y < h - 1) {
                    gx = lum[i + 1] - lum[i - 1];
                    gy = lum[i + w] - lum[i - w];
                }
                double edge = Math.min(1, Math.hypot(gx, gy) * 3.2);
                int c = px[i];
                double r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
                double dark = 1 - lum[i];
                // coloured pencil: weaker than the picture, with diagonal hatching that thins out in the light parts
                double hatch = ((x + y) % 5 < 2 ? 1.0 : 0.45) * (0.75 + 0.25 * rnd.nextDouble());
                double cover = Math.min(1, (0.28 + 0.55 * (1 - lum[i] * 0.6)) * hatch);
                r = 150 + (r - 150) * 0.8;
                g = 140 + (g - 140) * 0.8;
                b = 120 + (b - 120) * 0.8;
                // graphite on the outlines, a little broken
                double line = edge * (0.6 + 0.4 * rnd.nextDouble()) * (rnd.nextDouble() < 0.9 ? 1 : 0.3);
                double a = Math.max(cover * 0.8, line);
                double k = Math.min(1, line * 1.2);
                int rr = (int) (r * (1 - k) + 45 * k), gg = (int) (g * (1 - k) + 42 * k), bb = (int) (b * (1 - k) + 48 * k);
                // heavy darks (the eye patch, the mouth) stay dark graphite
                if (dark > 0.85) { rr = 50; gg = 46; bb = 52; a = Math.max(a, 0.9 * (0.8 + 0.2 * rnd.nextDouble())); }
                out.setRGB(x, y, ((int) (Math.max(0, Math.min(1, a)) * 255) << 24) | (rr << 16) | (gg << 8) | bb);
            }
        }
        return out;
    }

    /** A hand-drawn frame in pencil round the print: doubled, wobbling, not quite closing. */
    private static void pencilFrame(Graphics2D c, double pw, double ph, double u, int idx) {
        java.util.Random rnd = new java.util.Random(idx * 31L + 5);
        c.setColor(new Color(60, 54, 58, 170));
        c.setStroke(new BasicStroke((float) Math.max(1.0, u * 0.18), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int pass = 0; pass < 2; pass++) {
            Path2D.Double p = new Path2D.Double();
            double[][] corner = {{-pw / 2, -ph / 2}, {pw / 2, -ph / 2}, {pw / 2, ph / 2}, {-pw / 2, ph / 2}, {-pw / 2, -ph / 2}};
            double jx = u * 0.2;
            p.moveTo(corner[0][0] + (rnd.nextDouble() - 0.5) * jx * 3, corner[0][1] + (rnd.nextDouble() - 0.5) * jx * 3);
            for (int e = 0; e < 4; e++) {
                for (int s = 1; s <= 6; s++) {
                    double f = s / 6.0;
                    p.lineTo(corner[e][0] + (corner[e + 1][0] - corner[e][0]) * f + (rnd.nextDouble() - 0.5) * jx,
                            corner[e][1] + (corner[e + 1][1] - corner[e][1]) * f + (rnd.nextDouble() - 0.5) * jx);
                }
            }
            c.draw(p);
        }
    }

    /** One card, centred at (cx, cy), {@code u} the size of a skin unit there. */
    static void card(Graphics2D g, double cx, double cy, double u, int idx, double rot, double alpha) {
        double w = u * 8.4, h = u * 10.0;
        Graphics2D c = (Graphics2D) g.create();
        c.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        c.translate(cx, cy);
        c.rotate(rot);
        c.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, (float) Math.max(0, Math.min(1, alpha))));
        c.setColor(new Color(0, 0, 0, 105));                                                    // the shadow
        c.fill(new RoundRectangle2D.Double(-w / 2 + u * 0.35, -h / 2 + u * 0.45, w, h, 6, 6));
        c.setColor(new Color(196, 160, 112));                                                   // the cardboard
        c.fill(new RoundRectangle2D.Double(-w / 2, -h / 2, w, h, 6, 6));
        double m = u * 0.55;
        Color hue = HUES[Math.floorMod(idx, HUES.length)];
        java.awt.image.BufferedImage pic = print(Math.floorMod(idx, NAMES.length));
        if (pic != null) {                                                                      // the author's drawing, redone in pencil straight on the cardboard
            double pw = w - 2 * m, ph = h - 2 * m, sc = Math.min(pw / pic.getWidth(), ph / pic.getHeight());
            c.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            c.drawImage(pic, (int) (-pic.getWidth() * sc / 2), (int) (-pic.getHeight() * sc / 2), (int) (pic.getWidth() * sc), (int) (pic.getHeight() * sc), null);
            pencilFrame(c, w - 2 * m, h - 2 * m, u, idx);
            c.dispose();
            return;
        }
        c.setPaint(new GradientPaint(0, (float) (-h / 2), hue, 0, (float) (h / 2), hue.darker().darker()));
        c.fill(new Rectangle2D.Double(-w / 2 + m, -h / 2 + m, w - 2 * m, h - 2 * m));
        boolean light = hue.getRed() + hue.getGreen() + hue.getBlue() > 480;                    // the placeholder print: eyes, a mouth, the name
        c.setColor(light ? new Color(30, 30, 40) : new Color(250, 240, 230));
        c.fill(new Ellipse2D.Double(-w * 0.24, -h * 0.16, w * 0.14, w * 0.14));
        c.fill(new Ellipse2D.Double(w * 0.10, -h * 0.16, w * 0.14, w * 0.14));
        c.setStroke(new BasicStroke((float) Math.max(1.5, u * 0.3), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        c.draw(new Arc2D.Double(-w * 0.2, h * 0.02, w * 0.4, h * 0.22, 200, 140, Arc2D.OPEN));
        c.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.max(8, u * 1.15)));
        FontMetrics fm = c.getFontMetrics();
        String name = NAMES[Math.floorMod(idx, NAMES.length)];
        c.drawString(name, (float) (-fm.stringWidth(name) / 2.0), (float) (h / 2 - m - u * 0.7));
        c.setColor(new Color(0, 0, 0, 45));                                                     // the crease of a bent corner
        Path2D.Double fold = new Path2D.Double();
        fold.moveTo(w / 2 - u * 2.2, -h / 2);
        fold.lineTo(w / 2, -h / 2 + u * 2.2);
        fold.lineTo(w / 2, -h / 2);
        fold.closePath();
        c.fill(fold);
        c.dispose();
    }
}
