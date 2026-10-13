package com.lotusblight.overlay;

/**
 * A character drawn on the 15-joint rig, driven by the scenes' simple poses. The scenes set a
 * {@link SoftRenderer.Pose} (a turn for the body, head, arms and legs) exactly as they always have; the
 * figure turns that into a {@link Pose15} and adds what makes it move like a body:
 * <ul>
 *   <li>every turn is followed through a spring, so a pose that changes at once is arrived at smoothly,
 *       with a little give at the end for the arms and the head;</li>
 *   <li>forearms and shins bend with how fast the limb above is moving (loose when it swings, straight when it
 *       is held), the hands follow the forearms, and the feet stay near flat under a swinging leg;</li>
 *   <li>the elbows, knees and wrists are real joints, so none of it shears the limb.</li>
 * </ul>
 * A figure keeps its own state between frames, so one is made per character and kept.
 */
final class Figure {
    private static final int[] LEGACY = {
            Rig15.Joint.HEAD.ordinal(), Rig15.Joint.UPPER_TORSO.ordinal(),
            Rig15.Joint.R_UPPER_ARM.ordinal(), Rig15.Joint.L_UPPER_ARM.ordinal(),
            Rig15.Joint.R_UPPER_LEG.ordinal(), Rig15.Joint.L_UPPER_LEG.ordinal()};
    /** Spring stiffness (rad/s) and damping ratio for each legacy part, in the same order. */
    private static final double[] STIFF = {24, 20, 42, 42, 38, 38};
    private static final double[] DAMP = {0.75, 1.0, 0.8, 0.8, 0.9, 0.9};

    private final Rig15 rig;
    private final int[] skin;
    private final Quat[] cur = new Quat[LEGACY.length];
    private final double[][] vel = new double[LEGACY.length][3];
    private final Pose15 pose = new Pose15();
    /** How closed each hand is wanted (0 open .. 1 fist), right then left; the hands follow it smoothly. */
    private final double[] fistWant = new double[2], fistCur = new double[2], fistVel = new double[2];
    private boolean primed;
    private long lastNano;

    Figure(int[] skin, boolean honcho) {
        this.rig = honcho ? Rig15.honcho() : Rig15.standard();
        this.skin = skin;
    }

    /** Draws the pose with its feet at (originX, originY); {@code scale} is image pixels per skin pixel. */
    void draw(SoftRenderer r, SoftRenderer.Pose legacy, double scale, double originX, double originY) {
        long now = PauseClock.nano();
        double dt = primed ? (now - lastNano) / 1e9 : 0;
        lastNano = now;
        draw(r, legacy, scale, originX, originY, dt);
    }

    /** As {@link #draw} with the time since the last frame given (0 or a long gap snaps to the pose). */
    void draw(SoftRenderer r, SoftRenderer.Pose legacy, double scale, double originX, double originY, double dt) {
        Rig15.Skel sk = rig.solve(follow(legacy, dt));
        r.drawRig(rig, sk, skin, 0, 0, scale, originX, originY);
    }

    /** Asks for the hands to close (1) or open (0); the scene calls it each frame it wants a fist. */
    void fists(double right, double left) {
        fistWant[0] = right;
        fistWant[1] = left;
    }

    /** Forgets the smoothing: the next frame is taken as it is. */
    void snap() {
        primed = false;
    }

    /** The smoothed pose for a frame of the scene's own pose, {@code dt} seconds after the last. */
    Pose15 follow(SoftRenderer.Pose legacy, double dt) {
        Quat[] target = new Quat[LEGACY.length];
        for (int i = 0; i < LEGACY.length; i++) {
            target[i] = Quat.euler(Math.toDegrees(legacy.partPitch[i]), Math.toDegrees(legacy.partYaw[i]), Math.toDegrees(legacy.partRoll[i]));
        }
        boolean snap = !primed || dt > 0.3;
        if (snap) {
            for (int i = 0; i < LEGACY.length; i++) {
                cur[i] = target[i];
                java.util.Arrays.fill(vel[i], 0);
            }
            primed = true;
        } else if (dt >= 0.002) {
            for (int i = 0; i < LEGACY.length; i++) step(i, target[i], dt);
        }

        pose.reset();
        // The whole body's turn, about the feet, as the scenes mean it.
        Quat body = Quat.axisAngle(1, 0, 0, legacy.pitch).mul(Quat.axisAngle(0, 1, 0, legacy.yaw));
        pose.rootRot = body;
        double[] pv = rig.pivot[Rig15.Joint.LOWER_TORSO.ordinal()];
        pose.rootPos[0] = body.rotate(pv)[0] - pv[0];
        pose.rootPos[1] = body.rotate(pv)[1] - pv[1];
        pose.rootPos[2] = body.rotate(pv)[2] - pv[2];
        for (int i = 0; i < LEGACY.length; i++) {
            pose.rot[LEGACY[i]] = cur[i];
            System.arraycopy(legacy.partOffset[i], 0, pose.offset[LEGACY[i]], 0, 3);
        }
        // The hands: loose by default, tighter the faster the arm moves, closed when the scene wants a fist.
        for (int side = 0; side < 2; side++) {
            double want = Math.max(fistWant[side], Math.min(0.35, speed(2 + side) * 0.03));
            if (snap) {
                fistCur[side] = want;
                fistVel[side] = 0;
            } else if (dt > 0) {
                double w0 = 34, d = want - fistCur[side];
                fistVel[side] += (w0 * w0 * d - 2 * 0.85 * w0 * fistVel[side]) * Math.min(dt, 0.05);
                fistCur[side] = Math.max(0, Math.min(1, fistCur[side] + fistVel[side] * Math.min(dt, 0.05)));
            }
            pose.hand(side == 0, fistCur[side], 0.3);
        }
        // Bends that follow the swing.
        arm(Rig15.Joint.R_LOWER_ARM, Rig15.Joint.R_HAND, speed(2));
        arm(Rig15.Joint.L_LOWER_ARM, Rig15.Joint.L_HAND, speed(3));
        leg(Rig15.Joint.R_LOWER_LEG, Rig15.Joint.R_FOOT, 4);
        leg(Rig15.Joint.L_LOWER_LEG, Rig15.Joint.L_FOOT, 5);
        return pose;
    }

    private double speed(int i) {
        return Math.sqrt(vel[i][0] * vel[i][0] + vel[i][1] * vel[i][1] + vel[i][2] * vel[i][2]);
    }

    private void arm(Rig15.Joint lower, Rig15.Joint hand, double speed) {
        double flex = -Math.min(75, 2.5 + 5.0 * speed);
        pose.rot[lower.ordinal()] = Quat.euler(flex, 0, 0);
        pose.rot[hand.ordinal()] = Quat.euler(flex * 0.4, 0, 0);
    }

    private void leg(Rig15.Joint lower, Rig15.Joint foot, int part) {
        double knee = Math.min(50, 1.5 + 4.0 * speed(part));
        double[] down = cur[part].rotate(new double[]{0, -1, 0});
        double thigh = Math.toDegrees(Math.atan2(-down[2], -down[1]));        // forward is negative, as in the authoring of the scenes
        pose.rot[lower.ordinal()] = Quat.euler(knee, 0, 0);
        pose.rot[foot.ordinal()] = Quat.euler(-(thigh + knee) * 0.6, 0, 0);
    }

    /** One spring step for a part, in small slices so a stiff spring stays stable. */
    private void step(int i, Quat target, double dt) {
        double w0 = STIFF[i], zeta = DAMP[i];
        int n = Math.max(1, (int) Math.ceil(dt / 0.004));
        double h = dt / n;
        for (int k = 0; k < n; k++) {
            Quat e = target.mul(cur[i].conj());
            double sgn = e.w < 0 ? -1 : 1;
            double ex = e.x * sgn, ey = e.y * sgn, ez = e.z * sgn, ew = Math.min(1, e.w * sgn);
            double s = Math.sqrt(Math.max(0, 1 - ew * ew));
            double ang = 2 * Math.acos(ew);
            double tx = 0, ty = 0, tz = 0;
            if (s > 1e-9) {
                tx = ex / s * ang;
                ty = ey / s * ang;
                tz = ez / s * ang;
            }
            double[] v = vel[i];
            v[0] += (w0 * w0 * tx - 2 * zeta * w0 * v[0]) * h;
            v[1] += (w0 * w0 * ty - 2 * zeta * w0 * v[1]) * h;
            v[2] += (w0 * w0 * tz - 2 * zeta * w0 * v[2]) * h;
            double sp = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
            if (sp > 1e-9) cur[i] = Quat.axisAngle(v[0], v[1], v[2], sp * h).mul(cur[i]).normalized();
        }
    }
}
