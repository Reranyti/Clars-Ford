package com.lotusblight.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * What gets in the player's way during the rhythm game: the Glitcher himself wandering across and, now
 * and then, a purple wall sliding over the playfield to hide the circles behind it. Drawn on top of the
 * circles so it really does block them. Purely visual - it never changes the notes, only how well you
 * can see and reach them.
 */
final class Hazards {
    private static final double FAKE_START_MS = 81_000;   // 1:21 - the Glitcher starts spewing decoys

    private static final double WATCH_START_MS = 92_000; // 1:32 - the field draws away, something looks on
    private static final double RETURN_MS = 104_000;      // 1:44 - all of it goes, back to the wallpaper
    private static final double AERO_IN_MS = 30_000;      // 0:30 - the desktop turns into an Aero wallpaper
    private static final int EYES = 800;
    private static final int PER_RING = 26;             // eyes in one ring; the rings grow outwards like scales
    private static final double RING_GROWTH = 1.125;
    private static final double SPIN_RAMP_S = 6.0;         // how long the eyes take to get up to speed
    private static final double SPIN_MAX = 0.6;            // rad/s once they do

    private final GlitcherActor actor;
    private java.util.List<BufferedImage> icons;
    private final BufferedImage eyeSprite = makeEyeSprite();
    // Each eye: polar angle, distance from the centre (0 hole edge .. 1 screen rim), tilt jitter, size jitter.
    private final double[][] eyes = new double[EYES][4];
    private final double[][] stars = new double[320][4];     // x, y (0..1), brightness, twinkle
    private final double[][] streaks = new double[520][3];   // angle, how far out, brightness

    /** How far the Glitcher is turned about the middle of the eyes (the Chromo difficulty spins him). */
    double actorSpin;

    Hazards(int[] skin, int height) {
        java.util.Random rnd = new java.util.Random(81);
        for (double[] s : stars) { s[0] = rnd.nextDouble(); s[1] = rnd.nextDouble(); s[2] = 0.3 + rnd.nextDouble() * 0.7; s[3] = rnd.nextDouble(); }
        for (double[] s : streaks) { s[0] = rnd.nextDouble() * Math.PI * 2; s[1] = Math.pow(rnd.nextDouble(), 1.6); s[2] = rnd.nextDouble(); }
        for (double[] e : eyes) {
            e[0] = rnd.nextDouble() * Math.PI * 2;
            e[1] = Math.pow(rnd.nextDouble(), 0.75);
            e[2] = (rnd.nextDouble() - 0.5) * 1.2;
            e[3] = 0.8 + rnd.nextDouble() * 0.5;
        }
        this.actor = new GlitcherActor(skin, height);
    }

    void render(Graphics2D g, int w, int h, double timeMs) {
        double t = timeMs / 1000.0;

        // From 1:21 the eyes open all over the screen and he goes to stand in the middle of them.
        // From 1:32 the whole field draws away and something behind it looks on; at 1:44 it is all gone.
        double k = fieldAmount(timeMs);
        double z = recede(timeMs);
        double fs = 1 - 0.7 * z;                                   // how much of its size the field keeps
        double fcy = h * 0.5 + h * 0.24 * z;                       // and where it sits

        // The Glitcher walks, throws icons, stomps and rises to the middle of the eyes.
        if (icons == null && timeMs >= GlitcherActor.COLLECT_FROM - 2000) icons = loadIcons();
        if (!actorBehind(timeMs)) renderActor(g, w, h, timeMs, fcy, fs);
        actor.renderEffects(g, w, h, timeMs, h * 0.72);
    }

    private static java.util.List<BufferedImage> loadIcons() {
        java.util.List<BufferedImage> found = GameMain.desktopIcons();
        return found.isEmpty() ? GlitcherActor.fallbackIcons() : found;
    }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    /** How much of the eye field is there: opens from 1:21, is gone again by 1:44. */
    /** The state of the first song's scenery at a time, for the playfield to follow (see {@link PlayfieldFx}). */
    static double fieldAt(double ms) { return fieldAmount(ms); }

    static double recedeAt(double ms) { return recede(ms); }

    static double aeroAt(double ms) { return aeroAmount(ms); }

    private static double fieldAmount(double timeMs) {
        return smooth((timeMs - FAKE_START_MS) / 1000.0) * (1 - smooth((timeMs - RETURN_MS) / 1500.0));
    }

    /** How far the field has drawn away: 0 up to 1:32, 1 while it looks on, back to 0 as it all ends. */
    private static double recede(double timeMs) {
        return smooth((timeMs - WATCH_START_MS) / 2500.0) * (1 - smooth((timeMs - RETURN_MS) / 1500.0));
    }

    /** How much of the Aero wallpaper shows: in from 0:30, torn away for the eyes at 1:20, back at 1:44. */
    private static double aeroAmount(double ms) {
        double first = ms < GlitcherActor.BLACKOUT_MS ? smooth((ms - AERO_IN_MS) / 3000.0) : 0;
        double again = smooth((ms - RETURN_MS) / 2000.0);
        return Math.max(first, again);
    }

    /** Glitching only while it is being torn away or put back; the slow fade in at 0:30 is smooth. */
    private static double aeroGlitch(double ms) {
        if (ms >= RETURN_MS && ms < RETURN_MS + 2000) return 1 - (ms - RETURN_MS) / 2000.0;
        if (ms >= RETURN_MS + 2000) {
            // Afterwards the picture rots: it tears more often, and harder, as the end of the song comes.
            double grow = smooth((ms - 108_000) / 14_000.0);
            java.util.Random r = new java.util.Random((long) (ms / 110));
            return r.nextDouble() < 0.2 + 0.4 * grow ? 0.12 + 0.55 * grow : 0;
        }
        return 0;
    }

    // The wallpaper before the eyes and the (different) one the desktop comes back to after them.
    private final BufferedImage[] aeroPics = new BufferedImage[2];

    private void drawAero(Graphics2D g, int w, int h, double ms) {
        drawAeroPicture(g, w, h, ms, aeroAmount(ms), ms >= RETURN_MS ? 1 : 0, aeroGlitch(ms));
    }

    private void drawAeroPicture(Graphics2D g, int w, int h, double ms, double a, int variant, double glitch) {
        if (a <= 0) return;
        BufferedImage aero = aeroPics[variant];
        if (aero == null || aero.getWidth() != w || aero.getHeight() != h) {
            aero = AeroWallpaper.paint(w, h, variant);
            aeroPics[variant] = aero;
        }
        double t = ms / 1000.0;
        Graphics2D b = (Graphics2D) g.create();
        b.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, (float) a));
        if (glitch <= 0) {
            b.drawImage(aero, 0, 0, null);
        } else {
            // The picture comes apart in strips that slide sideways, and the colour channels drift.
            java.util.Random rnd = new java.util.Random((long) (ms / 90));
            int strips = 26;
            int sh = h / strips + 1;
            for (int i = 0; i < strips; i++) {
                int y = i * sh;
                int dx = (int) ((rnd.nextDouble() - 0.5) * 2 * w * 0.09 * glitch);
                b.drawImage(aero, dx, y, dx + w, Math.min(h, y + sh), 0, y, w, Math.min(h, y + sh), null);
            }
        }
        // Soft bubbles rising, a little different every moment.
        AeroWallpaper.bubbles(b, w, h, t, a);
        b.dispose();
    }

    /**
     * The first seconds of the second song still show what the first one ended on - the rotting wallpaper and
     * its eyes - and let them melt away, tearing more and more, into the desktop.
     */
    void renderCarryover(Graphics2D g, int w, int h, double t2) {
        if (t2 > 7_000) return;
        double a = 1 - smooth((t2 - 1_500) / 5_000.0);
        if (a <= 0) return;
        double glitch = 0.35 + 0.5 * smooth(t2 / 6_000.0);
        drawAeroPicture(g, w, h, 125_000 + t2, a, 1, glitch);
        drawWatchers(g, w, h, 125_000 + t2, a);
    }

    /** The Glitcher in the second song's first minute: forms again, throws error windows, falls apart at 1:17. */
    double[] songTwoHand(double ms, int w, int h) {
        return actor.songTwoHand(ms, w, h);
    }

    double[] songTwoHead(double ms, int w, int h) {
        return actor.songTwoHead(ms, w, h);
    }

    void renderSong2(Graphics2D g, int w, int h, double t2) {
        actor.renderSong2(g, w, h, t2);
    }

    private static final double WATCH_FROM_MS = 108_500;

    /** After the eyes, more of them open on the wallpaper, one after another, all looking at the middle. */
    private void drawWatchers(Graphics2D g, int w, int h, double ms) {
        drawWatchers(g, w, h, ms, 1.0);
    }

    private void drawWatchers(Graphics2D g, int w, int h, double ms, double fade) {
        if (ms < WATCH_FROM_MS || fade <= 0) return;
        Graphics2D b = (Graphics2D) g.create();
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        java.util.Random rnd = new java.util.Random(1976);
        double cx = w / 2.0, cy = h / 2.0;
        for (int i = 0; i < 16; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2, rad = 0.3 + 0.2 * rnd.nextDouble();
            double size = h * (0.045 + 0.07 * rnd.nextDouble()), phase = rnd.nextDouble() * 7;
            double a = smooth((ms - (WATCH_FROM_MS + i * 750)) / 600.0) * fade;
            if (a <= 0) continue;
            double ex = cx + Math.cos(ang) * w * rad, ey = cy + Math.sin(ang) * h * rad * 1.05;
            boolean blinking = ((ms / 1000.0) * 0.4 + phase) % 1.0 < 0.05;
            double hw = size * 1.1, hh = size * (blinking ? 0.08 : 0.5);
            java.awt.geom.Path2D.Double almond = new java.awt.geom.Path2D.Double();
            almond.moveTo(ex - hw, ey);
            almond.quadTo(ex, ey - hh * 1.7, ex + hw, ey);
            almond.quadTo(ex, ey + hh * 1.7, ex - hw, ey);
            almond.closePath();
            b.setColor(new Color(246, 240, 246, (int) (232 * a)));
            b.fill(almond);
            if (!blinking) {
                double dx = cx - ex, dy = cy - ey, len = Math.max(1, Math.hypot(dx, dy));
                double ix = ex + dx / len * hw * 0.28, iy = ey + dy / len * hh * 0.25, ir = Math.min(hh * 0.95, hw * 0.4);
                java.awt.Shape old = b.getClip();
                b.clip(almond);
                b.setColor(new Color(20, 12, 34, (int) (245 * a)));
                b.fill(new java.awt.geom.Ellipse2D.Double(ix - ir, iy - ir, ir * 2, ir * 2));
                b.setColor(new Color(255, 255, 255, (int) (230 * a)));
                b.fill(new java.awt.geom.Ellipse2D.Double(ix - ir * 0.5, iy - ir * 0.55, ir * 0.35, ir * 0.35));
                b.setClip(old);
            }
            b.setStroke(new BasicStroke((float) (size * 0.09)));
            b.translate(size * 0.04, size * 0.02);
            b.setColor(new Color(255, 70, 190, (int) (140 * a)));
            b.draw(almond);
            b.translate(-size * 0.08, -size * 0.04);
            b.setColor(new Color(60, 230, 255, (int) (140 * a)));
            b.draw(almond);
            b.translate(size * 0.04, size * 0.02);
            b.setColor(new Color(12, 6, 24, (int) (240 * a)));
            b.draw(almond);
        }
        b.dispose();
    }

    /**
     * The part that lies behind everything, including the circles, so they stay in reach: while the field
     * has drawn away, the screen goes to a starry dark with a black disc and a single eye in it, streaks
     * winding into it, watching the game - and the cursor.
     */
    private boolean aeroWarmStarted;

    /** Paints both wallpapers off to the side while the song begins, so that neither is made in the middle of a frame. */
    private void warmAero(int w, int h) {
        if (aeroWarmStarted) return;
        aeroWarmStarted = true;
        Thread th = new Thread(() -> {
            for (int v = 0; v < 2; v++) {
                BufferedImage img = AeroWallpaper.paint(w, h, v);
                synchronized (aeroPics) {
                    if (aeroPics[v] == null) aeroPics[v] = img;
                }
            }
        }, "aero-warm");
        th.setDaemon(true);
        th.start();
    }

    void renderBack(Graphics2D g, int w, int h, double timeMs) {
        warmAero(w, h);
        drawAero(g, w, h, timeMs);
        drawWatchers(g, w, h, timeMs);
        actor.renderAnger(g, w, h, timeMs);
        double dark = GlitcherActor.blackout(timeMs);
        if (dark > 0) {
            g.setColor(new Color(0, 0, 0, (int) (255 * dark)));
            g.fillRect(0, 0, w, h);
        }
        double z = recede(timeMs);
        if (z > 0) drawVoid(g, w, h, timeMs, z);
        // The eyes lie behind the circles and the health bars, so the notes stay easy to read.
        double fs = 1 - 0.7 * z, fcy = h * 0.5 + h * 0.24 * z;
        drawEyes(g, w, h, timeMs, fieldAmount(timeMs), fcy, fs);
        if (actorBehind(timeMs)) {
            if (icons == null) icons = loadIcons();
            renderActor(g, w, h, timeMs, fcy, fs);
        }
    }

    private void renderActor(Graphics2D g, int w, int h, double timeMs, double fcy, double fs) {
        if (actorSpin == 0) {
            actor.render(g, w, h, timeMs, fcy + h * 0.2 * fs, fs, icons, GameMain.desktopIconSpots());
            return;
        }
        Graphics2D r = (Graphics2D) g.create();
        r.rotate(actorSpin, w * 0.5, fcy);
        actor.render(r, w, h, timeMs, fcy + h * 0.2 * fs, fs, icons, GameMain.desktopIconSpots());
        r.dispose();
    }

    /** Just the field of eyes of 1:21, full strength, for other scenes; {@code timeMs} steers how far it has turned. */
    void renderEyeField(Graphics2D g, int w, int h, double timeMs) {
        drawEyes(g, w, h, timeMs, 1.0, h * 0.5, 1.0);
    }

    /** While the eyes are open he floats behind the circles, so he never hides a note. */
    private static boolean actorBehind(double ms) {
        return ms >= GlitcherActor.STOMP_MS && ms < GlitcherActor.RETURN_MS + 3000;
    }

    private void drawVoid(Graphics2D g, int w, int h, double timeMs, double z) {
        double t = timeMs / 1000.0;
        Graphics2D b = (Graphics2D) g.create();
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        b.setColor(new Color(0, 0, 0, (int) (250 * z)));
        b.fillRect(0, 0, w, h);

        for (int i = 0; i < stars.length; i++) {
            double[] s = stars[i];
            float tw = (float) (0.55 + 0.45 * Math.sin(t * (0.8 + s[3]) + s[2] * 6.28));
            b.setColor(new Color(1f, 1f, 1f, (float) (z * tw * s[2])));
            int sz = s[3] > 0.8 ? 3 : 2;
            b.fillRect((int) (s[0] * w), (int) (s[1] * h), sz, sz);
        }

        double cx = w * 0.5, cy = h * 0.36, disc = h * 0.2;
        // The streaks wind into the disc from all sides and slowly turn.
        b.setStroke(new BasicStroke(1.4f));
        for (double[] s : streaks) {
            double r0 = disc * (1.25 + s[1] * 5.0);
            double a0 = s[0] + t * 0.05 * (1 + s[2]);
            double px = 0, py = 0;
            for (int j = 0; j <= 7; j++) {
                double r = r0 * (1 - j / 8.0 * 0.32);
                double a = a0 + Math.log(r0 / r) * 2.2 + 0.0;
                double x = cx + Math.cos(a) * r, y = cy + Math.sin(a) * r * 0.9;
                if (j > 0) {
                    float al = (float) (z * 0.5 * (1 - (r / (disc * 6.5))) * (0.4 + 0.6 * s[2]));
                    if (al > 0) {
                        b.setColor(new Color(1f, 1f, 1f, Math.min(1f, al)));
                        b.draw(new java.awt.geom.Line2D.Double(px, py, x, y));
                    }
                }
                px = x; py = y;
            }
        }
        // The line of light across the middle.
        b.setPaint(new GradientPaint(0, (float) cy, new Color(1f, 1f, 1f, 0f), (float) (w * 0.5), (float) cy,
                new Color(1f, 1f, 1f, (float) (0.9 * z))));
        b.fillRect(0, (int) cy - 1, w / 2, 2);
        b.setPaint(new GradientPaint((float) (w * 0.5), (float) cy, new Color(1f, 1f, 1f, (float) (0.9 * z)), w, (float) cy,
                new Color(1f, 1f, 1f, 0f)));
        b.fillRect(w / 2, (int) cy - 1, w - w / 2, 2);

        // The disc, and the eye in it.
        b.setColor(new Color(0, 0, 0, (int) (255 * z)));
        b.fillOval((int) (cx - disc), (int) (cy - disc), (int) (disc * 2), (int) (disc * 2));
        b.setColor(new Color(1f, 1f, 1f, (float) (0.35 * z)));
        b.setStroke(new BasicStroke(3f));
        b.drawOval((int) (cx - disc), (int) (cy - disc), (int) (disc * 2), (int) (disc * 2));

        double blink = 1 - 0.9 * Math.max(0, 1 - Math.abs(((t % 5.3) - 2.65)) * 7);     // a slow blink now and then
        double ew = disc * 0.95, eh = disc * 0.5 * blink;
        java.awt.geom.Path2D.Double eye = new java.awt.geom.Path2D.Double();
        eye.moveTo(cx - ew, cy);
        eye.quadTo(cx, cy - eh * 2, cx + ew, cy);
        eye.quadTo(cx, cy + eh * 2, cx - ew, cy);
        eye.closePath();
        b.setColor(new Color(1f, 1f, 1f, (float) z));
        b.setStroke(new BasicStroke(4f));
        b.draw(eye);
        java.awt.Shape oldClip = b.getClip();
        b.clip(eye);
        // It looks where the cursor is.
        double lx = 0, ly = 0;
        try {
            java.awt.PointerInfo pi = java.awt.MouseInfo.getPointerInfo();
            if (pi != null) {
                lx = pi.getLocation().x - cx;
                ly = pi.getLocation().y - cy;
            }
        } catch (RuntimeException ignored) { }
        double len = Math.hypot(lx, ly);
        double reach = disc * 0.32;
        double ix = cx + (len > 0 ? lx / len * Math.min(reach, len * 0.25) : 0);
        double iy = cy + (len > 0 ? ly / len * Math.min(reach * 0.5, len * 0.12) : 0);
        double ir = disc * 0.3;
        b.setColor(new Color(1f, 1f, 1f, (float) z));
        b.fillOval((int) (ix - ir), (int) (iy - ir), (int) (ir * 2), (int) (ir * 2));
        b.setColor(new Color(0, 0, 0, (int) (255 * z)));
        double pr = ir * 0.45;
        b.fillOval((int) (ix - pr), (int) (iy - pr), (int) (pr * 2), (int) (pr * 2));
        b.setClip(oldClip);
        b.dispose();
    }

    /**
     * From 1:21 a great many eyes fill the screen around a black hole with the Glitcher in it, all
     * looking at him. After a few seconds the whole field starts to turn, slowly and then faster.
     */
    private void drawEyes(Graphics2D g, int w, int h, double timeMs, double k, double cy, double fs) {
        if (k <= 0) return;
        double cx = w * 0.5;
        double hole = h * 0.24 * fs;
        double maxR = Math.hypot(w, h) / 2 * 1.02 * fs;
        double dt = Math.max(0, (timeMs - FAKE_START_MS) / 1000.0 - 2.0);
        double rot = SPIN_MAX * (dt - SPIN_RAMP_S * (1 - Math.exp(-dt / SPIN_RAMP_S)));

        Graphics2D b = (Graphics2D) g.create();
        b.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        b.setColor(new Color(0, 0, 0, (int) (235 * k)));
        b.fillOval((int) (cx - hole), (int) (cy - hole), (int) (hole * 2), (int) (hole * 2));
        double t = timeMs / 1000.0;
        float halo = (float) (hole * 2.3);
        double breath = 0.75 + 0.25 * Math.sin(t * 2.4);
        b.setPaint(new java.awt.RadialGradientPaint((float) cx, (float) cy, halo, new float[]{0.42f, 1f},
                new Color[]{new Color(150, 100, 255, (int) (95 * k * breath)), new Color(150, 100, 255, 0)}));
        b.fillRect((int) (cx - halo), (int) (cy - halo), (int) (halo * 2), (int) (halo * 2));
        // Rings of eyes, each ring a step further out and a step bigger, lying along the ring like scales.
        int idx = 0;
        double r = hole * 1.14;
        for (int ring = 0; r < maxR * 1.08 && idx + PER_RING <= EYES; ring++, r *= RING_GROWTH) {
            double spin = rot;
            double stagger = (ring % 2) * Math.PI / PER_RING;
            double depth = Math.min(1.0, 0.55 + 0.05 * ring);
            double len = Math.PI * 2 * r / PER_RING;
            for (int j = 0; j < PER_RING; j++, idx++) {
                double[] e = eyes[idx];
                double ang = j * Math.PI * 2 / PER_RING + stagger + spin + (e[2] * 0.02);
                double x = cx + Math.cos(ang) * r;
                double y = cy + Math.sin(ang) * r;
                if (x < -len * 1.2 || x > w + len * 1.2 || y < -len * 1.2 || y > h + len * 1.2) continue;      // wholly off the screen
                double sc = len * 1.02 * (0.94 + 0.12 * e[3]) / eyeSprite.getWidth();
                double tilt = ang + Math.PI / 2 + e[2] * 0.08;
                // A slow blink, each eye on its own time.
                double bl = (t * 0.27 + e[0] * 0.16) % 1.0;
                double lid = bl < 0.05 ? Math.abs(bl / 0.05 - 0.5) * 2 : 1;
                double ea = k * depth;
                b.setComposite(ea >= 0.995 ? java.awt.AlphaComposite.SrcOver : java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, (float) ea));
                java.awt.geom.AffineTransform old = b.getTransform();
                b.translate(x, y);
                b.rotate(tilt);
                b.scale(sc, sc * (0.15 + 0.85 * lid));
                // The pupil looks at the middle: inwards along the eye's own y axis. It is part of the open eye's picture.
                BufferedImage pic = lid > 0.5 ? openEye() : eyeSprite;
                b.drawImage(pic, -pic.getWidth() / 2, -pic.getHeight() / 2, null);
                b.setTransform(old);
            }
        }
        b.dispose();
    }

    private BufferedImage openEyeSprite;

    /** The eye with its pupil, made once: two oval fills per eye per frame, a few hundred eyes, was the most expensive thing on the screen. */
    private BufferedImage openEye() {
        if (openEyeSprite == null) {
            BufferedImage im = new BufferedImage(eyeSprite.getWidth(), eyeSprite.getHeight(), BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D pg = im.createGraphics();
            pg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            pg.drawImage(eyeSprite, 0, 0, null);
            int cx = im.getWidth() / 2, cy = im.getHeight() / 2;
            pg.setColor(new Color(255, 255, 255, 235));
            pg.fillOval(cx - 11, cy + 6 - 11, 22, 22);
            pg.setColor(new Color(0x9A, 0x90, 0x30, 200));
            pg.fillOval(cx - 13, cy + 4 - 4, 10, 10);
            pg.dispose();
            openEyeSprite = im;
        }
        return openEyeSprite;
    }

    /** One eye outline, doubled with a pink and an olive ghost like a badly printed page. */
    static BufferedImage makeEyeSprite() {
        int sw = 200, sh = 100;
        BufferedImage img = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        java.awt.geom.Path2D.Double almond = new java.awt.geom.Path2D.Double();
        almond.moveTo(12, sh / 2.0);
        almond.quadTo(sw / 2.0, -34, sw - 12, sh / 2.0);
        almond.quadTo(sw / 2.0, sh + 34, 12, sh / 2.0);
        almond.closePath();
        g.setColor(new Color(0, 0, 0, 120));
        g.fill(almond);
        g.setStroke(new BasicStroke(3.5f));
        g.translate(4, 3);
        g.setColor(new Color(0xB0, 0x50, 0x90, 170));
        g.draw(almond);
        g.translate(-8, 1);
        g.setColor(new Color(0x90, 0x90, 0x30, 170));
        g.draw(almond);
        g.translate(4, -4);
        g.setColor(new Color(255, 255, 255, 235));
        g.draw(almond);
        g.dispose();
        return img;
    }
}
