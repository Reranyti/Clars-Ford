package com.lotusblight.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;

/**
 * The Glitcher during the first song: he walks, throws the desktop icons at the player, gets angry,
 * stomps - and at that the screen goes dark, the eyes open and he rises to the middle of them.
 * Everything is a function of the song clock only, so it needs no state and survives jumping in time.
 */
final class GlitcherActor {
    static final double THROW_FROM_MS = 40_000;
    static final double THROW_EVERY_MS = 3_500;
    static final double THROW_TO_MS = 73_500;
    // After the eyes he throws again, faster and in a short burst.
    private static final double[] WAVE2 = {106_400, 108_000, 109_600, 111_200};
    private static final double[] STARTS = throwStarts();
    static final double ANGER_MS = 70_000;         // he starts to seethe
    static final double GATHER_MS = 74_000;        // and comes to the middle to stand there
    static final double STOMP_MS = 80_000;         // the foot comes down
    static final double BLACKOUT_MS = 80_300;      // and everything on the screen is gone
    static final double LIFT_MS = 80_500;          // he rises to the middle of the eyes
    static final double RETURN_MS = 104_000;       // and it all lets go again
    static final double KNOCK_FROM = 12_000;       // he stops and knocks on the glass
    static final double WAVE_FROM = 14_800;
    static final double KNOCK_TO = 17_000;
    static final double COLLECT_FROM = 24_000;     // and goes to the left edge to pick up icons
    static final double COLLECT_TO = 37_000;
    static final double[] GRABS = {27_500, 31_000, 34_500};
    static final double STARE_FROM = 112_000;      // after the eyes: he stops and looks at you
    static final double STARE_TO = 118_000;
    static final double DISSOLVE_FROM = 122_500;   // and at the end of the song comes apart
    static final double DISSOLVE_MS = 3_500;

    private static final double LEG_ROLL = -0.9;
    private final double ss;                       // drawn this many times larger, then scaled down smooth (less on a big screen: the figure is a great many pixels there)
    private static final int RIGHT_ARM = SkinModel.Part.RIGHT_ARM.ordinal();
    private static final int LEFT_ARM = SkinModel.Part.LEFT_ARM.ordinal();
    private static final int RIGHT_LEG = SkinModel.Part.RIGHT_LEG.ordinal();
    private static final int LEFT_LEG = SkinModel.Part.LEFT_LEG.ordinal();
    private static final int BODY = SkinModel.Part.BODY.ordinal();
    private static final int HEAD = SkinModel.Part.HEAD.ordinal();

    private final Actor actor;
    private final int[] skin;
    private final double unit;                     // screen pixels per skin pixel at scale 1
    private final SoftRenderer renderer;
    private final double ox, oy;

    GlitcherActor(int[] skin, int screenH) {
        this.skin = skin;
        this.unit = screenH * 0.40 / 32.0;
        this.ss = 1.5;
        double u = unit * ss;
        this.actor = new Actor(skin, false);
        this.renderer = new SoftRenderer((int) (52 * u), (int) (46 * u));
        this.ox = renderer.width / 2.0;
        this.oy = renderer.height - 3 * u;
    }

    static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    /** 0 up to the stomp, 1 from the blackout on until the eyes let go. */
    static double blackout(double ms) {
        return ms >= BLACKOUT_MS ? 1 - smooth((ms - RETURN_MS) / 1500.0) : 0;
    }

    /** How angry he is, 0..1: builds from 1:10, gone the moment the foot is down. */
    static double anger(double ms) {
        return ms >= STOMP_MS ? 0 : smooth((ms - ANGER_MS) / 8000.0);
    }

    private static double pull(double x, double anchor, double weight) {
        return x + (anchor - x) * weight;
    }

    private static double pathX(double ms, int w) {
        return w * (0.5 + 0.42 * Math.sin(ms / 1000.0 * 0.55));
    }

    private static double bell(double ms, double from, double len) {
        double f = (ms - from) / len;
        return f <= 0 || f >= 1 ? 0 : Math.sin(Math.PI * f);
    }

    /**
     * @param hoverFootY where his feet are when he floats in the middle of the field
     * @param hoverScale how big he is then (the field can draw away and take him with it)
     * @param spots where the desktop icons sit, so he can walk up and take them (may be empty)
     */
    void render(Graphics2D g, int w, int h, double ms, double hoverFootY, double hoverScale,
                List<BufferedImage> icons, List<java.awt.Rectangle> spots) {
        double t = ms / 1000.0;
        double anger = anger(ms);
        double cx = w * 0.5;

        double pathX = pathX(ms, w);
        double heading = Math.cos(t * 0.55) >= 0 ? 1 : -1;
        double x = pathX;
        x = pull(x, -w * 0.09, 1 - smooth(ms / 4500.0));                      // walking in from the left edge
        double knock = smooth((ms - KNOCK_FROM + 500) / 700.0) * (1 - smooth((ms - KNOCK_TO) / 700.0));
        x = pull(x, pathX(KNOCK_FROM, w), knock);                              // stopped to knock on the glass
        double collect = smooth((ms - COLLECT_FROM) / 3000.0) * (1 - smooth((ms - COLLECT_TO) / 3000.0));
        x = pull(x, w * 0.075, collect);                                       // at the left edge, taking icons
        if (collect > 0.3) heading = -1;
        double gather = smooth((ms - GATHER_MS) / 3000.0) * (1 - smooth((ms - RETURN_MS) / 3000.0));
        x = pull(x, cx, gather);                                               // in the middle before the stomp
        double stare = smooth((ms - STARE_FROM) / 2000.0) * (1 - smooth((ms - STARE_TO) / 1500.0));
        x = pull(x, pathX(STARE_FROM, w), stare);                              // looking out at you
        double toEnd = smooth((ms - STARE_TO) / 3000.0);
        x = pull(x, cx, toEnd);                                                // and to the middle, to fall apart
        double standing = smooth((ms - GATHER_MS - 2000) / 1000.0) * (1 - smooth((ms - RETURN_MS - 1000) / 1000.0));
        double atEnd = smooth((ms - STARE_TO - 2500) / 800.0);
        double idle = Math.max(Math.max(standing, knock), Math.max(Math.max(stare, atEnd), Math.pow(collect, 6)));
        double lev = smooth((ms - LIFT_MS) / 2700.0) * (1 - smooth((ms - RETURN_MS) / 1500.0));

        double calm = smooth((ms - RETURN_MS - 2000) / 2000.0);
        double freq = (2.4 + 2.2 * anger) * (1 - 0.4 * calm);
        double phase = t * freq * Math.PI;
        double walk = 1 - idle;
        double groundY = h * 0.72 + Math.abs(Math.sin(phase)) * h * 0.012 * walk;
        double footY = groundY + (hoverFootY - groundY) * lev;
        double scale = 1 + (hoverScale - 1) * lev;

        Actor a = actor;
        a.begin();
        double facing = heading * 1.15 * (1 - idle);
        a.viewYaw = facing;
        GlitcherPoses.walk(a, phase, walk, anger, t);

        // Knocking on the glass with a fist, then a wave with the other hand.
        double handX = x - unit * 5.5 * scale, handY = footY - unit * 17 * scale;
        if (knock > 0.02) {
            double pulse = 0;
            for (int i = 0; i < 3; i++) pulse = Math.max(pulse, Math.exp(-Math.pow((ms - (KNOCK_FROM + 500 + i * 800)) / 110.0, 2)));
            double wave = smooth((ms - WAVE_FROM) / 300.0) * (1 - smooth((ms - KNOCK_TO) / 500.0));
            GlitcherPoses.knock(a, ms, knock, pulse, wave);
        }
        // Picking icons up off the edge between finger and thumb.
        double grab = 0;
        for (double gt : GRABS) grab = Math.max(grab, bell(ms, gt, 900));
        GlitcherPoses.pickUp(a, grab);
        // Throwing one: wind up with a closed hand, let go, follow through.
        GlitcherPoses.throwIcon(a, throwProgress(ms));
        // The stomp: arms up and out, fists clenched, one knee raised, held, then down hard.
        GlitcherPoses.stomp(a, ms, STOMP_MS);
        // Floating: arms out, hands open, legs hanging, turning slowly to look out at the eyes.
        if (lev > 0) {
            a.viewYaw += (Math.sin(t * 0.6) * 0.35 - facing) * lev;
            GlitcherPoses.hover(a, lev, t);
            footY += Math.sin(t * 1.4) * h * 0.012 * lev;
        }
        // Standing and looking at the player, head a little to one side.
        if (stare > 0) a.blendTurn(Rig15.Joint.HEAD, -6, 0, 12.6, stare);

        // A tremor while he seethes, and a jolt at the stomp.
        double shakeX = 0, shakeY = 0;
        if (anger > 0) {
            Random jitter = new Random((long) (ms / 40));
            shakeX = (jitter.nextDouble() - 0.5) * 7 * anger;
            shakeY = (jitter.nextDouble() - 0.5) * 4 * anger;
        }
        if (ms >= STOMP_MS && ms < STOMP_MS + 600) {
            double f = 1 - (ms - STOMP_MS) / 600.0;
            Random jitter = new Random((long) (ms / 30));
            shakeX += (jitter.nextDouble() - 0.5) * 26 * f;
            shakeY += (jitter.nextDouble() - 0.5) * 20 * f;
        }

        double aeroAmt = Hazards.aeroAt(ms), eyesAmt = Hazards.fieldAt(ms);
        setFigureLight(aeroAmt, eyesAmt, anger);
        renderer.clearDirty();
        actor.draw(renderer, unit * ss, ox, oy);
        rimFigure(aeroAmt, eyesAmt, anger);

        Graphics2D b = (Graphics2D) g.create();
        b.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double px = x + shakeX, py = footY + shakeY;
        drawAura(b, px, py - unit * 17 * scale, unit * 27 * scale, aeroAmt, eyesAmt, anger, ms);
        if (walk > 0.5 && lev < 0.3) drawFootDust(b, px, groundY, heading, phase);
        if (lev < 1) {
            double sr = unit * 9 * scale;
            b.setPaint(new RadialGradientPaint((float) px, (float) (groundY + unit * 0.6), (float) sr, new float[]{0f, 1f},
                    new Color[]{new Color(0, 0, 0, (int) (90 * (1 - lev))), new Color(0, 0, 0, 0)}));
            b.fill(new java.awt.geom.Ellipse2D.Double(px - sr, groundY + unit * 0.6 - sr * 0.28, sr * 2, sr * 0.56));
        }
        AffineTransform base = b.getTransform();
        b.translate(px, py);
        b.scale(scale / ss, scale / ss);
        double dissolve = Math.max(0, Math.min(1, (ms - DISSOLVE_FROM) / DISSOLVE_MS));
        double burst = glitchBurst(ms, Math.max(anger, stare), lev);
        if (dissolve > 0) {
            drawDissolving(b, dissolve);
        } else if (burst > 0) {
            // Now and then he tears: strips of him slide sideways.
            Random rnd = new Random((long) (ms / 60));
            int strips = 18;
            int sh = renderer.height / strips + 1;
            for (int i = 0; i < strips; i++) {
                int y = i * sh;
                int dx = (int) ((rnd.nextDouble() - 0.5) * 2 * unit * ss * 3.5 * burst);
                b.drawImage(renderer.image, (int) -ox + dx, (int) -oy + y, (int) -ox + dx + renderer.width, (int) -oy + y + sh,
                        0, y, renderer.width, Math.min(renderer.height, y + sh), null);
            }
        } else {
            // After the eyes he leaves faint copies of himself behind.
            if (ms > RETURN_MS + 1500 && lev < 0.2) {
                for (int i = 2; i >= 1; i--) {
                    b.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.2f / i));
                    b.drawImage(renderer.image, (int) -ox - (int) (heading * unit * ss * 4 * i), (int) -oy, null);
                }
                b.setComposite(java.awt.AlphaComposite.SrcOver);
            }
            b.drawImage(renderer.image, (int) -ox, (int) -oy, null);
        }
        b.setTransform(base);
        b.dispose();

        drawKnockRipples(g, ms, handX, handY);
        drawKnockCracks(g, ms, handX, handY);
        drawCarried(g, w, h, ms, x, footY, scale, heading, icons, spots);
        drawThrown(g, w, h, ms, x, groundY, scale, icons);
        drawStompFx(g, w, h, ms, px, groundY);
        drawThrowCharge(g, ms, x + heading * unit * 6 * scale, footY - unit * 20 * scale);
        drawGrabSparkles(g, ms, x - unit * 5, footY - unit * 14);
    }

    /** A soft light behind him that sets him off from the picture: its colour follows the phase of the song. */
    private void drawAura(Graphics2D b, double cx, double cy, double r, double aero, double eyes, double anger, double ms) {
        double[] c = {110, 130, 255};
        c = mix(c, new double[]{255, 215, 140}, aero);
        c = mix(c, new double[]{160, 110, 255}, eyes);
        c = mix(c, new double[]{255, 60, 80}, anger);
        double breath = 0.8 + 0.2 * Math.sin(ms / 1000.0 * (1.6 + 3 * anger));
        int a = (int) Math.min(255, (58 + 70 * anger + 20 * eyes) * breath);
        b.setPaint(new RadialGradientPaint((float) cx, (float) cy, (float) r, new float[]{0f, 1f},
                new Color[]{new Color((int) c[0], (int) c[1], (int) c[2], a), new Color((int) c[0], (int) c[1], (int) c[2], 0)}));
        b.fill(new java.awt.geom.Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
    }

    /** A puff kicked up at each footfall, drifting back and fading. */
    private void drawFootDust(Graphics2D b, double px, double groundY, double heading, double phase) {
        BufferedImage sprite = puff();
        double stepF = phase / Math.PI;
        for (int back = 0; back < 2; back++) {
            double steps = Math.floor(stepF) - back;
            double age = stepF - steps;                                     // 0..1 (+1 for the one before)
            if (age < 0 || age > 1.6) continue;
            double fade = 1 - age / 1.6;
            double size = unit * (1.6 + 2.2 * age);
            double x = px - heading * unit * (1.5 + 3.5 * age) + ((long) steps % 2 == 0 ? -1 : 1) * unit * 1.4;
            double y = groundY - size * 0.25 - unit * 1.5 * age;
            b.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, (float) (0.32 * fade)));
            b.drawImage(sprite, (int) (x - size), (int) (y - size), (int) (size * 2), (int) (size * 2), null);
        }
        b.setComposite(java.awt.AlphaComposite.SrcOver);
    }

    /** The wind-up of a throw: a light gathers in his hand, and bursts as the icon is let go. */
    private void drawThrowCharge(Graphics2D g, double ms, double hx, double hy) {
        double tp = throwProgress(ms);
        if (tp < 0 || tp > 800) return;
        Graphics2D b = (Graphics2D) g.create();
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double charge = Math.min(1, tp / 640.0);
        double r = unit * (1.2 + 3.2 * charge * charge);
        b.setPaint(new RadialGradientPaint((float) hx, (float) hy, (float) r, new float[]{0f, 1f},
                new Color[]{new Color(255, 255, 255, (int) (200 * charge)), new Color(160, 110, 255, 0)}));
        b.fill(new java.awt.geom.Ellipse2D.Double(hx - r, hy - r, r * 2, r * 2));
        if (tp >= 640) {
            double f = (tp - 640) / 160.0;
            double rr = unit * (3 + 7 * f);
            b.setStroke(new BasicStroke((float) (3 * (1 - f) + 1)));
            b.setColor(new Color(255, 255, 255, (int) (220 * (1 - f))));
            b.draw(new java.awt.geom.Ellipse2D.Double(hx - rr, hy - rr, rr * 2, rr * 2));
        }
        b.dispose();
    }

    /** Sparks as he picks an icon off the edge. */
    private void drawGrabSparkles(Graphics2D g, double ms, double cx, double cy) {
        for (double gt : GRABS) {
            double age = ms - gt;
            if (age < -150 || age > 650) continue;
            Graphics2D b = (Graphics2D) g.create();
            b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double f = Math.max(0, age) / 650.0, fade = 1 - f;
            Random rnd = new Random((long) gt);
            for (int i = 0; i < 9; i++) {
                double ang = rnd.nextDouble() * Math.PI * 2, dist = unit * (2 + 7 * rnd.nextDouble()) * (0.3 + f);
                double sx = cx + Math.cos(ang) * dist, sy = cy + Math.sin(ang) * dist - unit * 2 * f;
                double sz = unit * (0.5 + 0.9 * rnd.nextDouble()) * (1 - 0.5 * f);
                b.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                b.setColor(new Color(255, 236, 160, (int) (230 * fade)));
                b.draw(new java.awt.geom.Line2D.Double(sx - sz, sy, sx + sz, sy));
                b.draw(new java.awt.geom.Line2D.Double(sx, sy - sz, sx, sy + sz));
                b.setColor(new Color(255, 255, 255, (int) (200 * fade)));
                b.draw(new java.awt.geom.Line2D.Double(sx - sz * 0.5, sy - sz * 0.5, sx + sz * 0.5, sy + sz * 0.5));
                b.draw(new java.awt.geom.Line2D.Double(sx - sz * 0.5, sy + sz * 0.5, sx + sz * 0.5, sy - sz * 0.5));
            }
            b.dispose();
        }
    }

    private static double[] mix(double[] a, double[] b, double t) {
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    /** The colour of the light on him: cool at first, sunny on the Aero wallpaper, violet among the eyes, red as he seethes. */
    private void setFigureLight(double aero, double eyes, double anger) {
        double[] key = {1.15, 1.15, 1.35}, amb = {0.80, 0.78, 0.95};
        key = mix(key, new double[]{1.30, 1.20, 1.02}, aero);
        amb = mix(amb, new double[]{0.92, 0.88, 0.90}, aero);
        key = mix(key, new double[]{1.25, 1.05, 1.50}, eyes);
        amb = mix(amb, new double[]{0.78, 0.70, 1.00}, eyes);
        key = mix(key, new double[]{1.45, 0.90, 0.88}, anger);
        amb = mix(amb, new double[]{0.95, 0.68, 0.68}, anger);
        renderer.setLight(key, amb);
    }

    /** The coloured light along the edge of him: from above-left, and a second colour from the right when he is angry or among the eyes. */
    private void rimFigure(double aero, double eyes, double anger) {
        int thick = Math.max(2, (int) Math.round(unit * ss * 0.28));
        int cool = aero > 0.5 ? 0xFFE4A8 : (eyes > 0.5 ? 0xC9A8FF : 0xA8C4FF);
        ActorFx.rim(renderer, cool, 0.55, -0.6, 0.9, thick);
        if (anger > 0.02) ActorFx.rim(renderer, 0xFF3A55, 0.9 * anger, 0.8, 0.3, thick);
        else if (eyes > 0.02) ActorFx.rim(renderer, 0x8F6BFF, 0.8 * eyes, 0.9, 0.2, thick);
    }

    /** Cracks that run out over the "glass" from where each knock lands, then fade. */
    private void drawKnockCracks(Graphics2D g, double ms, double hx, double hy) {
        Graphics2D b = null;
        for (int i = 0; i < 3; i++) {
            double age = ms - (KNOCK_FROM + 500 + i * 800);
            if (age < 0 || age > 1700) continue;
            if (b == null) {
                b = (Graphics2D) g.create();
                b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            }
            double grow = Math.min(1, age / 140.0), fade = age < 800 ? 1 : 1 - (age - 800) / 900.0;
            Random rnd = new Random(31L * i + 5);
            for (int ray = 0; ray < 7; ray++) {
                double ang = ray * Math.PI * 2 / 7 + rnd.nextDouble() * 0.6;
                double px = hx, py = hy;
                int segs = 4;
                double[][] pts = new double[segs + 1][2];
                pts[0] = new double[]{px, py};
                for (int sgm = 1; sgm <= segs; sgm++) {
                    ang += (rnd.nextDouble() - 0.5) * 0.7;
                    double len = unit * (2.2 + rnd.nextDouble() * 3.2);
                    px += Math.cos(ang) * len;
                    py += Math.sin(ang) * len;
                    pts[sgm] = new double[]{px, py};
                }
                int n = 1 + (int) Math.ceil(grow * segs);
                for (int pass = 0; pass < 2; pass++) {
                    b.setStroke(new BasicStroke(pass == 0 ? 5f : 1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    b.setColor(pass == 0 ? new Color(150, 110, 255, (int) (90 * fade)) : new Color(255, 255, 255, (int) (230 * fade)));
                    for (int k = 1; k < Math.min(n, segs + 1); k++) {
                        b.drawLine((int) pts[k - 1][0], (int) pts[k - 1][1], (int) pts[k][0], (int) pts[k][1]);
                    }
                }
            }
        }
        if (b != null) b.dispose();
    }

    private static BufferedImage puffSprite;

    private static BufferedImage puff() {
        if (puffSprite == null) {
            BufferedImage im = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D pg = im.createGraphics();
            pg.setPaint(new RadialGradientPaint(32, 32, 32, new float[]{0f, 1f},
                    new Color[]{new Color(210, 200, 235, 255), new Color(210, 200, 235, 0)}));
            pg.fillRect(0, 0, 64, 64);
            pg.dispose();
            puffSprite = im;
        }
        return puffSprite;
    }

    /** One ring of light running out over the floor from a point (age 0..1). */
    private static void floorRing(Graphics2D g, double cx, double cy, double age, double maxR, double flat, int w, double h) {
        if (age <= 0 || age >= 1) return;
        double r = maxR * (1 - Math.pow(1 - age, 2.2));
        int alpha = (int) (210 * Math.pow(1 - age, 1.6));
        Graphics2D rg = (Graphics2D) g.create();
        rg.clipRect(0, (int) (cy - r * flat * 1.15) - 4, w, (int) (r * flat * 2.3) + 8);
        rg.translate(cx, cy);
        rg.scale(1, flat);
        rg.setStroke(new BasicStroke((float) Math.max(1.5, h * 0.012 * (1 - age))));
        rg.setColor(new Color(180, 130, 255, alpha / 2));
        rg.draw(new java.awt.geom.Ellipse2D.Double(-r, -r, r * 2, r * 2));
        rg.setColor(new Color(255, 255, 255, Math.min(255, alpha)));
        rg.setStroke(new BasicStroke((float) Math.max(1f, h * 0.004 * (1 - age))));
        rg.draw(new java.awt.geom.Ellipse2D.Double(-r, -r, r * 2, r * 2));
        rg.dispose();
    }

    /** The foot comes down: a flash, two rings of light over the floor and a burst of dust. */
    private void drawStompFx(Graphics2D g, int w, int h, double ms, double fx, double fy) {
        double age = ms - STOMP_MS;
        if (age < 0 || age > 1500) return;
        Graphics2D b = (Graphics2D) g.create();
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (age < 170) {
            b.setColor(new Color(255, 255, 255, (int) (150 * (1 - age / 170.0))));
            b.fillRect(0, 0, w, h);
        }
        floorRing(b, fx, fy, (age - 160) / 950.0, w * 0.5, 0.16, w, h);
        BufferedImage sprite = puff();
        Random r = new Random(77);
        double s = age / 1000.0;
        for (int i = 0; i < 30; i++) {
            double side = r.nextBoolean() ? 1 : -1;
            double speed = h * (0.25 + 0.9 * r.nextDouble());
            double rise = h * (0.02 + 0.10 * r.nextDouble());
            double size = h * (0.03 + 0.05 * r.nextDouble()) * (1 + 1.6 * s);
            double x = fx + side * speed * s * (1 - 0.32 * s);
            double y = fy - rise * s * 3 - size * 0.2;
            float a = (float) (0.5 * Math.max(0, 1 - age / 1500.0));
            b.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, a));
            b.drawImage(sprite, (int) (x - size), (int) (y - size), (int) (size * 2), (int) (size * 2), null);
        }
        b.dispose();
    }

    /** Thin rings spreading over the "glass" where his hand knocks. */
    private void drawKnockRipples(Graphics2D g, double ms, double hx, double hy) {
        Graphics2D b = (Graphics2D) g.create();
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (int i = 0; i < 3; i++) {
            double age = ms - (KNOCK_FROM + 500 + i * 800);
            if (age < 0 || age > 700) continue;
            double f = age / 700.0;
            double r = unit * 3 + f * unit * 14;
            b.setStroke(new BasicStroke((float) (3 * (1 - f) + 1)));
            b.setColor(new Color(255, 255, 255, (int) (200 * (1 - f))));
            b.draw(new java.awt.geom.Ellipse2D.Double(hx - r, hy - r, r * 2, r * 2));
        }
        b.dispose();
    }

    /** The icons he takes: they fly to his hand off the edge, then ride in a little stack over his head. */
    private void drawCarried(Graphics2D g, int w, int h, double ms, double x, double footY, double scale,
                             double heading, List<BufferedImage> icons, List<java.awt.Rectangle> spots) {
        if (icons == null || icons.isEmpty()) return;
        Graphics2D b = (Graphics2D) g.create();
        b.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        double handX = x + heading * unit * 5 * scale, handY = footY - unit * 8 * scale;
        int grabbed = 0;
        for (int k = 0; k < GRABS.length; k++) {
            int pick = (k * 7 + 1) % icons.size();
            double f = (ms - (GRABS[k] + 250)) / 550.0;
            if (f >= 0) GameMain.takeDesktopIcon(pick);
            if (f > 0 && f < 1) {
                java.awt.Rectangle r = spots != null && pick < spots.size() ? spots.get(pick) : null;
                double sx = r != null ? r.getCenterX() : w * 0.03, sy = r != null ? r.getCenterY() : h * (0.2 + 0.2 * k);
                double e = f * f * (3 - 2 * f);
                double ix = sx + (handX - sx) * e, iy = sy + (handY - sy) * e - Math.sin(Math.PI * f) * h * 0.06;
                drawIcon(b, icons.get(pick), ix, iy, h * 0.075 * (1 - 0.3 * e), f * 6);
            }
            if (ms >= GRABS[k] + 800) grabbed++;
        }
        int thrown = ms < THROW_FROM_MS + 640 ? 0 : (int) Math.min(10, (ms - THROW_FROM_MS - 640) / THROW_EVERY_MS + 1);
        int carried = Math.max(0, Math.min(3, grabbed - thrown));
        for (int i = 0; i < carried; i++) {
            int pick = (i * 7 + 1) % icons.size();
            drawIcon(b, icons.get(pick), x + (i - (carried - 1) / 2.0) * h * 0.02, footY - unit * 37 * scale - i * h * 0.03,
                    h * 0.06, (i - 1) * 0.15);
        }
        b.dispose();
    }

    private static void drawIcon(Graphics2D b, BufferedImage icon, double x, double y, double size, double rot) {
        double sc = size / Math.max(icon.getWidth(), icon.getHeight());
        AffineTransform old = b.getTransform();
        b.translate(x, y);
        b.rotate(rot);
        b.scale(sc, sc);
        b.drawImage(icon, -icon.getWidth() / 2, -icon.getHeight() / 2, null);
        b.setTransform(old);
    }

    /** He comes apart into squares that peel off in turn, drift up and away, and fade. */
    private void drawDissolving(Graphics2D b, double d) {
        // the light of him going out: a violet glow that swells and fades behind the squares
        double glow = Math.sin(Math.PI * Math.min(1, d * 1.15));
        if (glow > 0.02) {
            double gr = unit * ss * 22;
            b.setPaint(new RadialGradientPaint(0f, (float) (-unit * ss * 14), (float) gr, new float[]{0f, 1f},
                    new Color[]{new Color(150, 100, 255, (int) (120 * glow)), new Color(150, 100, 255, 0)}));
            b.fill(new java.awt.geom.Ellipse2D.Double(-gr, -unit * ss * 14 - gr, gr * 2, gr * 2));
        }
        int tile = 22;
        Random rnd = new Random(2607);
        for (int ty = 0; ty < renderer.height; ty += tile) {
            for (int tx = 0; tx < renderer.width; tx += tile) {
                double delay = rnd.nextDouble(), vx = (rnd.nextDouble() - 0.5), vy = rnd.nextDouble();
                int mx = Math.min(renderer.width - 1, tx + tile / 2), my = Math.min(renderer.height - 1, ty + tile / 2);
                if ((renderer.image.getRGB(mx, my) >>> 24) == 0) continue;
                double local = Math.max(0, Math.min(1, (d - delay * 0.55) / 0.45));
                double ox2 = vx * 700 * local * ss, oy2 = (-vy * 500 * local + 900 * local * local) * ss * 0.4;
                float alpha = (float) Math.max(0, 1 - local * local);
                if (alpha <= 0) continue;
                b.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha));
                int tw = Math.min(tile, renderer.width - tx), th = Math.min(tile, renderer.height - ty);
                b.drawImage(renderer.image, (int) (-ox + tx + ox2), (int) (-oy + ty + oy2), (int) (-ox + tx + ox2 + tw), (int) (-oy + ty + oy2 + th),
                        tx, ty, tx + tw, ty + th, null);
            }
        }
        b.setComposite(java.awt.AlphaComposite.SrcOver);
    }

    private double glitchBurst(double ms, double anger, double lev) {
        double rate = 2.8 - 1.4 * Math.max(anger, lev);
        double inCycle = (ms / 1000.0) % rate;
        double len = 0.12 + 0.1 * Math.max(anger, lev);
        return inCycle < len ? 1 : 0;
    }

    private static double[] throwStarts() {
        int first = (int) ((THROW_TO_MS - THROW_FROM_MS) / THROW_EVERY_MS) + 1;
        double[] out = new double[first + WAVE2.length];
        for (int i = 0; i < first; i++) out[i] = THROW_FROM_MS + i * THROW_EVERY_MS;
        System.arraycopy(WAVE2, 0, out, first, WAVE2.length);
        return out;
    }

    /** Milliseconds since the current throw began, or -1 when he isn't throwing. */
    private static double throwProgress(double ms) {
        for (double start : STARTS) {
            double d = ms - start;
            if (d >= 0 && d < 1500) return d;
        }
        return -1;
    }

    /** The icons in flight: from his hand to somewhere in the playfield, tumbling, staying a moment. */
    private void drawThrown(Graphics2D g, int w, int h, double ms, double gx, double groundY, double scale, List<BufferedImage> icons) {
        drawThrownGeneric(g, w, h, ms, STARTS, icons, 0.11, r -> throwerX(r, w), groundY - unit * 22);
    }

    /** Things thrown at the given moments (each starts a wind-up), flying from where he stood to somewhere on the screen. */
    private void drawThrownGeneric(Graphics2D g, int w, int h, double ms, double[] starts, List<BufferedImage> sprites,
                                   double sizeK, java.util.function.DoubleUnaryOperator fromX, double fromY) {
        if (sprites == null || sprites.isEmpty() || starts.length == 0 || ms < starts[0]) return;
        Graphics2D b = (Graphics2D) g.create();
        b.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        for (int k = 0; k < starts.length; k++) {
            double release = starts[k] + 640;
            if (release > ms) break;
            double age = ms - release;
            if (age > 3400) continue;
            Random rnd = new Random(k * 7919L + 13);
            BufferedImage icon = sprites.get(rnd.nextInt(sprites.size()));
            double tx = w * (0.15 + 0.7 * rnd.nextDouble());
            double ty = h * (0.22 + 0.45 * rnd.nextDouble());
            double sx = fromX.applyAsDouble(release);
            double sy = fromY;
            double f = Math.min(1, age / 1500.0);
            double ease = 1 - (1 - f) * (1 - f);
            double x = sx + (tx - sx) * ease;
            double y = sy + (ty - sy) * ease - Math.sin(Math.PI * f) * h * 0.22;
            double grow = 0.55 + 0.75 * ease;
            double alpha = age < 2600 ? 1 : 1 - (age - 2600) / 800.0;
            double size = h * sizeK * grow;
            double sc = size / Math.max(icon.getWidth(), icon.getHeight());
            if (age < 1700) {                                                   // the air leaves ghosts of it behind
                for (int gi = 3; gi >= 1; gi--) {
                    double fg = Math.max(0, f - 0.055 * gi), eg = 1 - (1 - fg) * (1 - fg);
                    double gx2 = sx + (tx - sx) * eg, gy2 = sy + (ty - sy) * eg - Math.sin(Math.PI * fg) * h * 0.22;
                    double gsc = h * sizeK * (0.55 + 0.75 * eg) / Math.max(icon.getWidth(), icon.getHeight());
                    AffineTransform keep = b.getTransform();
                    b.translate(gx2, gy2);
                    b.scale(gsc, gsc);
                    b.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, (float) (0.2 / gi * Math.max(0, alpha))));
                    b.drawImage(icon, -icon.getWidth() / 2, -icon.getHeight() / 2, null);
                    b.setTransform(keep);
                }
            }
            if (age >= 1500 && age < 1900) {                                    // and a ring where it lands
                double lf = (age - 1500) / 400.0;
                b.setStroke(new BasicStroke((float) (3 * (1 - lf) + 1)));
                b.setColor(new Color(255, 255, 255, (int) (170 * (1 - lf))));
                double rr = size * (0.4 + 0.9 * lf);
                b.draw(new java.awt.geom.Ellipse2D.Double(x - rr, y - rr, rr * 2, rr * 2));
            }
            AffineTransform old = b.getTransform();
            b.translate(x, y);
            b.rotate(age / 1000.0 * (rnd.nextBoolean() ? 5 : -5) * (1 - f * 0.8) * (sizeK > 0.15 ? 0.25 : 1));
            b.scale(sc, sc);
            b.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, (float) Math.max(0, alpha) * 0.3f));
            b.setColor(Color.BLACK);
            b.fillRoundRect(-icon.getWidth() / 2 + 8, -icon.getHeight() / 2 + 10, icon.getWidth(), icon.getHeight(), 8, 8);
            b.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, (float) Math.max(0, alpha)));
            b.drawImage(icon, -icon.getWidth() / 2, -icon.getHeight() / 2, null);
            b.setTransform(old);
        }
        b.dispose();
    }

    // -------- the second song's first minute --------
    private static final double[] STARTS2 = songTwoStarts();
    private List<BufferedImage> errorWindows;

    private static double[] songTwoStarts() {
        int n = (int) ((72_000 - 8_000) / 2_400) + 1;
        double[] out = new double[n];
        for (int i = 0; i < n; i++) out[i] = 8_000 + i * 2_400.0;
        return out;
    }

    /** How much he walks (0: stands in the middle where he formed, 1: paces the bottom of the screen). */
    private static double walking2(double ms) {
        return smooth((ms - 5_800) / 1_500.0) * (1 - smooth((ms - 73_500) / 2_000.0));
    }

    private static double songTwoX(double ms, int w) {
        double cx = w * 0.5;
        return cx + (pathX(ms, w) - cx) * walking2(ms);
    }

    /**
     * From the very start of the second song to 1:17: he forms again out of pixels where he fell apart, then
     * paces the bottom of the screen throwing error windows into the playfield, and comes apart once more as
     * the screen tears.
     */
    void renderSong2(Graphics2D g, int w, int h, double ms) {
        if (ms < 0 || ms > 77_500) return;
        if (errorWindows == null) errorWindows = errorWindowSprites();
        double t = ms / 1000.0;
        double reform = smooth((ms - 800) / 5_000.0);
        double leave = smooth((ms - 73_500) / 3_500.0);
        double whole = Math.min(reform, 1 - leave);
        whole = Math.min(whole, 1 - smooth((ms - 19_000) / 3_500.0));          // 0:19: the flat one comes apart, the solid one has come
        if (whole <= 0) return;
        double walking = walking2(ms);
        double scale = 0.85;
        double groundY = h * 0.9;
        double heading = Math.cos(t * 0.55) >= 0 ? 1 : -1;
        double x = songTwoX(ms, w);

        Actor a = actor;
        a.begin();
        a.viewYaw = heading * 1.15 * walking;
        double phase = t * 2.6 * Math.PI;
        GlitcherPoses.walk(a, phase, walking, 0.2, t);
        // Showing the TV (while the snakes run): he turns his front to the player and holds the TV out at the side at chest height, as in the reference.
        double show = SnakesFx.presenting(ms);
        a.viewYaw *= 1 - show;
        // the arm with the TV: low by his side while he walks, up and out at chest height, bent, while he shows it
        a.reach(Rig15.Limb.L_ARM, 6.0 + 3.5 * show, 13.0 + 7.5 * show, 4.5 + 1.5 * show, 1);
        a.shapeL = Actor.Hand.GRIP;
        a.fistL = 0.6;
        if (show > 0.02) a.blendTurn(Rig15.Joint.HEAD, 4, 0, -5, show);
        if (walking < 0.2) a.blendTurn(Rig15.Joint.HEAD, 8, 0, 0, 1 - walking / 0.2);      // just formed: a little dazed
        renderer.clearDirty();
        actor.draw(renderer, unit * ss, ox, oy);
        if (actor.lastSkel != null) {                                          // where his right fist is, relative to his feet, in screen pixels
            double[] hp = actor.onScreen(actor.lastSkel.endPoint(Rig15.Limb.L_ARM));
            handDx = hp[0] * unit * scale;
            handDy = hp[1] * unit * scale;
        }

        Graphics2D b = (Graphics2D) g.create();
        b.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        AffineTransform base = b.getTransform();
        b.translate(x, groundY);
        b.scale(scale / ss, scale / ss);
        if (whole < 1) drawDissolving(b, 1 - whole);
        else b.drawImage(renderer.image, (int) -ox, (int) -oy, null);
        b.setTransform(base);
        b.dispose();

    }

    private double handDx, handDy;

    /** Where the fist holding the TV is in the second song ({x, y} in screen pixels), or null while he is not there. */
    double[] songTwoHand(double ms, int w, int h) {
        double reform = smooth((ms - 800) / 5_000.0), leave = smooth((ms - 73_500) / 3_500.0);
        if (ms < 0 || Math.min(reform, 1 - leave) < 0.6) return null;
        return new double[]{songTwoX(ms, w) + handDx, h * 0.9 - handDy};
    }

    /** Where his head is in the second song ({x, y}); in the middle of the screen once he is gone (the snakes then come out of the middle). */
    double[] songTwoHead(double ms, int w, int h) {
        double reform = smooth((ms - 800) / 5_000.0), leave = smooth((ms - 73_500) / 3_500.0);
        if (Math.min(reform, 1 - leave) < 0.4) return new double[]{w * 0.5, h * 0.5};
        return new double[]{songTwoX(ms, w), h * 0.9 - unit * 29 * 0.85};
    }

    /** Error windows to throw: pale body, blue title bar, red button, a line of text and a button. */
    static List<BufferedImage> errorWindowSprites() {
        String[][] texts = {
                {"Error", "Click Fix to fix error", "Fix"},
                {"Windows error", "Click OK to continue", "OK"},
                {"Warning", "Critical process died", "OK"},
                {"System", "Your files are being seen", "Fix"}};
        java.util.List<BufferedImage> out = new java.util.ArrayList<>();
        for (int i = 0; i < texts.length; i++) {
            int w = 330, h = 168;
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(0xEDE4F6));
            g.fillRect(0, 0, w, h);
            g.setPaint(new java.awt.GradientPaint(0, 0, new Color(0x2E5CE0), 0, 30, new Color(0x1B3FB8)));
            g.fillRect(0, 0, w, 30);
            g.setColor(Color.WHITE);
            g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 17));
            g.drawString(texts[i][0], 10, 21);
            g.setColor(new Color(0xD83030));
            g.fillRect(w - 30, 5, 22, 20);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2f));
            g.drawLine(w - 25, 9, w - 13, 21);
            g.drawLine(w - 13, 9, w - 25, 21);
            if (i % 2 == 0) {
                g.setColor(new Color(0xD82828));
                g.fillOval(18, 56, 44, 44);
                g.setColor(Color.WHITE);
                g.drawLine(29, 67, 51, 89);
                g.drawLine(51, 67, 29, 89);
            } else {
                g.setColor(new Color(0xF0C010));
                g.fillPolygon(new int[]{16, 66, 41}, new int[]{102, 102, 54}, 3);
                g.setColor(new Color(0x402000));
                g.fillRect(38, 66, 6, 20);
                g.fillRect(38, 90, 6, 6);
            }
            g.setColor(new Color(0x2A2A50));
            g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 18));
            g.drawString(texts[i][1], 80, 84);
            g.setColor(new Color(0xC6BCD8));
            g.fillRect(w / 2 - 44, h - 46, 88, 32);
            g.setColor(new Color(0x30304C));
            g.setStroke(new BasicStroke(2f));
            g.drawRect(w / 2 - 44, h - 46, 88, 32);
            g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 17));
            g.drawString(texts[i][2], w / 2 - g.getFontMetrics().stringWidth(texts[i][2]) / 2, h - 24);
            g.setColor(new Color(0x30304C));
            g.drawRect(0, 0, w - 1, h - 1);
            g.dispose();
            out.add(img);
        }
        return out;
    }

    /** Where he stood when a given throw let go. */
    private static double throwerX(double ms, int w) {
        return w * (0.5 + 0.42 * Math.sin(ms / 1000.0 * 0.55));
    }

    /** The shockwave from the stomp, the cracks, the flash: everything drawn over the top. */
    void renderEffects(Graphics2D g, int w, int h, double ms, double groundY) {
        double a = ms - STOMP_MS;
        if (a >= 0 && a < 900) {
            Graphics2D b = (Graphics2D) g.create();
            b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double cx = w * 0.5;
            double fy = h * 0.72 + unit * 0.6;
            double r = a / 900.0;
            double ring = w * 0.65 * (1 - (1 - r) * (1 - r));
            b.setStroke(new BasicStroke((float) (12 * (1 - r) + 2)));
            b.setColor(new Color(255, 255, 255, (int) (200 * (1 - r))));
            b.draw(new java.awt.geom.Ellipse2D.Double(cx - ring, fy - ring * 0.22, ring * 2, ring * 0.44));
            // Cracks in the screen running out from under his foot.
            Random rnd = new Random(80);
            b.setStroke(new BasicStroke(2.5f));
            int alpha = (int) (255 * Math.min(1, (1 - r) * 1.4));
            b.setColor(new Color(255, 255, 255, Math.max(0, alpha)));
            for (int i = 0; i < 16; i++) {
                double ang = -Math.PI + rnd.nextDouble() * Math.PI;
                double px = cx, py = fy;
                double reach = Math.min(1, a / 260.0) * w * (0.25 + 0.4 * rnd.nextDouble());
                double walked = 0;
                while (walked < reach) {
                    double seg = 24 + rnd.nextDouble() * 30;
                    ang += (rnd.nextDouble() - 0.5) * 0.7;
                    double nx = px + Math.cos(ang) * seg, ny = py + Math.sin(ang) * seg * 0.85;
                    b.draw(new java.awt.geom.Line2D.Double(px, py, nx, ny));
                    px = nx; py = ny; walked += seg;
                }
            }
            b.dispose();
        }
        // The eyes let go (1:44): a violet flash and rings of light going out from the middle.
        double rt = ms - RETURN_MS;
        if (rt >= 0 && rt < 1500) {
            Graphics2D b = (Graphics2D) g.create();
            b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (rt < 420) {
                b.setColor(new Color(190, 150, 255, (int) (150 * (1 - rt / 420.0))));
                b.fillRect(0, 0, w, h);
            }
            for (int i = 0; i < 2; i++) {
                double r = (rt - i * 150) / 1200.0;
                if (r <= 0 || r >= 1) continue;
                double rad = Math.hypot(w, h) * 0.55 * (1 - (1 - r) * (1 - r));
                b.setStroke(new BasicStroke((float) (10 * (1 - r) + 2)));
                b.setColor(new Color(220, 190, 255, (int) (190 * (1 - r))));
                b.draw(new java.awt.geom.Ellipse2D.Double(w / 2.0 - rad, h / 2.0 - rad, rad * 2, rad * 2));
            }
            b.dispose();
        }
        // White flash at the moment it all goes.
        double f = ms - BLACKOUT_MS;
        if (f >= 0 && f < 500) {
            Graphics2D b = (Graphics2D) g.create();
            b.setColor(new Color(255, 255, 255, (int) (255 * (1 - f / 500.0))));
            b.fillRect(0, 0, w, h);
            b.dispose();
        }
    }

    /** A red creeping in at the edges as he gets angrier. */
    void renderAnger(Graphics2D g, int w, int h, double ms) {
        double anger = anger(ms);
        double stareAmt = smooth((ms - STARE_FROM) / 2000.0) * (1 - smooth((ms - STARE_TO) / 1500.0));
        if (stareAmt > 0.01) {
            Graphics2D v = (Graphics2D) g.create();
            double rr = Math.hypot(w, h) / 2;
            v.setPaint(new RadialGradientPaint((float) (w / 2.0), (float) (h / 2.0), (float) rr, new float[]{0.4f, 1f},
                    new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, (int) (170 * stareAmt))}));
            v.fillRect(0, 0, w, h);
            v.dispose();
        }
        if (anger <= 0) return;
        double pulse = 0.65 + 0.35 * Math.sin(ms / 1000.0 * (4 + 6 * anger));
        int alpha = (int) (150 * anger * pulse);
        double r = Math.hypot(w, h) / 2;
        Graphics2D b = (Graphics2D) g.create();
        b.setPaint(new RadialGradientPaint((float) (w / 2.0), (float) (h / 2.0), (float) r, new float[]{0.45f, 1f},
                new Color[]{new Color(180, 0, 20, 0), new Color(180, 0, 20, Math.max(0, alpha))}));
        b.fillRect(0, 0, w, h);
        // embers rising, brighter and more of them as he seethes
        b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int n = (int) (14 + 50 * anger);
        Random er = new Random(404);
        for (int i = 0; i < n; i++) {
            double ex = er.nextDouble() * w, speed = 0.05 + 0.10 * er.nextDouble(), phase = er.nextDouble() * 10_000, sway = er.nextDouble() * 6;
            double life = ((ms + phase) * speed / 1000.0) % 1.0;
            double y = h * (1.02 - 1.04 * life), x = ex + Math.sin(ms / 1000.0 * 1.3 + sway) * h * 0.02;
            double sz = h * (0.004 + 0.006 * er.nextDouble()) * (1 - 0.4 * life);
            int a = (int) (200 * anger * Math.sin(Math.PI * life));
            b.setColor(new Color(255, 90 + (int) (110 * (1 - life)), 40, Math.max(0, Math.min(255, a))));
            b.fill(new java.awt.geom.Ellipse2D.Double(x - sz, y - sz, sz * 2, sz * 2));
        }
        b.dispose();
    }

    /** Plain stand-in icons for when the desktop icons couldn't be read. */
    static List<BufferedImage> fallbackIcons() {
        BufferedImage[] list = new BufferedImage[4];
        for (int i = 0; i < list.length; i++) {
            BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            switch (i) {
                case 0 -> {                                   // a folder
                    g.setColor(new Color(0xF2C14E));
                    g.fillRoundRect(4, 14, 56, 40, 6, 6);
                    g.fillRoundRect(4, 8, 24, 12, 4, 4);
                    g.setColor(new Color(0xFFDD7A));
                    g.fillRoundRect(4, 20, 56, 34, 6, 6);
                }
                case 1 -> {                                   // a document
                    g.setColor(Color.WHITE);
                    g.fillRect(12, 4, 40, 56);
                    g.setColor(new Color(0x8AA4C8));
                    for (int l = 0; l < 6; l++) g.fillRect(18, 14 + l * 7, 28, 3);
                }
                case 2 -> {                                   // a program window
                    g.setColor(new Color(0x2F6FD6));
                    g.fillRoundRect(4, 8, 56, 48, 6, 6);
                    g.setColor(new Color(0xEAF2FF));
                    g.fillRect(8, 20, 48, 32);
                }
                default -> {                                  // a picture
                    g.setColor(new Color(0xEAF2FF));
                    g.fillRect(6, 10, 52, 44);
                    g.setColor(new Color(0x4CAF50));
                    g.fillPolygon(new int[]{6, 26, 40, 58, 58, 6}, new int[]{54, 28, 42, 22, 54, 54}, 6);
                    g.setColor(new Color(0xFFC107));
                    g.fillOval(42, 14, 10, 10);
                }
            }
            g.dispose();
            list[i] = img;
        }
        return List.of(list);
    }
}
