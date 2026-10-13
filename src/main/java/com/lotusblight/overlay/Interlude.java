package com.lotusblight.overlay;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Between the second song and the third. The Glitcher stands on the desktop and speaks to the player; a portal
 * opens and Honcho is thrown out of it; the Glitcher takes him by the throat and asks who he is - the player has
 * twelve seconds to push him off, or Honcho dies and so does the player. Furious, the Glitcher says they will play
 * by his rules, the desktop breaks like glass and everyone falls; the third song is the fight on the way down.
 *
 * <p>The scene runs on its own wall clock from the moment it is made.
 */
final class Interlude {
    static final double MONO_END = 14_000;          // the Glitcher's questions end
    static final double RIFT_END = 22_600;          // the portal, the throw, the grab and his question: the choke begins
    private static final double PORTAL_FROM = 14_000, EXIT_FROM = 16_800, GRAB_FROM = 18_600, WHO_FROM = 19_600, WHO_TO = 22_200;
    static final double CHOKE_MS = 12_000;          // to push him off
    private static final double PUSH = 0.03;        // one press
    private static final double DECAY = 0.04;       // his weight, per second
    private static final double ANGRY_AFTER = 400, ANGRY_LEN = 3_400, COLLAPSE_LEN = 3_600, FAIL_LEN = 3_000;

    /** {from, to, Japanese, English} - what the Glitcher says while the first part runs. */
    private static final Object[][] MONO = {
            {1_600.0, 4_900.0, "私の物語を知ろうとしているのか？", "Trying to learn my story?"},
            {5_500.0, 7_600.0, "私は消されたのか？", "Have I been erased?"},
            {8_000.0, 9_600.0, "わからない", "I don't know"},
            {10_300.0, 12_300.0, "私は生きているのか？", "Am I alive?"},
            {12_700.0, 14_000.0, "わからない", "I don't know"},
    };
    private static final String WHO_JP = "お前は一体何者だ？";
    private static final String WHO_EN = "Who the hell are you?";
    private static final String ANGRY_JP = "お前たちは、私のルールで遊ぶんだ";
    private static final String ANGRY_EN = "You will play by my rules";

    private final BufferedImage face;
    private final BufferedImage desktop;
    private final FallScene fall;
    private final GlitchBackdrop backdrop = new GlitchBackdrop();
    private BufferedImage fakeDesktop;

    private long startNano = PauseClock.nano();
    private double meter;
    private double lastFrame, lastPress = -1e9, winAt = -1, failAt = -1;

    private final int[] glitcherSkin, honchoSkin;

    Interlude(int[] glitcherSkin, int[] honchoSkin, BufferedImage desktop, FallScene fall) {
        this.glitcherSkin = glitcherSkin;
        this.honchoSkin = honchoSkin;
        this.desktop = desktop;
        this.fall = fall;
        backdrop.setNoWall(true);
        backdrop.setNoWindows(true);
        BufferedImage f = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int base = glitcherSkin[(8 + y) * 64 + 8 + x];
                int hat = glitcherSkin[(8 + y) * 64 + 40 + x];
                f.setRGB(x, y, (hat >>> 24) > 128 ? hat : base | 0xFF000000);
            }
        }
        this.face = f;
    }

    /** Starts the scene as if it had been running for the given number of seconds (to try a later part). */
    void seek(double seconds) {
        startNano = PauseClock.nano() - (long) (seconds * 1e9);
    }

    private double now() {
        return (PauseClock.nano() - startNano) / 1e6;
    }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    private boolean choking(double c) {
        return c >= RIFT_END && winAt < 0 && failAt < 0;
    }

    /** A press of space (or Enter, Z, X): one push against the Glitcher, if he is at Honcho's throat. */
    void press() {
        push(PUSH);
    }

    /** A click: it counts if it lands on the Glitcher. */
    void click(double x, double y) {
        if (hitBox.contains(x, y)) push(PUSH * 1.3);
    }

    private void push(double amount) {
        double c = now();
        if (!choking(c)) return;
        meter = Math.min(1, meter + amount);
        lastPress = c;
        if (meter >= 1) winAt = c;
    }

    /** Milliseconds until the scene is over (won or lost); a very large number while it is still undecided. */
    double doneIn() {
        double c = now();
        if (winAt >= 0) return winAt + ANGRY_AFTER + ANGRY_LEN + COLLAPSE_LEN - c;
        if (failAt >= 0) return failAt + FAIL_LEN - c;
        return 1e9;
    }

    /** True once Honcho has been strangled and the black has closed in. */
    boolean failed() {
        return failAt >= 0 && now() >= failAt + FAIL_LEN;
    }

    /** True once the desktop has come down and everyone is falling. */
    boolean done() {
        return winAt >= 0 && now() >= winAt + ANGRY_AFTER + ANGRY_LEN + COLLAPSE_LEN;
    }

    void render(Graphics2D g, int w, int h) {
        double c = now();
        double dt = Math.max(0, Math.min(100, c - lastFrame));
        lastFrame = c;
        if (choking(c)) {
            meter = Math.max(0, meter - DECAY * dt / 1000.0);
            if (c >= RIFT_END + CHOKE_MS) failAt = c;
        }
        Graphics2D b = (Graphics2D) g.create();
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        b.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        b.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (winAt >= 0) {
            drawAfterWin(b, w, h, c - winAt);
        } else if (failAt >= 0) {
            double f = smooth((c - failAt) / FAIL_LEN);
            drawStrangle(b, w, h, c, meter, true, 0);
            b.setColor(new Color(0, 0, 0, (int) (255 * f)));                      // and it all goes dark
            b.fillRect(0, 0, w, h);
        } else if (c < RIFT_END) {
            drawStrangle(b, w, h, c, 0, false, 0);
            drawCaptions(b, w, h, c);
        } else {
            drawStrangle(b, w, h, c, meter, false, 0);
            drawChokeHud(b, w, h, c);
        }
        b.dispose();
    }

    // ---------------------------------------------------------------- what he says

    private void drawCaptions(Graphics2D b, int w, int h, double c) {
        Object[] line = null;
        for (Object[] m : MONO) {
            if (c >= (Double) m[0] && c <= (Double) m[1] + 200) line = m;
        }
        String jp = null, en = null;
        double from = 0, to = 0;
        if (line != null) {
            jp = (String) line[2];
            en = (String) line[3];
            from = (Double) line[0];
            to = (Double) line[1];
        } else if (c >= WHO_FROM && c <= WHO_TO + 200) {
            jp = WHO_JP;
            en = WHO_EN;
            from = WHO_FROM;
            to = WHO_TO;
        }
        if (jp != null) {
            double a = Math.min(1, (c - from) / 300.0) * (1 - smooth((c - to) / 200.0));
            if (new Random((long) (c / 80)).nextInt(9) == 0) a *= 0.45;
            ghosted(b, jp, w / 2.0, h * 0.8, h * 0.065, a, false);
            ghosted(b, en, w / 2.0, h * 0.88, h * 0.04, a, false);
        }
        if (c < 1200) {                                              // out of the dark
            b.setColor(new Color(0, 0, 0, (int) (255 * (1 - c / 1200.0))));
            b.fillRect(0, 0, w, h);
        }
    }

    // ---------------------------------------------------------------- the choke, seen from the side

    private static final int RIGHT_ARM = SkinModel.Part.RIGHT_ARM.ordinal();
    private static final int LEFT_ARM = SkinModel.Part.LEFT_ARM.ordinal();
    private static final int RIGHT_LEG = SkinModel.Part.RIGHT_LEG.ordinal();
    private static final int LEFT_LEG = SkinModel.Part.LEFT_LEG.ordinal();
    private static final int HEAD = SkinModel.Part.HEAD.ordinal();

    private Actor glitcherActor, honchoActor;
    private SoftRenderer glitcherRender, honchoRender;
    private double figureUnit;
    private java.awt.geom.Rectangle2D.Double hitBox = new java.awt.geom.Rectangle2D.Double();

    /** What the Glitcher does with his hands at each of the five lines: the right hand's point, the left's (null = at rest). */
    private static final double[][] GESTURE_R = {{-7.2, 20.0, 7.0}, {-1.0, 19.0, 4.8}, {-9.8, 17.5, 3.5}, {-2.6, 18.5, 7.0}, {-9.6, 17.0, 3.5}};
    private static final double[][] GESTURE_L = {null, null, {9.8, 17.5, 3.5}, {2.6, 18.5, 7.0}, {9.6, 17.0, 3.5}};

    /**
     * The room on the desktop, from the first word to the end of the choke: the Glitcher speaking to us with his
     * face to the screen, the portal opening, Honcho thrown out of it, the Glitcher turning and taking him by the
     * throat - and then the choke, seen from the side, where the player has to knock him off by clicking on him.
     *
     * @param m     how far he has been pushed back, 0..1
     * @param limp  Honcho has gone slack
     * @param freed 0 while he is held, growing to 1 as he is let go and drops
     */
    private void drawStrangle(Graphics2D b, int w, int h, double c, double m, boolean limp, double freed) {
        double unit = h * 0.62 / 32.0;
        if (glitcherRender == null || figureUnit != unit) {
            figureUnit = unit;
            glitcherActor = new Actor(glitcherSkin, false);
            honchoActor = new Actor(honchoSkin, true);
            glitcherRender = new SoftRenderer((int) (60 * unit), (int) (50 * unit));
            honchoRender = new SoftRenderer((int) (60 * unit), (int) (50 * unit));
        }
        b.setColor(new Color(6, 3, 10, c < PORTAL_FROM ? 80 : 120));           // the real desktop is the room they are in
        b.fillRect(0, 0, w, h);
        backdrop.render(b, w, h, c + 50_000, 0.12);
        // A dim light on the two of them.
        b.setPaint(new RadialGradientPaint((float) (w * 0.5), (float) (h * 0.55), (float) (h * 0.8), new float[]{0f, 1f},
                new Color[]{new Color(120, 90, 200, 40), new Color(0, 0, 0, 140)}));
        b.fillRect(0, 0, w, h);

        double t = c / 1000.0;
        double ground = h * 0.92;
        double turnPos = smooth((c - 14_300) / 1300.0);                      // he stands in the middle to speak, then steps aside to the portal's line
        double gx = w * (0.5 + 0.13 * turnPos + 0.14 * m + 0.13 * freed);
        double px = w * 0.2, py = h * 0.5;                                   // the portal stands on the left
        double portalA = smooth((c - PORTAL_FROM) / 1800.0) * (1 - smooth((c - 20_600) / 1000.0));
        double burst = Math.sin(Math.PI * Math.max(0, Math.min(1, (c - (EXIT_FROM - 200)) / 1100.0)));
        if (c >= PORTAL_FROM && winAt < 0) PortalFx.draw(b, px, py, h * 0.12, h * 0.25, portalA, t, h, burst);

        // ---- the Glitcher
        double turn = smooth((c - 14_300) / 1300.0);                         // from facing us to facing the portal
        double prep = smooth((c - EXIT_FROM) / 900.0) * (1 - smooth((c - GRAB_FROM) / 600.0));
        double grab = smooth((c - GRAB_FROM) / 700.0);
        double hold = Math.max(grab, c >= RIFT_END ? 1 : 0) * (1 - freed);
        double breath = Math.sin(t * 1.5);
        {
            Actor a = glitcherActor;
            Pose15 p = a.begin();
            a.viewYaw = -1.15 * turn;
            a.viewPitch = -0.32 * freed + 0.05 * Math.sin(t * 9) * hold * (1 - freed);
            p.reach(Rig15.Limb.R_LEG, -2.6, 3, -0.6 - 1.2 * turn, 1).reach(Rig15.Limb.L_LEG, 2.6, 3, 0.6 + 2.0 * turn, 1);
            p.rootPos[1] = -0.8 - 0.4 * breath * (1 - turn) - 0.5 * Math.sin(t * 3.1) * hold;
            p.rootPos[0] = 0.5 * Math.sin(t * 0.7) * (1 - turn);
            // The gestures of the five questions.
            double[] gw = new double[MONO.length];
            double sumR = 0, sumL = 0;
            double[] tr = new double[3], tl = new double[3];
            for (int i = 0; i < MONO.length; i++) {
                double from = (Double) MONO[i][0], to = (Double) MONO[i][1];
                gw[i] = smooth((c - (from - 350)) / 450.0) * (1 - smooth((c - (to + 100)) / 500.0));
                sumR += gw[i];
                for (int k = 0; k < 3; k++) tr[k] += GESTURE_R[i][k] * gw[i];
                if (GESTURE_L[i] != null) {
                    sumL += gw[i];
                    for (int k = 0; k < 3; k++) tl[k] += GESTURE_L[i][k] * gw[i];
                }
            }
            // The right hand: a gesture, then held out for Honcho, then at his throat.
            double[] prepPt = {-3.3, 24.5, 9.0}, holdPt = {-3.3, 25.5, 9.5};   // arm raised: he is held up off the floor, at arm's length
            double wr = sumR + prep * 0.8 + hold * 3;
            double[] rt = new double[3];
            for (int k = 0; k < 3; k++) rt[k] = (tr[k] + prepPt[k] * prep * 0.8 + holdPt[k] * hold * 3) / Math.max(1e-6, wr);
            if (wr > 0.01) a.reach(Rig15.Limb.R_ARM, rt[0], rt[1], rt[2], Math.min(1, wr));
            if (sumL > 0.01 && hold < 0.5) a.reach(Rig15.Limb.L_ARM, tl[0] / sumL, tl[1] / sumL, tl[2] / sumL, Math.min(1, sumL) * (1 - hold));
            p.turn(Rig15.Joint.R_UPPER_ARM, 4 * freed, 0, -4 - 6 * freed);
            p.turn(Rig15.Joint.L_UPPER_ARM, (-2 + 2 * breath) * (1 - hold) + (-17 + 11 * Math.sin(t * 5)) * hold, 0, 5 + 9 * hold);
            p.turn(Rig15.Joint.L_LOWER_ARM, -8 * (1 - hold) - (24 + 8 * Math.sin(t * 5 + 1)) * hold, 0, 0);
            p.turn(Rig15.Joint.R_LOWER_ARM, -8, 0, 0);
            double nod = gw[3] * 22 + gw[1] * 10 + gw[0] * -2 + gw[2] * -4;
            p.turn(Rig15.Joint.UPPER_TORSO, 2 + breath + 6 * gw[3] + (3 + 1.5 * Math.sin(t * 6) * hold) * turn, 10 * hold, 0);
            p.turn(Rig15.Joint.HEAD, -1 + nod * (1 - turn) + (9 - 14 * freed) * turn, (12 * Math.sin(t * 5) * gw[4]) * (1 - turn) - 6 * hold, 7 * gw[0] - 6 * gw[2]);
            a.shapeR = hold > 0.3 ? Actor.Hand.GRIP : (gw[1] > 0.3 ? Actor.Hand.LOOSE : Actor.Hand.OPEN);      // the fingers close round the throat
            a.fistR = Math.max(0.3 * gw[1], 0.6 * grab * (1 - freed));
            a.shapeL = gw[2] + gw[3] + gw[4] > 0.3 ? Actor.Hand.OPEN : Actor.Hand.LOOSE;
            a.fistL = 0.25 * hold;
            a.spread = 1 - 0.7 * hold;
        }
        glitcherRender.clear();
        glitcherActor.draw(glitcherRender, unit, glitcherRender.width / 2.0, glitcherRender.height - 6 * unit);
        double gDrawX = gx - glitcherRender.width / 2.0;
        double gDrawY = ground - glitcherRender.height + 6 * unit;
        hitBox.setRect(gx - 10 * unit, ground - 36 * unit, 20 * unit, 38 * unit);

        // ---- Honcho: pushed out of the portal, tumbling, caught by the throat, then hanging and fighting for air.
        // Where his neck is when he is held: at the fist (seen from the side it is about 10 units ahead of the Glitcher and 25.5 up: the arm stretched out level),
        // a little beyond it, and he hangs in profile, so that the two bodies stay well clear of each other.
        double hxN = gx - 10.6 * unit, hyN = ground - 25.3 * unit;
        if (c >= EXIT_FROM) {
            double flightMs = GRAB_FROM + 500 - EXIT_FROM, tArrive = GRAB_FROM + 500;
            double ex = Math.min(1, (c - EXIT_FROM) / flightMs);
            double hitAge = c - tArrive;                                     // ms since he was caught
            double impact = hitAge >= 0 ? Math.exp(-hitAge / 150.0) : 0;
            double pend = hitAge >= 0 ? 0.3 * Math.exp(-hitAge / 1200.0) * Math.cos(hitAge * 0.0058) : 0;
            double fatigue = limp ? 1 : Math.min(1, Math.max(0, (c - RIFT_END) / CHOKE_MS) * (1 - 0.6 * m));
            double[] flight = honchoFlight(ex, px, py, hxN, hyN, h);         // {x, y, turn, scale}
            double arrive = smooth((ex - 0.75) / 0.25);
            double flail = (1 - 0.9 * fatigue) * (1 - freed) * arrive;
            double gasp = Math.pow(Math.max(0, Math.sin(t * 6.0)), 4) * (0.3 + 0.7 * (1 - fatigue)) * arrive;
            Actor a = honchoActor;
            Pose15 p = a.begin();
            double fe = 1 - Math.pow(1 - ex, 2.0);
            a.viewYaw = 1.45 + (1 - fe) * (1 - fe) * Math.PI * 3.2;                // turning in the air, settling facing the Glitcher
            a.viewPitch = -0.25 * (1 - fe) * Math.sin(ex * 9) + 0.2 * impact;
            double fl1 = Math.sin(ex * 19 - 0.3), fl2 = Math.sin(ex * 19 - 0.9), fl3 = Math.sin(ex * 23 + 1.2), fl4 = Math.sin(ex * 17 + 2.3);
            double limpW = 1 - arrive;                                       // in the air: the limbs are loose and trail behind
            double emerge = 1 - smooth(ex / 0.25);                           // at the mouth of the portal the arms are still flung ahead
            double kick = Math.sin(t * 15) * flail, kick2 = Math.sin(t * 15 + 2.2) * flail;
            // the arms: flung ahead, trailing, then up to the fist at his throat
            double armUp = -160 * emerge + (-135 + 45 * fl1) * (1 - emerge);
            double grabIk = Math.min(1, 1.3 * flail + 0.25 * impact);
            double clutch = 0.6 + 0.3 * Math.sin(t * 13);
            a.reach(Rig15.Limb.R_ARM, -2.2 + 1.2 * Math.sin(t * 17) * flail - 0.6 * gasp, 26.5 + 1.0 * Math.cos(t * 13) * flail + 0.8 * gasp, 3.6, grabIk);
            a.reach(Rig15.Limb.L_ARM, 2.2 + 1.2 * Math.cos(t * 19) * flail + 0.6 * gasp, 26.0 + 1.0 * Math.sin(t * 14) * flail + 0.8 * gasp, 3.8, grabIk);
            p.turn(Rig15.Joint.R_UPPER_ARM, 8 * arrive + armUp * limpW, 0, -8 - (30 + 10 * fl2) * limpW)
                    .turn(Rig15.Joint.L_UPPER_ARM, 10 * arrive + ((-115 + 45 * fl2) * (1 - emerge) - 160 * emerge) * limpW, 0, 8 + (30 + 10 * fl3) * limpW);
            p.turn(Rig15.Joint.R_LOWER_ARM, (-30 - 25 * fl3) * limpW, 0, 0).turn(Rig15.Joint.L_LOWER_ARM, (-40 - 25 * fl1) * limpW, 0, 0);
            // the legs: scrabbling in the air once he hangs, loose in flight, whipped forward when he is caught
            double whip = -55 * impact;
            p.turn(Rig15.Joint.R_UPPER_LEG, 46 * kick - 6 * arrive + (-45 + 30 * fl2) * limpW + whip, 0, 4)
                    .turn(Rig15.Joint.L_UPPER_LEG, -46 * kick2 - 6 * arrive + (20 + 30 * fl3) * limpW + whip * 0.7, 0, -4);
            p.turn(Rig15.Joint.R_LOWER_LEG, 48 + 34 * Math.max(0, -kick) + (20 + 25 * fl1) * limpW + 25 * impact, 0, 0)
                    .turn(Rig15.Joint.L_LOWER_LEG, 55 + 34 * Math.max(0, kick2) + (15 + 25 * fl2) * limpW + 25 * impact, 0, 0);
            p.turn(Rig15.Joint.R_FOOT, 25 - 10 * Math.max(0, kick), 0, 0).turn(Rig15.Joint.L_FOOT, 25 - 10 * Math.max(0, -kick2), 0, 0);
            // the body: arches in the air, snaps when caught, heaves with every gasp, then sags
            p.turn(Rig15.Joint.UPPER_TORSO, -4 - 8 * fatigue + 5 * kick - 10 * limpW * fl2 - 16 * impact - 7 * gasp, 6 * kick2 + 10 * limpW * fl3, 3 * Math.sin(t * 9) * flail);
            p.turn(Rig15.Joint.HEAD, -14 - 29 * fatigue - 12 * limpW * fl4 - 34 * impact - 14 * gasp, 8 * Math.sin(t * 11) * flail + 12 * limpW * fl2, 6 * Math.sin(t * 9) * flail + 5 * gasp * Math.sin(t * 40));
            p.rootPos[1] = -0.6 * gasp - 0.5 * impact;
            a.hands(limpW > 0.5 ? Actor.Hand.OPEN : Actor.Hand.GRIP, limpW > 0.5 ? Actor.Hand.OPEN : Actor.Hand.GRIP);      // thrown: open, flung; held: clawing at the fist on his throat
            a.fistR = a.fistL = arrive * (0.3 * flail * clutch);
            a.spread = 0.4 + 0.6 * limpW;
            honchoRender.clear();
            honchoActor.draw(honchoRender, unit, honchoRender.width / 2.0, honchoRender.height - 6 * unit);
            double drop = freed * freed * h * 0.9;
            java.awt.Shape keepClip = b.getClip();
            if (ex < 0.22) b.clip(new java.awt.geom.Rectangle2D.Double(px, 0, w, h));   // he is still coming through the portal
            // the air leaves streaks behind him
            if (ex > 0.05 && ex < 0.97) {
                for (int g = 3; g >= 1; g--) {
                    double[] gp = honchoFlight(Math.max(0, ex - 0.045 * g), px, py, hxN, hyN, h);
                    drawHoncho(b, unit, gp[0], gp[1], gp[2], gp[3], 1, 1, 0.2 / g, false);
                }
            }
            double sx = 1 - 0.45 * smooth(1 - ex / 0.12) * (ex < 0.12 ? 1 : 0), sy = 1 + 0.3 * (ex < 0.12 ? smooth(1 - ex / 0.12) : 0) - 0.07 * impact;
            double shakeX = (Math.random() - 0.5) * h * 0.012 * impact, shakeY = (Math.random() - 0.5) * h * 0.012 * impact;
            drawHoncho(b, unit, flight[0] + shakeX, flight[1] + drop + shakeY, flight[2] + pend + 0.36 * arrive * (1 - freed) + 0.3 * freed + Math.sin(t * 8) * 0.03 * flail, flight[3], sx, sy, 1, true);
            b.setClip(keepClip);
        }

        b.drawImage(glitcherRender.image, (int) gDrawX, (int) gDrawY, null);       // on top, so his arm and fist are in front of Honcho

        // The dark closing in as the time runs out, red at its edge.
        double left = Math.max(0, RIFT_END + CHOKE_MS - c);
        if (winAt < 0 && c >= RIFT_END) {
            double danger = 1 - Math.min(1, left / CHOKE_MS);
            b.setPaint(new RadialGradientPaint((float) (w / 2.0), (float) (h / 2.0), (float) (Math.hypot(w, h) / 2), new float[]{0.55f, 1f},
                    new Color[]{new Color(120, 0, 10, 0), new Color(120, 0, 10, (int) (140 * danger * (0.6 + 0.4 * Math.sin(c * 0.012))))}));
            b.fillRect(0, 0, w, h);
        }
        // The portal tears open in a flash.
        double flash = c >= PORTAL_FROM ? Math.max(0, 1 - (c - PORTAL_FROM) / 600.0) : 0;
        if (flash > 0) {
            b.setColor(new Color(255, 255, 255, (int) (150 * flash)));
            b.fillRect(0, 0, w, h);
        }
        // Every press knocks the picture a moment.
        double since = c - lastPress;
        if (since >= 0 && since < 140) {
            b.setColor(new Color(255, 255, 255, (int) (60 * (1 - since / 140.0))));
            b.fillRect(0, 0, w, h);
        }
    }

    /** Honcho's neck on the way from the portal to the fist: {x, y, turn (rad), scale}. Fast off the mark, slowing as he arrives. */
    private double[] honchoFlight(double ex, double px, double py, double hxN, double hyN, int h) {
        double fe = 1 - Math.pow(1 - ex, 2.0);
        double x = px + (hxN - px) * fe;
        double y = py + (hyN - py) * fe - Math.sin(Math.PI * fe) * h * 0.14;
        double turn = -(1 - fe) * (1 - fe) * Math.PI * 2.4;
        double scale = 0.35 + 0.65 * smooth(ex / 0.6);
        return new double[]{x, y, turn, scale};
    }

    /** Draws his sprite with the neck at (x, y), turned about the neck. {@code pivotNeck} true for the real figure. */
    private void drawHoncho(Graphics2D b, double unit, double x, double y, double turn, double scale, double sx, double sy, double alpha, boolean real) {
        Graphics2D d = (Graphics2D) b.create();
        if (alpha < 1) d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alpha));
        d.translate(x, y);
        d.rotate(turn);
        d.scale(scale * sx, scale * sy);
        d.translate(0, 24 * unit);
        d.drawImage(honchoRender.image, -honchoRender.width / 2, -honchoRender.height + (int) (6 * unit), null);
        d.dispose();
    }

    private void drawChokeHud(Graphics2D b, int w, int h, double c) {
        double left = Math.max(0, RIFT_END + CHOKE_MS - c);
        b.setFont(new Font(Font.DIALOG, Font.BOLD, (int) (h * 0.032)));
        String title = "СПАСИ ХОНЧО: КЛИКАЙ ПО ГЛИТЧЕРУ";
        int tw = b.getFontMetrics().stringWidth(title);
        b.setColor(new Color(0, 0, 0, 200));
        b.drawString(title, (w - tw) / 2 + 2, (int) (h * 0.07) + 2);
        b.setColor(new Color(255, 90, 100));
        b.drawString(title, (w - tw) / 2, (int) (h * 0.07));
        b.setFont(new Font(Font.DIALOG, Font.BOLD, (int) (h * 0.11)));
        String secs = String.format(java.util.Locale.ROOT, "%.1f", left / 1000.0);
        int sw = b.getFontMetrics().stringWidth(secs);
        b.setColor(new Color(0, 0, 0, 200));
        b.drawString(secs, (w - sw) / 2 + 3, (int) (h * 0.18) + 3);
        b.setColor(left < 4000 ? new Color(255, 70, 80) : Color.WHITE);
        b.drawString(secs, (w - sw) / 2, (int) (h * 0.18));

        double bw = w * 0.4, bh = h * 0.035, bx = (w - bw) / 2, by = h * 0.94;
        b.setColor(new Color(0, 0, 0, 190));
        b.fillRoundRect((int) bx - 6, (int) by - 6, (int) bw + 12, (int) bh + 12, 14, 14);
        b.setColor(new Color((int) (255 * (1 - meter)), (int) (90 + 165 * meter), 90));
        b.fillRoundRect((int) bx, (int) by, (int) (bw * meter), (int) bh, 10, 10);
        b.setColor(new Color(255, 255, 255, 220));
        b.setStroke(new BasicStroke(2f));
        b.drawRoundRect((int) bx, (int) by, (int) bw, (int) bh, 10, 10);
        b.setFont(new Font(Font.DIALOG, Font.BOLD, (int) (h * 0.024)));
        String hint = c < RIFT_END + 4_000 ? "жми на него мышью (или пробел, Enter, Z, X)" : "ЖМИ!";
        int hw = b.getFontMetrics().stringWidth(hint);
        b.setColor(new Color(0, 0, 0, 200));
        b.drawString(hint, (w - hw) / 2 + 2, (int) (by - h * 0.012) + 2);
        b.setColor(Color.WHITE);
        b.drawString(hint, (w - hw) / 2, (int) (by - h * 0.012));
        // A ring round where to press, at first.
        if (c < RIFT_END + 3_500) {
            double pulse = 0.5 + 0.5 * Math.sin(c * 0.012);
            b.setColor(new Color(255, 255, 255, (int) (200 * pulse)));
            b.setStroke(new BasicStroke(3f));
            b.draw(new java.awt.geom.RoundRectangle2D.Double(hitBox.x - 8, hitBox.y - 8, hitBox.width + 16, hitBox.height + 16, 24, 24));
        }
    }

    // ---------------------------------------------------------------- pushed off; the rules; the fall

    private void drawAfterWin(Graphics2D b, int w, int h, double since) {
        double c = now();
        if (since < ANGRY_AFTER) {
            // Knocked back: the face flies off and the hands let go.
            drawStrangle(b, w, h, c, 1, false, smooth(since / ANGRY_AFTER));
            return;
        }
        double s = since - ANGRY_AFTER;
        if (s < ANGRY_LEN) {
            // Furious.
            if (shatter == null) shatter = new DeskShatter(desktopPicture(w, h), w, h);
            b.setColor(new Color(60, 0, 10, 165));
            b.fillRect(0, 0, w, h);
            backdrop.render(b, w, h, c + 50_000, 0.3);
            Random sh = new Random((long) (c / 35));
            AffineTransform old = b.getTransform();
            double amp = h * 0.012;
            b.translate((sh.nextDouble() - 0.5) * amp, (sh.nextDouble() - 0.5) * amp);
            drawFace(b, w * 0.5, h * 0.4, h * (0.55 + 0.05 * Math.sin(c * 0.02)), 0.95, c, 1, new Color(200, 0, 30, 110));
            b.setTransform(old);
            double a = smooth(s / 300.0) * (1 - smooth((s - (ANGRY_LEN - 300)) / 300.0));
            ghosted(b, ANGRY_JP, w / 2.0, h * 0.8, h * 0.066, a, true);
            ghosted(b, ANGRY_EN, w / 2.0, h * 0.88, h * 0.04, a, true);
            return;
        }
        // The desktop cracks and comes down; the fall goes on beneath it.
        double k = s - ANGRY_LEN;
        fall.render(b, w, h, k, 1.6 + smooth(k / 1500.0));
        if (shatter == null) shatter = new DeskShatter(desktopPicture(w, h), w, h);
        shatter.render(b, k);
    }

    private DeskShatter shatter;

    private BufferedImage desktopPicture(int w, int h) {
        if (desktop != null) return desktop;
        if (fakeDesktop == null) {
            fakeDesktop = AeroWallpaper.paint(w, h, 1);
            Graphics2D g = fakeDesktop.createGraphics();
            g.setColor(new Color(20, 30, 60, 235));
            g.fillRect(0, h - (int) (h * 0.045), w, (int) (h * 0.045));
            g.dispose();
        }
        return fakeDesktop;
    }

    // ---------------------------------------------------------------- pieces

    private BufferedImage faceBuf;

    /**
     * The Glitcher's face - the front of his skin's head - large, pixelated, torn into strips as he speaks, its
     * edges melting into the dark (no box round it).
     */
    private void drawFace(Graphics2D b, double cx, double cy, double size, double alpha, double ms, double agitation, Color tint) {
        int S = (int) Math.ceil(size);
        if (alpha <= 0 || S < 4) return;
        if (faceBuf == null || faceBuf.getWidth() < S) faceBuf = new BufferedImage(Math.max(S, 64), Math.max(S, 64), BufferedImage.TYPE_INT_ARGB);
        Graphics2D f = faceBuf.createGraphics();
        f.setComposite(AlphaComposite.Clear);
        f.fillRect(0, 0, faceBuf.getWidth(), faceBuf.getHeight());
        f.setComposite(AlphaComposite.SrcOver);
        f.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        Random rnd = new Random((long) (ms / 70));
        int strips = 18, sh = (int) Math.ceil(S / (double) strips);
        for (int i = 0; i < strips; i++) {
            int dx = rnd.nextDouble() < 0.14 * agitation ? (int) ((rnd.nextDouble() - 0.5) * S * 0.1 * (0.4 + agitation)) : 0;
            f.drawImage(face, dx, i * sh, S + dx, (i + 1) * sh, 0, i * 8 / strips, 8, Math.max(i * 8 / strips + 1, (i + 1) * 8 / strips), null);
        }
        // Melt the edge into nothing, then colour it if asked.
        f.setComposite(AlphaComposite.DstIn);
        f.setPaint(new RadialGradientPaint(S / 2f, S / 2f, S / 2f, new float[]{0.5f, 1f},
                new Color[]{new Color(255, 255, 255, 255), new Color(255, 255, 255, 0)}));
        f.fillRect(0, 0, S, S);
        if (tint != null) {
            f.setComposite(AlphaComposite.SrcAtop);
            f.setColor(tint);
            f.fillRect(0, 0, S, S);
        }
        f.dispose();
        Graphics2D o = (Graphics2D) b.create();
        o.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.min(1, alpha)));
        o.drawImage(faceBuf, (int) (cx - S / 2.0), (int) (cy - S / 2.0), (int) (cx + S / 2.0), (int) (cy + S / 2.0), 0, 0, S, S, null);
        o.dispose();
    }

    private static void vignette(Graphics2D b, int w, int h, double radius, int darkness) {
        radius = Math.max(4, radius);
        b.setPaint(new RadialGradientPaint((float) (w / 2.0), (float) (h * 0.45), (float) radius, new float[]{0.35f, 1f},
                new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, darkness)}));
        b.fillRect(0, 0, w, h);
        // Outside the circle it is black.
        b.setColor(new Color(0, 0, 0, darkness));
        java.awt.geom.Area outside = new java.awt.geom.Area(new java.awt.geom.Rectangle2D.Double(0, 0, w, h));
        outside.subtract(new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Double(w / 2.0 - radius, h * 0.45 - radius, radius * 2, radius * 2)));
        b.fill(outside);
    }

    private static void ghosted(Graphics2D g, String text, double cx, double y, double size, double alpha, boolean angry) {
        g.setFont(new Font(Font.DIALOG, Font.BOLD, (int) size));
        int tw = g.getFontMetrics().stringWidth(text);
        int x = (int) (cx - tw / 2.0);
        int a = (int) (255 * Math.max(0, Math.min(1, alpha)));
        g.setColor(new Color(255, 40, 80, a / 2));
        g.drawString(text, x - 5, (int) y + 2);
        g.setColor(new Color(40, 220, 255, a / 2));
        g.drawString(text, x + 5, (int) y - 2);
        g.setColor(angry ? new Color(255, 120, 130, a) : new Color(255, 255, 255, a));
        g.drawString(text, x, (int) y);
    }
}
