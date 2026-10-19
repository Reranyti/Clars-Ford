package com.lotusblight.client;

import net.minecraft.client.gui.GuiGraphics;

import java.util.Random;

/**
 * The moving background of an episode's own menu. Each episode gets its own scene here; the first, "Полуночь в твоих кошмарах",
 * is a midnight sky: a deep blue gradient, twinkling stars, a pale square moon drifting very slowly, banks of mist.
 * Everything is a function of time, so a screen only calls {@link #render}.
 */
final class EpisodeBackdrop {
    private static final int STARS = 120;
    private final float[] sx = new float[STARS];
    private final float[] sy = new float[STARS];
    private final float[] sp = new float[STARS];
    private final float[] ss = new float[STARS];

    EpisodeBackdrop(int episode) {
        Random r = new Random(1000L + episode);
        for (int i = 0; i < STARS; i++) {
            sx[i] = r.nextFloat();
            sy[i] = r.nextFloat() * 0.7f;
            sp[i] = r.nextFloat() * 6.28f;
            ss[i] = 0.6f + r.nextFloat() * 1.4f;
        }
    }

    void render(GuiGraphics g, int w, int h, double t) {
        g.fillGradient(0, 0, w, h, 0xFF02060F, 0xFF0A2038);
        g.fillGradient(0, (int) (h * 0.55), w, h, 0x00000000, 0x6020507A);
        for (int i = 0; i < STARS; i++) {
            float tw = 0.5f + 0.5f * (float) Math.sin(t * ss[i] + sp[i]);
            int a = (int) (60 + 170 * tw);
            int x = (int) (sx[i] * w), y = (int) (sy[i] * h);
            int c = (a << 24) | (i % 7 == 0 ? 0xBFD8FF : 0xFFFFFF);
            g.fill(x, y, x + 1 + (i % 11 == 0 ? 1 : 0), y + 1 + (i % 11 == 0 ? 1 : 0), c);
        }
        // the moon: a pale square, with its own few lighter squares, drifting across slowly
        float mx = (float) (w * (0.18 + 0.66 * (0.5 + 0.5 * Math.sin(t * 0.03))));
        float my = h * 0.16f + (float) (Math.sin(t * 0.05) * h * 0.02);
        int ms = Math.max(10, (int) (h * 0.075));
        g.fillGradient((int) mx - ms * 2, (int) my - ms * 2, (int) mx + ms * 3, (int) my + ms * 3, 0x00203050, 0x18406088);
        g.fill((int) mx, (int) my, (int) mx + ms, (int) my + ms, 0xFFEAF4FF);
        g.fill((int) mx + ms / 5, (int) my + ms / 5, (int) mx + ms / 2, (int) my + ms / 2, 0xFFB8D0EC);
        g.fill((int) mx + ms / 2, (int) my + ms * 3 / 5, (int) mx + ms * 4 / 5, (int) my + ms * 4 / 5, 0xFFC4D8F0);
        // mist: soft long bands that slide along the bottom
        for (int k = 0; k < 3; k++) {
            float off = (float) ((t * (6 + k * 3) + k * 400) % (w + 600)) - 300;
            int bh = (int) (h * (0.05 + 0.02 * k));
            int by = (int) (h * (0.62 + 0.11 * k));
            g.fillGradient((int) off, by, (int) off + 600, by + bh, 0x00304A6A, 0x2A3E6288);
            g.fillGradient((int) off, by + bh, (int) off + 600, by + bh * 2, 0x2A3E6288, 0x00304A6A);
        }
    }
}
