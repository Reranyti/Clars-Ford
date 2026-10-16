package com.lotusblight.overlay;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

/**
 * A tiny software rasterizer for the skin model: orthographic view, a depth buffer, nearest-texel
 * sampling and alpha testing like Minecraft's cutout layer. It runs in the overlay's own process, which
 * has no OpenGL of its own, and the model is a few dozen quads - plenty fast on the CPU.
 */
final class SoftRenderer {
    /** Per-frame pose: whole-body turn plus a rotation and an offset for every part. */
    static final class Pose {
        double yaw;
        double pitch;
        final double[] partPitch = new double[6];
        final double[] partYaw = new double[6];
        final double[] partRoll = new double[6];
        final double[][] partOffset = new double[6][3];

        void reset() {
            yaw = 0;
            pitch = 0;
            Arrays.fill(partPitch, 0);
            Arrays.fill(partYaw, 0);
            Arrays.fill(partRoll, 0);
            for (double[] o : partOffset) Arrays.fill(o, 0);
        }
    }

    // Light from the upper front-left, the way Minecraft lights entities in inventories.
    private static final double[] LIGHT = normalize(new double[]{-0.4, 0.8, 0.6});

    final int width;
    final int height;
    final BufferedImage image;
    final int[] pixels;
    /** The same pixels seen as premultiplied: {@link #premultiply()} turns them over, and then this is what to scale onto the screen. */
    final BufferedImage imagePre;
    private final float[] depth;

    SoftRenderer(int width, int height) {
        this.width = width;
        this.height = height;
        this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        this.pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        java.awt.image.DataBufferInt db = new java.awt.image.DataBufferInt(pixels, pixels.length);
        java.awt.image.WritableRaster wr = java.awt.image.Raster.createPackedRaster(db, width, height, width, new int[]{0xFF0000, 0xFF00, 0xFF, 0xFF000000}, null);
        java.awt.image.ColorModel pre = new java.awt.image.DirectColorModel(java.awt.color.ColorSpace.getInstance(java.awt.color.ColorSpace.CS_sRGB),
                32, 0xFF0000, 0xFF00, 0xFF, 0xFF000000, true, java.awt.image.DataBuffer.TYPE_INT);
        this.imagePre = new BufferedImage(pre, wr, true, null);
        this.depth = new float[width * height];
    }

    /** The colour of the light: what the lit side is multiplied by, and what the shadowed side keeps. 1,1,1 is the plain look. */
    double[] key = {1, 1, 1};
    double[] ambient = {1, 1, 1};
    /** The box that has been drawn on since {@link #clear()} (for the finishing passes); empty when bx1 < bx0. */
    int bx0, by0, bx1, by1;

    void clear() {
        Arrays.fill(pixels, 0);
        Arrays.fill(depth, Float.NEGATIVE_INFINITY);
        bx0 = width;
        by0 = height;
        bx1 = -1;
        by1 = -1;
    }

    private boolean everCleared;

    /**
     * Clears only what was drawn since the last clear (the box the triangles touched), for a renderer that nothing else writes into:
     * the buffer is large and the figure usually fills a part of it.
     */
    void clearDirty() {
        if (!everCleared) {
            clear();
            everCleared = true;
            return;
        }
        if (bx1 >= bx0) {
            for (int y = by0; y <= by1; y++) {
                int row = y * width;
                Arrays.fill(pixels, row + bx0, row + bx1 + 1, 0);
                Arrays.fill(depth, row + bx0, row + bx1 + 1, Float.NEGATIVE_INFINITY);
            }
        }
        bx0 = width;
        by0 = height;
        bx1 = -1;
        by1 = -1;
    }

    void setLight(double[] key, double[] ambient) {
        this.key = key;
        this.ambient = ambient;
    }

    /**
     * Draws the model with its feet at (originX, originY) of the image, scale image pixels per skin
     * pixel.
     */
    void draw(SkinModel model, int[] skin, Pose pose, double scale, double originX, double originY) {
        double[][] p = new double[4][];
        for (SkinModel.Face face : model.faces) {
            int part = face.part().ordinal();
            for (int i = 0; i < 4; i++) {
                p[i] = transform(face.corners()[i], part, pose);
            }
            // Outward normal from the transformed corners; faces turned away from the viewer are skipped.
            double[] n = normalize(cross(sub(p[3], p[0]), sub(p[1], p[0])));
            if (n[2] <= 1e-6) continue;
            double light = 0.55 + 0.45 * Math.max(0, dot(n, LIGHT));

            double[][] s = new double[4][];
            for (int i = 0; i < 4; i++) {
                s[i] = new double[]{originX + p[i][0] * scale, originY - p[i][1] * scale, p[i][2]};
            }
            double[][] uv = {
                    {face.u0(), face.v0()}, {face.u1(), face.v0()},
                    {face.u1(), face.v1()}, {face.u0(), face.v1()}};
            triangle(s[0], s[1], s[2], uv[0], uv[1], uv[2], face, skin, light);
            triangle(s[0], s[2], s[3], uv[0], uv[2], uv[3], face, skin, light);
        }
    }

    /**
     * Draws a posed {@link Rig15}: every face is carried along with its joint, the whole turned by
     * {@code viewYaw} about the vertical (0 = seen from the front) and tipped by {@code viewPitch}.
     * The floor point (0, 0, 0) lands at (originX, originY); {@code scale} is image pixels per skin pixel.
     */
    /**
     * The camera's distance from the figure in skin units, or 0 for the plain orthographic view. With a distance the figure is seen in real
     * perspective: what is nearer the camera (z towards it) is bigger, textures are mapped with the depth taken into account.
     */
    double camDist = 0;
    /** The point the camera looks at and turns the figure about (x, y), and the height the camera stands at, in skin units; 0, 0, 16 is the old chest-high view. */
    double pivotX = 0, pivotY = 0, camH = 16;
    private boolean perspective;

    void drawRig(Rig15 rig, Rig15.Skel sk, int[] skin, double viewYaw, double viewPitch, double scale, double originX, double originY) {
        double[][] p = new double[4][];
        perspective = camDist > 0;
        for (Rig15.Face face : rig.faces) {
            for (int i = 0; i < 4; i++) {
                double[] c0 = sk.carry(face.joint(), face.corners()[i]);
                if (perspective) {                                                         // the figure is turned about the point the camera looks at
                    double[] q = rotateX(rotateY(new double[]{c0[0] - pivotX, c0[1] - pivotY, c0[2]}, viewYaw), viewPitch);
                    p[i] = new double[]{q[0] + pivotX, q[1] + pivotY, q[2]};
                } else {
                    p[i] = rotateX(rotateY(c0, viewYaw), viewPitch);
                }
            }
            double[] n = normalize(cross(sub(p[3], p[0]), sub(p[1], p[0])));
            double[][] s = new double[4][];
            double[][] uv = {
                    {face.u0(), face.v0()}, {face.u1(), face.v0()},
                    {face.u1(), face.v1()}, {face.u0(), face.v1()}};
            if (perspective) {
                // the face must look towards the camera, which stands in front of the figure at the height of its chest
                double[] toCam = {pivotX - p[0][0], camH - p[0][1], camDist - p[0][2]};
                if (dot(n, toCam) <= 1e-6) continue;
                boolean behind = false;
                for (int i = 0; i < 4; i++) {
                    double zc = camDist - p[i][2];
                    if (zc < 2) {
                        behind = true;
                        break;
                    }
                    double k = camDist / zc, inv = 1.0 / zc;
                    s[i] = new double[]{originX + (pivotX + (p[i][0] - pivotX) * k) * scale, originY - (pivotY + (p[i][1] - pivotY) * k) * scale, inv};
                    uv[i] = new double[]{uv[i][0] * inv, uv[i][1] * inv};              // divided by the depth, to be divided back per pixel
                }
                if (behind) continue;
            } else {
                if (n[2] <= 1e-6) continue;
                for (int i = 0; i < 4; i++) {
                    s[i] = new double[]{originX + p[i][0] * scale, originY - p[i][1] * scale, p[i][2]};
                }
            }
            double light = 0.55 + 0.45 * Math.max(0, dot(n, LIGHT));
            SkinModel.Face legacy = new SkinModel.Face(SkinModel.Part.BODY, null, face.u0(), face.v0(), face.u1(), face.v1());
            triangle(s[0], s[1], s[2], uv[0], uv[1], uv[2], legacy, skin, light);
            triangle(s[0], s[2], s[3], uv[0], uv[2], uv[3], legacy, skin, light);
        }
    }

    /** Turns the pixels into premultiplied ones, in place, over the part that was drawn on (after this only {@link #imagePre} may be shown). */
    void premultiply() {
        int y0 = Math.max(0, by0), y1 = Math.min(height - 1, by1), x0 = Math.max(0, bx0), x1 = Math.min(width - 1, bx1);
        for (int y = y0; y <= y1; y++) {
            for (int i = y * width + x0, e = y * width + x1; i <= e; i++) {
                int p = pixels[i], a = p >>> 24;
                if (a == 255 || a == 0) continue;
                int r = ((p >> 16 & 255) * a + 127) / 255, g = ((p >> 8 & 255) * a + 127) / 255, b = ((p & 255) * a + 127) / 255;
                pixels[i] = a << 24 | r << 16 | g << 8 | b;
            }
        }
    }

    /** Projects a stage point the same way {@link #drawRig} does, to image coordinates {x, y}. */
    static double[] project(double[] stage, double viewYaw, double viewPitch, double scale, double originX, double originY) {
        double[] p = rotateX(rotateY(stage, viewYaw), viewPitch);
        return new double[]{originX + p[0] * scale, originY - p[1] * scale};
    }

    static double[] transform(double[] c, int part, Pose pose) {
        double[] pivot = SkinModel.PIVOTS[part];
        double x = c[0] - pivot[0], y = c[1] - pivot[1], z = c[2] - pivot[2];
        // The part's own rotation around its joint: pitch (x), yaw (y), roll (z).
        double[] r = rotateZ(rotateY(rotateX(new double[]{x, y, z}, pose.partPitch[part]), pose.partYaw[part]), pose.partRoll[part]);
        double[] off = pose.partOffset[part];
        r[0] += pivot[0] + off[0];
        r[1] += pivot[1] + off[1];
        r[2] += pivot[2] + off[2];
        // Then the whole body, turned around its feet.
        return rotateX(rotateY(r, pose.yaw), pose.pitch);
    }

    private void triangle(double[] a, double[] b, double[] c, double[] ta, double[] tb, double[] tc,
                          SkinModel.Face face, int[] skin, double light) {
        int minX = (int) Math.max(0, Math.floor(Math.min(a[0], Math.min(b[0], c[0]))));
        int maxX = (int) Math.min(width - 1, Math.ceil(Math.max(a[0], Math.max(b[0], c[0]))));
        int minY = (int) Math.max(0, Math.floor(Math.min(a[1], Math.min(b[1], c[1]))));
        int maxY = (int) Math.min(height - 1, Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))));
        double area = edge(a, b, c[0], c[1]);
        if (Math.abs(area) < 1e-9) return;

        int uMin = Math.min(face.u0(), face.u1()), uMax = Math.max(face.u0(), face.u1()) - 1;
        int vMin = Math.min(face.v0(), face.v1()), vMax = Math.max(face.v0(), face.v1()) - 1;
        // The barycentric weights change by a fixed step per pixel, so they are stepped rather than recomputed.
        double invArea = 1 / area;
        double dx0 = -(c[1] - b[1]) * invArea, dy0 = (c[0] - b[0]) * invArea;
        double dx1 = -(a[1] - c[1]) * invArea, dy1 = (a[0] - c[0]) * invArea;
        double w0row = edge(b, c, minX + 0.5, minY + 0.5) * invArea, w1row = edge(c, a, minX + 0.5, minY + 0.5) * invArea;
        double dx2 = -(dx0 + dx1);
        int span = maxX - minX;
        for (int y = minY; y <= maxY; y++, w0row += dy0, w1row += dy1) {
            // The three weights are straight lines along the row: where all of them are non-negative is one run of pixels, found
            // directly instead of testing every pixel of the triangle's box.
            double w2row = 1 - w0row - w1row;
            int lo = 0, hi = span;
            if (dx0 > 1e-12) lo = Math.max(lo, (int) Math.ceil(-w0row / dx0));
            else if (dx0 < -1e-12) hi = Math.min(hi, (int) Math.floor(-w0row / dx0));
            else if (w0row < 0) continue;
            if (dx1 > 1e-12) lo = Math.max(lo, (int) Math.ceil(-w1row / dx1));
            else if (dx1 < -1e-12) hi = Math.min(hi, (int) Math.floor(-w1row / dx1));
            else if (w1row < 0) continue;
            if (dx2 > 1e-12) lo = Math.max(lo, (int) Math.ceil(-w2row / dx2));
            else if (dx2 < -1e-12) hi = Math.min(hi, (int) Math.floor(-w2row / dx2));
            else if (w2row < 0) continue;
            lo = Math.max(0, lo - 1);                  // the run is a guess to a pixel; the exact test below decides the edges
            hi = Math.min(span, hi + 1);
            for (int xi = lo; xi <= hi; xi++) {
                int x = minX + xi;
                double w0 = w0row + dx0 * xi, w1 = w1row + dx1 * xi;
                double w2 = 1 - w0 - w1;
                if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                double z = w0 * a[2] + w1 * b[2] + w2 * c[2];
                int i = y * width + x;
                float zf = (float) z;
                if (zf <= depth[i]) continue;
                double us = w0 * ta[0] + w1 * tb[0] + w2 * tc[0], vs = w0 * ta[1] + w1 * tb[1] + w2 * tc[1];
                if (perspective) {                                                   // z holds 1/depth here: the texture coordinates were divided by the depth
                    double iz = 1.0 / z;
                    us *= iz;
                    vs *= iz;
                }
                int u = clamp((int) Math.floor(us), uMin, uMax);
                int v = clamp((int) Math.floor(vs), vMin, vMax);
                int texel = skin[v * 64 + u];
                if ((texel >>> 24) < 128) continue;
                depth[i] = zf;
                pixels[i] = shade(texel, light);
                if (x < bx0) bx0 = x;
                if (x > bx1) bx1 = x;
                if (y < by0) by0 = y;
                if (y > by1) by1 = y;
            }
        }
    }

    private int shade(int argb, double light) {
        // 0.55 of the light is ambient, the other 0.45 comes from the key light by how much the face turns to it.
        double lit = Math.max(0, (light - 0.55) / 0.45);
        int r = Math.min(255, (int) (((argb >> 16) & 0xFF) * (0.55 * ambient[0] + 0.45 * lit * key[0])));
        int g = Math.min(255, (int) (((argb >> 8) & 0xFF) * (0.55 * ambient[1] + 0.45 * lit * key[1])));
        int b = Math.min(255, (int) ((argb & 0xFF) * (0.55 * ambient[2] + 0.45 * lit * key[2])));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static double edge(double[] a, double[] b, double x, double y) {
        return (b[0] - a[0]) * (y - a[1]) - (b[1] - a[1]) * (x - a[0]);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private static double[] rotateX(double[] p, double a) {
        if (a == 0) return p;
        double c = Math.cos(a), s = Math.sin(a);
        return new double[]{p[0], p[1] * c - p[2] * s, p[1] * s + p[2] * c};
    }

    private static double[] rotateY(double[] p, double a) {
        if (a == 0) return p;
        double c = Math.cos(a), s = Math.sin(a);
        return new double[]{p[0] * c + p[2] * s, p[1], -p[0] * s + p[2] * c};
    }

    private static double[] rotateZ(double[] p, double a) {
        if (a == 0) return p;
        double c = Math.cos(a), s = Math.sin(a);
        return new double[]{p[0] * c - p[1] * s, p[0] * s + p[1] * c, p[2]};
    }

    private static double[] sub(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] normalize(double[] v) {
        double len = Math.sqrt(dot(v, v));
        return len == 0 ? v : new double[]{v[0] / len, v[1] / len, v[2] / len};
    }
}
