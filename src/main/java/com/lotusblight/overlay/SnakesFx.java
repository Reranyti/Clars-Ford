package com.lotusblight.overlay;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The snakes of errors: on the fast runs of the second song (snakes.txt: the moments, in seconds, the author marked by ear) a huge snake
 * made of error windows pours out of the Glitcher's head and winds across the whole screen. Every link of its body is an error window,
 * big at the head and smaller towards the tail; the head has eyes and a tongue.
 * They replace the error windows that used to be thrown under the music. They are only a picture: nothing to click, no damage.
 */
final class SnakesFx {
    /** Where the snakes come from at a given moment: {x, y} in screen pixels. */
    interface Origin {
        double[] at(double ms);
    }

    private static final double LIFE_MS = 2_300;
    private static final int LINKS = 17;

    private final List<Double> marks = new ArrayList<>();
    private static List<BufferedImage> windows;

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

    private static List<BufferedImage> windows() {
        if (windows == null) {
            List<BufferedImage> base = GlitcherActor.errorWindowSprites();
            windows = new ArrayList<>();
            for (BufferedImage b : base) {
                BufferedImage pre = new BufferedImage(b.getWidth(), b.getHeight(), BufferedImage.TYPE_INT_ARGB_PRE);   // the fast kind to draw turned and scaled
                Graphics2D g = pre.createGraphics();
                g.drawImage(b, 0, 0, null);
                g.dispose();
                windows.add(pre);
            }
        }
        return windows;
    }

    /** The geometry of one snake: where it starts, the way it goes, how it winds. */
    private record Shape(double x0, double y0, double dx, double dy, double amp, double wave, double phase, double speed, long seed) {}

    private static Shape shape(double[] o, int w, int h, double mark, int idx) {
        Random rnd = new Random((long) (mark * 7) + idx * 131L);
        // across the screen: towards a point on the far side from where it starts, so that it crosses the whole picture
        double tx = o[0] < w / 2.0 ? w * (0.8 + 0.4 * rnd.nextDouble()) : -w * (0.1 + 0.3 * rnd.nextDouble());
        double ty = h * (0.1 + 0.8 * rnd.nextDouble());
        double dx = tx - o[0], dy = ty - o[1], len = Math.max(1, Math.hypot(dx, dy));
        return new Shape(o[0], o[1], dx / len, dy / len, h * (0.16 + 0.10 * rnd.nextDouble()), h * (0.75 + 0.5 * rnd.nextDouble()),
                rnd.nextDouble() * Math.PI * 2, w * (1.05 + 0.4 * rnd.nextDouble()) / 1000.0, (long) (mark * 13) + idx);
    }

    /** The point of its body at distance s along its way: the straight line from the head's start, swayed from side to side. */
    private static double[] at(Shape sh, double s) {
        double grow = Math.min(1, Math.max(0, s) / (sh.wave * 0.9));                  // it leaves the head straight, then starts to wind
        double side = sh.amp * grow * Math.sin(s / sh.wave * Math.PI * 2 + sh.phase);
        double px = -sh.dy, py = sh.dx;
        return new double[]{sh.x0 + sh.dx * s + px * side, sh.y0 + sh.dy * s + py * side};
    }

    void render(Graphics2D g, int w, int h, double ms, Origin origin) {
        Graphics2D b = null;
        for (int m = 0; m < marks.size(); m++) {
            double mark = marks.get(m), age = ms - mark;
            if (age < 0 || age > LIFE_MS) continue;
            if (b == null) {
                b = (Graphics2D) g.create();
                b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                b.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            }
            Shape sh = shape(origin.at(mark), w, h, mark, m % 2);
            double spacing = h * 0.14;
            double head = sh.speed * age;
            double life = age < LIFE_MS - 400 ? 1 : 1 - (age - (LIFE_MS - 400)) / 400.0;
            List<BufferedImage> pics = windows();
            Random rnd = new Random(sh.seed);
            // the tail first, the head on top
            for (int j = LINKS - 1; j >= 0; j--) {
                BufferedImage pic = pics.get(rnd.nextInt(pics.size()));
                double s = head - j * spacing;
                if (s < -spacing * 0.5) continue;                          // this link has not come out of the head yet
                double[] p = at(sh, s), q = at(sh, s + h * 0.02);
                double ang = Math.atan2(q[1] - p[1], q[0] - p[0]);
                double taper = 1 - 0.58 * j / (double) (LINKS - 1);
                double width = h * (j == 0 ? 0.56 : 0.42) * taper;
                double sc = width / pic.getWidth();
                AffineTransform keep = b.getTransform();
                b.translate(p[0], p[1]);
                // the window is turned the way the snake goes but never upside down, so its text stays readable
                double turn = ang + (j % 2 == 0 ? 0.1 : -0.1);
                if (Math.cos(ang) < 0) turn += Math.PI;
                b.rotate(turn);
                b.scale(sc, sc);
                b.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, Math.min(1, life * (0.55 + 0.45 * taper)))));
                b.drawImage(pic, -pic.getWidth() / 2, -pic.getHeight() / 2, null);
                b.setTransform(keep);
                if (j == 0) {                                                  // the face looks the way the snake goes
                    b.translate(p[0], p[1]);
                    b.rotate(ang);
                    b.scale(sc, sc);
                    b.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, Math.min(1, life))));
                    drawFace(b, pic.getWidth(), pic.getHeight(), age);
                    b.setTransform(keep);
                }
            }
            b.setComposite(AlphaComposite.SrcOver);
        }
        if (b != null) b.dispose();
    }

    /** The head: two eyes on the title bar and a forked tongue out of the front, flickering. */
    private static void drawFace(Graphics2D b, int pw, int ph, double age) {
        double fx = pw * 0.30, eyeGap = ph * 0.22, er = ph * 0.15;
        b.setColor(new Color(255, 255, 255));
        b.fill(new Ellipse2D.Double(fx - er, -eyeGap - er, er * 2, er * 2));
        b.fill(new Ellipse2D.Double(fx - er, eyeGap - er, er * 2, er * 2));
        b.setColor(new Color(20, 12, 34));
        b.fill(new Ellipse2D.Double(fx + er * 0.1 - er * 0.5, -eyeGap - er * 0.75, er, er * 1.5));
        b.fill(new Ellipse2D.Double(fx + er * 0.1 - er * 0.5, eyeGap - er * 0.75, er, er * 1.5));
        if (((int) (age / 90)) % 3 != 2) {                                            // the tongue flickers
            double tx = pw / 2.0, ty = 0, len = pw * 0.34;
            b.setColor(new Color(255, 40, 70));
            b.setStroke(new BasicStroke((float) (ph * 0.06), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            b.draw(new Line2D.Double(tx, ty, tx + len * 0.7, ty));
            b.draw(new Line2D.Double(tx + len * 0.7, ty, tx + len, ty - ph * 0.18));
            b.draw(new Line2D.Double(tx + len * 0.7, ty, tx + len, ty + ph * 0.18));
        }
    }
}
