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
