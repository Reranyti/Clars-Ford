package com.lotusblight.overlay;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;

/** Finishing passes for the figures of the fight: a coloured light along the edge facing the light, and the shadow on the floor. */
final class ActorFx {
    private ActorFx() {}

    /**
     * Lights the edge of a drawn figure that faces the light. {@code lx, ly} point from the figure towards the light (any length);
     * {@code thick} is how many pixels deep the lit rim goes; {@code strength} 0..1.
     */
    /**
     * Lights the edge of a drawn figure that faces the light. {@code lx, ly} point from the figure towards the light (any length);
     * {@code thick} is how many pixels deep the lit rim goes; {@code strength} 0..1.
     */
    private static float[] scratch = new float[0];

    static void rim(SoftRenderer r, int rgb, double strength, double lx, double ly, int thick) {
        if (strength <= 0.01 || r.bx1 < r.bx0 || thick < 1) return;
        double len = Math.hypot(lx, ly);
        if (len == 0) return;
        double ux = lx / len, uy = ly / len;
        int w = r.width, h = r.height;
        int x0 = Math.max(0, r.bx0 - thick), x1 = Math.min(w - 1, r.bx1 + thick), y0 = Math.max(0, r.by0 - thick), y1 = Math.min(h - 1, r.by1 + thick);
        int[] px = r.pixels;
        int cr = (rgb >> 16) & 255, cg = (rgb >> 8) & 255, cb = rgb & 255;
        // the step towards the light, worked out once for each distance
        int[] sdx = new int[thick + 1], sdy = new int[thick + 1];
        for (int k = 1; k <= thick; k++) {
            sdx[k] = (int) Math.round(ux * k);
            sdy[k] = -(int) Math.round(uy * k);
        }
        int bw = x1 - x0 + 1, bh = y1 - y0 + 1;
        if (scratch.length < bw * bh) scratch = new float[bw * bh];
        float[] amount = scratch;
        java.util.Arrays.fill(amount, 0, bw * bh, 0f);
        // The pixels to light, found first (from the untouched picture), then painted.
        for (int y = y0; y <= y1; y++) {
            int row = y * w;
            for (int x = x0; x <= x1; x++) {
                if ((px[row + x] >>> 24) == 0) continue;
                int k = 1;
                for (; k <= thick; k++) {
                    int sx = x + sdx[k], sy = y + sdy[k];
                    if (sx < 0 || sy < 0 || sx >= w || sy >= h || (px[sy * w + sx] >>> 24) == 0) break;
                }
                if (k <= thick) amount[(y - y0) * bw + (x - x0)] = (float) (1.0 - (k - 1) / (double) thick);
            }
        }
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                float a = amount[(y - y0) * bw + (x - x0)];
                if (a <= 0) continue;
                int p = px[y * w + x];
                double t = Math.min(1, a * strength);
                int rr = (int) (((p >> 16) & 255) * (1 - t) + cr * t), gg = (int) (((p >> 8) & 255) * (1 - t) + cg * t), bb = (int) ((p & 255) * (1 - t) + cb * t);
                px[y * w + x] = (p & 0xFF000000) | rr << 16 | gg << 8 | bb;
            }
        }
    }

    /** A soft dark patch on the floor under a figure: wide when it stands on the floor, small and faint when it is high above it. */
    static void floorShadow(Graphics2D b, double cx, double floorY, double halfW, double halfH, double alpha) {
        if (alpha <= 0.01) return;
        java.awt.geom.AffineTransform old = b.getTransform();
        b.translate(cx, floorY);
        b.scale(1, halfH / halfW);
        b.setPaint(new RadialGradientPaint(0, 0, (float) halfW, new float[]{0f, 0.6f, 1f},
                new Color[]{new Color(0, 0, 0, (int) (200 * alpha)), new Color(0, 0, 0, (int) (110 * alpha)), new Color(0, 0, 0, 0)}));
        b.fill(new java.awt.geom.Ellipse2D.Double(-halfW, -halfW, halfW * 2, halfW * 2));
        b.setTransform(old);
    }
}
