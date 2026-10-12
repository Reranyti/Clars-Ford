package com.lotusblight.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * "Выбор эпизода": a dark panel with the act and the overall readiness bar on top, three episode cards to a page (art
 * fills the whole card, number and name in the corner, a strip with the status at the bottom) and the pager below.
 * One page is one act of three episodes; four acts make the twelve. The pictures and the descriptions are placeholders.
 * Nothing starts from here yet.
 */
public final class EpisodesScreen extends Screen {
    private static final int COUNT = 12;
    private static final int PER_PAGE = 3;
    private static final int PAGES = COUNT / PER_PAGE;

    /** How far along an episode is. Colour and weight (share of "ready") of each: complete, in development, soon, not ready. */
    enum Status {
        DONE("Завершено", 0x4CAF7A, 1.0f),
        DEV("В разработке", 0x5AA0FF, 0.66f),
        SOON("Скоро", 0xE8C34A, 0.33f),
        NONE("Не готово", 0xE0534F, 0.0f);

        final String label;
        final int rgb;
        final float weight;

        Status(String label, int rgb, float weight) {
            this.label = label;
            this.rgb = rgb;
            this.weight = weight;
        }
    }

    /** The author sets these: the first episode is being made, nothing else has a script yet. */
    private static final Status[] STATUS = new Status[COUNT];
    private static final String[] NAMES = new String[COUNT];
    private static final String[] ROMAN = {"I", "II", "III", "IV"};
    private static int page;

    static {
        java.util.Arrays.fill(STATUS, Status.NONE);
        STATUS[0] = Status.DEV;
        NAMES[0] = "Полуночь в твоих кошмарах";
    }

    private final Screen parent;
    private long noteUntil;
    private String note = "";
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    public EpisodesScreen(Screen parent) {
        super(Component.literal("Выбор эпизода"));
        this.parent = parent;
    }

    private static int readiness() {
        float sum = 0;
        for (Status s : STATUS) sum += s.weight;
        return Math.round(sum / COUNT * 100);
    }

    @Override
    protected void init() {
        panelW = (int) (width * 0.78);
        panelH = (int) (height * 0.78);
        panelX = (width - panelW) / 2;
        panelY = (int) (height * 0.06);
        int pad = Math.max(8, panelW / 40);
        int gap = Math.max(6, panelW / 60);
        int cardsTop = panelY + (int) (panelH * 0.2);
        int cardsBottom = panelY + panelH - (int) (panelH * 0.1);
        int cardW = (panelW - pad * 2 - gap * (PER_PAGE - 1)) / PER_PAGE;
        for (int i = 0; i < PER_PAGE; i++) {
            int index = page * PER_PAGE + i;
            int number = index + 1;
            addRenderableWidget(new EpisodeCard(panelX + pad + i * (cardW + gap), cardsTop, cardW, cardsBottom - cardsTop, number, NAMES[index], STATUS[index], b -> {
                note = "Эпизод " + number + ": " + STATUS[number - 1].label.toLowerCase();
                noteUntil = Util.getMillis() + 2500;
            }));
        }
        int arrow = Math.max(14, (int) (height * 0.045));
        int pagerY = panelY + panelH - (int) (panelH * 0.075);
        int pagerRight = panelX + panelW - pad;
        if (page < PAGES - 1) addRenderableWidget(new GlassButton(GlassButton.Kind.SMALL, pagerRight - arrow, pagerY, arrow, arrow, ">", null, b -> turn(1)));
        if (page > 0) addRenderableWidget(new GlassButton(GlassButton.Kind.SMALL, pagerRight - arrow * 5, pagerY, arrow, arrow, "<", null, b -> turn(-1)));
        addRenderableWidget(new GlassButton(GlassButton.Kind.SMALL, (int) (width * 0.43), (int) (height * 0.9), (int) (width * 0.14), (int) (height * 0.065), "Назад", null, b -> onClose()));
    }

    private void turn(int delta) {
        page = Math.max(0, Math.min(PAGES - 1, page + delta));
        rebuildWidgets();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        // the scene is drawn in render()
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        MenuScene.renderBack(g, width, height);
        int pad = Math.max(8, panelW / 40);
        int x1 = panelX + panelW;
        int y1 = panelY + panelH;
        MenuScene.glass(g, panelX, panelY, panelW, panelH, width, height, false);
        g.fillGradient(panelX, panelY, x1, y1, 0xB00A1424, 0xD0050A14);
        g.fill(panelX, panelY, x1, panelY + 1, 0xFF9FD6FF);
        g.fill(panelX, panelY, panelX + 1, y1, 0xFF9FD6FF);
        g.fill(panelX, y1 - 1, x1, y1, 0xC8000000);
        g.fill(x1 - 1, panelY, x1, y1, 0xC8000000);

        float titleScale = Math.max(1.4f, height * 0.035f / font.lineHeight);
        drawLeft(g, "ВЫБОР ЭПИЗОДА", panelX + pad, panelY + pad, titleScale, 0xF0F4FA);
        drawLeft(g, "АКТ " + ROMAN[page], panelX + pad, panelY + pad + font.lineHeight * titleScale + 3, 1f, 0x7F8CA0);
        String ready = readiness() + "% · ОБЩАЯ ГОТОВНОСТЬ";
        float rs = Math.max(1f, titleScale * 0.55f);
        drawLeft(g, ready, x1 - pad - font.width(ready) * rs, panelY + pad + 2, rs, 0x8FA8FF);

        int barY = panelY + (int) (panelH * 0.165);
        int barH = Math.max(3, height / 200);
        int barW = panelW - pad * 2;
        int segGap = 2;
        int segW = (barW - segGap * (COUNT - 1)) / COUNT;
        for (int i = 0; i < COUNT; i++) {
            boolean here = i / PER_PAGE == page;
            int sx = panelX + pad + i * (segW + segGap);
            g.fill(sx, barY, sx + segW, barY + barH, (here ? 0xFF000000 : 0x70000000) | STATUS[i].rgb);
            if (here) g.fill(sx, barY - 1, sx + segW, barY, 0x40FFFFFF);
        }

        super.render(g, mouseX, mouseY, partial);

        int footY = y1 - (int) (panelH * 0.06);
        drawLeft(g, "АКТ " + String.format("%02d", page + 1) + " / " + String.format("%02d", PAGES), panelX + pad, footY, 1f, 0x7F8CA0);
        String pager = (page + 1) + " из " + PAGES;
        int arrow = Math.max(14, (int) (height * 0.045));
        drawLeft(g, pager, x1 - pad - arrow * 3 - font.width(pager) / 2f, footY, 1f, 0xE8F2FA);
        MenuScene.text(g, font, "Эпизоды v3", width - 40, height - 12, 0.8f, 0x607890);
        if (Util.getMillis() < noteUntil) {
            MenuScene.text(g, font, note, width / 2f, height * 0.865f, 1.1f, 0xFFFFA0);
        }
    }

    private void drawLeft(GuiGraphics g, String text, float x, float y, float scale, int rgb) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1);
        g.drawString(font, text, 0, 0, 0xFF000000 | rgb, true);
        pose.popPose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 262 && page < PAGES - 1) {
            turn(1);
            return true;
        }
        if (key == 263 && page > 0) {
            turn(-1);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    /** One episode card: art placeholder over the whole card, number and name in the corner, status strip at the bottom. */
    private static final class EpisodeCard extends Button {
        private final int number;
        private final String title;
        private final Status status;

        EpisodeCard(int x, int y, int w, int h, int number, String title, Status status, OnPress press) {
            super(x, y, w, h, Component.literal("Эпизод " + number), press, DEFAULT_NARRATION);
            this.number = number;
            this.title = title;
            this.status = status;
        }

        private static int mix(int a, int b, float t) {
            int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
            int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
            int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
            return (r << 16) | (g << 8) | bl;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            Font font = Minecraft.getInstance().font;
            boolean hot = isHoveredOrFocused();
            int x = getX();
            int y = getY();
            int x1 = x + width;
            int y1 = y + height;
            int pad = Math.max(5, width / 16);

            int top = mix(status.rgb, 0x0A1424, 0.45f);
            int bottom = mix(status.rgb, 0x0A1424, 0.75f);
            g.fillGradient(x, y, x1, y1, 0xFF000000 | top, 0xFF000000 | bottom);
            g.fill(x, y, x1, y + 1, 0xFF9FD6FF);
            g.fill(x, y, x + 1, y1, 0xFF9FD6FF);
            g.fill(x, y1 - 1, x1, y1, 0xC8000000);
            g.fill(x1 - 1, y, x1, y1, 0xC8000000);
            if (hot) g.renderOutline(x - 1, y - 1, width + 2, height + 2, 0xFFFFFFFF);

            // art placeholder in the middle: a diamond and its caption
            int cx = x + width / 2;
            int cy = y + height / 2 - font.lineHeight;
            int d = Math.max(4, width / 22);
            var pose = g.pose();
            pose.pushPose();
            pose.translate(cx, cy - font.lineHeight, 0);
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(45));
            g.renderOutline(-d, -d, d * 2, d * 2, 0xFFE8F2FA);
            pose.popPose();

            // number and name, top left
            float ts = Math.max(1f, Math.min(1.8f, width / 90f));
            pose.pushPose();
            pose.translate(x + pad, y + pad, 0);
            pose.scale(0.85f, 0.85f, 1);
            g.drawString(font, "ЭПИЗОД " + String.format("%02d", number), 0, 0, 0xFFF0F4FA, true);
            pose.popPose();
            float ty = y + pad + font.lineHeight * 0.85f + 3;
            String shown = title != null ? title.toUpperCase() : "БЕЗ НАЗВАНИЯ";
            List<FormattedCharSequence> lines = font.split(Component.literal(shown), (int) ((width - pad * 2) / ts));
            for (FormattedCharSequence line : lines) {
                pose.pushPose();
                pose.translate(x + pad, ty, 0);
                pose.scale(ts, ts, 1);
                g.drawString(font, line, 0, 0, hot ? 0xFFFFFFA0 : 0xFFFFFFFF, true);
                pose.popPose();
                ty += font.lineHeight * ts + 1;
            }

            // bottom strip: description (placeholder) and status
            int stripH = Math.max(font.lineHeight * 2 + pad + 2, height / 5);
            g.fill(x + 1, y1 - stripH, x1 - 1, y1 - 1, 0xC0080C14);
            g.fill(x + 1, y1 - stripH, x1 - 1, y1 - stripH + 1, 0x409FD6FF);
            g.drawString(font, "Описание будет позже", x + pad, y1 - stripH + pad / 2 + 1, 0xFFB8C8DA, true);
            g.fill(x + pad, y1 - pad - font.lineHeight + 2, x + pad + 3, y1 - pad + 1, 0xFF000000 | status.rgb);
            g.drawString(font, status.label.toUpperCase(), x + pad + 7, y1 - pad - font.lineHeight + 2, 0xFF000000 | status.rgb, true);
        }
    }
}
