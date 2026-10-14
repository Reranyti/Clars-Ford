package com.lotusblight.overlay;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The snakes of errors: on the fast runs of the second song (snakes.txt: the moments, in seconds, the author marked by ear) chains of glitching
 * squares shoot out of the Glitcher's head and race over the screen, turning at right angles, their heads little error windows.
 * They replace the error windows that used to be thrown under the music. They are only a picture: nothing to click, no damage.
 */
final class SnakesFx {
    /** Where the snakes come from at a given moment: {x, y} in screen pixels. */
    interface Origin {
        double[] at(double ms);
    }

    private static final double LIFE_MS = 1_500;
    private static final int PER_MARK = 2;
    private static final int[] PALETTE = {0x7300FF, 0xB25CFF, 0xFFFFFF, 0x9A6BFF, 0xFF3A55};

    private final List<Double> marks = new ArrayList<>();

    SnakesFx() {
        try (InputStream in = SnakesFx.class.getResourceAsStream("/assets/lotusblight/overlay/snakes.txt")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    marks.add(Double.parseDouble(line.trim().split("\\s+")[0]) * 1000);
                }
            }
        } catch (Exception ignored) {
            // no marks: no snakes
        }
    }

    /** The corners of a path: it starts at (x0, y0), runs along one axis, turns a right angle, and so on until it has left the screen. */
    private static List<double[]> path(double x0, double y0, int w, int h, Random rnd) {
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[]{x0, y0});
        double x = x0, y = y0;
        // first away from the nearer edge, so that it has room to run
        int dir = rnd.nextBoolean() ? (x0 < w / 2.0 ? 0 : 2) : (y0 < h / 2.0 ? 1 : 3);     // 0 right, 1 down, 2 left, 3 up
        for (int k = 0; k < 10; k++) {
            boolean horiz = dir % 2 == 0;
            double len = horiz ? w * (0.08 + 0.22 * rnd.nextDouble()) : h * (0.12 + 0.34 * rnd.nextDouble());
            if (dir == 0) x += len; else if (dir == 2) x -= len; else if (dir == 1) y += len; else y -= len;
            pts.add(new double[]{x, y});
            if (x < -w * 0.05 || x > w * 1.05 || y < -h * 0.05 || y > h * 1.05) break;
            dir = (dir + (rnd.nextBoolean() ? 1 : 3)) % 4;
        }
        return pts;
    }

    /** The point at a distance along the path, and the way it is heading. */
    private static double[] along(List<double[]> pts, double dist) {
        double left = Math.max(0, dist);
        for (int i = 1; i < pts.size(); i++) {
            double[] a = pts.get(i - 1), b = pts.get(i);
            double len = Math.abs(b[0] - a[0]) + Math.abs(b[1] - a[1]);
            if (left <= len || i == pts.size() - 1) {
                double f = len == 0 ? 0 : Math.min(1, left / len);
                return new double[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, b[0] - a[0], b[1] - a[1]};
            }
            left -= len;
        }
        double[] e = pts.get(pts.size() - 1);
        return new double[]{e[0], e[1], 0, 0};
    }

    void render(Graphics2D g, int w, int h, double ms, Origin origin) {
        Graphics2D b = null;
        for (double mark : marks) {
            double age = ms - mark;
            if (age < 0 || age > LIFE_MS) continue;
            if (b == null) {
                b = (Graphics2D) g.create();
                b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            }
            double[] o = origin.at(mark);
            for (int s = 0; s < PER_MARK; s++) {
                Random rnd = new Random((long) (mark * 7) + s * 131L);
                List<double[]> pts = path(o[0], o[1], w, h, rnd);
                double speed = w * (1.5 + 0.8 * rnd.nextDouble()) / 1000.0;           // px per ms
                double trail = w * (0.22 + 0.16 * rnd.nextDouble());
                double cell = h * 0.03;
                double head = speed * age;
                Random jit = new Random((long) (ms / 35) + s);
                for (double d = head; d > Math.max(-cell, head - trail); d -= cell) {
                    double[] p = along(pts, d);
                    double fall = 1 - (head - d) / trail;                                // 1 at the head, 0 at the tail
                    double life = 1 - Math.pow(age / LIFE_MS, 3);
                    int alpha = (int) Math.max(0, Math.min(255, 255 * fall * life));
                    if (alpha < 8) continue;
                    int c = PALETTE[Math.floorMod((int) (d / cell) + s * 2, PALETTE.length)];
                    double jx = (jit.nextDouble() - 0.5) * cell * 0.5, jy = (jit.nextDouble() - 0.5) * cell * 0.5;
                    double sz = cell * (0.55 + 0.45 * fall);
                    b.setColor(new Color((c >> 16) & 255, (c >> 8) & 255, c & 255, alpha));
                    b.fill(new Rectangle2D.Double(p[0] - sz / 2 + jx, p[1] - sz / 2 + jy, sz, sz));
                }
                if (head >= 0) drawHead(b, along(pts, head), h, 1 - Math.pow(age / LIFE_MS, 3), jit);
            }
        }
        if (b != null) b.dispose();
    }

    /** The head: a little error window (pale body, blue title bar, a red button). */
    private static void drawHead(Graphics2D b, double[] p, int h, double life, Random jit) {
        double ww = h * 0.075, hh = h * 0.055;
        double x = p[0] - ww / 2 + (jit.nextDouble() - 0.5) * 4, y = p[1] - hh / 2 + (jit.nextDouble() - 0.5) * 4;
        int a = (int) Math.max(0, Math.min(255, 255 * life));
        b.setColor(new Color(237, 228, 246, a));
        b.fill(new Rectangle2D.Double(x, y, ww, hh));
        b.setColor(new Color(46, 92, 224, a));
        b.fill(new Rectangle2D.Double(x, y, ww, hh * 0.28));
        b.setColor(new Color(216, 48, 48, a));
        b.fill(new Rectangle2D.Double(x + ww * 0.82, y + hh * 0.04, ww * 0.15, hh * 0.2));
        b.setColor(new Color(60, 50, 90, a));
        b.fill(new Rectangle2D.Double(x + ww * 0.12, y + hh * 0.5, ww * 0.5, hh * 0.08));
        b.fill(new Rectangle2D.Double(x + ww * 0.12, y + hh * 0.68, ww * 0.34, hh * 0.08));
    }
}
