package com.lotusblight.overlay;

import java.util.ArrayList;
import java.util.List;

/**
 * The 15-joint rig (the "R15" layout): hips, waist, neck, and for each arm a shoulder, elbow and wrist, for
 * each leg a hip, knee and ankle. Units are skin pixels, origin between the feet, y up, facing +z - the same
 * space as {@link SkinModel}, so the same 64x64 skins fit. The boxes of a limb are cut at the joints, the
 * cuts being where the texture of the limb is cut as well.
 *
 * <p>{@link #solve} turns a {@link Pose15} into the world transform of every joint: forward kinematics for the
 * turns, then two-bone IK for the limbs that have a target, and a look-at for the head.
 */
final class Rig15 {
    /** The 15 joints of the body, then five fingers of two bones on each hand (the right hand's first). */
    static final int N = 35;
    static final int BODY_JOINTS = 15;

    enum Joint {
        LOWER_TORSO, UPPER_TORSO, HEAD,
        R_UPPER_ARM, R_LOWER_ARM, R_HAND,
        L_UPPER_ARM, L_LOWER_ARM, L_HAND,
        R_UPPER_LEG, R_LOWER_LEG, R_FOOT,
        L_UPPER_LEG, L_LOWER_LEG, L_FOOT,
        R_THUMB_1, R_THUMB_2, R_INDEX_1, R_INDEX_2, R_MIDDLE_1, R_MIDDLE_2, R_RING_1, R_RING_2, R_PINKY_1, R_PINKY_2,
        L_THUMB_1, L_THUMB_2, L_INDEX_1, L_INDEX_2, L_MIDDLE_1, L_MIDDLE_2, L_RING_1, L_RING_2, L_PINKY_1, L_PINKY_2;

        /** Channel-name form: {@code r_upper_arm}. */
        String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        static Joint byKey(String key) {
            for (Joint j : values()) if (j.key().equals(key)) return j;
            return null;
        }
    }

    /** A chain that can be solved to a target: the upper and lower joint and the end (hand or foot). */
    enum Limb {
        R_ARM(Joint.R_UPPER_ARM, Joint.R_LOWER_ARM, Joint.R_HAND, true),
        L_ARM(Joint.L_UPPER_ARM, Joint.L_LOWER_ARM, Joint.L_HAND, true),
        R_LEG(Joint.R_UPPER_LEG, Joint.R_LOWER_LEG, Joint.R_FOOT, false),
        L_LEG(Joint.L_UPPER_LEG, Joint.L_LOWER_LEG, Joint.L_FOOT, false);

        final Joint upper, lower, end;
        final boolean arm;

        Limb(Joint upper, Joint lower, Joint end, boolean arm) {
            this.upper = upper;
            this.lower = lower;
            this.end = end;
            this.arm = arm;
        }

        String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        static Limb byKey(String key) {
            for (Limb l : values()) if (l.key().equals(key)) return l;
            return null;
        }
    }

    /** One textured face of one joint's box: corners in rest-pose model space and the skin rectangle over them. */
    record Face(Joint joint, double[][] corners, int u0, int v0, int u1, int v1) {}

    private static final int[] PARENT = {
            -1, 0, 1,
            1, 3, 4,
            1, 6, 7,
            0, 9, 10,
            0, 12, 13,
            // right hand's fingers: the first bone on the hand (5), the second on the first
            5, 15, 5, 17, 5, 19, 5, 21, 5, 23,
            // left hand's (8)
            8, 25, 8, 27, 8, 29, 8, 31, 8, 33};

    /** Length of a hand from the wrist to the fist's tip, and a foot's height from the ankle to the sole. */
    static final double HAND_LENGTH = 3, ANKLE_HEIGHT = 3;

    final boolean honcho;
    /** Rest-pose world position of every joint's pivot. */
    final double[][] pivot = new double[N][];
    final double[] limbUpper = new double[4], limbLower = new double[4];
    final List<Face> faces = new ArrayList<>();

    /** How round the boxes are, 0 (plain boxes) to about 0.5: their corners and edges are pulled in towards a sphere, the faces cut at the skin's pixels so that it stays sharp. */
    final double round;

    private Rig15(boolean honcho) {
        this(honcho, 0);
    }

    private Rig15(boolean honcho, double round) {
        this.round = round;
        this.honcho = honcho;
        double armC = honcho ? 6 : 5.5;
        pivot[Joint.LOWER_TORSO.ordinal()] = new double[]{0, 13, 0};
        pivot[Joint.UPPER_TORSO.ordinal()] = new double[]{0, 17, 0};
        pivot[Joint.HEAD.ordinal()] = new double[]{0, 24, 0};
        for (int side = 0; side < 2; side++) {
            double s = side == 0 ? -1 : 1;                              // right limbs are on -x, as in the skin model
            int arm = Joint.R_UPPER_ARM.ordinal() + side * 3, leg = Joint.R_UPPER_LEG.ordinal() + side * 3;
            pivot[arm] = new double[]{s * armC, 22, 0};
            pivot[arm + 1] = new double[]{s * armC, 18, 0};
            pivot[arm + 2] = new double[]{s * armC, 15, 0};
            pivot[leg] = new double[]{s * 2, 12, 0};
            pivot[leg + 1] = new double[]{s * 2, 7, 0};
            pivot[leg + 2] = new double[]{s * 2, 3, 0};
        }
        for (int side = 0; side < 2; side++) {
            double s = side == 0 ? -1 : 1, medial = -s;              // towards the body
            double cx = s * armC;
            int first = Joint.R_THUMB_1.ordinal() + side * 10;
            for (int f = 0; f < 5; f++) {
                double[] a = fingerPivot(f, cx, medial, honcho), b = fingerTip(f, cx, medial, honcho);
                pivot[first + f * 2] = a;
                pivot[first + f * 2 + 1] = b;
            }
        }
        for (Limb l : Limb.values()) {
            double[] a = pivot[l.upper.ordinal()], b = pivot[l.lower.ordinal()], c = pivot[l.end.ordinal()];
            limbUpper[l.ordinal()] = Quat.length(Quat.sub(b, a));
            // The arm is solved to the tip of the fist, taking the hand as straight on from the forearm.
            limbLower[l.ordinal()] = Quat.length(Quat.sub(c, b)) + (l.arm ? HAND_LENGTH : 0);
        }
        buildModel(armC);
    }

    /** The standard slim skin: 3-pixel arms, with the outer layers. */
    static Rig15 standard() {
        return new Rig15(false);
    }

    /** The standard skin with its boxes rounded off by {@code round} (0 is the plain rig). */
    static Rig15 rounded(double round) {
        return new Rig15(false, round);
    }

    /** Honcho's proportions: 4-pixel arms, only the layer over the head. */
    static Rig15 honcho() {
        return new Rig15(true);
    }

    static Joint parentOf(Joint j) {
        int p = PARENT[j.ordinal()];
        return p < 0 ? null : Joint.values()[p];
    }

    // ------------------------------------------------------------ the model

    private void buildModel(double armC) {
        int armW = honcho ? 4 : 3;
        double armX = honcho ? 8 : 7;
        // Head: the base box and the layer over it.
        slab(Joint.HEAD, -4, 24, -4, 8, 8, 8, 0, 0, 0, 24, 32);
        slab(Joint.HEAD, -4, 24, -4, 8, 8, 8, 32, 0, 0.5, 24, 32);
        // Body, cut at the waist.
        for (double grow : honcho ? new double[]{0} : new double[]{0, 0.25}) {
            int v = grow == 0 ? 16 : 32;
            slab(Joint.LOWER_TORSO, -4, 12, -2, 8, 12, 4, 16, v, grow, 12, 17);
            slab(Joint.UPPER_TORSO, -4, 12, -2, 8, 12, 4, 16, v, grow, 17, 24);
        }
        // Arms: upper (with the shoulder cap), forearm, hand.
        for (double grow : honcho ? new double[]{0} : new double[]{0, 0.25}) {
            int v = grow == 0 ? 16 : 32, lv = grow == 0 ? 48 : 48, lu = grow == 0 ? 32 : 48;
            Joint[] r = {Joint.R_UPPER_ARM, Joint.R_LOWER_ARM, Joint.R_HAND};
            Joint[] l = {Joint.L_UPPER_ARM, Joint.L_LOWER_ARM, Joint.L_HAND};
            double[][] cuts = {{18, 24}, {15, 18}, {13.8, 15}};                  // the hand is the palm; the fingers hang from it
            for (int i = 0; i < 3; i++) {
                slab(r[i], -armX, 12, -2, armW, 12, 4, 40, v, grow, cuts[i][0], cuts[i][1]);
                slab(l[i], 4, 12, -2, armW, 12, 4, lu, lv, grow, cuts[i][0], cuts[i][1]);
            }
        }
        for (int side = 0; side < 2; side++) fingers(side, armC, armW);
        // Legs: thigh, shin, foot.
        for (double grow : honcho ? new double[]{0} : new double[]{0, 0.25}) {
            int rv = grow == 0 ? 16 : 32, lu = grow == 0 ? 16 : 0, lv = 48;
            Joint[] r = {Joint.R_UPPER_LEG, Joint.R_LOWER_LEG, Joint.R_FOOT};
            Joint[] l = {Joint.L_UPPER_LEG, Joint.L_LOWER_LEG, Joint.L_FOOT};
            double[][] cuts = {{7, 12}, {3, 7}, {0, 3}};
            for (int i = 0; i < 3; i++) {
                slab(r[i], -4, 0, -2, 4, 12, 4, 0, rv, grow, cuts[i][0], cuts[i][1]);
                slab(l[i], 0, 0, -2, 4, 12, 4, lu, lv, grow, cuts[i][0], cuts[i][1]);
            }
        }
    }

    /**
     * The part of a Minecraft-layout box between heights y0 and y1, as a joint's box: its four sides cut from
     * the box's own side rectangles, and a cap above and below (inside the model in the straight pose, shown
     * where a joint bends). {@code grow} puffs the box out, at the very top and bottom of the box as well.
     */
    private void slab(Joint j, double bx, double by, double bz, int w, int h, int d, int u, int v, double grow, double y0, double y1) {
        double x0 = bx - grow, x1 = bx + w + grow, z0 = bz - grow, z1 = bz + d + grow;
        double top = by + h, bottom = by;
        double ya = y0 <= bottom ? y0 - grow : y0, yb = y1 >= top ? y1 + grow : y1;
        int r1 = v + d + (int) (top - y1), r0 = v + d + (int) (top - y0);
        if (round > 0) {
            roundedSlab(j, bx, by, bz, w, h, d, u, v, grow, ya, yb, y0, y1);
            return;
        }
        faces.add(new Face(j, new double[][]{{x0, yb, z0}, {x1, yb, z0}, {x1, yb, z1}, {x0, yb, z1}}, u + d, v, u + d + w, v + d));
        faces.add(new Face(j, new double[][]{{x0, ya, z1}, {x1, ya, z1}, {x1, ya, z0}, {x0, ya, z0}}, u + d + w, v, u + d + 2 * w, v + d));
        faces.add(new Face(j, new double[][]{{x0, yb, z0}, {x0, yb, z1}, {x0, ya, z1}, {x0, ya, z0}}, u, r1, u + d, r0));
        faces.add(new Face(j, new double[][]{{x0, yb, z1}, {x1, yb, z1}, {x1, ya, z1}, {x0, ya, z1}}, u + d, r1, u + d + w, r0));
        faces.add(new Face(j, new double[][]{{x1, yb, z1}, {x1, yb, z0}, {x1, ya, z0}, {x1, ya, z1}}, u + d + w, r1, u + 2 * d + w, r0));
        faces.add(new Face(j, new double[][]{{x1, yb, z0}, {x0, yb, z0}, {x0, ya, z0}, {x1, ya, z0}}, u + 2 * d + w, r1, u + 2 * d + 2 * w, r0));
    }

    /** A point of the box pulled in towards the ellipsoid round the whole box (not only this slab of it), so that the pieces of a limb meet. */
    private double[] rounded(double[] c, double[] e, double x, double y, double z) {
        double qx = (x - c[0]) / e[0], qy = (y - c[1]) / e[1], qz = (z - c[2]) / e[2];
        double len = Math.sqrt(qx * qx + qy * qy + qz * qz);
        double f = len < 1e-9 ? 1 : (1 - round) + round / Math.max(1, len);
        return new double[]{c[0] + qx * f * e[0], c[1] + qy * f * e[1], c[2] + qz * f * e[2]};
    }

    /**
     * The same box as {@link #slab} but every face cut into one quad per skin pixel, its points pulled in at the corners and edges. The sides
     * run between ya and yb in whole rows of the skin (a row at the ends may be a part of one).
     */
    private void roundedSlab(Joint j, double bx, double by, double bz, int w, int h, int d, int u, int v, double grow, double ya, double yb, double y0, double y1) {
        double x0 = bx - grow, x1 = bx + w + grow, z0 = bz - grow, z1 = bz + d + grow;
        double top = by + h;
        double[] c = {bx + w / 2.0, by + h / 2.0, bz + d / 2.0}, e = {w / 2.0 + grow, h / 2.0 + grow, d / 2.0 + grow};
        // the horizontal rings of the sides: at every whole unit of height between ya and yb
        java.util.List<Double> ys = new java.util.ArrayList<>();
        ys.add(yb);
        for (double y = Math.floor(yb - 1e-9); y > ya + 1e-9; y -= 1) if (y < yb - 1e-9) ys.add(y);
        ys.add(ya);
        // top and bottom: grids of w x d pixels
        for (int cap = 0; cap < 2; cap++) {
            double y = cap == 0 ? yb : ya;
            boolean real = cap == 0 ? y1 >= top : y0 <= by;                                  // the cap on a cut inside the limb is not rounded off at its edges either
            for (int i = 0; i < w; i++) {
                for (int k = 0; k < d; k++) {
                    double xa = x0 + (x1 - x0) * i / w, xb = x0 + (x1 - x0) * (i + 1) / w, za = z0 + (z1 - z0) * k / d, zb = z0 + (z1 - z0) * (k + 1) / d;
                    double[][] q = {{xa, y, za}, {xb, y, za}, {xb, y, zb}, {xa, y, zb}};
                    double[][] pts = new double[4][];
                    for (int m = 0; m < 4; m++) pts[m] = rounded(c, e, q[m][0], q[m][1], q[m][2]);
                    if (cap == 0) faces.add(new Face(j, pts, u + d + i, v + k, u + d + i + 1, v + k + 1));
                    else faces.add(new Face(j, new double[][]{pts[3], pts[2], pts[1], pts[0]}, u + d + w + i, v + k, u + d + w + i + 1, v + k + 1));
                }
            }
        }
        // the four sides, a quad for every pixel column and every unit of height
        for (int s = 0; s < 4; s++) {
            int cols = s % 2 == 0 ? d : w, uBase = s == 0 ? u : s == 1 ? u + d : s == 2 ? u + d + w : u + 2 * d + w;
            for (int i = 0; i < cols; i++) {
                for (int r = 0; r + 1 < ys.size(); r++) {
                    double ta = ys.get(r), tb = ys.get(r + 1);
                    double[][] q = new double[4][];
                    double f0 = (double) i / cols, f1 = (double) (i + 1) / cols;
                    // side 0: -x (z runs 0..d), 1: +z (x 0..w), 2: +x (z runs d..0), 3: -z (x runs w..0)
                    double[] a0, a1;
                    switch (s) {
                        case 0 -> { a0 = new double[]{x0, z0 + (z1 - z0) * f0}; a1 = new double[]{x0, z0 + (z1 - z0) * f1}; }
                        case 1 -> { a0 = new double[]{x0 + (x1 - x0) * f0, z1}; a1 = new double[]{x0 + (x1 - x0) * f1, z1}; }
                        case 2 -> { a0 = new double[]{x1, z1 - (z1 - z0) * f0}; a1 = new double[]{x1, z1 - (z1 - z0) * f1}; }
                        default -> { a0 = new double[]{x1 - (x1 - x0) * f0, z0}; a1 = new double[]{x1 - (x1 - x0) * f1, z0}; }
                    }
                    q[0] = rounded(c, e, a0[0], ta, a0[1]);
                    q[1] = rounded(c, e, a1[0], ta, a1[1]);
                    q[2] = rounded(c, e, a1[0], tb, a1[1]);
                    q[3] = rounded(c, e, a0[0], tb, a0[1]);
                    int vTop = v + d + (int) Math.floor(top - ta + 1e-9), vBot = v + d + (int) Math.ceil(top - tb - 1e-9);
                    vTop = Math.max(v + d, Math.min(v + d + h - 1, vTop));                 // the strips of the grown layer past the ends keep the skin's end rows
                    vBot = Math.max(vTop + 1, Math.min(v + d + h, vBot));
                    faces.add(new Face(j, q, uBase + i, vTop, uBase + i + 1, vBot));
                }
            }
        }
    }

    // ------------------------------------------------------------ fingers

    /** Finger 0 = thumb, 1 = index, 2 = middle, 3 = ring, 4 = pinky. */
    static Joint finger(boolean right, int finger, int bone) {
        return Joint.values()[Joint.R_THUMB_1.ordinal() + (right ? 0 : 10) + finger * 2 + bone];
    }

    /** z of a finger's slot on the palm (the index at the front), its bone lengths, and its thickness. */
    private static final double[] FINGER_Z = {1.75, 1.5, 0.5, -0.5, -1.5};
    private static final double[][] FINGER_LEN = {{0.95, 0.85}, {1.05, 0.95}, {1.15, 1.0}, {1.05, 0.9}, {0.85, 0.75}};
    private static final double KNUCKLE_Y = 13.8;

    private static double[] fingerPivot(int f, double cx, double medial, boolean honcho) {
        double w = honcho ? 4 : 3;
        double x = f == 0 ? cx + medial * (w / 2 + 0.35) : cx + medial * 0.1;
        double y = f == 0 ? 14.4 : KNUCKLE_Y;
        return new double[]{x, y, FINGER_Z[f]};
    }

    private static double[] fingerTip(int f, double cx, double medial, boolean honcho) {
        double[] a = fingerPivot(f, cx, medial, honcho);
        return new double[]{a[0], a[1] - FINGER_LEN[f][0], a[2]};
    }

    /** The two bones of each of a hand's five fingers: little boxes coloured from the hand's own part of the skin. */
    private void fingers(int side, double armC, int armW) {
        boolean right = side == 0;
        double s = right ? -1 : 1, medial = -s, cx = s * armC;
        int u = right ? 40 : 32, v = right ? 16 : 48, d = 4;
        for (int f = 0; f < 5; f++) {
            double[] a = fingerPivot(f, cx, medial, honcho);
            double thick = f == 0 ? 1.0 : 1.3, slot = f == 0 ? 0.85 : 0.7;          // narrow enough to leave a gap between fingers
            double y = a[1];
            for (int bone = 0; bone < 2; bone++) {
                double len = FINGER_LEN[f][bone];
                double ya = y - len, yb = y;
                double hw = thick / 2, hz = slot / 2;
                Joint j = finger(right, f, bone);
                int row = v + d + Math.max(9, Math.min(11, (int) (24 - (ya + yb) / 2)));
                fingerBox(j, a[0] - hw, a[0] + hw, ya, yb, a[2] - hz, a[2] + hz, u, v, armW, d, row);
                y = ya;
            }
        }
    }

    /** A box of one skin pixel per face, sampled from the arm's own side faces at row {@code row}. */
    private void fingerBox(Joint j, double x0, double x1, double ya, double yb, double z0, double z1, int u, int v, int w, int d, int row) {
        int capU = u + d + w + w / 2, capV = v + d / 2;                      // the bottom of the arm: skin colour
        faces.add(new Face(j, new double[][]{{x0, yb, z0}, {x1, yb, z0}, {x1, yb, z1}, {x0, yb, z1}}, capU, capV, capU + 1, capV + 1));
        faces.add(new Face(j, new double[][]{{x0, ya, z1}, {x1, ya, z1}, {x1, ya, z0}, {x0, ya, z0}}, capU, capV, capU + 1, capV + 1));
        faces.add(new Face(j, new double[][]{{x0, yb, z0}, {x0, yb, z1}, {x0, ya, z1}, {x0, ya, z0}}, u + d / 2, row, u + d / 2 + 1, row + 1));
        faces.add(new Face(j, new double[][]{{x0, yb, z1}, {x1, yb, z1}, {x1, ya, z1}, {x0, ya, z1}}, u + d + w / 2, row, u + d + w / 2 + 1, row + 1));
        faces.add(new Face(j, new double[][]{{x1, yb, z1}, {x1, yb, z0}, {x1, ya, z0}, {x1, ya, z1}}, u + d + w + d / 2, row, u + d + w + d / 2 + 1, row + 1));
        faces.add(new Face(j, new double[][]{{x1, yb, z0}, {x0, yb, z0}, {x0, ya, z0}, {x1, ya, z0}}, u + 2 * d + w + w / 2, row, u + 2 * d + w + w / 2 + 1, row + 1));
    }

    // ------------------------------------------------------------ the solver

    /** What {@link #solve} makes of a pose: the world transform of every joint and what it took to get there. */
    static final class Skel {
        final Rig15 rig;
        /** Maps a joint's own space (origin at its pivot) to the stage. */
        final Quat.Xf[] world = new Quat.Xf[N];
        final Quat[] worldRot = new Quat[N];
        /** The limb solves: how far out of reach each target was (1 = exactly at full stretch, above 1 = too far). */
        final double[] stretch = new double[4];
        final boolean[] solved = new boolean[4];

        Skel(Rig15 rig) {
            this.rig = rig;
        }

        /** A point given in rest-pose model space, carried along with the joint. */
        double[] carry(Joint j, double[] restPoint) {
            return world[j.ordinal()].apply(Quat.sub(restPoint, rig.pivot[j.ordinal()]));
        }

        double[] pivotPos(Joint j) {
            return world[j.ordinal()].t.clone();
        }

        /** Tip of the fist, or the sole under the ankle. */
        double[] endPoint(Limb l) {
            double[] p = rig.pivot[l.end.ordinal()];
            return carry(l.end, new double[]{p[0], p[1] - (l.arm ? HAND_LENGTH : ANKLE_HEIGHT), p[2]});
        }

        double[] headTop() {
            return carry(Joint.HEAD, new double[]{0, 32, 0});
        }
    }

    /** Solves a pose into joint transforms. */
    Skel solve(Pose15 pose) {
        Skel sk = new Skel(this);
        Quat[] local = pose.rot.clone();
        local[0] = pose.rootRot;
        double[] rootPos = Quat.add(pivot[0], pose.rootPos);
        sk.world[0] = Quat.Xf.of(pose.rootRot, rootPos);
        sk.worldRot[0] = pose.rootRot;
        double[] fwd = pose.rootRot.rotate(new double[]{0, 0, 1});
        double yaw = Math.atan2(fwd[0], fwd[2]);
        Quat yawQ = Quat.axisAngle(0, 1, 0, yaw);

        Quat[] ikLocal = new Quat[N];
        double[] ikW = new double[N];
        for (int i = 1; i < N; i++) {
            Joint j = Joint.values()[i];
            int p = PARENT[i];
            // A limb's solve runs when its upper joint is reached, and sets the turns of the whole chain.
            for (Limb l : Limb.values()) {
                Pose15.Ik k = pose.ik[l.ordinal()];
                if (l.upper == j && k.w > 1e-4) solveLimb(sk, l, k, p, yawQ, yaw, ikLocal, ikW);
            }
            if (j == Joint.HEAD && pose.lookW > 1e-4) {
                Quat look = lookAt(sk, pose, p);
                local[i] = Quat.slerp(local[i], look, pose.lookW);
            }
            if (ikLocal[i] != null) local[i] = Quat.slerp(local[i], ikLocal[i], ikW[i]);
            Quat.Xf step = Quat.Xf.of(local[i], Quat.add(Quat.sub(pivot[i], pivot[p]), pose.offset[i]));
            sk.world[i] = sk.world[p].mul(Quat.Xf.of(Quat.IDENTITY, step.t)).mul(Quat.Xf.of(local[i], new double[]{0, 0, 0}));
            sk.worldRot[i] = sk.worldRot[p].mul(local[i]);
        }
        return sk;
    }

    /** The head's turn so that it faces the look target, within what a neck can do. */
    private Quat lookAt(Skel sk, Pose15 pose, int parent) {
        double[] neck = sk.world[parent].apply(Quat.sub(pivot[Joint.HEAD.ordinal()], pivot[parent]));
        double[] dir = Quat.unit(Quat.sub(pose.lookPos, neck));
        double[] d = sk.worldRot[parent].conj().rotate(dir);
        double yaw = Math.atan2(d[0], d[2]);
        double pitch = Math.atan2(-d[1], Math.hypot(d[0], d[2]));
        yaw = Math.max(-Math.toRadians(75), Math.min(Math.toRadians(75), yaw));
        pitch = Math.max(-Math.toRadians(50), Math.min(Math.toRadians(50), pitch));
        return Quat.euler(Math.toDegrees(pitch), Math.toDegrees(yaw), 0);
    }

    /**
     * Two-bone IK with a true hinge at the elbow or knee: the upper and lower bone share one axis, so the
     * forearm or shin never twists against the arm or thigh. The pole says which way the joint points.
     */
    private void solveLimb(Skel sk, Limb l, Pose15.Ik k, int parent, Quat yawQ, double yaw, Quat[] ikLocal, double[] ikW) {
        int li = l.ordinal();
        double l1 = limbUpper[li], l2 = limbLower[li];
        double[] shoulder = sk.world[parent].apply(Quat.sub(pivot[l.upper.ordinal()], pivot[parent]));
        double[] toTarget = Quat.sub(k.pos, shoulder);
        double dist = Quat.length(toTarget);
        sk.stretch[li] = dist / (l1 + l2);
        sk.solved[li] = true;
        double maxD = (l1 + l2) * 0.9995, minD = Math.abs(l1 - l2) * 1.0005 + 0.02;
        double d = Math.max(minD, Math.min(maxD, dist));
        double[] a = dist < 1e-9 ? new double[]{0, -1, 0} : Quat.scale(toTarget, 1 / dist);
        double[] target = Quat.add(shoulder, Quat.scale(a, d));

        double[] pole = k.pole;
        if (Quat.length(pole) < 1e-6) {
            double side = pivot[l.upper.ordinal()][0] < 0 ? -1 : 1;
            // Arms: the elbow points down and back, so it stays natural when the fist goes out in front. Legs: the knee forward.
            pole = sk.worldRot[parent].rotate(l.arm ? new double[]{side * 0.35, -1, -0.6} : new double[]{side * 0.12, 0, 1});
        }
        double[] pp = Quat.sub(pole, Quat.scale(a, Quat.dot(pole, a)));
        if (Quat.length(pp) < 1e-6) pp = Quat.cross(a, new double[]{1, 0, 0});
        if (Quat.length(pp) < 1e-6) pp = new double[]{0, 0, 1};
        pp = Quat.unit(pp);

        double proj = (l1 * l1 - l2 * l2 + d * d) / (2 * d);
        double hgt = Math.sqrt(Math.max(0, l1 * l1 - proj * proj));
        double[] elbow = Quat.add(Quat.add(shoulder, Quat.scale(a, proj)), Quat.scale(pp, hgt));
        double[] u = Quat.unit(Quat.sub(elbow, shoulder));
        double[] lo = Quat.unit(Quat.sub(target, elbow));

        // The hinge axis is perpendicular to the bend plane; it is taken on the side that keeps the limb's own
        // sideways axis from flipping.
        double[] n = Quat.unit(Quat.cross(pp, a));
        double[] xArc = Quat.arc(new double[]{0, -1, 0}, u).rotate(new double[]{1, 0, 0});
        double[] xw = Quat.dot(n, xArc) >= 0 ? n : Quat.scale(n, -1);

        Quat rUpper = frame(xw, u), rLower = frame(xw, lo);
        Quat parentRot = sk.worldRot[parent];
        int ui = l.upper.ordinal(), lo_i = l.lower.ordinal(), ei = l.end.ordinal();
        ikLocal[ui] = parentRot.conj().mul(rUpper);
        ikLocal[lo_i] = rUpper.conj().mul(rLower);
        ikW[ui] = ikW[lo_i] = k.w;
        if (!l.arm) {
            // The foot lies flat on the floor (pitched by roll), facing where the body does plus its own turn.
            Quat desired = Quat.axisAngle(0, 1, 0, yaw + Math.toRadians(k.yaw)).mul(Quat.axisAngle(1, 0, 0, Math.toRadians(k.roll)));
            ikLocal[ei] = rLower.conj().mul(desired);
            ikW[ei] = k.w;
        }
    }

    /** The turn that puts a limb's hinge axis on {@code xw} and its length axis (down at rest) along {@code dir}. */
    private static Quat frame(double[] xw, double[] dir) {
        double[] third = Quat.cross(xw, dir);
        // Columns: the hinge axis, the direction opposite the length axis, the direction opposite the third.
        double[] c1 = Quat.scale(dir, -1), c2 = Quat.scale(third, -1);
        return Quat.fromMatrix(new double[]{
                xw[0], c1[0], c2[0],
                xw[1], c1[1], c2[1],
                xw[2], c1[2], c2[2]});
    }
}
