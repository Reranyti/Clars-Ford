package com.lotusblight.overlay;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Glitcher of the second song as a real, solid figure, close to the screen and big (the 2D one on the floor glitches apart at 0:19):
 * first only a silhouette, black, edged with light, coming nearer; from 0:38 whole, standing front-on behind the "glass" and pounding on it, the
 * animation sticking now and then, repeating a moment as a frozen picture does; at 0:56 he screams and snaps his head round; in the part with
 * the eye (from 1:20) he hammers the screen on every beat, harder and harder, and the glass cracks more and more. He holds the old TV in his
 * hand (the snakes come out of it).
 *
 * <p>All timings here are the author's; the screams, the hits and the rest are drawn by the same soft renderer as the other figures, at half
 * the screen's size and stretched (the picture is blocky by nature, and the cost stays small).
 */
final class GlitcherCloseup {
    static final double SIL_FROM = 19_000, FULL_FROM = 38_000, SCREAM_FROM = 56_000, SCREAM_TO = 57_500, SONG_END = 77_500;
    static final double EYES_FROM = 79_500, EYES_TO = 133_500;
    /** Stretches in which the animation sticks and repeats its first moments: {from, to}, ms. */
    private static final double[][] STUCK = {{43_200, 44_700}, {49_400, 51_000}, {67_600, 68_800}};
    private static final double STUCK_LOOP_MS = 330;
    private static final double RES = 0.5;

    private final Actor actor;
    private SoftRenderer rend;
    private final GlitchFx glitch = new GlitchFx();
    private final double[] hits;
    private double fistX, fistY;
    private boolean haveFist;

    GlitcherCloseup(int[] skin) {
        this.actor = new Actor(skin, false);
        List<Double> h = new ArrayList<>();
        for (double t = FULL_FROM; t < SCREAM_FROM - 100; t += 600) h.add(t);              // the pounding before the scream
        for (double t = SCREAM_TO + 500; t < SONG_END - 600; t += 600) h.add(t);           // and after it
        for (double t : ContactBreak.beatTimes()) if (t < EYES_TO) h.add(t);               // in the part with the eye: on every beat
        hits = new double[h.size()];
        for (int i = 0; i < hits.length; i++) hits[i] = h.get(i);
    }

    boolean active(double ms) {
        return (ms >= SIL_FROM && ms < SONG_END) || (ms >= EYES_FROM && ms < EYES_TO);
    }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    /** The time the animation is on: the same as the clock, except in the stretches where it sticks and repeats. */
    private static double animTime(double ms) {
        for (double[] s : STUCK) {
            if (ms >= s[0] && ms < s[1]) return s[0] + ((ms - s[0]) % STUCK_LOOP_MS);
        }
        return ms;
    }

    /** Index of the last hit at or before the time, or -1. */
    private int lastHit(double ms) {
        int lo = 0, hi = hits.length - 1, ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (hits[mid] <= ms) {
                ans = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return ans;
    }

    /** {roll (rad), zoom, dx, dy (px)}: the whole picture is struck by every hit. */
    double[] camera(double ms, int w, int h) {
        if (!active(ms)) return new double[]{0, 1, 0, 0};
        int i = lastHit(ms);
        double age = i < 0 ? 1e9 : ms - hits[i];
        double roll = 0, zoom = 1, dx = 0, dy = 0;
        if (ms >= FULL_FROM && age < 400) {
            double k = Math.exp(-age / 90.0);
            Random rnd = new Random((long) (ms / 22));
            roll = (i % 2 == 0 ? 1 : -1) * 0.012 * k;
            zoom = 1 + 0.045 * k;
            dx = (rnd.nextDouble() - 0.5) * h * 0.024 * k;
            dy = (rnd.nextDouble() - 0.5) * h * 0.024 * k;
        }
        if (ms >= SCREAM_FROM && ms < SCREAM_TO + 200) {                                     // the scream: a push in and a constant tremor
            double s = smooth((ms - SCREAM_FROM) / 180.0) * (1 - smooth((ms - SCREAM_TO) / 200.0));
            Random rnd = new Random((long) (ms / 20));
            zoom *= 1 + 0.10 * s;
            dx += (rnd.nextDouble() - 0.5) * h * 0.03 * s;
            dy += (rnd.nextDouble() - 0.5) * h * 0.03 * s;
        }
        return new double[]{roll, zoom, dx, dy};
    }

    /** Where the fist that holds the TV is ({x, y, width of the set}), or null while he is not there. */
    double[] hand(double ms, int h) {
        if (!active(ms) || !haveFist || SnakesFx.presenting(ms) < 0.02) return null;          // he carries the TV only while the snakes run
        return new double[]{fistX, fistY, h * 0.30};
    }

    void render(Graphics2D g, int w, int h, double ms) {
        if (!active(ms)) {
            haveFist = false;
            return;
        }
        int rw = Math.max(2, (int) (w * RES)), rh = Math.max(2, (int) (h * RES));
        if (rend == null || rend.width != rw || rend.height != rh) rend = new SoftRenderer(rw, rh);
        double anim = animTime(ms);
        // how solid he is: black against the light until 0:38, then switched on with a flash
        double full = smooth((ms - FULL_FROM) / 380.0);
        double approach = ms < FULL_FROM ? smooth((ms - SIL_FROM) / (FULL_FROM - SIL_FROM)) : 1;   // it comes nearer over the 19 s
        double sf = 0.55 + 0.45 * approach;
        double u = h * 0.034 * sf;                                                                  // screen pixels per skin unit
        double ox = w * 0.44, oy = h * (1.21 + (1 - sf) * 0.62);

        // ---- the pose
        Pose15 p = actor.begin();
        double breath = Math.sin(ms / 1000.0 * 1.5);
        p.reach(Rig15.Limb.R_LEG, -2.4, 3, 0, 1).reach(Rig15.Limb.L_LEG, 2.4, 3, 0, 1);
        p.rootPos[1] = -0.7 - 0.25 * breath;
        actor.viewYaw = 0;
        actor.viewPitch = 0;
        // the arm with the TV: up and out at chest height, bent
        double show = SnakesFx.presenting(ms);
        if (show > 0.01) {
            actor.reach(Rig15.Limb.L_ARM, 10.0, 20.0, 6.5, show);
            actor.shapeL = show > 0.3 ? Actor.Hand.GRIP : Actor.Hand.LOOSE;
            actor.fistL = 0.6 * show;
        }
        // the pounding arm: raised, then down hard on the beat
        int li = lastHit(anim);
        double rr = 0.5;
        if (ms >= FULL_FROM && li >= 0 && li + 1 < hits.length) {
            double spacing = Math.min(1200, hits[li + 1] - hits[li]);
            double uu = Math.max(0, Math.min(1, (anim - hits[li]) / spacing));
            rr = uu < 0.78 ? smooth(uu / 0.78) : 1 - smooth((uu - 0.78) / 0.22);
        }
        double[] down = {-4.5, 20.0, 9.5}, up = {-7.5, 30.0, 3.5};
        actor.reach(Rig15.Limb.R_ARM, down[0] + (up[0] - down[0]) * rr, down[1] + (up[1] - down[1]) * rr, down[2] + (up[2] - down[2]) * rr, 1);
        actor.shapeR = Actor.Hand.GRIP;
        actor.fistR = 1;
        actor.blendTurn(Rig15.Joint.UPPER_TORSO, 7 * (1 - rr), 0, 0, 1);                              // leans into every blow
        actor.blendTurn(Rig15.Joint.HEAD, 9 * (1 - rr), 0, 0, 1);
        // the scream, and the head snapped round
        double scream = smooth((ms - SCREAM_FROM) / 200.0) * (1 - smooth((ms - SCREAM_TO + 150) / 200.0));
        double snap = smooth((ms - 56_900) / 90.0) * (1 - smooth((ms - 57_450) / 110.0));
        if (scream > 0) actor.blendTurn(Rig15.Joint.HEAD, -26, 0, 0, scream * (1 - snap));
        if (snap > 0) actor.blendTurn(Rig15.Joint.HEAD, 0, 40, 8, snap);

        // ---- the light: none at first (a silhouette), then the cold light with the TV's glow at his side
        double[] key = {1.2 * full, 1.16 * full, 1.45 * full}, amb = {0.8 * full, 0.74 * full, 1.0 * full};
        rend.clearDirty();
        rend.setLight(key, amb);
        actor.draw(rend, u * RES, ox * RES, oy * RES);
        int thick = Math.max(2, (int) Math.round(u * RES * 0.28));
        ActorFx.rim(rend, 0xA8C4FF, 0.9, -0.6, 0.8, thick);                                           // the edge of him against the dark
        ActorFx.rim(rend, 0x6FE0FF, 0.85, 1, 0.1, thick + 1);                                         // the glow of the TV on the side that holds it
        if (ms < FULL_FROM) glitch.apply(rend.pixels, rend.width, rend.height, 0.18 * (1 - approach) + 0.07);
        double alpha = ms < FULL_FROM ? 0.30 + 0.65 * approach : 1;

        Graphics2D b = (Graphics2D) g.create();
        b.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        b.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alpha));
        b.drawImage(rend.image, 0, 0, w, h, null);
        b.setComposite(AlphaComposite.SrcOver);

        // where the fist with the TV is on the screen
        if (actor.lastSkel != null) {
            double[] hp = actor.onScreen(actor.lastSkel.endPoint(Rig15.Limb.L_ARM));
            fistX = ox + hp[0] * u;
            fistY = oy - hp[1] * u;
            haveFist = true;
        }
        // ---- the scream: his mouth opens in the black of the face
        if (scream > 0.15 && actor.lastSkel != null) {
            double[] top = actor.onScreen(actor.lastSkel.headTop());
            drawMouth(b, ox + top[0] * u, oy - top[1] * u + u * 4.2, u, scream);
        }
        drawCracks(b, w, h, ms, u);
        // a white flash on each blow of the fist
        int li2 = lastHit(ms);
        if (ms >= FULL_FROM && li2 >= 0 && ms - hits[li2] < 80) {
            b.setColor(new Color(255, 255, 255, (int) (95 * (1 - (ms - hits[li2]) / 80.0))));
            b.fillRect(0, 0, w, h);
        }
        b.dispose();
    }

    /** A jagged white mouth, wide open, in the middle of the face. */
    private static void drawMouth(Graphics2D b, double cx, double cy, double u, double open) {
        Random rnd = new Random(55);
        double wd = u * 4.2, ht = u * 5.2 * open;
        Path2D.Double m = new Path2D.Double();
        int teeth = 9;
        m.moveTo(cx - wd / 2, cy);
        for (int i = 1; i <= teeth; i++) {
            double x = cx - wd / 2 + wd * i / teeth;
            m.lineTo(x, cy + (i % 2 == 0 ? 0.0 : u * 0.8 * open) + (rnd.nextDouble() - 0.5) * u * 0.3);
        }
        for (int i = teeth; i >= 0; i--) {
            double x = cx - wd / 2 + wd * i / teeth;
            m.lineTo(x, cy + ht * (0.8 + 0.2 * Math.sin(i * 1.7)) - (i % 2 == 0 ? u * 0.8 * open : 0));
        }
        m.closePath();
        b.setColor(new Color(10, 4, 20));
        b.fill(m);
        b.setColor(new Color(255, 255, 255));
        b.setStroke(new BasicStroke((float) Math.max(2, u * 0.25), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        b.draw(m);
    }

    /** Cracks in the glass, where the blows landed: more of them and longer as the song goes on; they stay. */
    private void drawCracks(Graphics2D b, int w, int h, double ms, double u) {
        int last = lastHit(ms);
        if (last < 0 || ms < FULL_FROM) return;
        int from = Math.max(0, last - 34);
        b.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = from; i <= last; i++) {
            if (hits[i] < FULL_FROM) continue;
            Random rnd = new Random(7000 + i);
            double age = ms - hits[i];
            double grow = Math.min(1, age / 130.0);
            double ix = w * (0.12 + 0.76 * rnd.nextDouble()), iy = h * (0.1 + 0.7 * rnd.nextDouble());
            double mood = Math.min(1, (hits[i] - FULL_FROM) / (EYES_TO - FULL_FROM));              // the later the blow, the worse the crack
            int rays = 3 + (int) (2.5 * mood);
            double fade = 0.45 + 0.55 * (i - from) / (double) Math.max(1, last - from);
            for (int r = 0; r < rays; r++) {
                double ang = rnd.nextDouble() * Math.PI * 2, px = ix, py = iy;
                int segs = 4;
                for (int s = 0; s < segs * grow; s++) {
                    ang += (rnd.nextDouble() - 0.5) * 0.8;
                    double len = h * (0.05 + 0.09 * rnd.nextDouble()) * (0.6 + 0.9 * mood);
                    double nx = px + Math.cos(ang) * len, ny = py + Math.sin(ang) * len;
                    b.setColor(new Color(160, 120, 255, (int) (70 * fade)));
                    b.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    b.drawLine((int) px, (int) py, (int) nx, (int) ny);
                    b.setColor(new Color(255, 255, 255, (int) (200 * fade)));
                    b.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    b.drawLine((int) px, (int) py, (int) nx, (int) ny);
                    px = nx;
                    py = ny;
                }
            }
        }
    }
}
