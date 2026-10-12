package com.lotusblight.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * The list of the twelve episodes: cards, three to a page, with arrows between the pages. Only the first one has a name
 * so far ("Полуночь в твоих кошмарах"); the others are numbered placeholders, and the pictures on the cards are
 * placeholders too. Nothing starts from here yet: the cards say the episode is not out.
 */
public final class EpisodesScreen extends Screen {
    private static final int COUNT = 12;
    private static final int PER_PAGE = 3;
    private static final int PAGES = COUNT / PER_PAGE;
    private static final String[] NAMES = new String[COUNT];
    private static int page;

    static {
        NAMES[0] = "Полуночь в твоих кошмарах";
    }

    private final Screen parent;
    private long noteUntil;
    private String note = "";

    public EpisodesScreen(Screen parent) {
        super(Component.literal("Эпизоды"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int gap = (int) (width * 0.03);
        int cardW = (int) (width * 0.22);
        int left = (width - (cardW * PER_PAGE + gap * (PER_PAGE - 1))) / 2;
        int top = (int) (height * 0.2);
        int cardH = (int) (height * 0.58);
        for (int i = 0; i < PER_PAGE; i++) {
            int index = page * PER_PAGE + i;
            int number = index + 1;
            String title = NAMES[index];
            Item art = index == 0 ? Items.CLOCK : Items.BARRIER;
            addRenderableWidget(new EpisodeCard(left + i * (cardW + gap), top, cardW, cardH, number, title, art, index == 0, b -> {
                note = "Эпизод " + number + " ещё не вышел";
                noteUntil = Util.getMillis() + 2500;
            }));
        }
        int arrowW = (int) (width * 0.05);
        int arrowH = (int) (height * 0.12);
        if (page > 0) {
            addRenderableWidget(new GlassButton(GlassButton.Kind.SMALL, left - gap - arrowW, top + cardH / 2 - arrowH / 2, arrowW, arrowH, "<", null, b -> turn(-1)));
        }
        if (page < PAGES - 1) {
            addRenderableWidget(new GlassButton(GlassButton.Kind.SMALL, left + cardW * PER_PAGE + gap * (PER_PAGE - 1) + gap, top + cardH / 2 - arrowH / 2, arrowW, arrowH, ">", null, b -> turn(1)));
        }
        addRenderableWidget(new GlassButton(GlassButton.Kind.SMALL, (int) (width * 0.42), (int) (height * 0.9), (int) (width * 0.16), (int) (height * 0.07), "Назад", null, b -> onClose()));
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
        float titleScale = Math.max(2f, height * 0.07f / font.lineHeight);
        float ty = height * 0.05f;
        MenuScene.text(g, font, MenuScene.spaced("ЭПИЗОДЫ"), width / 2f + titleScale * 0.5f, ty + titleScale * 0.5f, titleScale, 0x1A2A44);
        MenuScene.text(g, font, MenuScene.spaced("ЭПИЗОДЫ"), width / 2f, ty, titleScale, 0xE8F4FF);
        super.render(g, mouseX, mouseY, partial);
        MenuScene.text(g, font, "Страница " + (page + 1) + " из " + PAGES, width / 2f, height * 0.82f, 1f, 0x9FC0D8);
        if (Util.getMillis() < noteUntil) {
            MenuScene.text(g, font, note, width / 2f, height * 0.86f, 1.2f, 0xFFFFA0);
        }
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

    /** One episode card: a picture box (placeholder), the number, the name and a "soon" badge. */
    private static final class EpisodeCard extends Button {
        private final int number;
        private final String title;
        private final ItemStack art;
        private final boolean featured;

        EpisodeCard(int x, int y, int w, int h, int number, String title, Item art, boolean featured, OnPress press) {
            super(x, y, w, h, Component.literal("Эпизод " + number), press, DEFAULT_NARRATION);
            this.number = number;
            this.title = title;
            this.art = new ItemStack(art);
            this.featured = featured;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            Minecraft mc = Minecraft.getInstance();
            Font font = mc.font;
            int sw = mc.getWindow().getGuiScaledWidth();
            int sh = mc.getWindow().getGuiScaledHeight();
            boolean hot = isHoveredOrFocused();
            int x = getX();
            int y = getY();
            int x1 = x + width;
            int y1 = y + height;
            int accent = 0x9FD6FF;

            MenuScene.glass(g, x, y, width, height, sw, sh, hot);
            g.fillGradient(x, y, x1, y1, hot ? 0x40061A30 : 0x70061A30, hot ? 0x70020A14 : 0xA0020A14);
            g.fill(x, y, x1, y + 1, 0xFF000000 | accent);
            g.fill(x, y, x + 1, y1, 0xFF000000 | accent);
            g.fill(x, y1 - 1, x1, y1, 0xC8000000);
            g.fill(x1 - 1, y, x1, y1, 0xC8000000);
            if (hot) g.renderOutline(x - 1, y - 1, width + 2, height + 2, 0xFFFFFFFF);
            if (featured) {
                g.fill(x + 3, y + 3, x + 16, y + 4, 0xFF000000 | accent);
                g.fill(x + 3, y + 3, x + 4, y + 16, 0xFF000000 | accent);
                g.fill(x1 - 16, y1 - 4, x1 - 3, y1 - 3, 0xFF000000 | accent);
                g.fill(x1 - 4, y1 - 16, x1 - 3, y1 - 3, 0xFF000000 | accent);
            }

            int pad = Math.max(4, width / 14);
            int artX = x + pad;
            int artY = y + pad;
            int artW = width - pad * 2;
            int artH = (int) (height * 0.48);
            g.fillGradient(artX, artY, artX + artW, artY + artH, 0xB0081C32, 0xB0020810);
            g.renderOutline(artX, artY, artW, artH, 0xFF1C3A57);
            float itemScale = artH * 0.5f / 16f;
            var pose = g.pose();
            pose.pushPose();
            pose.translate(artX + artW / 2f - 8 * itemScale, artY + artH / 2f - 8 * itemScale, 0);
            pose.scale(itemScale, itemScale, 1);
            g.renderItem(art, 0, 0);
            pose.popPose();
            MenuScene.text(g, font, "ПЛЕЙСХОЛДЕР", artX + artW / 2f, artY + artH - font.lineHeight - 3, 0.75f, 0x4F7090);

            float ty = artY + artH + pad;
            MenuScene.text(g, font, "ЭПИЗОД " + number, x + width / 2f, ty, 1f, 0x9FC0D8);
            ty += font.lineHeight + 3;
            String shown = title != null ? title : "Без названия";
            float scale = Math.max(1f, Math.min(1.5f, width / 110f));
            List<FormattedCharSequence> lines = font.split(Component.literal(shown), (int) ((width - pad * 2) / scale));
            for (FormattedCharSequence line : lines) {
                pose.pushPose();
                pose.translate(x + width / 2f, ty, 0);
                pose.scale(scale, scale, 1);
                int lw = font.width(line);
                g.drawString(font, line, -lw / 2, 0, hot ? 0xFFFFFFA0 : 0xFFE8F2FA, true);
                pose.popPose();
                ty += font.lineHeight * scale + 1;
            }

            String badge = "СКОРО";
            int bw = font.width(badge) + 10;
            int bx = x + (width - bw) / 2;
            int by = y1 - pad - font.lineHeight - 4;
            g.fill(bx, by, bx + bw, by + font.lineHeight + 4, 0x90102840);
            g.renderOutline(bx, by, bw, font.lineHeight + 4, 0xFF3A6A94);
            g.drawString(font, badge, bx + 5, by + 3, 0xFF9FD0F0, false);
        }
    }
}
