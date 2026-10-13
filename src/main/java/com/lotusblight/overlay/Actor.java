package com.lotusblight.overlay;

/**
 * A character posed directly on the rig, in its own space (feet at the origin, facing +z), by the scene that owns
 * it: joint turns, IK targets for the fists and feet, and the shape of each hand. Nothing here is translated from
 * the old six-part poses. On top of what the scene sets it keeps a light follow-through on the head and chest and
 * moves every finger on its own: each one is sprung towards the curl the hand's shape asks for, never holds quite
 * still, and a hand that changes shape closes from the knuckles, finger by finger.
 *
 * <p>The whole body's turn about the vertical ({@link #viewYaw}) and its lean ({@link #viewPitch}) are applied
 * when drawing, about the feet, so the IK targets can be given in the character's own space however it is turned.
 */
final class Actor {
    /** What a hand is doing; the curl of the five fingers (thumb, index, middle, ring, little) for each. */
    enum Hand {
        LOOSE(0.10, 0.20, 0.28, 0.36, 0.44),
        OPEN(0, 0, 0, 0, 0),
        FIST(0.8, 1, 1, 1, 1),
        PINCH(0.5, 0.58, 0.85, 0.9, 0.92),
        POINT(0.7, 0, 1, 1, 1),
        CLAW(0.25, 0.5, 0.58, 0.6, 0.62),
        GRIP(0.55, 0.78, 0.82, 0.85, 0.88);

        final double[] curl;

        Hand(double... curl) {
            this.curl = curl;
        }
    }

    final Rig15 rig;
    final int[] skin;
    final Pose15 pose = new Pose15();
    double viewYaw, viewPitch;
    /** How closed each hand is, right and left, on top of its shape: 0 leaves the shape alone, 1 is a fist. */
    double fistR, fistL;
    /** How much the open fingers fan out, 0..1. */
    double spread = 0.3;
    /** The shape of the right and left hands. */
    Hand shapeR = Hand.LOOSE, shapeL = Hand.LOOSE;
    /** The skeleton of the frame last drawn. */
    Rig15.Skel lastSkel;

    private final Quat[] follow = new Quat[2];
    private final double[][] followVel = new double[2][3];
    private final double[][] curl = new double[2][5], curlVel = new double[2][5];
    private boolean primed;
    private long lastNano;
    private final long born = PauseClock.nano();

    Actor(int[] skin, boolean honcho) {
        this.rig = honcho ? Rig15.honcho() : Rig15.standard();
        this.skin = skin;
    }

    /** Starts a frame: everything at rest, the hands loose. */
    Pose15 begin() {
        pose.reset();
        viewYaw = 0;
        viewPitch = 0;
        fistR = 0;
        fistL = 0;
        spread = 0.3;
        shapeR = Hand.LOOSE;
        shapeL = Hand.LOOSE;
        return pose;
    }

    /** Points a limb's end at a point in the character's own space. {@code weight} blends from the joint turns to the solve. */
    Actor reach(Rig15.Limb limb, double x, double y, double z, double weight) {
        pose.reach(limb, x, y, z, weight);
        return this;
    }

    /** Sets the direction the elbow or knee of a limb points, in the character's own space. */
    Actor pole(Rig15.Limb limb, double x, double y, double z) {
        double[] p = pose.ik[limb.ordinal()].pole;
        p[0] = x;
        p[1] = y;
        p[2] = z;
        return this;
    }

    /** Turns a joint (Euler degrees, x pitch first). */
    Actor turn(Rig15.Joint j, double x, double y, double z) {
        pose.turn(j, x, y, z);
        return this;
    }

    /** Turns a joint part of the way from whatever it was to these angles. */
    Actor blendTurn(Rig15.Joint j, double x, double y, double z, double weight) {
        if (weight <= 0) return this;
        Quat want = Quat.euler(x, y, z);
        if (j == Rig15.Joint.LOWER_TORSO) pose.rootRot = Quat.slerp(pose.rootRot, want, weight);
        else pose.rot[j.ordinal()] = Quat.slerp(pose.rot[j.ordinal()], want, weight);
        return this;
    }

    Actor hands(Hand right, Hand left) {
        shapeR = right;
        shapeL = left;
        return this;
    }

    /** Ends the frame: the hands are shaped, the trailing parts follow, and the figure is drawn with its feet at the origin given. */
    void draw(SoftRenderer r, double scale, double originX, double originY) {
        long now = PauseClock.nano();
        double dt = primed ? Math.min(0.05, (now - lastNano) / 1e9) : 0;
        lastNano = now;
        double t = (now - born) / 1e9;
        shapeFingers(dt, t);
        followThrough(dt);
        primed = true;
        Rig15.Skel sk = rig.solve(pose);
        lastSkel = sk;
        r.drawRig(rig, sk, skin, viewYaw, viewPitch, scale, originX, originY);
    }

    /** The finger curls follow their wanted values through springs, each finger a little behind the one before. */
    private void shapeFingers(double dt, double t) {
        for (int side = 0; side < 2; side++) {
            Hand shape = side == 0 ? shapeR : shapeL;
            double fist = Math.max(0, Math.min(1, side == 0 ? fistR : fistL));
            for (int f = 0; f < 5; f++) {
                double want = shape.curl[f] * (1 - fist) + Hand.FIST.curl[f] * fist;
                if (!primed) {
                    curl[side][f] = want;
                    curlVel[side][f] = 0;
                    continue;
                }
                double w0 = 34 - 3 * f, zeta = 0.78;                     // the outer fingers are a little slower: they close last
                int n = Math.max(1, (int) Math.ceil(dt / 0.004));
                double h = dt / n;
                for (int k = 0; k < n && dt > 0; k++) {
                    curlVel[side][f] += (w0 * w0 * (want - curl[side][f]) - 2 * zeta * w0 * curlVel[side][f]) * h;
                    curl[side][f] += curlVel[side][f] * h;
                }
                curl[side][f] = Math.max(-0.1, Math.min(1.05, curl[side][f]));
            }
            pose.fingers(side == 0, curl[side], spread, t);
        }
    }

    /** The head and chest are carried by springs: they lag a sudden turn and settle with a little give. */
    private void followThrough(double dt) {
        Rig15.Joint[] joints = {Rig15.Joint.HEAD, Rig15.Joint.UPPER_TORSO};
        double[] stiff = {30, 26}, damp = {0.72, 0.9};
        for (int i = 0; i < 2; i++) {
            Quat target = pose.rot[joints[i].ordinal()];
            if (!primed || follow[i] == null) {
                follow[i] = target;
                continue;
            }
            int n = Math.max(1, (int) Math.ceil(dt / 0.004));
            double h = dt / n;
            for (int k = 0; k < n && dt > 0; k++) {
                Quat e = target.mul(follow[i].conj());
                double sgn = e.w < 0 ? -1 : 1;
                double ew = Math.min(1, e.w * sgn), s = Math.sqrt(Math.max(0, 1 - ew * ew)), ang = 2 * Math.acos(ew);
                double tx = 0, ty = 0, tz = 0;
                if (s > 1e-9) {
                    tx = e.x * sgn / s * ang;
                    ty = e.y * sgn / s * ang;
                    tz = e.z * sgn / s * ang;
                }
                double[] v = followVel[i];
                v[0] += (stiff[i] * stiff[i] * tx - 2 * damp[i] * stiff[i] * v[0]) * h;
                v[1] += (stiff[i] * stiff[i] * ty - 2 * damp[i] * stiff[i] * v[1]) * h;
                v[2] += (stiff[i] * stiff[i] * tz - 2 * damp[i] * stiff[i] * v[2]) * h;
                double sp = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
                if (sp > 1e-9) follow[i] = Quat.axisAngle(v[0], v[1], v[2], sp * h).mul(follow[i]).normalized();
            }
            pose.rot[joints[i].ordinal()] = follow[i];
        }
    }

    /** Where a point of the body is on the picture, in units to the right of and above the feet. */
    double[] onScreen(double[] stagePoint) {
        double[] p = SoftRenderer.project(stagePoint, viewYaw, viewPitch, 1, 0, 0);
        return new double[]{p[0], -p[1]};
    }
}
