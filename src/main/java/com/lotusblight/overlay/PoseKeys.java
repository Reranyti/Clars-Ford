package com.lotusblight.overlay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The poses the author sets for the close-up Glitcher in the editor ("K" records of the scene): at a moment the body is held the way the keys say,
 * and between two moments it goes smoothly from one pose to the other. Every key holds some of: the turn of joints (Euler degrees, as in
 * {@link Pose15#turn}), the offset of the hips, the aim of a limb (IK). What a key does not mention is left to the code's own animation, and the key's
 * weight says how much the keyed pose counts against it (0 gives the body back to the code, 1 holds it fully).
 *
 * <p>The values, by name: {@code <joint>} = x,y,z (a joint's turn); {@code root.pos} = x,y,z; {@code ik.<limb>} = w, x,y,z, pole x,y,z, roll, yaw.
 * Before the first key and after the last the code is in charge.
 */
final class PoseKeys {
    record Key(double t, double weight, Map<String, double[]> values) {}

    /** The editor's capture of the code's own pose wants nothing laid over it. */
    static boolean skip;

    /** {@code name=a,b,c;name=...} to numbers. */
    static Map<String, double[]> parse(String data) {
        Map<String, double[]> out = new LinkedHashMap<>();
        for (String item : data.split(";")) {
            int eq = item.indexOf('=');
            if (eq <= 0) continue;
            String[] n = item.substring(eq + 1).split(",");
            double[] v = new double[n.length];
            try {
                for (int i = 0; i < n.length; i++) v[i] = Double.parseDouble(n[i].trim());
            } catch (NumberFormatException e) {
                continue;
            }
            out.put(item.substring(0, eq).trim(), v);
        }
        return out;
    }

    private static double smooth(double u) {
        u = Math.max(0, Math.min(1, u));
        return u * u * (3 - 2 * u);
    }

    private static double[] lerp(double[] a, double[] b, double u) {
        double[] r = new double[Math.max(a.length, b.length)];
        for (int i = 0; i < r.length; i++) {
            double x = i < a.length ? a[i] : 0, y = i < b.length ? b[i] : 0;
            r[i] = x + (y - x) * u;
        }
        return r;
    }

    /** Lays the keyed pose over the actor's pose at a time (seconds). */
    static void apply(Actor actor, double t, List<Key> keys) {
        if (skip || keys == null || keys.size() < 1) return;
        int n = keys.size();
        if (t < keys.get(0).t || t >= keys.get(n - 1).t) return;
        int i = 0;
        while (i + 1 < n && keys.get(i + 1).t <= t) i++;
        if (i + 1 >= n) return;
        Key a = keys.get(i), b = keys.get(i + 1);
        double u = smooth((t - a.t) / Math.max(1e-6, b.t - a.t));
        double w = a.weight + (b.weight - a.weight) * u;
        if (w <= 0.0005) return;
        Pose15 p = actor.pose;
        List<String> names = new ArrayList<>(a.values.keySet());
        for (String s : b.values.keySet()) if (!names.contains(s)) names.add(s);
        for (String name : names) {
            double[] va = a.values.get(name), vb = b.values.get(name);
            if (va == null) va = vb;
            if (vb == null) vb = va;
            if (name.equals("root.pos")) {
                double[] v = lerp(va, vb, u);
                for (int k = 0; k < 3; k++) p.rootPos[k] += (v[k] - p.rootPos[k]) * w;
            } else if (name.startsWith("ik.")) {
                String limb = name.substring(3).toUpperCase(Locale.ROOT);
                Rig15.Limb l;
                try {
                    l = Rig15.Limb.valueOf(limb);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                double[] v = lerp(va, vb, u);
                if (v.length < 9) continue;
                Pose15.Ik k = p.ik[l.ordinal()];
                k.w += (v[0] - k.w) * w;
                for (int c = 0; c < 3; c++) {
                    k.pos[c] += (v[1 + c] - k.pos[c]) * w;
                    k.pole[c] += (v[4 + c] - k.pole[c]) * w;
                }
                k.roll += (v[7] - k.roll) * w;
                k.yaw += (v[8] - k.yaw) * w;
            } else {
                Rig15.Joint j = Rig15.Joint.byKey(name);
                if (j == null || va.length < 3 || vb.length < 3) continue;
                Quat q = Quat.slerp(Quat.euler(va[0], va[1], va[2]), Quat.euler(vb[0], vb[1], vb[2]), u);
                if (j == Rig15.Joint.LOWER_TORSO) p.rootRot = Quat.slerp(p.rootRot, q, w);
                else if (j.ordinal() >= Rig15.BODY_JOINTS) {                  // a finger: the actor lays it over the hand's own shape when it draws
                    actor.keyedRot[j.ordinal()] = q;
                    actor.keyedW[j.ordinal()] = w;
                } else p.rot[j.ordinal()] = Quat.slerp(p.rot[j.ordinal()], q, w);
            }
        }
    }

    /** The whole pose as key data (what the code is doing now), to start a key from. */
    static String dump(Pose15 p) {
        StringBuilder sb = new StringBuilder();
        for (Rig15.Joint j : Rig15.Joint.values()) {
            Quat q = j == Rig15.Joint.LOWER_TORSO ? p.rootRot : p.rot[j.ordinal()];
            double[] e = q.toEuler();
            sb.append(String.format(Locale.ROOT, "%s=%.2f,%.2f,%.2f;", j.key(), e[0], e[1], e[2]));
        }
        sb.append(String.format(Locale.ROOT, "root.pos=%.3f,%.3f,%.3f;", p.rootPos[0], p.rootPos[1], p.rootPos[2]));
        for (Rig15.Limb l : Rig15.Limb.values()) {
            Pose15.Ik k = p.ik[l.ordinal()];
            sb.append(String.format(Locale.ROOT, "ik.%s=%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.2f,%.2f;", l.name().toLowerCase(Locale.ROOT), k.w, k.pos[0], k.pos[1], k.pos[2],
                    k.pole[0], k.pole[1], k.pole[2], k.roll, k.yaw));
        }
        return sb.toString();
    }
}
