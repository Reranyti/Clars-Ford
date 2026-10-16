package com.lotusblight.overlay;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * The picture of the second song for the Lotos Editor: the game's own visuals (the backdrop, the columns, the solid Glitcher, the eye, the
 * snakes with the TV, the black phrase screens), with no circles and no sound, drawn at the moment asked for. The editor starts this and
 * talks to it through the pipes: a line {@code R <ms> <width> <height>} in, a picture out (four bytes of length, then a PNG).
 * Everything that comes from the editor's scenes (the camera) is read live, so a change shows on the next frame.
 */
public final class PreviewMain {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        OutputStream raw = System.out;
        System.setOut(new PrintStream(System.err, true));      // nothing but pictures goes down the pipe

        BufferedImage skinImg;
        try (var in = PreviewMain.class.getResourceAsStream("/assets/lotusblight/textures/overlay/glitcher.png")) {
            skinImg = javax.imageio.ImageIO.read(in);
        }
        int[] skin = skinImg.getRGB(0, 0, 64, 64, null, 0, 64);
        ColumnsFx columns = new ColumnsFx();
        SnakesFx snakes = new SnakesFx();
        GlitcherCloseup closeup = new GlitcherCloseup(skin);
        PhraseScreen phrases = new PhraseScreen();
        ContactBreak contact = new ContactBreak(ContactBreak.beatTimes(), d -> { }, () -> { });
        Hazards[] hazards = {null};
        int hazardsH = 0;

        DataOutputStream out = new DataOutputStream(raw);
        BufferedImage frame = null;
        BufferedReader r = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
        String line;
        while ((line = r.readLine()) != null) {
            String[] p = line.trim().split("\\s+");
            if (!(p[0].equals("R") && p.length >= 4) && !(p[0].equals("P") && p.length >= 3) && !(p[0].equals("B") && p.length >= 2)) continue;
            if (p[0].equals("P") && p.length >= 3) {                                  // which layer is under this point of the last picture
                String id = Layers.keep == null ? null : Layers.pick(Layers.keep, Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                byte[] b = (id == null ? "" : id).getBytes("UTF-8");
                out.writeInt(b.length + 1);
                out.writeByte(1);
                out.write(b);
                out.flush();
                continue;
            }
            if (p[0].equals("B") && p.length >= 2) {                                  // the box of a layer in the last picture
                byte[] b = (Layers.keep == null ? "" : Layers.bounds(Layers.keep, p[1])).getBytes("UTF-8");
                out.writeInt(b.length + 1);
                out.writeByte(2);
                out.write(b);
                out.flush();
                continue;
            }
            double t = Double.parseDouble(p[1]);
            int w = Integer.parseInt(p[2]), h = Integer.parseInt(p[3]);
            GlitcherCloseup.RES = w >= 1400 ? 0.9 : w >= 1000 ? 0.6 : 0.42;
            if (hazards[0] == null || hazardsH != h) {
                hazards[0] = new Hazards(skin, h);
                hazardsH = h;
            }
            if (frame == null || frame.getWidth() != w || frame.getHeight() != h) frame = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = frame.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(6, 6, 14));
            g.fillRect(0, 0, w, h);
            Layers.keep = Layers.newKeep();
            try {
                render(g, w, h, t, contact, columns, closeup, hazards[0], snakes, phrases);
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
            g.dispose();
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(frame, "png", png);
            out.writeInt(png.size() + 1);
            out.writeByte(0);
            out.write(png.toByteArray());
            out.flush();
        }
    }

    /** The camera of the whole picture: the tilt and push of the columns and of the Glitcher's blows, as the game applies it. */
    private static Graphics2D camera(Graphics2D g, int w, int h, double t, ColumnsFx columns, GlitcherCloseup closeup) {
        Graphics2D c = (Graphics2D) g.create();
        double[] k1 = columns.camera(t), k2 = closeup.camera(t, w, h);
        double roll = k1[0] + k2[0], zoom = k1[1] * k2[1];
        if (roll != 0 || zoom != 1 || k2[2] != 0 || k2[3] != 0) {
            c.translate(w / 2.0 + k2[2], h / 2.0 + k2[3]);
            c.rotate(roll);
            c.scale(zoom, zoom);
            c.translate(-w / 2.0, -h / 2.0);
        }
        return c;
    }

    private static void render(Graphics2D g, int w, int h, double t, ContactBreak contact, ColumnsFx columns, GlitcherCloseup closeup,
                               Hazards hazards, SnakesFx snakes, PhraseScreen phrases) {
        contact.update(t);
        Graphics2D cam = camera(g, w, h, t, columns, closeup);
        Layers.draw(cam, w, h, "backdrop", c -> contact.renderBackdrop(c, w, h, t));
        Layers.draw(cam, w, h, "columns", c -> columns.render(c, w, h, t));
        Layers.draw(cam, w, h, "glitcher", c -> closeup.render(c, w, h, t));
        cam.dispose();
        Layers.draw(g, w, h, "eye", c -> contact.renderEye(c, w, h, t));
        Layers.draw(g, w, h, "windows", c -> hazards.renderCarryover(c, w, h, t));
        Graphics2D cam2 = camera(g, w, h, t, columns, closeup);
        Layers.draw(cam2, w, h, "windows2", c -> hazards.renderSong2(c, w, h, t));
        cam2.dispose();
        Graphics2D cam3 = camera(g, w, h, t, columns, closeup);
        Layers.draw(cam3, w, h, "snakes", c -> snakes.render(c, w, h, t, ms -> {
            double[] c3 = closeup.hand(ms, h);
            return c3 != null ? c3 : hazards.songTwoHand(ms, w, h);
        }));
        cam3.dispose();
        Layers.draw(g, w, h, "phrases", c -> phrases.render(c, w, h, t));
    }
}
