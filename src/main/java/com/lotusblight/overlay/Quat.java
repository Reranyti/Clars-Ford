package com.lotusblight.overlay;

/**
 * A rotation as a unit quaternion, plus the small amount of vector maths the rig needs. Vectors are plain
 * {x, y, z} arrays; everything is right-handed with y up and the model facing +z, in skin pixels.
 */
final class Quat {
    static final Quat IDENTITY = new Quat(1, 0, 0, 0);

    final double w, x, y, z;

    Quat(double w, double x, double y, double z) {
        this.w = w;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    static Quat axisAngle(double ax, double ay, double az, double angle) {
        double len = Math.sqrt(ax * ax + ay * ay + az * az);
        if (len < 1e-12) return IDENTITY;
        double s = Math.sin(angle / 2) / len;
        return new Quat(Math.cos(angle / 2), ax * s, ay * s, az * s);
    }

    /** Euler angles in degrees, applied x first, then y, then z (so a limb's pitch is x, its turn y, its roll z). */
    static Quat euler(double xDeg, double yDeg, double zDeg) {
        Quat qx = axisAngle(1, 0, 0, Math.toRadians(xDeg));
        Quat qy = axisAngle(0, 1, 0, Math.toRadians(yDeg));
        Quat qz = axisAngle(0, 0, 1, Math.toRadians(zDeg));
        return qz.mul(qy).mul(qx);
    }

    /** The Euler angles in degrees that {@link #euler} would be given to make this turn: {x, y, z}. */
    double[] toEuler() {
        double[] m = matrix();                                      // z after y after x: m[6] = -sin(y)
        double sy = Math.max(-1, Math.min(1, -m[6]));
        double y = Math.asin(sy), x, z;
        if (Math.abs(sy) > 0.99999) {                               // looking straight up or down: the x and z turns cannot be told apart
            x = Math.atan2(-m[5], m[4]);
            z = 0;
        } else {
            x = Math.atan2(m[7], m[8]);
            z = Math.atan2(m[3], m[0]);
        }
        return new double[]{Math.toDegrees(x), Math.toDegrees(y), Math.toDegrees(z)};
    }

    /** {@code this} after {@code b}: the result turns by b first, then by this. */
    Quat mul(Quat b) {
        return new Quat(
                w * b.w - x * b.x - y * b.y - z * b.z,
                w * b.x + x * b.w + y * b.z - z * b.y,
                w * b.y - x * b.z + y * b.w + z * b.x,
                w * b.z + x * b.y - y * b.x + z * b.w);
    }

    Quat conj() {
        return new Quat(w, -x, -y, -z);
    }

    Quat normalized() {
        double n = Math.sqrt(w * w + x * x + y * y + z * z);
        if (n < 1e-12) return IDENTITY;
        return new Quat(w / n, x / n, y / n, z / n);
    }

    double dot(Quat b) {
        return w * b.w + x * b.x + y * b.y + z * b.z;
    }

    /** Spherical interpolation along the shorter way round. */
    static Quat slerp(Quat a, Quat b, double t) {
        double d = a.dot(b);
        double bw = b.w, bx = b.x, by = b.y, bz = b.z;
        if (d < 0) { d = -d; bw = -bw; bx = -bx; by = -by; bz = -bz; }
        if (d > 0.9995) {
            return new Quat(a.w + (bw - a.w) * t, a.x + (bx - a.x) * t, a.y + (by - a.y) * t, a.z + (bz - a.z) * t).normalized();
        }
        double theta = Math.acos(d);
        double sa = Math.sin((1 - t) * theta) / Math.sin(theta), sb = Math.sin(t * theta) / Math.sin(theta);
        return new Quat(a.w * sa + bw * sb, a.x * sa + bx * sb, a.y * sa + by * sb, a.z * sa + bz * sb);
    }

    double[] rotate(double[] v) {
        double[] m = matrix();
        return new double[]{
                m[0] * v[0] + m[1] * v[1] + m[2] * v[2],
                m[3] * v[0] + m[4] * v[1] + m[5] * v[2],
                m[6] * v[0] + m[7] * v[1] + m[8] * v[2]};
    }

    /** The 3x3 rotation matrix, row by row. */
    double[] matrix() {
        double xx = x * x, yy = y * y, zz = z * z, xy = x * y, xz = x * z, yz = y * z, wx = w * x, wy = w * y, wz = w * z;
        return new double[]{
                1 - 2 * (yy + zz), 2 * (xy - wz), 2 * (xz + wy),
                2 * (xy + wz), 1 - 2 * (xx + zz), 2 * (yz - wx),
                2 * (xz - wy), 2 * (yz + wx), 1 - 2 * (xx + yy)};
    }

    static Quat fromMatrix(double[] m) {
        double tr = m[0] + m[4] + m[8];
        double w, x, y, z;
        if (tr > 0) {
            double s = Math.sqrt(tr + 1) * 2;
            w = 0.25 * s;
            x = (m[7] - m[5]) / s;
            y = (m[2] - m[6]) / s;
            z = (m[3] - m[1]) / s;
        } else if (m[0] > m[4] && m[0] > m[8]) {
            double s = Math.sqrt(1 + m[0] - m[4] - m[8]) * 2;
            w = (m[7] - m[5]) / s;
            x = 0.25 * s;
            y = (m[1] + m[3]) / s;
            z = (m[2] + m[6]) / s;
        } else if (m[4] > m[8]) {
            double s = Math.sqrt(1 + m[4] - m[0] - m[8]) * 2;
            w = (m[2] - m[6]) / s;
            x = (m[1] + m[3]) / s;
            y = 0.25 * s;
            z = (m[5] + m[7]) / s;
        } else {
            double s = Math.sqrt(1 + m[8] - m[0] - m[4]) * 2;
            w = (m[3] - m[1]) / s;
            x = (m[2] + m[6]) / s;
            y = (m[5] + m[7]) / s;
            z = 0.25 * s;
        }
        return new Quat(w, x, y, z).normalized();
    }

    /** The smallest turn that takes direction a onto direction b. */
    static Quat arc(double[] a, double[] b) {
        double[] u = unit(a), v = unit(b);
        double d = dot(u, v);
        if (d > 0.999999) return IDENTITY;
        if (d < -0.999999) {                                   // opposite: any axis across will do
            double[] axis = Math.abs(u[0]) < 0.9 ? cross(u, new double[]{1, 0, 0}) : cross(u, new double[]{0, 1, 0});
            return axisAngle(axis[0], axis[1], axis[2], Math.PI);
        }
        double[] c = cross(u, v);
        return new Quat(1 + d, c[0], c[1], c[2]).normalized();
    }

    // ---- vectors

    static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static double[] add(double[] a, double[] b) {
        return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    static double[] sub(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    static double[] scale(double[] a, double s) {
        return new double[]{a[0] * s, a[1] * s, a[2] * s};
    }

    static double length(double[] a) {
        return Math.sqrt(dot(a, a));
    }

    static double[] unit(double[] a) {
        double n = length(a);
        return n < 1e-12 ? new double[]{0, 0, 0} : new double[]{a[0] / n, a[1] / n, a[2] / n};
    }

    /** A rigid transform: a 3x3 rotation (row-major) and a translation. */
    static final class Xf {
        final double[] r;
        final double[] t;

        Xf(double[] r, double[] t) {
            this.r = r;
            this.t = t;
        }

        static Xf of(Quat q, double[] t) {
            return new Xf(q.matrix(), t.clone());
        }

        static Xf translation(double[] t) {
            return new Xf(IDENTITY.matrix(), t.clone());
        }

        double[] apply(double[] p) {
            return new double[]{
                    r[0] * p[0] + r[1] * p[1] + r[2] * p[2] + t[0],
                    r[3] * p[0] + r[4] * p[1] + r[5] * p[2] + t[1],
                    r[6] * p[0] + r[7] * p[1] + r[8] * p[2] + t[2]};
        }

        /** Only the turn, no move: for directions. */
        double[] applyDir(double[] p) {
            return new double[]{
                    r[0] * p[0] + r[1] * p[1] + r[2] * p[2],
                    r[3] * p[0] + r[4] * p[1] + r[5] * p[2],
                    r[6] * p[0] + r[7] * p[1] + r[8] * p[2]};
        }

        /** {@code this} after {@code b}. */
        Xf mul(Xf b) {
            double[] m = new double[9];
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    m[i * 3 + j] = r[i * 3] * b.r[j] + r[i * 3 + 1] * b.r[3 + j] + r[i * 3 + 2] * b.r[6 + j];
                }
            }
            return new Xf(m, apply(b.t));
        }

        Quat rotation() {
            return Quat.fromMatrix(r);
        }
    }
}
