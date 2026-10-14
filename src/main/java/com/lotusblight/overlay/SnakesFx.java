package com.lotusblight.overlay;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The snakes of errors: on the fast runs of the second song (snakes.txt: the moments, in seconds, the author marked by ear) a real, huge
 * snake pours out of the Glitcher's head and winds across the whole screen: a living body that narrows to a tail, a wedge head with eyes,
 * jaws and a forked tongue. It is made OF errors, not of anything tidy: scales of flickering glitch colours, the red and cyan of a torn signal
 * split off its edges, horizontal slices of it jumping sideways, and it moves in stutters, as a frozen picture does.
 * A picture only: nothing to click, no damage.
 */
final class SnakesFx {
    /** Where the snakes come from at a given moment: {x, y} in screen pixels. */
    interface Origin {
        double[] at(double ms);
    }

    private static final double LIFE_MS = 2_400;
    private static final int SAMPLES = 48;

    private final List<Double> marks = new ArrayList<>();
    private BufferedImage buf;

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

    /** One snake: where it starts, the way it goes, how it winds, how fast. */
    private record Shape(double x0, double y0, double dx, double dy, double amp, double wave, double phase, double speed, double girth, long seed) {}

    private static Shape shape(double[] o, int w, int h, double mark, int idx) {
        Random rnd = new Random((long) (mark * 7) + idx * 131L);
        double tx = o[0] < w / 2.0 ? w * (0.9 + 0.4 * rnd.nextDouble()) : -w * (0.15 + 0.3 * rnd.nextDouble());
        double ty = h * (0.12 + 0.76 * rnd.nextDouble());
        double dx = tx - o[0], dy = ty - o[1], len = Math.max(1, Math.hypot(dx, dy));
        return new Shape(o[0], o[1], dx / len, dy / len, h * (0.14 + 0.10 * rnd.nextDouble()), h * (0.8 + 0.5 * rnd.nextDouble()),
                rnd.nextDouble() * Math.PI * 2, w * (0.95 + 0.4 * rnd.nextDouble()) / 1000.0, h * (0.17 + 0.05 * rnd.nextDouble()), (long) (mark * 13) + idx);
    }

    /** The point of the snake at distance s behind its start along the way: the straight line, swayed from side to side. */
    private static double[] at(Shape sh, double s) {
        double grow = Math.min(1, Math.max(0, s) / (sh.wave * 0.7));
        double side = sh.amp * grow * Math.sin(s / sh.wave * Math.PI * 2 + sh.phase);
        return new double[]{sh.x0 + sh.dx * s - sh.dy * side, sh.y0 + sh.dy * s + sh.dx * side};
    }

    void render(Graphics2D g, int w, int h, double ms, Origin origin) {
        List<Integer> live = new ArrayList<>();
        for (int m = 0; m < marks.size(); m++) {
            double age = ms - marks.get(m);
            if (age >= 0 && age <= LIFE_MS) live.add(m);
        }
        if (live.isEmpty()) return;
        if (buf == null || buf.getWidth() != w || buf.getHeight() != h) buf = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D b = buf.createGraphics();
        b.setComposite(AlphaComposite.Clear);
        b.fillRect(0, 0, w, h);
        b.setComposite(AlphaComposite.SrcOver);
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (int m : live) drawSnake(b, w, h, ms, marks.get(m), origin, m % 2);
        b.dispose();
        composite(g, w, h, ms);
    }

    private void drawSnake(Graphics2D b, int w, int h, double ms, double mark, Origin origin, int idx) {
        double age = ms - mark;
        Shape sh = shape(origin.at(mark), w, h, mark, idx);
        double bodyLen = w * 1.35;
        double stutter = Math.floor(age / 48.0) * 48.0;                      // it moves in jerks, like a picture that keeps freezing
        double head = sh.speed * stutter;
        double life = age < LIFE_MS - 450 ? 1 : 1 - (age - (LIFE_MS - 450)) / 450.0;
        if (life <= 0) return;

        double[][] c = new double[SAMPLES][];       // the centre line, head first
        double[][] n = new double[SAMPLES][];       // its normals
        double[] half = new double[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            double u = i / (double) (SAMPLES - 1);                          // 0 at the head, 1 at the tail
            double s = head - u * bodyLen;
            c[i] = at(sh, Math.max(-bodyLen * 0.02, s));
            double[] q = at(sh, Math.max(-bodyLen * 0.02, s) + 4);
            double tx = q[0] - c[i][0], ty = q[1] - c[i][1], l = Math.max(1e-6, Math.hypot(tx, ty));
            n[i] = new double[]{-ty / l, tx / l};
            double neck = u < 0.06 ? 0.55 + 0.45 * (u / 0.06) : 1;          // the neck is narrower than the head, the body fattest a little behind it
            half[i] = sh.girth * (0.35 + 0.65 * Math.pow(1 - u, 0.8)) * (0.9 + 0.35 * Math.sin(Math.PI * Math.min(1, u * 1.8))) * neck * 0.5 * (u > 0.97 ? (1 - u) / 0.03 : 1);
        }
        // the part of the body that has not come out of the head yet is not drawn
        int first = 0;
        while (first < SAMPLES && head - first / (double) (SAMPLES - 1) * bodyLen < 0) first++;
        if (SAMPLES - first < 3) return;

        Path2D.Double body = new Path2D.Double();
        for (int i = first; i < SAMPLES; i++) {
            double px = c[i][0] + n[i][0] * half[i], py = c[i][1] + n[i][1] * half[i];
            if (i == first) body.moveTo(px, py); else body.lineTo(px, py);
        }
        for (int i = SAMPLES - 1; i >= first; i--) body.lineTo(c[i][0] - n[i][0] * half[i], c[i][1] - n[i][1] * half[i]);
        body.closePath();

        float fl = (float) Math.max(0, Math.min(1, life));
        // the colours of a torn signal split off the edges
        b.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f * fl));
        java.awt.geom.AffineTransform keep = b.getTransform();
        b.translate(-w * 0.006, 0);
        b.setColor(new Color(255, 40, 70));
        b.fill(body);
        b.setTransform(keep);
        b.translate(w * 0.006, 0);
        b.setColor(new Color(40, 220, 255));
        b.fill(body);
        b.setTransform(keep);
        b.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fl));
        b.setColor(new Color(14, 6, 36));
        b.fill(body);

        // scales: diamonds of flickering glitch colours down the body
        java.awt.Shape oldClip = b.getClip();
        b.clip(body);
        Random rnd = new Random(sh.seed);
        long flick = (long) (ms / 70);
        for (int i = first; i < SAMPLES - 1; i += 1) {
            for (int k = -2; k <= 2; k++) {
                double lat = k / 2.0 * 0.8;
                double cx = c[i][0] + n[i][0] * half[i] * lat, cy = c[i][1] + n[i][1] * half[i] * lat;
                int pick = (int) ((rnd.nextInt(1000) + flick * (rnd.nextInt(4) == 0 ? 1 : 0) * 37L) % 1000);
                Color col;
                if (pick < 560) col = new Color(60 + rnd.nextInt(40), 24 + rnd.nextInt(30), 140 + rnd.nextInt(80));          // deep violet
                else if (pick < 760) col = new Color(115, 0, 255);
                else if (pick < 880) col = new Color(178, 92, 255);
                else if (pick < 940) col = new Color(255, 255, 255);
                else if (pick < 975) col = new Color(40, 220, 255);
                else col = new Color(255, 40, 70);
                double r = Math.max(2, half[i] * 0.44);
                Path2D.Double d = new Path2D.Double();
                d.moveTo(cx, cy - r);
                d.lineTo(cx + r, cy);
                d.lineTo(cx, cy + r);
                d.lineTo(cx - r, cy);
                d.closePath();
                b.setColor(col);
                b.fill(d);
            }
        }
        b.setClip(oldClip);
        b.setStroke(new BasicStroke(Math.max(1.5f, (float) (sh.girth * 0.04))));
        b.setColor(new Color(220, 200, 255, (int) (200 * fl)));
        b.draw(body);

        drawHead(b, c[first], c[Math.min(SAMPLES - 1, first + 3)], sh.girth, age, fl);
    }

    /** The wedge of the head, the eyes, the open jaws and the tongue, all turned the way the snake goes. */
    private static void drawHead(Graphics2D b, double[] p, double[] behind, double girth, double age, float fl) {
        double dx = p[0] - behind[0], dy = p[1] - behind[1], l = Math.max(1e-6, Math.hypot(dx, dy));
        double ux = dx / l, uy = dy / l, nx = -uy, ny = ux;
        double len = girth * 1.5, wd = girth * 0.62;
        java.awt.geom.AffineTransform keep = b.getTransform();
        b.translate(p[0], p[1]);
        b.rotate(Math.atan2(uy, ux));
        // the head: a wedge, wide at the back of the jaw and drawn to a snout
        Path2D.Double head = new Path2D.Double();
        head.moveTo(len * 0.9, 0);
        head.curveTo(len * 0.6, -wd * 0.55, len * 0.2, -wd, -len * 0.1, -wd * 0.7);
        head.lineTo(-len * 0.1, wd * 0.7);
        head.curveTo(len * 0.2, wd, len * 0.6, wd * 0.55, len * 0.9, 0);
        head.closePath();
        b.setColor(new Color(40, 220, 255, (int) (160 * fl)));
        b.translate(0, -girth * 0.05);
        b.fill(head);
        b.translate(0, girth * 0.1);
        b.setColor(new Color(255, 40, 70, (int) (160 * fl)));
        b.fill(head);
        b.translate(0, -girth * 0.05);
        b.setColor(new Color(20, 8, 48, (int) (255 * fl)));
        b.fill(head);
        b.setColor(new Color(220, 200, 255, (int) (210 * fl)));
        b.setStroke(new BasicStroke(Math.max(1.5f, (float) (girth * 0.04))));
        b.draw(head);
        // the jaws: a bright seam with teeth
        b.setColor(new Color(255, 255, 255, (int) (230 * fl)));
        b.draw(new Line2D.Double(len * 0.1, 0, len * 0.88, 0));
        for (int i = 0; i < 5; i++) {
            double x = len * (0.25 + 0.14 * i);
            b.fill(new Path2D.Double(new java.awt.geom.Rectangle2D.Double(x, 0, girth * 0.045, girth * 0.12 * (1 - i * 0.1))));
            b.fill(new Path2D.Double(new java.awt.geom.Rectangle2D.Double(x, -girth * 0.12 * (1 - i * 0.1), girth * 0.045, girth * 0.12 * (1 - i * 0.1))));
        }
        // the eyes: they flare white and the pupils are slits
        boolean blink = ((int) (age / 130)) % 7 == 3;
        for (int s = -1; s <= 1; s += 2) {
            double ex = len * 0.42, ey = s * wd * 0.42, er = girth * 0.12;
            b.setColor(new Color(255, 255, 255, (int) (255 * fl)));
            b.fill(new Ellipse2D.Double(ex - er, ey - er * (blink ? 0.15 : 1), er * 2, er * 2 * (blink ? 0.15 : 1)));
            if (!blink) {
                b.setColor(new Color(255, 30, 60, (int) (255 * fl)));
                b.fill(new Ellipse2D.Double(ex - er * 0.18, ey - er * 0.8, er * 0.36, er * 1.6));
            }
        }
        // the tongue, forked, flickering
        if (((int) (age / 80)) % 3 != 2) {
            double t0 = len * 0.9, tl = girth * 0.9;
            b.setColor(new Color(255, 40, 70, (int) (255 * fl)));
            b.setStroke(new BasicStroke((float) (girth * 0.05), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            b.draw(new Line2D.Double(t0, 0, t0 + tl * 0.7, 0));
            b.draw(new Line2D.Double(t0 + tl * 0.7, 0, t0 + tl, -girth * 0.16));
            b.draw(new Line2D.Double(t0 + tl * 0.7, 0, t0 + tl, girth * 0.16));
        }
        b.setTransform(keep);
    }

    /** The picture of the snakes goes to the screen in horizontal slices, a few of them thrown sideways: the tear of a bad signal. */
    private void composite(Graphics2D g, int w, int h, double ms) {
        Random rnd = new Random((long) (ms / 60));
        int strips = 22, sh = h / strips + 1;
        Graphics2D o = (Graphics2D) g.create();
        for (int i = 0; i < strips; i++) {
            int y = i * sh;
            int dx = rnd.nextInt(5) == 0 ? (int) ((rnd.nextDouble() - 0.5) * w * 0.09) : 0;
            o.drawImage(buf, dx, y, dx + w, y + sh, 0, y, w, Math.min(h, y + sh), null);
        }
        o.dispose();
    }
}
