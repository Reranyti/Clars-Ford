package com.lotusblight.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;

import java.util.Comparator;
import java.util.Random;

/**
 * The title screen of the episodic game: a night sky over an ocean (everything is drawn by code, no textures),
 * and the menu laid out the way the author sketched it. The ordinary Minecraft menu stays one click away
 * (the small "Меню" button) and EpisodeMenu.allowVanilla keeps it for the rest of the session.
 */
public final class EpisodeMenuScreen extends Screen {
    /** The sketch's frame is 1214x630; all rectangles are given in percent of it. */
    private static final int STARS = 140;
    private static final int WATER_LINES = 46;

    private final float[] starX = new float[STARS];
    private final float[] starY = new float[STARS];
    private final float[] starPhase = new float[STARS];
    private final float[] starSpeed = new float[STARS];
    private final float[] starDepth = new float[STARS];

    private long soonUntil;

    public EpisodeMenuScreen() {
        super(Component.literal("LOTOS"));
        Random random = new Random(7);
        for (int i = 0; i < STARS; i++) {
            starX[i] = random.nextFloat();
            starY[i] = random.nextFloat() * 0.52f;
            starPhase[i] = random.nextFloat() * 6.28f;
            starSpeed[i] = random.nextFloat() * 1.5f + 0.5f;
            starDepth[i] = random.nextFloat() * 1.4f + 0.3f;
        }
    }

    @Override
    protected void init() {
        add("Меню", 0.5, 1.1, 6.1, 4, true, false, b -> EpisodeMenu.openVanilla());
        add("Продолжить", 33.4, 31.4, 36.5, 14.6, false, false, b -> continueGame());
        add("Эпизоды", 20.3, 49.8, 25.1, 11, false, false, b -> soonUntil = Util.getMillis() + 2500);
        add("Концовки", 66.8, 49.5, 32.7, 10.9, false, false, b -> minecraft.setScreen(new EndingsScreen(this)));
        add("Настройки", 84.7, 62.7, 17.5, 11.1, false, false, b -> minecraft.setScreen(new OptionsScreen(this, minecraft.options)));
        add("Моды", 1.7, 68.6, 23.7, 9.5, false, false, b -> minecraft.setScreen(new net.minecraftforge.client.gui.ModListScreen(this)));
        add("Выйти из симуляции", 35.3, 89.5, 34.2, 8.6, false, true, b -> minecraft.stop());
    }

    private void add(String label, double left, double top, double w, double h, boolean small, boolean danger, Button.OnPress press) {
        int x = (int) Math.round(width * left / 100.0);
        int y = (int) Math.round(height * top / 100.0);
        int bw = (int) Math.round(width * w / 100.0);
        int bh = (int) Math.round(height * h / 100.0);
        // a button that runs past the screen edge (the sketch's settings button) is only as wide as what is visible
        bw = Math.min(bw, width - x);
        addRenderableWidget(new MenuButton(x, y, bw, bh, label, small, danger, press));
    }

    private void continueGame() {
        Minecraft mc = Minecraft.getInstance();
        try {
            mc.getLevelSource().loadLevelSummaries(mc.getLevelSource().findLevelCandidates()).thenAccept(list -> {
                LevelSummary last = list.stream().max(Comparator.comparingLong(LevelSummary::getLastPlayed)).orElse(null);
                mc.execute(() -> {
                    if (last == null || last.isDisabled()) {
                        mc.setScreen(new SelectWorldScreen(this));
                    } else {
                        mc.createWorldOpenFlows().loadLevel(this, last.getLevelId());
                    }
                });
            });
        } catch (Exception e) {
            mc.setScreen(new SelectWorldScreen(this));
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        double t = Util.getMillis() / 1000.0;
        drawScene(g, t);
        drawTitle(g);
        super.render(g, mouseX, mouseY, partial);
        if (Util.getMillis() < soonUntil) {
            g.drawCenteredString(font, "Эпизоды появятся позже", width / 2, height * 47 / 100 + 2, 0x9FD0F0);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        // the scene is drawn in render(), nothing from the vanilla panorama
    }

    private void drawScene(GuiGraphics g, double t) {
        int hz = (int) (height * 0.52);
        int drift = (int) (Math.sin(t * 0.07) * width * 0.012);
        g.fillGradient(0, 0, width, hz, 0xFF01030A, 0xFF12406A);
        for (int i = 0; i < STARS; i++) {
            double a = 0.45 + 0.55 * Math.sin(t * starSpeed[i] + starPhase[i]);
            int alpha = (int) (a * 255);
            int sx = (int) ((((starX[i] * width + drift * starDepth[i]) % width) + width) % width);
            g.fill(sx, (int) (starY[i] * height), sx + 1, (int) (starY[i] * height) + 1, (alpha << 24) | 0xD2E8FF);
        }
        int mx = (int) (width * 0.66) + drift * 2;
        int my = (int) (height * 0.2);
        int mr = Math.max(4, (int) (height * 0.07));
        for (int k = 5; k >= 1; k--) {
            disc(g, mx, my, mr + k * mr / 2, ((12 * (6 - k)) << 24) | 0xAAD7FF);
        }
        disc(g, mx, my, mr, 0xFFE8F4FF);
        disc(g, mx - mr * 3 / 10, my - mr / 5, Math.max(1, mr * 28 / 100), 0x5978A0C8);

        g.fillGradient(0, hz, width, hz + (height - hz) / 3, 0xFF0E3B5E, 0xFF052237);
        g.fillGradient(0, hz + (height - hz) / 3, width, height, 0xFF052237, 0xFF010608);
        for (int i = 0; i < WATER_LINES; i++) {
            double k = i / (double) WATER_LINES;
            int y = hz + (int) ((height - hz) * k * k);
            double len = Math.max(6, (1 - Math.abs(Math.sin(t * 0.6 + i * 1.7)) * 0.5) * width * (0.015 + 0.07 * k));
            int off = (int) (Math.sin(t * 0.9 + i * 2.1) * width * 0.01 * (1 + k * 2));
            int alpha = (int) (140 * (1 - k * 0.6));
            int x0 = mx + off - (int) (len / 2);
            g.fill(x0, y, x0 + (int) len, y + 1 + (int) (k * 2), (alpha << 24) | 0xBEE2FF);
        }
        g.fill(0, hz - 1, width, hz + 1, 0x1F78BEEB);
        g.fillGradient(0, 0, width, height, 0x00000000, 0x66000000);
    }

    private static void disc(GuiGraphics g, int cx, int cy, int r, int argb) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt((double) r * r - (double) dy * dy);
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, argb);
        }
    }

    private void drawTitle(GuiGraphics g) {
        spaced(g, font, "LOTOS", width / 2, (int) (height * 0.09), 5, 0xEEF7FF, 2);
        spaced(g, font, "ЭПИЗОДНАЯ ИСТОРИЯ", width / 2, (int) (height * 0.09) + 22, 3, 0x7FA6C2, 1);
    }

    /** Draws the text centred on cx with extra space between letters. */
    static void spaced(GuiGraphics g, Font font, String text, int cx, int y, int gap, int rgb, int scale) {
        int total = 0;
        for (char ch : text.toCharArray()) total += font.width(String.valueOf(ch)) * scale + gap;
        total -= gap;
        int x = cx - total / 2;
        var pose = g.pose();
        for (char ch : text.toCharArray()) {
            String s = String.valueOf(ch);
            pose.pushPose();
            pose.translate(x, y, 0);
            pose.scale(scale, scale, 1);
            g.drawString(font, s, 0, 0, 0xFF000000 | rgb, false);
            pose.popPose();
            x += font.width(s) * scale + gap;
        }
    }

    private static final class MenuButton extends Button {
        private final boolean small;
        private final boolean danger;

        MenuButton(int x, int y, int w, int h, String label, boolean small, boolean danger, OnPress press) {
            super(x, y, w, h, Component.literal(label), press, DEFAULT_NARRATION);
            this.small = small;
            this.danger = danger;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            boolean hot = isHoveredOrFocused();
            int x1 = getX() + width;
            int y1 = getY() + height;
            int edge = danger ? (hot ? 0xFFFFB0B0 : 0x80C87878) : (hot ? 0xFFBFE6FF : 0x738CC8F0);
            int topFill = hot ? 0x8C286496 : 0x730E2842;
            int botFill = hot ? 0xB308324E : 0x99040E1A;
            g.fillGradient(getX(), getY(), x1, y1, topFill, botFill);
            g.renderOutline(getX(), getY(), width, height, edge);
            if (hot) {
                g.renderOutline(getX() - 1, getY() - 1, width + 2, height + 2, danger ? 0x55FF7878 : 0x5578C8FF);
            }
            Font font = Minecraft.getInstance().font;
            int rgb = danger ? (hot ? 0xFFFFFF : 0xE6B8B8) : (hot ? 0xFFFFFF : 0xD6ECFA);
            int scale = small ? 1 : (height > 36 ? 2 : 1);
            int gap = small ? 1 : (hot ? 4 : 3);
            String text = getMessage().getString().toUpperCase();
            int textH = font.lineHeight * scale;
            spaced(g, font, text, getX() + width / 2, getY() + (height - textH) / 2 + 1, gap, rgb, scale);
        }
    }
}
