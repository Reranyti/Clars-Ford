package com.lotusblight.overlay;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The layers of a scene: every visible part of a picture (the backdrop, the columns, the Glitcher, the snakes...) is drawn through here under
 * its name, so that what the editor says about it is done to it: hidden, moved, scaled, turned, made fainter. In the game the layer
 * just takes the transform of the scene's "O" record (the default does nothing at all); in the editor's preview each layer is also kept as
 * a picture of its own, so that a click on the picture can tell which layer is under it.
 */
final class Layers {
    /** What the scene says about a layer: shown or not, the shift (parts of the screen's width and height), the scale, the turn (rad), the opacity. */
    record Xf(boolean visible, double dx, double dy, double scale, double rot, double alpha) {
        static final Xf PLAIN = new Xf(true, 0, 0, 1, 0, 1);

        boolean isPlain() {
            return visible && dx == 0 && dy == 0 && scale == 1 && rot == 0 && alpha >= 1;
        }
    }

    /** A drawn layer kept for picking: its picture and where it was put on the screen. */
    record Kept(String id, BufferedImage img, AffineTransform at) {}

    /** The scene program being drawn now (the editor sets it for each picture; the fight is the second song). */
    static String program = "song2";

    static SceneData.Cut cut() {
        return SceneData.current().forProgram(program);
    }

    /** Set by the editor's preview only: when present every layer is drawn into a picture of its own first. */
    static List<Kept> keep;

    static Xf of(String id) {
        SceneData.Cut c = cut();
        Xf x = c == null ? null : c.objects.get(id);
        return x == null ? Xf.PLAIN : x;
    }

    private static AffineTransform place(int w, int h, Xf x) {
        AffineTransform a = new AffineTransform();
        a.translate(w / 2.0 + x.dx * w, h / 2.0 + x.dy * h);
        a.rotate(x.rot);
        a.scale(x.scale, x.scale);
        a.translate(-w / 2.0, -h / 2.0);
        return a;
    }

    /** Draws the layer: {@code g} may already carry the camera of the picture; the layer's own transform goes on top of it, on the screen. */
    static void draw(Graphics2D g, int w, int h, String id, Consumer<Graphics2D> body) {
        Xf x = of(id);
        if (keep == null) {
            if (!x.visible) return;
            if (x.isPlain()) {
                body.accept(g);
                return;
            }
            Graphics2D c = (Graphics2D) g.create();
            AffineTransform lc = place(w, h, x);
            lc.concatenate(g.getTransform());
            c.setTransform(lc);
            if (x.alpha < 1) c.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, x.alpha)));
            body.accept(c);
            c.dispose();
            return;
        }
        if (!x.visible) {
            keep.add(new Kept(id, null, null));
            return;
        }
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D b = img.createGraphics();
        b.setRenderingHints(g.getRenderingHints());
        b.setTransform(g.getTransform());
        body.accept(b);
        b.dispose();
        AffineTransform at = place(w, h, x);
        keep.add(new Kept(id, img, at));
        Graphics2D c = (Graphics2D) g.create();
        c.setTransform(new AffineTransform());
        c.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (x.alpha < 1) c.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, x.alpha)));
        c.drawImage(img, at, null);
        c.dispose();
    }

    /** The topmost layer with something drawn at the screen point, or null. */
    static String pick(List<Kept> layers, int px, int py) {
        for (int i = layers.size() - 1; i >= 0; i--) {
            Kept k = layers.get(i);
            if (k.img == null) continue;
            try {
                double[] p = {px, py};
                k.at.inverseTransform(p, 0, p, 0, 1);
                int x = (int) Math.floor(p[0]), y = (int) Math.floor(p[1]);
                if (x >= 0 && y >= 0 && x < k.img.getWidth() && y < k.img.getHeight() && (k.img.getRGB(x, y) >>> 24) > 24) return k.id;
            } catch (NoninvertibleTransformException ignored) {
                // a layer scaled to nothing cannot be picked
            }
        }
        return null;
    }

    /** The box around what the layer has drawn, on the screen, as "x0 y0 x1 y1"; empty when it drew nothing. */
    static String bounds(List<Kept> layers, String id) {
        for (int i = layers.size() - 1; i >= 0; i--) {
            Kept k = layers.get(i);
            if (!k.id.equals(id) || k.img == null) continue;
            int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = -1, y1 = -1;
            for (int y = 0; y < k.img.getHeight(); y += 3)
                for (int x = 0; x < k.img.getWidth(); x += 3)
                    if ((k.img.getRGB(x, y) >>> 24) > 24) {
                        x0 = Math.min(x0, x);
                        y0 = Math.min(y0, y);
                        x1 = Math.max(x1, x);
                        y1 = Math.max(y1, y);
                    }
            if (x1 < 0) return "";
            double[] c = {x0, y0, x1, y0, x1, y1, x0, y1};
            k.at.transform(c, 0, c, 0, 4);
            double ax = Math.min(Math.min(c[0], c[2]), Math.min(c[4], c[6])), bx = Math.max(Math.max(c[0], c[2]), Math.max(c[4], c[6]));
            double ay = Math.min(Math.min(c[1], c[3]), Math.min(c[5], c[7])), by = Math.max(Math.max(c[1], c[3]), Math.max(c[5], c[7]));
            return (int) ax + " " + (int) ay + " " + (int) bx + " " + (int) by;
        }
        return "";
    }

    static List<Kept> newKeep() {
        return new ArrayList<>();
    }
}
