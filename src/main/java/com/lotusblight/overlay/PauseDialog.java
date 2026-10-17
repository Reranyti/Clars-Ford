package com.lotusblight.overlay;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

/** The question that stops a minigame on Esc: "Close the window?" with Yes and No. */
final class PauseDialog {
    static final int NONE = 0, YES = 1, NO = 2;

    private final Rectangle yes = new Rectangle();
    private final Rectangle no = new Rectangle();
    private int hover = NONE;
    private final String title, sub, yesLabel, noLabel;
    private final float titleSize, subSize;

    PauseDialog() {
        this("\u0417\u0430\u043a\u0440\u044b\u0442\u044c \u043e\u043a\u043d\u043e?", "\u0442\u044b \u043f\u0440\u043e\u0441\u0442\u043e \u0441\u043b\u0430\u0431 \u0447\u0442\u043e \u0431\u044b \u043f\u0440\u043e\u0434\u0435\u0440\u0436\u0430\u0442\u0441\u044f?",
                "\u0414\u0430", "\u041d\u0435\u0442", 0.06f, 0.032f);
    }

    PauseDialog(String title, String sub, String yesLabel, String noLabel, float titleSize, float subSize) {
        this.title = title;
        this.sub = sub;
        this.yesLabel = yesLabel;
        this.noLabel = noLabel;
        this.titleSize = titleSize;
        this.subSize = subSize;
    }

    void move(int x, int y) {
        hover = yes.contains(x, y) ? YES : no.contains(x, y) ? NO : NONE;
    }

    /** Which button a click at (x, y) lands on. */
    int hit(int x, int y) {
        return yes.contains(x, y) ? YES : no.contains(x, y) ? NO : NONE;
    }

    void render(Graphics2D g, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0, 0, 0, 190));
        g.fillRect(0, 0, w, h);

        int pw = (int) (h * 0.9), ph = (int) (h * 0.42);
        int px = (w - pw) / 2, py = (h - ph) / 2;
        g.setColor(new Color(0x0C0814));
        g.fillRect(px, py, pw, ph);
        g.setColor(new Color(0x9070B0));
        g.setStroke(new java.awt.BasicStroke(Math.max(2, h / 360f)));
        g.drawRect(px, py, pw, ph);

        line(g, title, w / 2, py + (int) (ph * 0.27), h * titleSize, new Color(0xE0C0FF));
        line(g, sub, w / 2, py + (int) (ph * 0.46), h * subSize, new Color(0xB8A0D8));

        int bw = (int) (pw * 0.30), bh = (int) (ph * 0.22), by = py + (int) (ph * 0.62);
        yes.setBounds(px + (int) (pw * 0.16), by, bw, bh);
        no.setBounds(px + pw - (int) (pw * 0.16) - bw, by, bw, bh);
        button(g, yes, yesLabel, hover == YES, h);
        button(g, no, noLabel, hover == NO, h);
    }

    private static void button(Graphics2D g, Rectangle r, String text, boolean hot, int h) {
        g.setColor(hot ? new Color(0x3A2860) : new Color(0x1A1030));
        g.fillRect(r.x, r.y, r.width, r.height);
        g.setColor(hot ? new Color(0xE0C0FF) : new Color(0x9070B0));
        g.drawRect(r.x, r.y, r.width, r.height);
        line(g, text, r.x + r.width / 2, r.y + (int) (r.height * 0.68), h * 0.04f, hot ? Color.WHITE : new Color(0xE0C0FF));
    }

    private static void line(Graphics2D g, String s, int cx, int baseline, float size, Color c) {
        g.setFont(g.getFont().deriveFont(Font.BOLD, size));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(c);
        g.drawString(s, cx - fm.stringWidth(s) / 2, baseline);
    }
}
