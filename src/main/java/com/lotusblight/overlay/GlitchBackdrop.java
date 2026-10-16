package com.lotusblight.overlay;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Random;

/**
 * The torn-up screen behind the eye in song two: error windows, big eyes, colour noise, smeared bars,
 * chunks of the picture shoved about, the colours split apart. It is built small and jumps about a dozen
 * times a second; each picture is smeared onto the next, so nothing ever quite settles.
 */
final class GlitchBackdrop {
    private static final int LW = 480, LH = 270;

    private static final String[][] WINDOWS = {
            {"Error", "Click Fix to fix error", "Fix"},
            {"Windows error", "Click OK to continue", "OK"},
            {"Warning", "Critical process died", "OK"},
            {"System", "0x0000007B  contact lost", "Fix"},
            {"Error", "Your files are being seen", "Fix"},
            {"Windows", "Do you want to keep going?", "No"},
    };
    private static final Color[] PALETTE = {
            new Color(0xFF1030), new Color(0x10FF40), new Color(0x1030FF), new Color(0xFF10E0),
            new Color(0x10F0FF), new Color(0xFFE010), new Color(0xFF6A00), new Color(0x8A10FF),
            new Color(0x000000), new Color(0x000000)};

    private final BufferedImage acc = new BufferedImage(LW, LH, BufferedImage.TYPE_INT_RGB);          // the working pictures are opaque: no alpha to carry
    private final BufferedImage work = new BufferedImage(LW, LH, BufferedImage.TYPE_INT_RGB);
    private final BufferedImage split = new BufferedImage(LW, LH, BufferedImage.TYPE_INT_ARGB_PRE);          // premultiplied: Java2D scales it much faster
    private BufferedImage big;
    private int bigW, bigH;
    private long bigFrame;
    private BufferedImage shade;                                                                                 // the scan lines and the dark middle, drawn once for a size
    private int shadeW, shadeH;
    private long lastFrame = Long.MIN_VALUE;
    private boolean noWall, noWindows;

    /** No error windows in the mess (the cutscene and the fall have none). */
    void setNoWindows(boolean noWindows) { this.noWindows = noWindows; }

    /** For the third song: the mess stays a mess, and never turns into the wall of "error". */
    void setNoWall(boolean noWall) { this.noWall = noWall; }
    private final Font small = new Font(Font.SANS_SERIF, Font.BOLD, 8);
    private final Font title = new Font(Font.SANS_SERIF, Font.BOLD, 7);

    void render(Graphics2D g, int w, int h, double ms, double amount) {
        if (amount <= 0) return;
        long frame = (long) (ms / 72);
        if (frame != lastFrame) {
            step(frame);
            lastFrame = frame;
        }
        // The picture changes only when a new step is made (a dozen times a second): scaled to the screen with the scan lines once, then just laid down.
        if (big == null || bigW != w || bigH != h) {
            big = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
            bigW = w;
            bigH = h;
            bigFrame = Long.MIN_VALUE;
        }
        if (bigFrame != lastFrame || shade == null) {
            if (shade == null || shadeW != w || shadeH != h) {
                shade = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
                Graphics2D sg = shade.createGraphics();
                sg.setColor(new Color(0, 0, 0, 55));
                for (int y = 0; y < h; y += 4) sg.fillRect(0, y, w, 1);         // scan lines
                sg.setPaint(new RadialGradientPaint((float) (w / 2.0), (float) (h / 2.0), (float) (h * 0.78), new float[]{0f, 0.6f, 1f},
                        new Color[]{new Color(0, 0, 0, 70), new Color(0, 0, 0, 40), new Color(0, 0, 0, 0)}));   // darker over the middle, so the circles read
                sg.fillRect(0, 0, w, h);
                sg.dispose();
                shadeW = w;
                shadeH = h;
            }
            Graphics2D bg = big.createGraphics();
            bg.setComposite(AlphaComposite.Clear);
            bg.fillRect(0, 0, w, h);
            bg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            bg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.92f));
            bg.drawImage(split, 0, 0, w, h, null);
            bg.setComposite(AlphaComposite.SrcOver);
            bg.drawImage(shade, 0, 0, null);
            bg.dispose();
            bigFrame = lastFrame;
        }
        Graphics2D o = (Graphics2D) g.create();
        o.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) amount));
        o.drawImage(big, 0, 0, null);
        o.dispose();
    }

    private void step(long frame) {
        Random rnd = new Random(frame * 104729L + 17);
        double wall = noWall ? 0 : ContactBreak.wallAmount(frame * 72.0);
        Graphics2D g = work.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        // The last picture, smeared a little outwards and to one side, over a dark base.
        g.setComposite(AlphaComposite.Src);
        g.setColor(new Color(9, 3, 18));
        g.fillRect(0, 0, LW, LH);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
        AffineTransform at = new AffineTransform();
        at.translate(LW / 2.0 + rnd.nextInt(7) - 3, LH / 2.0 + rnd.nextInt(5) - 1);
        at.scale(1.014, 1.014);
        at.translate(-LW / 2.0, -LH / 2.0);
        g.drawImage(acc, at, null);
        g.setComposite(AlphaComposite.SrcOver);

        // Chunks of the previous picture pushed somewhere else.
        for (int i = 0; i < 14; i++) {
            int sw = 24 + rnd.nextInt(110), sh = 6 + rnd.nextInt(46);
            int sx = rnd.nextInt(LW - sw), sy = rnd.nextInt(LH - sh);
            int dx = sx + rnd.nextInt(90) - 45, dy = sy + rnd.nextInt(30) - 15;
            g.drawImage(acc, dx, dy, dx + sw, dy + sh, sx, sy, sx + sw, sy + sh, null);
        }
        // Thin strips of the picture dragged out sideways.
        for (int i = 0; i < 16; i++) {
            int y = rnd.nextInt(LH), hh = 1 + rnd.nextInt(3);
            int sx = rnd.nextInt(LW - 3), span = 70 + rnd.nextInt(280), x0 = rnd.nextInt(LW - 40) - 20;
            g.drawImage(acc, x0, y, x0 + span, y + hh, sx, y, sx + 2, y + hh, null);
        }

        // Fresh colour: soft blobs, hard bars.
        for (int i = 0; i < 6; i++) {
            Color c = PALETTE[rnd.nextInt(PALETTE.length)];
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 90 + rnd.nextInt(120)));
            int bw = 30 + rnd.nextInt(150), bh = 12 + rnd.nextInt(60);
            g.fillOval(rnd.nextInt(LW) - bw / 2, rnd.nextInt(LH) - bh / 2, bw, bh);
        }
        for (int i = 0; i < 34; i++) {
            g.setColor(PALETTE[rnd.nextInt(PALETTE.length)]);
            int bw = 10 + rnd.nextInt(LW / 2), bh = 1 + rnd.nextInt(5);
            g.fillRect(rnd.nextInt(LW) - bw / 4, rnd.nextInt(LH), bw, bh);
        }

        // Big eyes and error windows sit still for a moment, so they read; the smearing does the rest.
        Random slow = new Random((frame / 9) * 7919L + 3);
        if (wall > 0) drawWall(g, frame, wall, rnd);
        int eyes = (int) Math.round(4 * (1 - 0.75 * wall)), windows = (int) Math.round(7 * (1 - 0.75 * wall));
        if (noWindows) windows = 0;
        for (int i = 0; i < eyes; i++) {
            eye(g, slow.nextInt(LW), slow.nextInt(LH), 26 + slow.nextInt(38), slow);
        }
        for (int i = 0; i < windows; i++) {
            window(g, slow.nextInt(LW - 60) - 10, slow.nextInt(LH - 40) - 6, slow);
        }
        // Sparkles and rain-lines.
        for (int i = 0; i < 9; i++) {
            int sx = rnd.nextInt(LW), sy = rnd.nextInt(LH), sr = 3 + rnd.nextInt(9);
            g.setColor(i % 3 == 0 ? new Color(255, 230, 40) : i % 3 == 1 ? Color.WHITE : new Color(80, 240, 255));
            g.fillRect(sx - sr, sy, sr * 2 + 1, 1);
            g.fillRect(sx, sy - sr, 1, sr * 2 + 1);
            g.fillRect(sx - 1, sy - 1, 3, 3);
        }
        g.setColor(new Color(255, 255, 255, 120));
        for (int i = 0; i < 6; i++) g.fillRect(rnd.nextInt(LW), rnd.nextInt(LH / 2), 1, 30 + rnd.nextInt(90));
        if (wall > 0.05) drawPlea(g, frame, wall, rnd);
        g.dispose();

        // Keep this picture for smearing into the next one.
        Graphics2D a = acc.createGraphics();
        a.setComposite(AlphaComposite.Src);
        a.drawImage(work, 0, 0, null);
        a.dispose();

        splitColours(rnd.nextInt(4) + 2);
    }

    /** Rows and rows of the word "error", the red and the cyan pulled apart, some rows garbled and running the other way. */
    private void drawWall(Graphics2D g, long frame, double a, Random rnd) {
        g.setFont(new Font(Font.SERIF, Font.BOLD, 20));
        int rowH = 23, unitW = g.getFontMetrics().stringWidth("error ");
        int copies = LW / unitW + 4;
        for (int row = 0; row * rowH < LH + rowH; row++) {
            int y = row * rowH + 18;
            int dir = row % 2 == 0 ? 1 : -1;
            int off = (int) Math.floorMod(frame * 3L * dir + row * 37L, (long) unitW);
            StringBuilder line = new StringBuilder();
            boolean garbled = (frame / 3 + row) % 9 == 0;
            for (int i = 0; i < copies; i++) line.append(garbled && rnd.nextInt(3) == 0 ? "eiror " : "error ");
            int x = -unitW + off;
            int aa = (int) (255 * a);
            g.setColor(new Color(255, 40, 60, aa * 3 / 4));
            g.drawString(line.toString(), x - 2, y);
            g.setColor(new Color(40, 230, 240, aa * 3 / 4));
            g.drawString(line.toString(), x + 2, y);
            g.setColor(new Color(245, 245, 245, aa * 9 / 10));
            g.drawString(line.toString(), x, y);
        }
    }

    /** The plea in the middle, red, shaking, going out for a beat now and then. */
    private void drawPlea(Graphics2D g, long frame, double a, Random rnd) {
        if (frame % 43 < 3) return;
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
        String[] lines = {"WHY DO YOU", "HATE ME??!!"};
        int aa = (int) (255 * Math.min(1, a * 1.3));
        for (int i = 0; i < lines.length; i++) {
            int tw = g.getFontMetrics().stringWidth(lines[i]);
            int x = (LW - tw) / 2 + rnd.nextInt(3) - 1, y = (int) (LH * 0.40) + i * 38 + rnd.nextInt(3) - 1;
            g.setColor(new Color(0, 0, 0, aa / 2));
            g.drawString(lines[i], x + 2, y + 2);
            g.setColor(new Color(40, 230, 240, aa / 2));
            g.drawString(lines[i], x + 2, y);
            g.setColor(new Color(255, 60, 45, aa));
            g.drawString(lines[i], x, y);
        }
    }

    /** Red pulled to one side, blue to the other. */
    private void splitColours(int shift) {
        int[] src = ((DataBufferInt) work.getRaster().getDataBuffer()).getData();
        int[] dst = ((DataBufferInt) split.getRaster().getDataBuffer()).getData();
        for (int y = 0; y < LH; y++) {
            int row = y * LW;
            for (int x = 0; x < LW; x++) {
                int rx = Math.max(0, x - shift), bx = Math.min(LW - 1, x + shift);
                int r = (src[row + rx] >> 16) & 0xFF;
                int gg = (src[row + x] >> 8) & 0xFF;
                int b = src[row + bx] & 0xFF;
                dst[row + x] = 0xFF000000 | (r << 16) | (gg << 8) | b;
            }
        }
    }

    private void window(Graphics2D g, int x, int y, Random rnd) {
        String[] spec = WINDOWS[rnd.nextInt(WINDOWS.length)];
        int ww = 96 + rnd.nextInt(34), wh = 46 + rnd.nextInt(14);
        boolean warning = spec[0].startsWith("Warn") || spec[0].startsWith("Wind");
        g.setColor(new Color(0, 0, 0, 90));
        g.fillRect(x + 3, y + 3, ww, wh);
        g.setColor(new Color(0xEDE4F6));
        g.fillRect(x, y, ww, wh);
        g.setColor(new Color(0x2452D8));
        g.fillRect(x, y, ww, 9);
        g.setColor(new Color(0x5C86F0));
        g.fillRect(x, y, ww, 3);
        g.setFont(title);
        g.setColor(Color.WHITE);
        g.drawString(spec[0], x + 3, y + 7);
        g.setColor(new Color(0xD83030));
        g.fillRect(x + ww - 10, y + 1, 8, 7);
        g.setColor(Color.WHITE);
        g.drawLine(x + ww - 8, y + 2, x + ww - 4, y + 6);
        g.drawLine(x + ww - 4, y + 2, x + ww - 8, y + 6);
        if (warning) {
            g.setColor(new Color(0xF0C010));
            g.fillPolygon(new int[]{x + 5, x + 17, x + 11}, new int[]{y + 28, y + 28, y + 15}, 3);
            g.setColor(new Color(0x402000));
            g.fillRect(x + 10, y + 19, 2, 5);
            g.fillRect(x + 10, y + 25, 2, 2);
        } else {
            g.setColor(new Color(0xD82828));
            g.fillOval(x + 5, y + 15, 13, 13);
            g.setColor(Color.WHITE);
            g.drawLine(x + 9, y + 19, x + 14, y + 24);
            g.drawLine(x + 14, y + 19, x + 9, y + 24);
        }
        g.setFont(small);
        g.setColor(new Color(0x2A2A50));
        g.drawString(spec[1], x + 22, y + 25);
        int bx = x + ww / 2 - 15, by = y + wh - 14;
        g.setColor(new Color(0xC6BCD8));
        g.fillRect(bx, by, 30, 10);
        g.setColor(new Color(0x30304C));
        g.drawRect(bx, by, 30, 10);
        g.drawString(spec[2], bx + 15 - g.getFontMetrics().stringWidth(spec[2]) / 2, by + 8);
    }

    /** A big staring eye: pale pink white, coloured iris, black pupil, lashes, coloured fringes. */
    private void eye(Graphics2D g, int cx, int cy, int r, Random rnd) {
        Path2D.Double almond = new Path2D.Double();
        almond.moveTo(cx - r, cy);
        almond.quadTo(cx, cy - r * 0.95, cx + r, cy);
        almond.quadTo(cx, cy + r * 0.95, cx - r, cy);
        almond.closePath();
        g.setPaint(new RadialGradientPaint(cx, cy, r, new float[]{0f, 0.7f, 1f},
                new Color[]{new Color(255, 236, 244), new Color(240, 190, 210), new Color(160, 60, 90)}));
        g.fill(almond);
        java.awt.Shape old = g.getClip();
        g.clip(almond);
        Color ic = PALETTE[rnd.nextInt(8)];
        int ir = (int) (r * 0.46), ix = cx + rnd.nextInt(Math.max(1, r / 3)) - r / 6, iy = cy + rnd.nextInt(Math.max(1, r / 6)) - r / 12;
        g.setPaint(new RadialGradientPaint(ix, iy, ir, new float[]{0f, 0.55f, 1f},
                new Color[]{ic.brighter(), ic, new Color(10, 0, 30)}));
        g.fillOval(ix - ir, iy - ir, ir * 2, ir * 2);
        g.setColor(Color.BLACK);
        int pr = Math.max(2, ir / 2);
        g.fillOval(ix - pr, iy - pr, pr * 2, pr * 2);
        g.setColor(Color.WHITE);
        g.fillOval(ix - pr + 1, iy - pr + 1, Math.max(2, pr / 2), Math.max(2, pr / 2));
        g.setClip(old);
        g.setStroke(new BasicStroke(2f));
        g.translate(1, 1);
        g.setColor(new Color(0, 220, 255));
        g.draw(almond);
        g.translate(-2, -2);
        g.setColor(new Color(255, 40, 140));
        g.draw(almond);
        g.translate(1, 1);
        g.setColor(new Color(20, 0, 30));
        g.draw(almond);
        g.setStroke(new BasicStroke(1f));
        for (int i = 1; i < 10; i++) {
            double u = i / 10.0;
            double px = cx - r + 2 * r * u, py = cy - r * 0.95 * 2 * u * (1 - u);
            double nx = (u - 0.5) * 1.2, ny = -1, nl = Math.hypot(nx, ny), ll = r * 0.2;
            g.draw(new java.awt.geom.Line2D.Double(px, py, px + nx / nl * ll, py + ny / nl * ll));
        }
        g.setStroke(new BasicStroke(1f));
    }
}
