package com.lotusblight.overlay;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The snakes of errors, as the author drew them: an error window of the old kind being dragged across the screen while the system hangs, leaving
 * behind it a trail of its own copies, one for every jerk, piled on each other and smaller and fainter towards the tail. The way it goes
 * winds like a snake's. They pour out of the glass of the old TV from the endings (the easter egg), which stands in the corner while they run.
 * On the fast runs of the second song (snakes.txt: the moments, in seconds, the author marked by ear). A picture only: nothing to click.
 */
final class SnakesFx {
    private static final double LIFE_MS = 2_500;
    private static final double STEP_MS = 46;               // the window jumps this often: it keeps freezing
    private static final int COPIES = 30;

    private final List<Double> marks = new ArrayList<>();
    private final OldTv tv = new OldTv();
    private BufferedImage buf;
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

    /** The error windows of the old kind (the same ones the Glitcher used to throw), in the fast form to draw scaled. */
    private static List<BufferedImage> windows() {
        if (windows == null) {
            windows = new ArrayList<>();
            for (BufferedImage b : GlitcherActor.errorWindowSprites()) {
                BufferedImage pre = new BufferedImage(b.getWidth(), b.getHeight(), BufferedImage.TYPE_INT_ARGB_PRE);
                Graphics2D g = pre.createGraphics();
                g.drawImage(b, 0, 0, null);
                g.dispose();
                windows.add(pre);
            }
        }
        return windows;
    }

    /** Where the Glitcher's right fist is at a moment ({x, y}); null while he is not on the screen. */
    interface Hand {
        double[] at(double ms);
    }

    // ---- the set: the Glitcher carries it in his right hand

    private static double tvW(int h) { return h * 0.34; }

    /** The set's place for a fist at (x, y): it is gripped by its side, so it hangs a little above and in front of the fist. */
    private static double[] setPlace(double[] fist, int h) {
        double tvW = tvW(h), tvH = tvW * 0.56;
        return new double[]{fist[0] + tvW * 0.28, fist[1] + tvH * 0.30};            // cx, bottom
    }

    /** The middle of the TV's glass, where the snakes come out. */
    private static double[] mouth(double[] fist, int h) {
        double[] p = setPlace(fist, h);
        double[] gl = OldTv.glassOf(p[0], p[1], tvW(h), false);
        return new double[]{gl[0] + gl[2] / 2, gl[1] + gl[3] / 2};
    }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    /** One snake: where it starts, the way it goes, how it winds, how fast, which window it is. */
    private record Shape(double x0, double y0, double dx, double dy, double amp, double wave, double phase, double speed, int pic) {}

    private static Shape shape(double[] o, int w, int h, double mark, int idx) {
        Random rnd = new Random((long) (mark * 7) + idx * 131L);
        double tx = w * (0.85 + 0.4 * rnd.nextDouble());                  // the set stands at the left, so they go across to the right
        double ty = h * (0.08 + 0.62 * rnd.nextDouble());
        double dx = tx - o[0], dy = ty - o[1], len = Math.max(1, Math.hypot(dx, dy));
        return new Shape(o[0], o[1], dx / len, dy / len, h * (0.16 + 0.12 * rnd.nextDouble()), h * (0.9 + 0.6 * rnd.nextDouble()),
                rnd.nextDouble() * Math.PI * 2, w * (0.85 + 0.4 * rnd.nextDouble()) / 1000.0, rnd.nextInt(4));
    }

    /** The point of the way at distance s: the straight line from the start, swayed from side to side, so that it winds like a snake. */
    private static double[] at(Shape sh, double s) {
        double grow = Math.min(1, Math.max(0, s) / (sh.wave * 0.7));
        double side = sh.amp * grow * Math.sin(s / sh.wave * Math.PI * 2 + sh.phase);
        return new double[]{sh.x0 + sh.dx * s - sh.dy * side, sh.y0 + sh.dy * s + sh.dx * side};
    }

    void render(Graphics2D g, int w, int h, double ms, Hand hand) {
        // the set, in his hand for as long as he is there; it jerks in his fist while the snakes come out
        double[] fist = hand.at(ms);
        if (fist != null) {
            double[] p = setPlace(fist, h);
            double jerk = 0;
            for (double m : marks) {
                double age = ms - m;
                if (age >= 0 && age < 260) jerk = Math.max(jerk, 1 - age / 260.0);
            }
            Random shake = new Random((long) (ms / 35));
            tv.draw(g, p[0] + (shake.nextDouble() - 0.5) * h * 0.012 * jerk, p[1] - jerk * h * 0.012, tvW(h), ms, 1, false);
        }
        // the snakes
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
        b.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        for (int m : live) {
            double[] f = hand.at(marks.get(m));
            if (f == null) continue;
            drawSnake(b, w, h, ms, marks.get(m), mouth(f, h), m % 2);
        }
        b.dispose();
        composite(g, w, h, ms);
    }

    private void drawSnake(Graphics2D b, int w, int h, double ms, double mark, double[] origin, int idx) {
        double age = ms - mark;
        Shape sh = shape(origin, w, h, mark, idx);
        BufferedImage pic = windows().get(sh.pic);
        double life = age < LIFE_MS - 450 ? 1 : 1 - (age - (LIFE_MS - 450)) / 450.0;
        if (life <= 0) return;
        long now = (long) Math.floor(age / STEP_MS);                      // the jerk the window is on
        // the oldest copy first, the window itself on top
        for (int n = COPIES - 1; n >= 0; n--) {
            long step = now - n;
            if (step < 0) continue;
            double[] p = at(sh, sh.speed * step * STEP_MS);
            double f = n / (double) (COPIES - 1);                         // 0 at the window, 1 at the oldest copy
            double sc = h * (0.40 - 0.20 * f) / pic.getWidth();
            double fade = (1 - 0.82 * f) * life;
            b.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, Math.min(1, fade))));
            int pw = (int) (pic.getWidth() * sc), ph = (int) (pic.getHeight() * sc);
            b.drawImage(pic, (int) (p[0] - pw / 2.0), (int) (p[1] - ph / 2.0), pw, ph, null);
        }
        b.setComposite(AlphaComposite.SrcOver);
    }

    /** The picture of the snakes goes to the screen in horizontal slices, a few of them thrown sideways: the tear of a bad signal. */
    private void composite(Graphics2D g, int w, int h, double ms) {
        Random rnd = new Random((long) (ms / 60));
        int strips = 18, sh = h / strips + 1;
        Graphics2D o = (Graphics2D) g.create();
        for (int i = 0; i < strips; i++) {
            int y = i * sh;
            int dx = rnd.nextInt(7) == 0 ? (int) ((rnd.nextDouble() - 0.5) * w * 0.06) : 0;
            o.drawImage(buf, dx, y, dx + w, y + sh, 0, y, w, Math.min(h, y + sh), null);
        }
        o.dispose();
    }
}
