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
    // The timings of the part (ms). The defaults are the author's; the events of the scene's timeline (the editor) take their place when it has them.
    double SIL_FROM = 19_000, FULL_FROM = 38_000, SCREAM_FROM = 56_000, SCREAM_TO = 57_500, SONG_END = 77_500;
    double EYES_FROM = 79_500, EYES_TO = 133_500;
    /** Stretches in which the animation sticks and repeats its first moments: {from, to}, ms. */
    private double[][] STUCK = {{43_200, 44_700}, {49_400, 51_000}, {67_600, 68_800}};
    private static final double STUCK_LOOP_MS = 330;
    /** The size of the soft-rendered figure against the screen: small in the game (it is blocky by nature), raised by the editor for a still picture. */
    static double RES = 0.42;
    static final long[] PROF = new long[5];
    /** The camera stands this far from the figure, in skin units: near enough for the fist that hits to come right at it. */
    private static final double CAM = 46;

    private final Actor actor;
    private SoftRenderer rend;
    private final GlitchFx glitch = new GlitchFx();
    private double[] hits = new double[0];
    private double fistX, fistY;
    private boolean haveFist;
    private final boolean useScene;
    private long seenVersion = Long.MIN_VALUE;

    GlitcherCloseup(int[] skin) {
        this(skin, true);
    }

    /** {@code useScene} false: only the author's own defaults, whatever the scene says (for listing them to the editor). */
    GlitcherCloseup(int[] skin, boolean useScene) {
        this.actor = new Actor(skin, false);
        this.useScene = useScene;
        rebuild(null);
    }

    /** Reads the timings again when the scenes have been read again. */
    private void refresh() {
        if (!useScene) return;
        long v = SceneData.version();
        if (v == seenVersion) return;
        seenVersion = v;
        rebuild(Layers.cut());
    }

    /** Works the timings out: the defaults, with what the scene's events on the actor track say put over them. */
    private void rebuild(SceneData.Cut cut) {
        SIL_FROM = 19_000;
        FULL_FROM = 38_000;
        SCREAM_FROM = 56_000;
        SCREAM_TO = 57_500;
        SONG_END = 77_500;
        EYES_FROM = 79_500;
        EYES_TO = 133_500;
        STUCK = new double[][]{{43_200, 44_700}, {49_400, 51_000}, {67_600, 68_800}};
        List<Seg> mine = new ArrayList<>();
        if (cut != null) {
            List<double[]> stuck = new ArrayList<>();
            for (SceneData.Event e : cut.events) {
                if (e.track() != SceneLabels.ACTOR) continue;
                String l = e.label();
                double a = e.from() * 1000, b = e.to() * 1000;
                if (l.equals(SceneLabels.SILHOUETTE)) SIL_FROM = a;
                else if (l.equals(SceneLabels.WHOLE)) {
                    FULL_FROM = a;
                    SONG_END = b;
                } else if (l.equals(SceneLabels.EYES)) {
                    EYES_FROM = a;
                    EYES_TO = b;
                } else if (l.equals(SceneLabels.SCREAM)) {
                    SCREAM_FROM = a;
                    SCREAM_TO = b;
                } else if (l.equals(SceneLabels.STUCK)) stuck.add(new double[]{a, b});
                else {
                    int sr = SceneLabels.indexOf(SceneLabels.SERIES, l), rs = SceneLabels.indexOf(SceneLabels.RESTS, l);
                    if (sr >= 0) mine.add(new Seg(a, b, false, sr));
                    if (rs >= 0) mine.add(new Seg(a, b, true, rs));
                }
            }
            if (!stuck.isEmpty()) STUCK = stuck.toArray(new double[0][]);
        }
        segs.clear();
        if (!mine.isEmpty()) {
            mine.sort((x, y) -> Double.compare(x.from, y.from));
            segs.addAll(mine);
        } else {
            defaultSegs();
        }
        List<Double> h = new ArrayList<>();
        for (double t = FULL_FROM; t < SCREAM_FROM - 100; t += 600) h.add(t);              // the pounding before the scream
        for (double t = SCREAM_TO + 500; t < SONG_END - 600; t += 600) h.add(t);           // and after it
        for (double t : ContactBreak.beatTimes()) if (t < EYES_TO) h.add(t);               // in the part with the eye: on every beat
        List<Double> kept = new ArrayList<>();
        for (double t : h) {
            boolean resting = false;
            for (Seg z : segs) if (z.rest && t >= z.from - 150 && t < z.to) resting = true;
            if (!resting) kept.add(t);
        }
        hits = new double[kept.size()];
        for (int i = 0; i < hits.length; i++) hits[i] = kept.get(i);
    }

    /** He does not pound without a stop: a series of blows (a different kind each time), then a rest of a different kind each time. */
    private void defaultSegs() {
        double[][] regions = {{FULL_FROM, SCREAM_FROM - 100, 3.6, 5.2, 1.9, 2.8}, {SCREAM_TO + 500, SONG_END, 3.6, 5.2, 1.9, 2.8}, {EYES_FROM, EYES_TO, 5.0, 8.0, 2.0, 3.2}};
        int[] order = {0, 3, 1, 5, 2, 6, 4};
        Random rnd = new Random(1313);
        int restN = 0, blowN = 0;
        for (double[] reg : regions) {
            double t = reg[0];
            while (t < reg[1] - 700) {
                double end = Math.min(reg[1], t + 1000 * (reg[2] + rnd.nextDouble() * (reg[3] - reg[2])));
                segs.add(new Seg(t, end, false, blowN++ % 3));
                t = end;
                if (t >= reg[1] - 900) break;
                end = Math.min(reg[1], t + 1000 * (reg[4] + rnd.nextDouble() * (reg[5] - reg[4])));
                segs.add(new Seg(t, end, true, order[restN++ % order.length]));
                t = end;
            }
        }
    }

    /** The pose the code itself gives the body at a time, as key data (the editor starts a key from it). */
    String capture(double ms) {
        PoseKeys.skip = true;
        try {
            java.awt.image.BufferedImage tmp = new java.awt.image.BufferedImage(64, 36, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = tmp.createGraphics();
            render(g, 64, 36, ms);
            g.dispose();
        } finally {
            PoseKeys.skip = false;
        }
        return PoseKeys.dump(actor.pose);
    }

    /** What the part is made of, as events for the editor's timeline: {track, from s, to s, value, label}; the author's defaults, not the scene's. */
    static List<Object[]> events(int[] skin) {
        GlitcherCloseup d = new GlitcherCloseup(skin, false);
        List<Object[]> out = new ArrayList<>();
        out.add(new Object[]{SceneLabels.ACTOR, d.SIL_FROM / 1000, d.FULL_FROM / 1000, 1.0, SceneLabels.SILHOUETTE});
        out.add(new Object[]{SceneLabels.ACTOR, d.FULL_FROM / 1000, d.SONG_END / 1000, 1.0, SceneLabels.WHOLE});
        out.add(new Object[]{SceneLabels.ACTOR, d.SCREAM_FROM / 1000, d.SCREAM_TO / 1000, 1.0, SceneLabels.SCREAM});
        out.add(new Object[]{SceneLabels.ACTOR, d.EYES_FROM / 1000, d.EYES_TO / 1000, 1.0, SceneLabels.EYES});
        for (double[] st : d.STUCK) out.add(new Object[]{SceneLabels.ACTOR, st[0] / 1000, st[1] / 1000, 1.0, SceneLabels.STUCK});
        for (Seg z : d.segs) {
            out.add(new Object[]{SceneLabels.ACTOR, z.from / 1000, z.to / 1000, 1.0, z.rest ? SceneLabels.RESTS[z.sub] : SceneLabels.SERIES[z.sub]});
        }
        return out;
    }

    /** A stretch of the dance: a series of blows (sub 0 right fist, 1 the two fists in turn, 2 both together) or a rest (sub: 0 slumped, 1 palm on the glass, 2 claws, 3 speaking, 4 peering, 5 staggering back, 6 glancing about). */
    private record Seg(double from, double to, boolean rest, int sub) {}

    private final List<Seg> segs = new ArrayList<>();

    private Seg segAt(double ms) {
        for (Seg z : segs) if (ms >= z.from && ms < z.to) return z;
        return null;
    }

    /** How strongly the rest of the moment holds him, 0..1: in over a short time before it, out after. */
    private double restWeight(Seg z, double ms) {
        if (z == null || !z.rest) return 0;
        return smooth((ms - (z.from - 250)) / 450.0) * (1 - smooth((ms - z.to) / 300.0));
    }

    /** The rest holding him at a time (also just before it starts and just after it ends), or null. */
    private Seg restAt(double ms) {
        for (Seg z : segs) if (z.rest && ms >= z.from - 250 && ms < z.to + 300) return z;
        return null;
    }

    /** Which fist the blow number i is struck with: 0 right, 1 left, 2 both. */
    private int side(int i) {
        if (i < 0 || i >= hits.length) return 0;
        Seg z = segAt(hits[i]);
        int sub = z == null ? 0 : z.sub;
        return sub == 0 ? 0 : sub == 1 ? i % 2 : 2;
    }

    boolean active(double ms) {
        refresh();
        return (ms >= SIL_FROM && ms < SONG_END) || (ms >= EYES_FROM && ms < EYES_TO);
    }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    /** The time the animation is on: the same as the clock, except in the stretches where it sticks and repeats. */
    private double animTime(double ms) {
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

    /** {roll (rad), zoom, dx, dy (px)}: the whole picture is struck by every hit, leans with every stretch of the dance, and never stands still. */
    double[] camera(double ms, int w, int h) {
        if (!active(ms)) return new double[]{0, 1, 0, 0};
        int i = lastHit(ms);
        double age = i < 0 ? 1e9 : ms - hits[i];
        double roll = 0.016 * Math.sin(ms / 1300.0) + 0.008 * Math.sin(ms / 470.0), zoom = 1 + 0.018 * Math.sin(ms / 900.0), dx = 0, dy = 0;
        double[] scn = sceneCamera(ms);
        if (scn != null) roll += Math.toRadians(scn[7]);                                     // the tilt set for the shot in the editor
        dx += h * 0.010 * Math.sin(ms / 1700.0);
        dy += h * 0.008 * Math.sin(ms / 1100.0 + 1);
        if (ms >= FULL_FROM) {
            Seg z = null;
            int zi = -1;
            for (int k = 0; k < segs.size(); k++) if (ms >= segs.get(k).from && ms < segs.get(k).to) { z = segs.get(k); zi = k; break; }
            if (z != null) {
                double since = ms - z.from, len = z.to - z.from;
                double whip = Math.exp(-since / 260.0);                                         // every new stretch starts with a whip of the camera
                roll += (zi % 2 == 0 ? 1 : -1) * 0.07 * whip;
                zoom *= 1 + 0.05 * whip;
                if (!z.rest) {
                    double dutch = (zi % 4 < 2 ? 1 : -1) * 0.035 * smooth(since / 500.0);       // a tilted horizon for the whole series
                    roll += dutch;
                    zoom *= 1 + 0.10 * smooth(since / Math.max(1, len));                         // and the camera creeps in as the series goes on
                    if (z.sub == 2) dy += h * 0.012 * Math.sin(ms / 90.0);
                } else {
                    zoom *= 1 - 0.03 * smooth(since / 600.0);                                     // after the last blow it draws back
                }
            }
        }
        if (ms >= FULL_FROM && age < 500) {
            double k = Math.exp(-age / 100.0), k2 = Math.exp(-age / 45.0);
            Random rnd = new Random((long) (ms / 22));
            roll += (i % 2 == 0 ? 1 : -1) * 0.034 * k;
            zoom *= 1 + 0.11 * k2 - 0.025 * Math.exp(-Math.pow((age - 170) / 70.0, 2));         // punches in, then recoils a little
            dx += (rnd.nextDouble() - 0.5) * h * 0.045 * k;
            dy += (rnd.nextDouble() - 0.5) * h * 0.045 * k - h * 0.02 * k2;
        }
        if (ms >= SCREAM_FROM && ms < SCREAM_TO + 200) {                                     // the scream: a push in and a constant tremor
            double sc = smooth((ms - SCREAM_FROM) / 180.0) * (1 - smooth((ms - SCREAM_TO) / 200.0));
            Random rnd = new Random((long) (ms / 20));
            zoom *= 1 + 0.16 * sc;
            roll += 0.05 * sc * Math.sin(ms / 38.0);
            dx += (rnd.nextDouble() - 0.5) * h * 0.05 * sc;
            dy += (rnd.nextDouble() - 0.5) * h * 0.05 * sc;
        }
        Seg rs = restAt(ms);
        if (rs != null) {
            double rw2 = restWeight(rs, ms);
            if (rs.sub == 4) zoom *= 1 + 0.12 * rw2;
            if (rs.sub == 5) roll += 0.05 * rw2 * Math.sin(ms / 650.0);
            else roll += 0.012 * rw2 * Math.sin(ms / 700.0);
            dy += rw2 * h * 0.006 * Math.sin(ms / 230.0);
        }
        double[] fx = SceneFx.camera(ms);                                                 // the camera events put on the timeline in the editor
        roll += fx[0];
        zoom *= fx[1];
        dx += fx[2] * h;
        dy += fx[3] * h;
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
        double[] cam = sceneCamera(ms);                                                            // the camera set in the editor, if the scene has one
        rend.camDist = CAM;
        rend.pivotX = 0;
        rend.pivotY = 0;
        rend.camH = 16;
        double anim = animTime(ms);
        // how solid he is: black against the light until 0:38, then switched on with a flash
        double full = smooth((ms - FULL_FROM) / 380.0);
        double approach = ms < FULL_FROM ? smooth((ms - SIL_FROM) / (FULL_FROM - SIL_FROM)) : 1;   // it comes nearer over the 19 s
        double sf = 0.55 + 0.45 * approach;
        double u = h * 0.031 * sf;                                                                  // screen pixels per skin unit
        double ox = w * 0.44, oy = h * (1.2 + (1 - sf) * 0.62);
        camYaw = 0;
        camPitch = 0;
        if (cam != null) {
            double dx = cam[0] - cam[3], dy = cam[1] - cam[4], dz = cam[2] - cam[5];
            double dist = Math.max(4, Math.sqrt(dx * dx + dy * dy + dz * dz));
            camYaw = -Math.atan2(dx, dz);
            camPitch = Math.asin(Math.max(-1, Math.min(1, dy / dist)));
            u = h * 0.5 / Math.tan(Math.toRadians(cam[6] / 2)) / dist;
            ox = w * 0.5 - cam[3] * u;
            oy = h * 0.5 + cam[4] * u;
            rend.camDist = dist;
            rend.pivotX = cam[3];
            rend.pivotY = cam[4];
            rend.camH = cam[4];
        }
        sceneDist = rend.camDist;
        scenePivotX = rend.pivotX;
        scenePivotY = rend.pivotY;

        // ---- the pose
        Pose15 p = actor.begin();
        double breath = Math.sin(ms / 1000.0 * 1.5);
        p.reach(Rig15.Limb.R_LEG, -2.4, 3, 0, 1).reach(Rig15.Limb.L_LEG, 2.4, 3, 0, 1);
        p.rootPos[1] = -0.7 - 0.25 * breath;
        int hi = lastHit(ms);
        double hage = hi < 0 ? 1e9 : ms - hits[hi];
        actor.viewYaw = camYaw + 0.07 * Math.sin(ms / 1100.0) + (ms >= FULL_FROM && hage < 400 ? (hi % 2 == 0 ? 1 : -1) * 0.05 * Math.exp(-hage / 120.0) : 0);
        actor.viewPitch = camPitch + 0.03 * Math.sin(ms / 1700.0);
        // ---- the dance: which blow he is winding up for, or which rest he is in
        Seg rs = ms >= FULL_FROM ? restAt(ms) : null;
        double rww = rs == null ? 0 : restWeight(rs, ms);
        int kind = rs == null ? 0 : rs.sub;
        double tr = rs == null ? 0 : (ms - rs.from) / 1000.0;                       // seconds into the rest
        double pr = rs == null ? 0 : Math.max(0, Math.min(1, (ms - rs.from) / (rs.to - rs.from)));
        int li = lastHit(anim);
        double rr = 0.5, uu = 0;
        int next = li + 1;
        if (ms >= FULL_FROM && li >= 0 && next < hits.length) {
            double spacing = Math.min(1200, hits[next] - hits[li]);
            uu = Math.max(0, Math.min(1, (anim - hits[li]) / spacing));
            rr = uu < 0.78 ? smooth(uu / 0.78) : 1 - smooth((uu - 0.78) / 0.22);
        }
        double[] down = {-4.5, 19.0, 15.0}, up = {-7.5, 30.0, 3.5}, guard = {-5.5, 14.0, 7.0};     // the blow comes forward, at the camera
        int sd = side(next), sdPrev = side(li);
        double[] armR = new double[3], armL = new double[3];
        for (int k = 0; k < 3; k++) {
            double strike = down[k] + (up[k] - down[k]) * rr;
            double rest = down[k] + (guard[k] - down[k]) * smooth(uu * 2.2);                     // the fist that has struck comes back to guard
            double mine = (sd == 0 || sd == 2) ? strike : (sdPrev == 0 || sdPrev == 2) ? rest : guard[k];
            double his = (sd == 1 || sd == 2) ? strike : (sdPrev == 1 || sdPrev == 2) ? rest : guard[k];
            armR[k] = mine;
            armL[k] = k == 0 ? -his : his;                                                          // mirrored for the left arm
        }
        double hit = 1 - rr;                                                                         // 1 at the moment of the blow
        double lunge = ms >= FULL_FROM ? hit * hit * hit : 0;
        double lean = 7 * (1 - rr);
        double headDip = 9 * (1 - rr);
        double headYaw = 0, headRoll = 0;
        double scaleMul = 1, dxScreen = 0;
        Actor.Hand shape = Actor.Hand.GRIP;
        double fist = 1;
        double rootZ = 3.2 * lunge, rootY = -1.1 * lunge, rootX = 0;
        if (ms >= FULL_FROM && rww < 1) {
            // the body goes into every blow: the weight shifts from side to side, the shoulders swing round
            double swing = (sd == 2 ? 0 : (sd == 1 ? 1 : -1)) * 14 * lunge * (1 - rww);
            actor.blendTurn(Rig15.Joint.UPPER_TORSO, 0, swing, 0, 1);
        }
        if (rww > 0) {
            double[][] calm = new double[2][3];
            double t = ms / 1000.0;
            double heave = Math.sin(t * 4.2) * 0.5 + 0.5;                                           // heavy, quick breathing
            double wob = Math.sin(t * 2.3);
            double[] leanTo = new double[1];
            double l2 = 0, hd = 0, hy = 0, hrl = 0, rz = 0, ry = 0, rx = 0, sm = 1, dx = 0;
            Actor.Hand sh = Actor.Hand.LOOSE;
            double fs = 0;
            switch (kind) {
                case 0 -> {                                                                         // slumped, spent
                    calm[0] = new double[]{-4, 8, 2};
                    calm[1] = new double[]{4, 8, 2};
                    l2 = 36 + 3 * heave; hd = 28; rz = 1; ry = -2.4 - 0.4 * heave; rx = 1.2 * Math.sin(t * 0.8); sm = 0.97;
                    hy = 8 * Math.sin(t * 0.6); hrl = 5 * Math.sin(t * 0.7);
                }
                case 1 -> {                                                                         // palm on the glass, sliding slowly down; the forehead leans
                    calm[0] = new double[]{-3, 27 - 12 * smooth(pr), 14.5};
                    calm[1] = new double[]{4, 10, 3};
                    l2 = 12; hd = 16 + 3 * heave; hrl = 6; rz = 4; ry = -0.8 * smooth(pr); sh = Actor.Hand.OPEN;
                }
                case 2 -> {                                                                         // claws drawn down the glass, one hand after the other
                    calm[0] = new double[]{-4, 31 - 15 * ((t * 0.9) % 1), 14};
                    calm[1] = new double[]{4, 31 - 15 * ((t * 0.9 + 0.5) % 1), 14};
                    l2 = 14; hd = 20; rz = 3; ry = -0.6; sh = Actor.Hand.CLAW; fs = 0.25; hrl = 4 * Math.sin(t * 1.1);
                }
                case 3 -> {                                                                         // steps back, hand half-raised, looks about, tries to speak
                    calm[0] = new double[]{-6 + 1.5 * wob, 18 + 1.5 * Math.sin(t * 1.7), 8};
                    calm[1] = new double[]{5, 14, 5};
                    l2 = 2; hd = -6 + 4 * Math.sin(t * 1.7); hy = 20 * Math.sin(t * 0.9); hrl = 5 * wob; rz = -3; sm = 0.9; dx = 0.05; sh = Actor.Hand.OPEN;
                }
                case 4 -> {                                                                         // comes right up to the glass, both palms flat, the face fills the view
                    calm[0] = new double[]{-6, 24, 15};
                    calm[1] = new double[]{6, 24, 15};
                    l2 = 14; hd = 10; hy = 10 * Math.sin(t * 0.8); hrl = 4 * Math.sin(t * 1.3); rz = 8; sm = 1.15; sh = Actor.Hand.OPEN;
                }
                case 5 -> {                                                                         // staggers back as if struck, arms flung out
                    calm[0] = new double[]{-9, 13, -2 + 1.5 * Math.sin(t * 5)};
                    calm[1] = new double[]{9, 13, -2 + 1.5 * Math.sin(t * 5 + 2)};
                    l2 = -14; hd = -8; rz = -6; rx = 3 * Math.sin(t * 1.4); sm = 0.86; dx = -0.06; hrl = 8 * Math.sin(t * 1.4);
                }
                default -> {                                                                        // glances about, jerking, twitching
                    calm[0] = new double[]{-4, 10, 4};
                    calm[1] = new double[]{4, 10, 4};
                    int step = (int) Math.floor(t * 1.7);
                    double jerk = ((step * 2654435761L >>> 7) % 5 - 2) / 2.0;                        // -1..1, changes at every step
                    hy = 38 * jerk * smooth((t * 1.7 - step) * 6); l2 = 5; hd = -4 + 6 * Math.abs(jerk); hrl = 8 * jerk;
                    rx = 0.6 * Math.sin(t * 25) * (Math.abs(jerk) > 0.9 ? 1 : 0);
                }
            }
            for (int k = 0; k < 3; k++) {
                armR[k] += (calm[0][k] - armR[k]) * rww;
                armL[k] += (calm[1][k] - armL[k]) * rww;
            }
            lean += (l2 - lean) * rww;
            headDip += (hd - headDip) * rww;
            headYaw = hy * rww;
            headRoll = hrl * rww;
            rootZ += (rz - rootZ) * rww;
            rootY += (ry - rootY) * rww - 0.35 * heave * rww;
            rootX = rx * rww;
            scaleMul = 1 + (sm - 1) * rww;
            dxScreen = dx * rww;
            if (rww > 0.5) {
                shape = sh;
                fist = fs;
            }
        }
        actor.reach(Rig15.Limb.R_ARM, armR[0], armR[1], armR[2], 1);
        actor.reach(Rig15.Limb.L_ARM, armL[0], armL[1], armL[2], 1);
        actor.shapeR = shape;
        actor.fistR = fist;
        actor.shapeL = shape;
        actor.fistL = fist;
        p.rootPos[0] += rootX;
        p.rootPos[1] += rootY;
        p.rootPos[2] += rootZ;
        actor.blendTurn(Rig15.Joint.UPPER_TORSO, lean, 0, 0, 1);                                      // leans into every blow
        actor.blendTurn(Rig15.Joint.HEAD, headDip, headYaw, headRoll, 1);
        // the arm with the TV: up and out at chest height, bent (over everything else)
        double show = SnakesFx.presenting(ms);
        if (show > 0.01) {
            actor.reach(Rig15.Limb.L_ARM, 10.0, 20.0, 6.5, show);
            actor.shapeL = show > 0.3 ? Actor.Hand.GRIP : Actor.Hand.LOOSE;
            actor.fistL = 0.6 * show;
        }
        // the scream, and the head snapped round
        double scream = smooth((ms - SCREAM_FROM) / 200.0) * (1 - smooth((ms - SCREAM_TO + 150) / 200.0));
        double snap = smooth((ms - 56_900) / 90.0) * (1 - smooth((ms - 57_450) / 110.0));
        if (scream > 0) actor.blendTurn(Rig15.Joint.HEAD, -26, 0, 0, scream * (1 - snap));
        if (snap > 0) actor.blendTurn(Rig15.Joint.HEAD, 0, 40, 8, snap);

        // ---- the light: none at first (a silhouette), then the cold light with the TV's glow at his side
        double[] key = {1.2 * full, 1.16 * full, 1.45 * full}, amb = {0.8 * full, 0.74 * full, 1.0 * full};
        rend.clearDirty();
        rend.setLight(key, amb);
        u *= scaleMul;
        ox += dxScreen * w;
        if (useScene && Layers.cut() != null) PoseKeys.apply(actor, ms / 1000.0, Layers.cut().keys);       // the poses set in the editor, laid over the code's own
        long pf0 = System.nanoTime();
        actor.draw(rend, u * RES, ox * RES, oy * RES);
        long pf1 = System.nanoTime();
        int thick = Math.max(2, (int) Math.round(u * RES * 0.28));
        ActorFx.rim(rend, 0xA8C4FF, 0.9, -0.6, 0.8, thick);                                           // the edge of him against the dark
        ActorFx.rim(rend, 0x6FE0FF, 0.85, 1, 0.1, thick + 1);                                         // the glow of the TV on the side that holds it
        if (ms < FULL_FROM) glitch.apply(rend.pixels, rend.width, rend.height, 0.18 * (1 - approach) + 0.07);
        double alpha = ms < FULL_FROM ? 0.30 + 0.65 * approach : 1;

        long pf2 = System.nanoTime();
        Graphics2D b = (Graphics2D) g.create();
        b.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        b.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alpha));
        rend.premultiply();
        {
            int m = 4 + thick;                                                  // the box of what was drawn, with room for the edge light
            int sx0 = Math.max(0, rend.bx0 - m), sy0 = Math.max(0, rend.by0 - m), sx1 = Math.min(rend.width, rend.bx1 + m + 1), sy1 = Math.min(rend.height, rend.by1 + m + 1);
            if (sx1 > sx0 && sy1 > sy0) {
                double kx = (double) w / rend.width, ky = (double) h / rend.height;
                b.drawImage(rend.imagePre, (int) Math.floor(sx0 * kx), (int) Math.floor(sy0 * ky), (int) Math.ceil(sx1 * kx), (int) Math.ceil(sy1 * ky), sx0, sy0, sx1, sy1, null);
            }
        }
        b.setComposite(AlphaComposite.SrcOver);

        // where the fist with the TV is on the screen
        if (actor.lastSkel != null) {
            double[] hp = proj(actor.lastSkel.endPoint(Rig15.Limb.L_ARM));
            fistX = ox + hp[0] * u;
            fistY = oy - hp[1] * u;
            haveFist = true;
        }
        // ---- the scream: his mouth opens in the black of the face
        if (scream > 0.15 && actor.lastSkel != null) {
            double[] top = proj(actor.lastSkel.headTop());
            drawMouth(b, ox + top[0] * u, oy - top[1] * u + u * 4.2, u, scream);
        }
        if (rww > 0.6 && kind == 3 && actor.lastSkel != null) {                                         // a small mouth, opening and closing without a sound
            double[] top = proj(actor.lastSkel.headTop());
            double t = ms / 1000.0;
            double open = Math.max(0, Math.sin(t * 9.0) * Math.sin(t * 3.1 + 1)) * 0.22 * rww;
            if (open > 0.02 && pr > 0.08) drawMouth(b, ox + top[0] * u, oy - top[1] * u + u * 4.2, u * 0.5, open * 2.2);
        }
        long pf3 = System.nanoTime();
        drawCracks(b, w, h, ms, u);
        long pf4 = System.nanoTime();
        PROF[0] += pf1 - pf0; PROF[1] += pf2 - pf1; PROF[2] += pf3 - pf2; PROF[3] += pf4 - pf3; PROF[4]++;
        // a white flash on each blow of the fist
        int li2 = lastHit(ms);
        if (ms >= FULL_FROM && li2 >= 0 && ms - hits[li2] < 80) {
            b.setColor(new Color(255, 255, 255, (int) (95 * (1 - (ms - hits[li2]) / 80.0))));
            b.fillRect(0, 0, w, h);
        }
        b.dispose();
    }

    /** A point of the body as it is seen from the camera: {x right, y up}, in skin units from the feet, with the perspective. */
    private double[] proj(double[] st) {
        double yaw = actor.viewYaw, pitch = actor.viewPitch;
        double x = st[0] - scenePivotX, y = st[1] - scenePivotY, z = st[2];
        double c = Math.cos(yaw), sn = Math.sin(yaw);
        double x1 = x * c + z * sn, z1 = -x * sn + z * c;
        double c2 = Math.cos(pitch), s2 = Math.sin(pitch);
        double y2 = y * c2 - z1 * s2, z2 = y * s2 + z1 * c2;
        double k = sceneDist / Math.max(2, sceneDist - z2);
        return new double[]{scenePivotX + x1 * k, scenePivotY + y2 * k};
    }

    private double camYaw, camPitch, sceneDist = CAM, scenePivotX, scenePivotY;
    private static final String CUT_NAME = "Песня 2";

    /** The camera of this scene as it stands in the editor's project ({x, y, z, target x, y, z, fov, roll}), or null to keep the old fixed view. */
    private double[] sceneCamera(double ms) {
        SceneData.Cut cut = Layers.cut();
        return cut == null ? null : cut.cameraAt(ms);
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
    private java.awt.image.BufferedImage crackCache;
    private long crackKey = -1;

    private void drawCracks(Graphics2D b, int w, int h, double ms, double u) {
        int last = lastHit(ms);
        if (last < 0 || ms < FULL_FROM) return;
        int from = Math.max(0, last - 34);
        int grown = last;                                                      // the newest crack that has finished growing
        while (grown >= from && ms - hits[grown] < 130) grown--;
        long key = ((long) last << 24) ^ ((long) (grown + 1) << 44) ^ w ^ ((long) h << 12);
        if (crackCache == null || crackCache.getWidth() != w || crackCache.getHeight() != h) {
            crackCache = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE);
            crackKey = -1;
        }
        if (key != crackKey) {                                                 // a new blow: the finished cracks are drawn again, once
            Graphics2D c = crackCache.createGraphics();
            c.setComposite(AlphaComposite.Clear);
            c.fillRect(0, 0, w, h);
            c.setComposite(AlphaComposite.SrcOver);
            c.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (int i = from; i <= grown; i++) drawCrack(c, i, from, last, 1, w, h);
            c.dispose();
            crackKey = key;
        }
        b.drawImage(crackCache, 0, 0, null);
        for (int i = Math.max(from, grown + 1); i <= last; i++) drawCrack(b, i, from, last, Math.min(1, (ms - hits[i]) / 130.0), w, h);
    }

    private void drawCrack(Graphics2D b, int i, int from, int last, double grow, int w, int h) {
        if (hits[i] < FULL_FROM) return;
        Random rnd = new Random(7000 + i);
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
