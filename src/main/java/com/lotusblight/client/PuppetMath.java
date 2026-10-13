package com.lotusblight.client;

/**
 * The same arithmetic as the title screen's figure shader (puppet.fsh), on the processor: where each fingertip is (so the threads and the
 * clicks know), and how a point on the screen maps to the shader's camera and back. Keep it in step with the shader.
 */
final class PuppetMath {
    private PuppetMath() {}

    static final double HS = 1.85;
    static final double FINGER_SCALE = 1.35;
    static final double[] RO = {0.0, 1.45, 0.8};
    static final double FOCAL = 2.4;
    /** The depth at which threads end: roughly where the buttons hang. */
    static final double THREAD_Z = 3.4;

    private static final double[][] LEN = {{0.0456, 0.0408}, {0.0504, 0.0456}, {0.0552, 0.0480}, {0.0504, 0.0432}, {0.0408, 0.0360}};

    private static double[] sub(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] add(double[] a, double[] b) {
        return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    private static double[] mul(double[] a, double k) {
        return new double[]{a[0] * k, a[1] * k, a[2] * k};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double[] norm(double[] a) {
        double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        return mul(a, 1 / l);
    }

    /**
     * World position of the tip of finger {@code f} (0 thumb, 1 index, 2 middle, 3 ring) of a hand (0 = the one on the screen's left).
     * {@code curl} is the shader's uCurl for that finger.
     */
    static double[] tip(int hand, int f, double curl, double spread, double lift) {
        double s = hand == 0 ? -1 : 1;
        double[] elbow = {s * 1.16, 2.02 + lift * 0.5, 3.98};
        double[] wrist = {s * 1.04, 1.42 + lift, 3.56};
        double[] y = norm(sub(wrist, elbow));
        double[] back = {0, 0, -1};
        double[] x = norm(cross(back, y));
        if (x[0] < 0) x = mul(x, -1);
        double[] z = norm(cross(x, y));
        if (z[2] < 0) z = mul(z, -1);
        double ix = -s;
        double[] palm = add(wrist, mul(y, 0.095 * HS));
        double[] base;
        double fan;
        double sp = 0.4 + 0.9 * spread;
        switch (f) {
            case 1 -> {
                base = add(palm, mul(x, ix * 0.058 * HS));
                fan = 0.10 * sp;
            }
            case 2 -> {
                base = add(add(palm, mul(x, ix * 0.020 * HS)), mul(y, 0.004 * HS));
                fan = -0.02 * sp;
            }
            case 3 -> {
                base = sub(palm, mul(x, ix * 0.019 * HS));
                fan = -0.16 * sp;
            }
            default -> {
                base = sub(add(add(wrist, mul(y, 0.030 * HS)), mul(x, ix * 0.058 * HS)), mul(z, 0.005 * HS));
                fan = 0.50 + 0.30 * spread;
            }
        }
        double c = curl * (f == 0 ? 0.55 : 1.0) + 0.08;
        double[] a = base;
        double ang = 0;
        for (int k = 0; k < 2; k++) {
            ang += c * (k == 0 ? 0.80 : 1.15) * 1.5;
            double sk = fan * (k == 0 ? 1.0 : 0.55);
            double[] dir = norm(add(add(mul(x, Math.sin(sk) * ix), mul(y, Math.cos(sk) * Math.cos(ang))), mul(z, Math.cos(sk) * Math.sin(ang))));
            a = add(a, mul(dir, LEN[f][k] * FINGER_SCALE * HS));
        }
        return a;
    }

    /** GUI position (pixels) of a world point. */
    static float[] toScreen(double[] p, int w, int h) {
        double aspect = w / (double) h;
        double dx = p[0] - RO[0], dy = p[1] - RO[1], dz = p[2] - RO[2];
        double qx = dx / dz * FOCAL, qy = dy / dz * FOCAL;
        return new float[]{(float) ((0.5 + qx / (2 * aspect)) * w), (float) ((0.5 - qy / 2) * h)};
    }

    /** The world point at depth {@code z} that appears at a GUI position. */
    static double[] fromScreen(float sx, float sy, int w, int h, double z) {
        double aspect = w / (double) h;
        double u = sx / w, v = sy / h;
        double qx = (u - 0.5) * aspect * 2, qy = (0.5 - v) * 2;
        double dz = z - RO[2];
        return new double[]{RO[0] + qx / FOCAL * dz, RO[1] + qy / FOCAL * dz, z};
    }
}
