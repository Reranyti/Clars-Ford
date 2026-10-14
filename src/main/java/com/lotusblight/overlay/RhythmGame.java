package com.lotusblight.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

/**
 * The osu!-style rhythm game that plays over the finale scene: circles rise out of the playfield with a
 * shrinking approach ring, and the player left-clicks each one as its ring meets the circle. A hit hurts
 * the Glitcher; a miss costs the player HP. Emptying the player's HP is a loss; reaching the end of the
 * chart is a win. It only reads the clock it is given, so the caller drives it from the music.
 */
final class RhythmGame {
    private static final double PLAYFIELD_W = 512, PLAYFIELD_H = 384;
    private static final double CS = 7.6;               // circle size from the map

    private final OsuMap map;
    private final int maxHp;
    private double hp;
    private final int bossMaxHp;
    private double bossHp;
    private int combo;
    private int maxCombo;
    private int hits;
    private int misses;
    private int muted;                                   // circles switched off for another mechanic
    private int skipped;                                 // circles taken out for a black screen of words: nothing to hit, no damage, no stats
    private int nextMiss;                                // first circle not yet hit or judged missed
    private final boolean[] hit;
    private final boolean[] missed;
    private final double[] endedAt;                       // when each circle was hit or missed, for what becomes of it
    private final java.util.ArrayDeque<Integer> recent = new java.util.ArrayDeque<>();
    private PlayfieldFx fx;
    private double lastFlash;                            // time of the last hit, for a small flash

    RhythmGame(OsuMap map, int maxHp) {
        this.map = map;
        this.maxHp = maxHp;
        this.hp = maxHp;
        this.bossMaxHp = Math.max(1, map.circles.size());
        this.bossHp = bossMaxHp;
        this.hit = new boolean[map.circles.size()];
        this.endedAt = new double[map.circles.size()];
        this.missed = new boolean[map.circles.size()];
        java.util.Arrays.fill(endedAt, -1e9);
    }

    /** The events of the song that move the playfield and change what the circles are made of. */
    void setFx(PlayfieldFx fx) { this.fx = fx; }

    private PlayfieldFx.Style styleOf(int i) {
        return fx == null ? PlayfieldFx.Style.NORMAL : fx.style(map.circles.get(i).timeMs(), i);
    }

    /** Starting mid-song: drop every note before timeMs without counting it as a miss. */
    void skipTo(double timeMs) {
        while (nextMiss < map.circles.size() && map.circles.get(nextMiss).timeMs() < timeMs - map.hitWindowMs) {
            hit[nextMiss] = true;
            bossHp -= 1;
            nextMiss++;
        }
    }

    /** Circles inside [from, to) are switched off: another mechanic owns that stretch of the song. */
    /** Takes the circles of a stretch out of the game altogether: no damage, no stats, and nothing for the Glitcher's bar (unlike {@link #mute}). */
    void skip(double fromMs, double toMs) {
        for (int i = 0; i < map.circles.size(); i++) {
            double t = map.circles.get(i).timeMs();
            if (t >= fromMs && t < toMs && !hit[i]) { hit[i] = true; skipped++; }
        }
    }

    void mute(double fromMs, double toMs) {
        for (int i = 0; i < map.circles.size(); i++) {
            double t = map.circles.get(i).timeMs();
            if (t >= fromMs && t < toMs && !hit[i]) { hit[i] = true; muted++; }
        }
    }

    /** One contact beat landed: the Glitcher's bar drops by that beat's share of the circles that were switched off. */
    void contactHit(double share) { bossHp -= muted * share; }

    private double lastHitAt = -1e9, lastMissAt = -1e9;
    private boolean hudFull;

    double lastHitAt() { return lastHitAt; }

    double lastMissAt() { return lastMissAt; }

    /** The health bars stay unfolded and named, as after the second song's eye scene. */
    void fullHud() { hudFull = true; }

    /** Damage that isn't a missed circle (the eye that wants the space bar). */
    void hurt(double amount) { hp -= amount; }

    boolean alive() { return hp > 0; }
    boolean finished(double timeMs) { return nextMiss >= map.circles.size() && timeMs > lastTime() + 500; }
    double hpFraction() { return Math.max(0, hp) / maxHp; }
    double bossFraction() { return Math.max(0, bossHp) / bossMaxHp; }
    int combo() { return combo; }
    int hits() { return hits; }
    int misses() { return misses; }
    int total() { return map.circles.size() - muted - skipped; }
    int maxCombo() { return maxCombo; }

    /** A rank for an accuracy 0..1, osu-style. */
    static String grade(double accuracy) {
        if (accuracy >= 0.97) return "S";
        if (accuracy >= 0.90) return "A";
        if (accuracy >= 0.80) return "B";
        if (accuracy >= 0.70) return "C";
        return "D";
    }

    private double lastTime() {
        return map.circles.isEmpty() ? 0 : map.circles.get(map.circles.size() - 1).timeMs();
    }

    /** Judges circles whose window has passed as misses. Call each frame before drawing. */
    void update(double timeMs) {
        while (nextMiss < map.circles.size()) {
            OsuMap.Circle c = map.circles.get(nextMiss);
            if (timeMs <= c.timeMs() + map.hitWindowMs) break;
            if (!hit[nextMiss]) { hp -= 1; misses++; combo = 0; lastMissAt = timeMs; endedAt[nextMiss] = timeMs; recent.add(nextMiss); missed[nextMiss] = true; }
            nextMiss++;
        }
    }

    /** A left click at screen (mx,my). Returns true if it landed on a circle in its hit window. */
    boolean click(double mx, double my, double timeMs, int w, int h) {
        if (fx != null) {                                   // the playfield may be turned, shrunk or held in hands
            java.awt.Shape clip = fx.clip(timeMs, w, h);
            if (clip != null && !clip.contains(mx, my)) return false;
            try {
                Point2D p = fx.transform(timeMs, w, h).inverseTransform(new Point2D.Double(mx, my), null);
                mx = p.getX();
                my = p.getY();
            } catch (java.awt.geom.NoninvertibleTransformException e) {
                return false;
            }
        }
        double[] geo = geometry(w, h);
        double radius = geo[2];
        for (int i = nextMiss; i < map.circles.size(); i++) {
            OsuMap.Circle c = map.circles.get(i);
            if (c.timeMs() - map.approachMs > timeMs) break;    // not visible yet
            if (hit[i]) continue;
            if (Math.abs(timeMs - c.timeMs()) > map.hitWindowMs) continue;
            double cx = geo[0] + c.x() / PLAYFIELD_W * geo[3];
            double cy = geo[1] + c.y() / PLAYFIELD_H * geo[4];
            if (Math.hypot(mx - cx, my - cy) <= radius * 1.9) {   // generous catch radius
                hit[i] = true;
                hits++;
                bossHp -= 1;
                combo++;
                maxCombo = Math.max(maxCombo, combo);
                lastFlash = timeMs;
                lastHitAt = timeMs;
                endedAt[i] = timeMs;
                recent.add(i);
                return true;
            }
        }
        return false;
    }

    void render(Graphics2D g, int w, int h, double timeMs) {
        render(g, w, h, timeMs, true);
    }

    /** hud false draws just the circles, no HP bars or combo (used where the bars are hidden). */
    void render(Graphics2D g, int w, int h, double timeMs, boolean hud) {
        Graphics2D gg = (Graphics2D) g.create();
        gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double glitch = 0;
        if (fx != null) {
            java.awt.Shape clip = fx.clip(timeMs, w, h);
            if (clip != null) gg.clip(clip);
            gg.transform(fx.transform(timeMs, w, h));
            glitch = fx.glitch(timeMs);
            if (glitch > 0.05) {                                       // the whole playfield stutters aside now and then
                java.util.Random jr = new java.util.Random((long) (timeMs / 60));
                if (jr.nextDouble() < glitch * 0.3) gg.translate((jr.nextDouble() - 0.5) * w * 0.012 * glitch, (jr.nextDouble() - 0.5) * h * 0.008 * glitch);
            }
        }
        java.awt.image.BufferedImage desktop = fx == null ? null : fx.desktop();
        CircleArt.focusX = w / 2.0;
        CircleArt.focusY = h / 2.0;
        double[] geo = geometry(w, h);
        double ox = geo[0], oy = geo[1], radius = geo[2], fw = geo[3], fh = geo[4];

        // What becomes of the circles hit or missed a moment ago.
        while (!recent.isEmpty() && timeMs - endedAt[recent.peekFirst()] > 460) recent.pollFirst();
        for (int i : recent) {
            OsuMap.Circle c = map.circles.get(i);
            CircleArt.after(gg, styleOf(i), ox + c.x() / PLAYFIELD_W * fw, oy + c.y() / PLAYFIELD_H * fh, radius,
                    timeMs - endedAt[i], missed[i], i, timeMs, desktop);
        }
        // Draw upcoming circles, farthest-in-time first so nearer ones sit on top.
        for (int i = map.circles.size() - 1; i >= nextMiss; i--) {
            OsuMap.Circle c = map.circles.get(i);
            double dt = c.timeMs() - timeMs;
            if (dt > map.approachMs || dt < -map.hitWindowMs) continue;
            if (hit[i]) continue;
            double cx = ox + c.x() / PLAYFIELD_W * fw;
            double cy = oy + c.y() / PLAYFIELD_H * fh;
            double appear = Math.min(1, Math.max(0, 1 - dt / map.approachMs));   // 0..1 as it comes in
            CircleArt.draw(gg, styleOf(i), cx, cy, radius, appear, dt, map.approachMs, i, timeMs, glitch, desktop);
        }

        if (hud) drawHud(gg, w, h, timeMs);
        gg.dispose();
    }

    /** Just the bars and the combo (when the circles are drawn somewhere else). */
    void renderHud(Graphics2D g, int w, int h, double timeMs) {
        Graphics2D gg = (Graphics2D) g.create();
        gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (fx != null) {
            java.awt.Shape clip = fx.clip(timeMs, w, h);
            if (clip != null) gg.clip(clip);
            gg.transform(fx.transform(timeMs, w, h));
        }
        drawHud(gg, w, h, timeMs);
        gg.dispose();
    }

    private void drawHud(Graphics2D g, int w, int h, double timeMs) {
        // From 1:19 of the second song the bars unfold to both sides and get their names.
        double u = hudFull ? 1 : ContactBreak.unfold(timeMs);
        double names = hudFull ? 1 : ContactBreak.labelAlpha(timeMs);
        // The Glitcher (boss) HP along the very top, wide and red-purple.
        int bw = (int) (w * (0.7 + 0.24 * u)), bx = (w - bw) / 2, by = (int) (h * 0.018), bh = (int) (h * (0.022 + 0.006 * u));
        g.setColor(new Color(30, 12, 40));
        g.fillRect(bx, by, bw, bh);
        g.setColor(new Color(0x8A2BE2));
        g.fillRect(bx, by, (int) (bw * bossFraction()), bh);
        g.setColor(new Color(0xE0C0FF));
        g.setStroke(new BasicStroke(2));
        g.drawRect(bx, by, bw, bh);

        // Player HP just under it, narrower.
        int barW = (int) (w * (0.5 + 0.34 * u)), barX = (w - barW) / 2, barY = by + bh + (int) (h * 0.012), barH = (int) (h * (0.016 + 0.006 * u));
        g.setColor(new Color(40, 20, 60));
        g.fillRect(barX, barY, barW, barH);
        g.setColor(hpFraction() > 0.3 ? new Color(0x60D0FF) : new Color(0xFF4060));
        g.fillRect(barX, barY, (int) (barW * hpFraction()), barH);
        g.setColor(new Color(0xC0E0FF));
        g.drawRect(barX, barY, barW, barH);

        if (names > 0) {
            g.setFont(g.getFont().deriveFont(Font.BOLD, (float) (h * 0.017)));
            label(g, "Ошибки", bx + 8, by + bh - (int) (bh * 0.22), names);
            label(g, "Ваше хп", barX + 8, barY + barH - (int) (barH * 0.18), names);
        }

        if (combo > 1) {
            g.setColor(new Color(0xE0, 0xC0, 0xFF, 220));
            g.setFont(g.getFont().deriveFont(Font.BOLD, (float) (h * 0.05)));
            String s = combo + "x";
            g.drawString(s, (int) (w * 0.03), (int) (h * 0.95));
        }
    }

    private static void label(Graphics2D g, String text, int x, int baseline, double alpha) {
        int a = (int) (255 * Math.max(0, Math.min(1, alpha)));
        g.setColor(new Color(0, 0, 0, a * 3 / 4));
        g.drawString(text, x + 1, baseline + 1);
        g.setColor(new Color(255, 255, 255, a));
        g.drawString(text, x, baseline);
    }

    /** {offsetX, offsetY, circleRadius, fieldW, fieldH} for the centred playfield. */
    private double[] geometry(int w, int h) {
        double fh = h * 0.82;
        double fw = fh * PLAYFIELD_W / PLAYFIELD_H;
        double ox = (w - fw) / 2, oy = (h - fh) / 2;
        double radiusOsu = 54.4 - 4.48 * CS;            // osu circle radius in playfield px
        double radius = radiusOsu / PLAYFIELD_H * fh * 1.7;   // bigger than osu, easier to see and hit
        return new double[]{ox, oy, radius, fw, fh};
    }
}
