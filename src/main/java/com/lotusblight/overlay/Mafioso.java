package com.lotusblight.overlay;

/**
 * A fedora and a long open coat for a figure on the rig. They are more faces on the rig's joints (the hat on the head, the coat on the chest and the
 * waist), so they move with the body and are lit and depth-sorted with it. The colours come from a few rows added under the skin: every face looks at
 * one pixel of them.
 */
final class Mafioso {
    private static final int ROWS = 64 + 8;
    /** Pixels of the extra rows: where each colour sits on the skin (u, v). */
    private static final int FELT = 0, BAND = 1, COAT = 2, LINING = 3, COLLAR = 4, SHINE = 5, SUIT = 6, SHIRT = 7, TIE = 8, LAPEL = 9, SHOE = 10, PIPE = 11;
    private static final int[] COLOURS = {
            0xFF16141C,          // the felt of the hat
            0xFF6A1020,          // the hat band
            0xFF3B5666,          // the coat
            0xFF7A1428,          // its lining
            0xFF2A2833,          // the collar
            0xFF3A3846,          // the lighter edge of the brim
            0xFF1A1A22,          // the suit
            0xFFE8E4DC,          // the shirt
            0xFF8A1428,          // the tie
            0xFF2E2E3A,          // the lapels
            0xFF0C0C10,          // the shoes
            0xFFC8C2D6};         // the piping along the edges of the cloak

    private Mafioso() {
    }

    /** Puts the hat and the coat on the rig's model and returns the skin with the colours under it (the original is not changed). */
    static int[] dress(Rig15 rig, int[] skin) {
        int[] out = new int[64 * ROWS];
        System.arraycopy(skin, 0, out, 0, Math.min(skin.length, 64 * 64));
        for (int i = 0; i < COLOURS.length; i++) out[64 * 64 + i] = COLOURS[i];
        suit(rig);
        hat(rig);
        coat(rig);
        return out;
    }

    /** The whole pieces that the suit's slices are cut from: {centre, half-sizes}. */
    private static final double[][] JACKET = {{0, 16.85, 0}, {4.5, 7.35, 2.5}}, SLEEVE_R = {{-5.5, 18, 0}, {1.95, 6.45, 2.4}}, SLEEVE_L = {{5.5, 18, 0}, {1.95, 6.45, 2.4}},
            TROUSER_R = {{-2, 6, 0}, {2.3, 6.3, 2.3}}, TROUSER_L = {{2, 6, 0}, {2.3, 6.3, 2.3}};

    /** A suit: the jacket on the chest and waist, its sleeves, a white shirt and a red tie in the opening, trousers and black shoes. */
    private static void suit(Rig15 rig) {
        Rig15.Joint up = Rig15.Joint.UPPER_TORSO, low = Rig15.Joint.LOWER_TORSO;
        box(rig, up, -4.45, 17, -2.45, 4.45, 24.2, 2.45, SUIT, SUIT, JACKET);                // the jacket's chest
        box(rig, low, -4.5, 9.5, -2.5, 4.5, 17, 2.5, SUIT, SUIT, JACKET);                    // the skirt of the jacket, to the hips
        box(rig, up, -1.3, 17.5, 2.42, 1.3, 24.2, 2.5, SHIRT, SHIRT);                // the shirt in the opening
        box(rig, up, -0.4, 16.0, 2.5, 0.4, 23.4, 2.58, TIE, TIE);                    // the tie
        box(rig, low, -0.5, 13.0, 2.5, 0.5, 16.0, 2.58, TIE, TIE);
        box(rig, up, -2.3, 17.5, 2.45, -1.3, 24.2, 2.55, LAPEL, LAPEL);              // the lapels
        box(rig, up, 1.3, 17.5, 2.45, 2.3, 24.2, 2.55, LAPEL, LAPEL);
        double[][] cuts = {{18, 24.4}, {15, 18}};
        Rig15.Joint[] rA = {Rig15.Joint.R_UPPER_ARM, Rig15.Joint.R_LOWER_ARM}, lA = {Rig15.Joint.L_UPPER_ARM, Rig15.Joint.L_LOWER_ARM};
        for (int i = 0; i < 2; i++) {                                                // the sleeves
            box(rig, rA[i], -7.45, cuts[i][0], -2.4, -3.55, cuts[i][1], 2.4, SUIT, SUIT, SLEEVE_R);
            box(rig, lA[i], 3.55, cuts[i][0], -2.4, 7.45, cuts[i][1], 2.4, SUIT, SUIT, SLEEVE_L);
        }
        box(rig, Rig15.Joint.R_LOWER_ARM, -7.2, 14.9, -2.2, -3.8, 15.6, 2.2, SHIRT, SHIRT);   // the cuffs
        box(rig, Rig15.Joint.L_LOWER_ARM, 3.8, 14.9, -2.2, 7.2, 15.6, 2.2, SHIRT, SHIRT);
        double[][] legCuts = {{7, 12}, {3, 7}};
        Rig15.Joint[] rL = {Rig15.Joint.R_UPPER_LEG, Rig15.Joint.R_LOWER_LEG}, lL = {Rig15.Joint.L_UPPER_LEG, Rig15.Joint.L_LOWER_LEG};
        for (int i = 0; i < 2; i++) {                                                // the trousers
            box(rig, rL[i], -4.3, legCuts[i][0], -2.3, 0.3, legCuts[i][1], 2.3, SUIT, SUIT, TROUSER_R);
            box(rig, lL[i], -0.3, legCuts[i][0], -2.3, 4.3, legCuts[i][1], 2.3, SUIT, SUIT, TROUSER_L);
        }
        box(rig, Rig15.Joint.R_FOOT, -4.35, 0, -2.4, 0.35, 3.0, 3.4, SHOE, SHOE);     // the shoes, longer at the toe
        box(rig, Rig15.Joint.L_FOOT, -0.35, 0, -2.4, 4.35, 3.0, 3.4, SHOE, SHOE);
    }

    private static void hat(Rig15 rig) {
        double top = 32;                                                             // the top of the head
        // the brim: wide, flat, a little wider at the front and the back
        box(rig, Rig15.Joint.HEAD, -6.6, top, -6.2, 6.6, top + 0.7, 6.8, SHINE, FELT);
        // the crown, narrowing a little towards the top; the band round its foot
        frustum(rig, Rig15.Joint.HEAD, top + 0.7, top + 2.4, 4.75, 4.75, 4.7, 4.7, BAND);
        frustum(rig, Rig15.Joint.HEAD, top + 2.4, top + 5.2, 4.7, 4.7, 4.0, 3.8, FELT);
        // the dent along the top
        box(rig, Rig15.Joint.HEAD, -0.6, top + 5.2, -3.5, 0.6, top + 5.6, 3.5, FELT, FELT);
    }

    /** The profile of the cloak from the collar to the hem: height, half-width (x) and the front-to-back ratio. */
    private static final double[][] PROFILE = {
            {26.2, 4.9, 0.74}, {25.2, 4.6, 0.74}, {24.4, 5.2, 0.72}, {23.6, 8.2, 0.60}, {22.0, 8.4, 0.56},
            {18.0, 8.7, 0.56}, {12.0, 9.0, 0.58}, {8.0, 9.6, 0.60}, {4.5, 10.4, 0.62}};

    /**
     * A round cloak that hangs from the shoulders: a smooth shell (many small quads, so that the light runs over it as over cloth) that
     * wraps round the back and is open at the front, with folds that deepen towards the hem, and a lining shell inside it.
     */
    private static void coat(Rig15 rig) {
        Rig15.Joint j = Rig15.Joint.UPPER_TORSO;
        int cols = DETAIL_COLS, per = DETAIL_PER;
        int rows = (PROFILE.length - 1) * per;
        double span = Math.toRadians(108);                                           // from the middle of the back to each side: it hangs behind the arms, which stay free
        double[][][] grid = new double[rows + 1][cols + 1][];
        double[][][] inner = new double[rows + 1][cols + 1][];
        for (int r = 0; r <= rows; r++) {
            double f = (double) r / per;
            int i = Math.min(PROFILE.length - 2, (int) f);
            double t = f - i;
            t = t * t * (3 - 2 * t);
            double y = PROFILE[i][0] + (PROFILE[i + 1][0] - PROFILE[i][0]) * ((f - i));
            double rx = PROFILE[i][1] + (PROFILE[i + 1][1] - PROFILE[i][1]) * t;
            double ratio = PROFILE[i][2] + (PROFILE[i + 1][2] - PROFILE[i][2]) * t;
            double down = Math.max(0, Math.min(1, (24 - y) / 19));                   // 0 at the shoulders, 1 at the hem
            for (int k = 0; k <= cols; k++) {
                double a = -span + 2 * span * k / cols;
                double fold = 1 + (0.04 + 0.16 * down) * (0.5 + 0.5 * Math.sin(a * 8.0 + 0.6)) + 0.07 * down * (0.5 + 0.5 * Math.sin(a * 14.0 + y * 0.35));   // the folds only go outwards, so that the arms never come through
                double edge = 1 + 0.05 * down * Math.pow(Math.abs(a) / span, 3);       // the hem swings out at the open edges
                double hem = -0.5 * down * down * (0.5 + 0.5 * Math.cos(a * 8.0 + 0.6)) - 0.8 * down * Math.pow(Math.abs(a) / span, 2);
                double rr = rx * fold * edge;
                grid[r][k] = new double[]{rr * Math.sin(a), y + hem, -rr * ratio * Math.cos(a) + 0.4};
                double ri = Math.max(1, rr - 0.22);                                    // the lining, a little inside
                inner[r][k] = new double[]{ri * Math.sin(a), y + hem, -ri * ratio * Math.cos(a) + 0.4};
            }
        }
        for (int r = 0; r < rows; r++) {
            for (int k = 0; k < cols; k++) {
                // seen from outside, the angle runs from right to left, so the quad goes (k+1, top), (k, top), (k, bottom), (k+1, bottom)
                boolean trim = k == 0 || k == cols - 1 || r >= rows - 1;                  // a pale piping along the open edges and the hem, so that it does not melt into the dark suit
                face(rig, j, grid[r][k + 1], grid[r][k], grid[r + 1][k], grid[r + 1][k + 1], trim && r >= per ? PIPE : r < per ? COLLAR : COAT);
                face(rig, j, inner[r][k], inner[r][k + 1], inner[r + 1][k + 1], inner[r + 1][k], LINING);
            }
        }
    }

    /** How finely the cloak is cut: quads round and down it (the dancing ones are cut coarser). */
    static int DETAIL_COLS = 40, DETAIL_PER = 3;

    /** A plain box on a joint; {@code outer} is the colour of its outer faces, {@code inner} of the face looking inward (the one towards the body: +z or -z). */
    private static void box(Rig15 rig, Rig15.Joint j, double x0, double y0, double z0, double x1, double y1, double z1, int outer, int inner) {
        box(rig, j, x0, y0, z0, x1, y1, z1, outer, inner, null);
    }

    /**
     * The same, rounded like the body when the rig is: {@code whole} is {centre, half-sizes} of the whole piece this one is a slice of (a sleeve
     * is cut at the elbow), or null for the box itself, so that the slices meet.
     */
    private static void box(Rig15 rig, Rig15.Joint j, double x0, double y0, double z0, double x1, double y1, double z1, int outer, int inner, double[][] whole) {
        double[][] c = corners(x0, y0, z0, x1, y1, z1, x0, z0, x1, z1);
        if (rig.round <= 0 || (x1 - x0 < 1.0 && whole == null) || (z1 - z0 < 1.0 && whole == null) || (y1 - y0 < 1.0 && whole == null)) {
            sides(rig, j, c, outer, inner);
            return;
        }
        double[][] shape = whole != null ? whole : new double[][]{{(x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2}, {(x1 - x0) / 2, (y1 - y0) / 2, (z1 - z0) / 2}};
        int[][] f = {{4, 6, 7, 5}, {1, 3, 2, 0}, {4, 5, 1, 0}, {5, 7, 3, 1}, {7, 6, 2, 3}, {6, 4, 0, 2}};
        int[] colours = {outer, outer, outer, inner, outer, outer};
        for (int s = 0; s < 6; s++) {
            double[] A = c[f[s][0]], B = c[f[s][1]], C = c[f[s][2]], D = c[f[s][3]];
            double lenS = Math.max(dist(A, B), 1e-6), lenT = Math.max(dist(A, D), 1e-6);
            int ns = Math.max(1, (int) Math.ceil(lenS / 1.1)), nt = Math.max(1, (int) Math.ceil(lenT / 1.1));
            for (int i = 0; i < ns; i++) {
                for (int k = 0; k < nt; k++) {
                    double[][] q = {pt(A, B, C, D, (double) i / ns, (double) k / nt), pt(A, B, C, D, (double) (i + 1) / ns, (double) k / nt),
                            pt(A, B, C, D, (double) (i + 1) / ns, (double) (k + 1) / nt), pt(A, B, C, D, (double) i / ns, (double) (k + 1) / nt)};
                    for (int m = 0; m < 4; m++) q[m] = pull(rig.round, shape, q[m]);
                    face(rig, j, q[0], q[1], q[2], q[3], colours[s]);
                }
            }
        }
    }

    private static double dist(double[] a, double[] b) {
        return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
    }

    /** A point of a face by its two fractions: A + s (B - A) + t (D - A). */
    private static double[] pt(double[] A, double[] B, double[] C, double[] D, double s, double t) {
        return new double[]{A[0] + (B[0] - A[0]) * s + (D[0] - A[0]) * t, A[1] + (B[1] - A[1]) * s + (D[1] - A[1]) * t, A[2] + (B[2] - A[2]) * s + (D[2] - A[2]) * t};
    }

    /** A point pulled in towards the ellipsoid round the piece, the way the body's boxes are. */
    private static double[] pull(double round, double[][] shape, double[] p) {
        double qx = (p[0] - shape[0][0]) / shape[1][0], qy = (p[1] - shape[0][1]) / shape[1][1], qz = (p[2] - shape[0][2]) / shape[1][2];
        double len = Math.sqrt(qx * qx + qy * qy + qz * qz);
        double f = len < 1e-9 ? 1 : (1 - round) + round / Math.max(1, len);
        return new double[]{shape[0][0] + qx * f * shape[1][0], shape[0][1] + qy * f * shape[1][1], shape[0][2] + qz * f * shape[1][2]};
    }

    /** A box that is narrower at the top: half-widths (x, z) at the foot and at the head, centred on the head's middle. */
    private static void frustum(Rig15 rig, Rig15.Joint j, double ya, double yb, double bx, double bz, double tx, double tz, int colour) {
        double[][] c = corners(-bx, ya, -bz, bx, yb, bz, -tx, -tz, tx, tz);
        sides(rig, j, c, colour, colour);
    }

    /**
     * The eight corners: the foot ring at ya (x0..x1, z0..z1) and the top ring at yb (given separately), as {x, y, z}, indexed by (top, x, z) = bits 4, 2, 1.
     */
    private static double[][] corners(double x0, double ya, double z0, double x1, double yb, double z1, double tx0, double tz0, double tx1, double tz1) {
        double[][] c = new double[8][];
        for (int i = 0; i < 8; i++) {
            boolean top = (i & 4) != 0, right = (i & 2) != 0, front = (i & 1) != 0;
            double x = top ? (right ? tx1 : tx0) : (right ? x1 : x0), z = top ? (front ? tz1 : tz0) : (front ? z1 : z0);
            c[i] = new double[]{x, top ? yb : ya, z};
        }
        return c;
    }

    private static void sides(Rig15 rig, Rig15.Joint j, double[][] c, int outer, int inner) {
        // corner index: top*4 + right*2 + front
        face(rig, j, c[4], c[6], c[7], c[5], outer);                                 // top (x0z0, x1z0, x1z1, x0z1)
        face(rig, j, c[1], c[3], c[2], c[0], outer);                                 // bottom
        face(rig, j, c[4], c[5], c[1], c[0], outer);                                 // -x
        face(rig, j, c[5], c[7], c[3], c[1], inner);                                 // +z (front)
        face(rig, j, c[7], c[6], c[2], c[3], outer);                                 // +x
        face(rig, j, c[6], c[4], c[0], c[2], outer);                                 // -z (back)
    }

    private static void face(Rig15 rig, Rig15.Joint j, double[] a, double[] b, double[] c, double[] d, int colour) {
        int u = colour % 64, v = 64 + colour / 64;
        rig.faces.add(new Rig15.Face(j, new double[][]{a, b, c, d}, u, v, u + 1, v + 1));
    }
}
