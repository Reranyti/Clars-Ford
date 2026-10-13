package com.lotusblight.overlay;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * The closing cutscene, after the results of the third song: a portal opens into Minecraft, the Glitcher takes Honcho by the
 * throat and throws him into it, two last lines are written, and the signal is lost. When it has played out the process
 * ends with {@link #EXIT_CODE}, which tells the game to put the horn in the player's hands; the game's own
 * window is brought back if it was hidden.
 *
 * <p>It runs on the wall clock, like the cutscene between the songs, and the scene is a pure function of that
 * clock, so any moment of it can be previewed with {@code --finale SECONDS}.
 */
final class Finale3 {
    /** The process ends with this when the scene has played out (the game checks for it: NormalBranchReward). */
    static final int EXIT_CODE = 77;

    /** {from ms, to ms, text} - what the Glitcher writes across the screen, letter by letter. */
    private static final Object[][] LINES = {
            {5_000.0, 9_000.0, "Ты всё ещё не достаточно силён"},
            {9_400.0, 12_700.0, "Первая часть у тебя. Проваливай"},
    };

    /** {from ms, to ms, kind} - what happens besides the words; drawBeat paints each kind. */
    private static final Object[][] BEATS = {
            {2_000.0, 12_700.0, "portal"},
            {13_200.0, 16_200.0, "signal"},
    };

    private static final double PORTAL_FROM_MS = 2_000;
    private static final double PORTAL_OPEN_MS = 2_600, PORTAL_CLOSE_FROM = 10_800, PORTAL_CLOSE_MS = 1_500;
    // Honcho walks up, takes the Glitcher by the throat, lifts him, swings him back and throws him into the portal.
    private static final double T_STEP = 3_200, T_REACH = 3_700, T_GRAB = 4_300, T_LIFT = 4_400, T_WIND = 5_100, T_SWEEP = 5_900;
    private static final double RELEASE = 6_250, FLIGHT_MS = 2_200;
    private static final double TYPE_CHAR = 40;
    private static final double FADE_IN_MS = 1_800, FADE_OUT_MS = 500, LEAD_OUT_MS = 300;
    private static final double SIGNAL_FROM = 13_200, SIGNAL_TO = 16_200;

    private final BufferedImage desktop;
    private final int[] glitcherSkin, honchoSkin;
    private final Actor[] rigs;
    private final SoftRenderer[] figures = new SoftRenderer[2];
    private final double unit;
    private final long startNano = PauseClock.nano();
    private double seekMs;

    Finale3(int[] glitcherSkin, int[] honchoSkin, BufferedImage desktop, int screenH) {
        this.glitcherSkin = glitcherSkin;
        this.honchoSkin = honchoSkin;
        this.desktop = desktop;
        this.unit = screenH * 0.36 / 32.0 * 0.6;
        this.rigs = new Actor[]{new Actor(honchoSkin, true), new Actor(glitcherSkin, false)};
        for (int i = 0; i < 2; i++) figures[i] = new SoftRenderer((int) (30 * unit), (int) (36 * unit));
    }

    /** Starts the scene at a given second (for previewing). */
    void seek(double seconds) {
        seekMs = seconds * 1000 - (PauseClock.nano() - startNano) / 1e6;
    }

    private double now() {
        return (PauseClock.nano() - startNano) / 1e6 + seekMs;
    }

    private static double length() {
        double end = 0;
        for (Object[] l : LINES) end = Math.max(end, (Double) l[1]);
        for (Object[] b : BEATS) end = Math.max(end, (Double) b[1]);
        return end + LEAD_OUT_MS;
    }

    /** True once the last of it has faded to black. */
    boolean done() {
        return now() >= length() + FADE_OUT_MS;
    }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    // The portal stands to the left of the two of them: the Glitcher throws Honcho into it.
    private static double portalX(int w) { return w * 0.18; }

    private static double portalY(int h) { return h * 0.46; }

    void render(Graphics2D g, int w, int h) {
        double c = now();
        Graphics2D b = (Graphics2D) g.create();
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        b.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        b.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // The stage: the real desktop, dimmed, with a soft light on the floor where they stand.
        b.setColor(new Color(4, 3, 10));
        b.fillRect(0, 0, w, h);
        if (desktop != null) {
            b.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
            b.drawImage(desktop, 0, 0, w, h, null);
            b.setComposite(AlphaComposite.SrcOver);
        }
        b.setPaint(new GradientPaint(0, 0, new Color(4, 3, 12, 150), 0, h, new Color(10, 6, 24, 200)));
        b.fillRect(0, 0, w, h);
        b.setPaint(new RadialGradientPaint((float) (w * 0.5), (float) (h * 0.78), (float) (w * 0.42), new float[]{0f, 1f},
                new Color[]{new Color(150, 120, 220, 70), new Color(150, 120, 220, 0)}));
        b.fillRect(0, 0, w, h);

        boolean signal = c >= SIGNAL_FROM;
        if (!signal) {
            for (Object[] beat : BEATS) {
                if ("portal".equals(beat[2]) && c >= (Double) beat[0] && c < (Double) beat[1]) drawPortal(b, w, h, c);
            }
            drawFigure(b, w, h, c, 0);
            drawFigure(b, w, h, c, 1);
            for (Object[] line : LINES) {
                if (c >= (Double) line[0] && c < (Double) line[1]) typed(b, w, h, c, line);
            }
        }
        for (Object[] beat : BEATS) {
            if ("signal".equals(beat[2]) && c >= (Double) beat[0] && c < (Double) beat[1]) drawSignal(b, w, h, c);
        }

        // In from black, out to black.
        double in = Math.min(1, c / FADE_IN_MS);
        double out = Math.max(0, Math.min(1, (c - length()) / FADE_OUT_MS));
        double dark = Math.max(1 - in, out);
        if (dark > 0) {
            b.setColor(new Color(0, 0, 0, (int) (255 * dark)));
            b.fillRect(0, 0, w, h);
        }
        b.dispose();
    }

    private double upx() {
        return unit / 0.6;
    }

    /** Honcho stands here to begin with (and is taken hold of here). */
    private double victimX0(int w) {
        return w * 0.44;
    }

    /** The Glitcher's place on the floor: where he starts, and where he has walked to when he takes hold. */
    private double throwerX(double c, int w) {
        double start = w * 0.70, grabX = victimX0(w) + 12 * upx();
        return start + (grabX - start) * smooth((c - T_STEP) / 1100.0);
    }

    /** Where the Glitcher's right fist is meant to be (in his own space) and how much it counts: reach, hold, lift, wind back, sweep. */
    private static double[] fistTarget(double c) {
        double lift = smooth((c - T_LIFT) / 600.0);
        double[] hold = {-3.3, 25.5 + 2.5 * lift, 9.4};
        double wind = smooth((c - T_WIND) / 700.0), sweep = smooth((c - T_SWEEP) / 350.0);
        double[] windPt = {-3.2, 29, -0.5}, sweepPt = {-3.0, 24.5, 11.2};
        double[] out = new double[4];
        for (int k = 0; k < 3; k++) {
            double v = hold[k] + (windPt[k] - hold[k]) * wind;
            out[k] = v + (sweepPt[k] - v) * sweep;
        }
        out[3] = smooth((c - T_REACH) / 600.0) * (1 - smooth((c - (RELEASE + 350)) / 700.0));
        return out;
    }

    /** Where that fist is on the picture, given the Glitcher stands at {@code x}; he faces left, nearly side-on. */
    private double[] fistOnScreen(double[] target, double x, double feetY) {
        double yaw = -1.4;
        double sx = target[2] * Math.sin(yaw) + target[0] * Math.cos(yaw);
        return new double[]{x + sx * upx(), feetY - target[1] * upx()};
    }

    /** Honcho's neck on the picture before he is thrown: standing, taken hold of, lifted, swung. */
    private double[] victimNeck(double c, int w, double feetY) {
        double up = upx();
        double[] standing = {victimX0(w), feetY - 24 * up};
        double[] fist = fistOnScreen(fistTarget(c), throwerX(c, w), feetY);
        double gb = smooth((c - (T_GRAB - 300)) / 350.0);
        // The throat is in the fist; he faces the Glitcher, so his neck is a little beyond it.
        return new double[]{standing[0] + (fist[0] - 1.6 * up - standing[0]) * gb, standing[1] + (fist[1] - standing[1]) * gb};
    }

    private void drawFigure(Graphics2D g, int w, int h, double c, int who) {
        double feetY = h * 0.86, t = c / 1000.0, up = upx();
        double breath = Math.sin(t * 1.6 + who);
        Actor a = rigs[who];
        Pose15 p = a.begin();
        double px = 0, py = 0, rot = 0, scale = 1;              // where the neck is, how it turns, how large
        if (who == 1) {
            // ---- The Glitcher: walks up, reaches, holds Honcho by the neck, lifts him, swings him back, throws him into the portal.
            px = throwerX(c, w);
            py = feetY - 24 * up;
            a.viewYaw = -1.4;
            double walkAmt = smooth((c - T_STEP) / 300.0) * (1 - smooth((c - (T_STEP + 1000)) / 250.0));
            GlitcherPoses.walk(a, c / 1000.0 * 2.2 * Math.PI, walkAmt, 0.2, t);
            double[] ft = fistTarget(c);
            if (ft[3] > 0.01) {
                a.reach(Rig15.Limb.R_ARM, ft[0], ft[1], ft[2], ft[3]);
                a.pole(Rig15.Limb.R_ARM, -0.3, -1, -0.3);
                double wind = smooth((c - T_WIND) / 700.0), sweep = smooth((c - T_SWEEP) / 350.0);
                a.blendTurn(Rig15.Joint.UPPER_TORSO, 6 - 10 * wind + 14 * sweep, -22 * wind + 28 * sweep, 0, ft[3]);
                a.blendTurn(Rig15.Joint.HEAD, -4, 0, 0, ft[3]);
            }
            boolean released = c >= RELEASE;
            a.shapeR = released ? Actor.Hand.OPEN : Actor.Hand.GRIP;
            a.fistR = released ? 0 : 0.45;
            a.spread = 0.6;
            a.blendTurn(Rig15.Joint.L_UPPER_ARM, -12 * smooth((c - T_WIND) / 700.0), 0, 6, 1);      // the other arm goes with the body
        } else {
            double e = (c - RELEASE) / FLIGHT_MS;
            if (e >= 1) return;                                    // gone through
            a.viewYaw = 1.4;
            if (e <= 0) {
                double[] n = victimNeck(c, w, feetY);
                px = n[0];
                py = n[1];
                double lift = smooth((c - T_LIFT) / 500.0);
                double wind = smooth((c - T_WIND) / 700.0), sweep = smooth((c - T_SWEEP) / 350.0);
                // standing: waiting, the head down a little
                p.reach(Rig15.Limb.R_LEG, -2.4, 3, 0, 1).reach(Rig15.Limb.L_LEG, 2.4, 3, 0, 1);
                p.rootPos[1] = -0.6;
                p.turn(Rig15.Joint.UPPER_TORSO, 2 + breath, 0, 0).turn(Rig15.Joint.HEAD, 6, 0, 0);
                p.turn(Rig15.Joint.R_UPPER_ARM, 3 * Math.sin(t * 1.1), 0, -5).turn(Rig15.Joint.L_UPPER_ARM, -3 * Math.sin(t * 1.1), 0, 5);
                p.turn(Rig15.Joint.R_LOWER_ARM, -6, 0, 0).turn(Rig15.Joint.L_LOWER_ARM, -6, 0, 0);
                // taken hold of: the struggle comes in as he leaves the floor
                if (lift > 0.02) GlitcherPoses.hanging(a, t, lift * (1 - 0.4 * sweep), 1.3);
                // hangs from the fist, swinging away from the Glitcher, trailing as the arm goes back and whips forward
                double since = Math.max(0, c - T_GRAB);
                rot = 0.3 * smooth((c - T_GRAB) / 400.0) - 0.5 * wind + 0.8 * sweep - 0.25 * Math.exp(-since / 900.0) * Math.cos(since * 0.006);
            } else {
                // Thrown: over in an arc, turning, growing small as it goes into the portal; the body goes limp, the limbs trailing.
                double[] n0 = victimNeck(RELEASE, w, feetY);
                double ee = e * e * (3 - 2 * e);
                double ptX = portalX(w), ptY = portalY(h);
                px = n0[0] + (ptX - n0[0]) * ee;
                py = n0[1] + (ptY - n0[1]) * ee - Math.sin(Math.PI * ee) * h * 0.16;
                double rot0 = 0.3 - 0.5 + 0.8;
                rot = rot0 + ee * Math.PI * 3.5;
                scale = 1 - 0.85 * ee * ee;
                double fl = Math.sin(e * 23), fl2 = Math.sin(e * 19 + 1.3), fl3 = Math.sin(e * 27 + 2.1);
                p.turn(Rig15.Joint.UPPER_TORSO, -8 + 6 * fl2, 8 * fl3, 5 * fl);
                p.turn(Rig15.Joint.HEAD, -25 + 14 * fl, 12 * fl2, 8 * fl3);
                p.turn(Rig15.Joint.R_UPPER_ARM, -150 + 40 * fl, 0, -40 + 25 * fl2).turn(Rig15.Joint.L_UPPER_ARM, -130 + 40 * fl2, 0, 40 + 25 * fl3);
                p.turn(Rig15.Joint.R_LOWER_ARM, -40 - 25 * fl3, 0, 0).turn(Rig15.Joint.L_LOWER_ARM, -55 - 25 * fl, 0, 0);
                p.turn(Rig15.Joint.R_UPPER_LEG, -45 + 35 * fl2, 0, 6).turn(Rig15.Joint.L_UPPER_LEG, 25 + 35 * fl3, 0, -6);
                p.turn(Rig15.Joint.R_LOWER_LEG, 55 + 25 * fl, 0, 0).turn(Rig15.Joint.L_LOWER_LEG, 35 + 25 * fl2, 0, 0);
                p.turn(Rig15.Joint.R_FOOT, 25, 0, 0).turn(Rig15.Joint.L_FOOT, 35, 0, 0);
                a.hands(Actor.Hand.OPEN, Actor.Hand.OPEN);
                a.spread = 1;
            }
        }
        SoftRenderer r = figures[who];
        r.clear();
        a.draw(r, unit, r.width / 2.0, r.height - 2 * unit);
        Graphics2D d = (Graphics2D) g.create();
        double sc = scale / 0.6;
        d.translate(px, py);                                        // the neck
        d.rotate(rot);
        d.scale(sc, sc);
        d.translate(0, 24 * unit);                                  // and the body hangs from it, down to the feet
        d.drawImage(r.image, -r.width / 2, -r.height + (int) (2 * unit), null);
        d.dispose();
    }

    /** The portal, to the left of the two of them. */
    private void drawPortal(Graphics2D g, int w, int h, double c) {
        double a = smooth((c - PORTAL_FROM_MS) / PORTAL_OPEN_MS) * (1 - smooth((c - PORTAL_CLOSE_FROM) / PORTAL_CLOSE_MS));
        PortalFx.draw(g, portalX(w), portalY(h), h * 0.13, h * 0.27, a, c / 1000.0, h);
    }

    /** "SIGNAL LOST": static, scanlines, the words split in colour and flickering, then black. */
    private void drawSignal(Graphics2D g, int w, int h, double c) {
        double k = (c - SIGNAL_FROM) / (SIGNAL_TO - SIGNAL_FROM);
        Random r = new Random((long) (c / 55));
        Graphics2D d = (Graphics2D) g.create();
        double cover = Math.min(1, k / 0.12);
        d.setColor(new Color(0, 0, 0, (int) (255 * cover)));
        d.fillRect(0, 0, w, h);
        double noise = k < 0.88 ? 1 : Math.max(0, 1 - (k - 0.88) / 0.12);
        for (int i = 0; i < (int) (260 * noise); i++) {
            int gv = 40 + r.nextInt(200);
            d.setColor(new Color(gv, gv, gv, 40 + r.nextInt(90)));
            d.fillRect(r.nextInt(w), r.nextInt(h), 6 + r.nextInt(w / 8), 1 + r.nextInt(3));
        }
        d.setColor(new Color(0, 0, 0, 70));
        for (int y = 0; y < h; y += 4) d.fillRect(0, y, w, 1);
        if (k < 0.9 && r.nextDouble() > 0.12) {
            d.setFont(new Font(Font.MONOSPACED, Font.BOLD, (int) (h * 0.13)));
            FontMetrics fm = d.getFontMetrics();
            String text = "SIGNAL LOST";
            int tw = fm.stringWidth(text);
            int jx = (int) ((r.nextDouble() - 0.5) * h * 0.02 * (1 + 3 * Math.max(0, k - 0.6)));
            int x = (w - tw) / 2 + jx, y = (int) (h * 0.54);
            d.setColor(new Color(255, 40, 80, 170));
            d.drawString(text, x - 5, y);
            d.setColor(new Color(40, 230, 255, 170));
            d.drawString(text, x + 5, y);
            d.setColor(new Color(240, 240, 245));
            d.drawString(text, x, y);
        }
        d.dispose();
    }

    /** The Glitcher's words, written across the screen a letter at a time with a blinking cursor, as he writes them on the desktop. */
    private void typed(Graphics2D g, int w, int h, double c, Object[] line) {
        double from = (Double) line[0], to = (Double) line[1];
        String full = (String) line[2];
        int n = Math.min(full.length(), (int) ((c - from) / TYPE_CHAR));
        if (n <= 0) return;
        double a = 1 - smooth((c - (to - 450)) / 450.0);
        if (a <= 0) return;
        boolean cursor = ((int) (c / 250)) % 2 == 0;
        Graphics2D t2 = (Graphics2D) g.create();
        t2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        t2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, Math.min(1, a))));
        t2.setFont(new Font(Font.MONOSPACED, Font.BOLD, (int) (h * 0.052)));
        int fullW = t2.getFontMetrics().stringWidth(full + "_");
        int x = (w - fullW) / 2, y = (int) (h * 0.2);
        String shown = full.substring(0, n) + (cursor || n < full.length() ? "_" : "");
        t2.setColor(new Color(0x73, 0x00, 0xFF, 120));
        t2.drawString(shown, x - 3, y);
        t2.drawString(shown, x + 3, y);
        t2.setColor(Color.WHITE);
        t2.drawString(shown, x, y);
        t2.dispose();
    }
}
