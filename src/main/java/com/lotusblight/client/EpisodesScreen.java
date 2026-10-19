package com.lotusblight.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * The episode picker: three big cards to a page (one page is one act, four acts make the twelve), the scene darkened behind
 * them and a readiness bar on top. The cards fly in one after another with a whoosh; picking one sends the cards up and
 * opens that episode's own menu ({@link EpisodeScreen}, a placeholder for now). The art on the cards is a placeholder.
 */
public final class EpisodesScreen extends Screen {
    private static final int COUNT = 12;
    private static final int PER_PAGE = 3;
    private static final int PAGES = COUNT / PER_PAGE;
    private static final long FLY_MS = 480;
    private static final long STAGGER_MS = 150;

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
    static final Status[] STATUS = new Status[COUNT];
    static final String[] NAMES = new String[COUNT];
    private static final String[] ROMAN = {"I", "II", "III", "IV"};
    private static int page;

    static {
        java.util.Arrays.fill(STATUS, Status.NONE);
        STATUS[0] = Status.DEV;
        NAMES[0] = "Полуночь в твоих кошмарах";
    }

    private final Screen parent;
    private final List<EpisodeCard> cards = new ArrayList<>();
    private final boolean[] whooshed = new boolean[PER_PAGE];
    private long enterAt;
    private long leaveAt;
    private int leaving = -1;

    public EpisodesScreen(Screen parent) {
        super(Component.literal("Эпизоды"));
        this.parent = parent;
    }

    private static int readiness() {
        float sum = 0;
        for (Status s : STATUS) sum += s.weight;
        return Math.round(sum / COUNT * 100);
    }

    @Override
    protected void init() {
        cards.clear();
        leaving = -1;               // coming back from an episode's menu: the cards are not "flying away" any more (it opened that menu again at once)
        enterAt = Util.getMillis();
        java.util.Arrays.fill(whooshed, false);
        int gap = (int) (width * 0.025);
        int cardW = (int) (width * 0.27);
        int left = (width - (cardW * PER_PAGE + gap * (PER_PAGE - 1))) / 2;
        int top = (int) (height * 0.2);
        int cardH = (int) (height * 0.62);
        for (int i = 0; i < PER_PAGE; i++) {
            int index = page * PER_PAGE + i;
            EpisodeCard card = new EpisodeCard(left + i * (cardW + gap), top, cardW, cardH, index + 1, NAMES[index], STATUS[index], this::pick);
            cards.add(card);
            addRenderableWidget(card);
        }
        int arrowW = (int) (width * 0.04);
        int arrowH = (int) (height * 0.14);
        int arrowY = top + cardH / 2 - arrowH / 2;
        if (page > 0) addRenderableWidget(new GlassButton(GlassButton.Kind.SMALL, Math.max(2, left - gap - arrowW), arrowY, arrowW, arrowH, "<", null, b -> turn(-1)));
        if (page < PAGES - 1) addRenderableWidget(new GlassButton(GlassButton.Kind.SMALL, Math.min(width - arrowW - 2, left + cardW * PER_PAGE + gap * (PER_PAGE - 1) + gap), arrowY, arrowW, arrowH, ">", null, b -> turn(1)));
        addRenderableWidget(new GlassButton(GlassButton.Kind.SMALL, (int) (width * 0.43), (int) (height * 0.9), (int) (width * 0.14), (int) (height * 0.065), "Назад", null, b -> onClose()));
    }

    private void pick(EpisodeCard card) {
        if (leaving >= 0) return;
        leaving = card.number;
        leaveAt = Util.getMillis();
        whoosh(0.8f);
    }

    private void turn(int delta) {
        if (leaving >= 0) return;
        page = Math.max(0, Math.min(PAGES - 1, page + delta));
        rebuildWidgets();
    }

    private static void whoosh(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_ATTACK_SWEEP, pitch, 0.6f));
    }

    @Override
    public void onClose() {
        if (leaving >= 0) return;
        minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        // the scene is drawn in render()
    }

    private static float easeOut(float t) {
        float u = 1 - Math.max(0, Math.min(1, t));
        return 1 - u * u * u;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        long now = Util.getMillis();
        MenuScene.renderBack(g, width, height);
        g.fill(0, 0, width, height, 0xA6000208);

        // cards: fly in one after another, or fly up after a pick
        float lt = leaving >= 0 ? (now - leaveAt) / (float) FLY_MS : 0;
        for (int i = 0; i < cards.size(); i++) {
            EpisodeCard card = cards.get(i);
            float t = (now - enterAt - i * STAGGER_MS) / (float) FLY_MS;
            if (t >= 0 && !whooshed[i]) {
                whooshed[i] = true;
                whoosh(1.15f - i * 0.12f);
            }
            float offset = (1 - easeOut(t)) * height * 0.8f;
            if (leaving >= 0) offset = -lt * lt * height * 1.1f - i * 12 * lt;
            card.setY(card.baseY + Math.round(offset));
        }
        if (leaving >= 0 && lt >= 1f) {
            int n = leaving;
            if (STATUS[n - 1] != Status.NONE) minecraft.setScreen(new SaveSlotsScreen(this, n, NAMES[n - 1]));       // an episode that is being made opens its files
            else minecraft.setScreen(new EpisodeScreen(this, n, NAMES[n - 1], STATUS[n - 1]));
            return;
        }

        int pad = (int) (width * 0.07);
        float titleScale = Math.max(1.4f, height * 0.035f / font.lineHeight);
        float headFade = leaving >= 0 ? Math.max(0f, 1 - lt * 1.5f) : 1f;
        int ha = ((int) (255 * headFade)) << 24;
        if (headFade > 0.02f) {
            drawLeft(g, "ВЫБОР ЭПИЗОДА", pad, height * 0.05f, titleScale, ha, 0xF0F4FA);
            drawLeft(g, "АКТ " + ROMAN[page], pad, height * 0.05f + font.lineHeight * titleScale + 3, 1f, ha, 0x7F8CA0);
            String ready = readiness() + "% · ОБЩАЯ ГОТОВНОСТЬ";
            float rs = Math.max(1f, titleScale * 0.55f);
            drawLeft(g, ready, width - pad - font.width(ready) * rs, height * 0.05f + 2, rs, ha, 0x8FA8FF);

            int barY = (int) (height * 0.14);
            int barH = Math.max(3, height / 190);
            int barW = width - pad * 2;
            int segGap = 3;
            int segW = (barW - segGap * (COUNT - 1)) / COUNT;
            for (int i = 0; i < COUNT; i++) {
                boolean here = i / PER_PAGE == page;
                int sx = pad + i * (segW + segGap);
                int a = (int) ((here ? 255 : 120) * headFade);
                g.fill(sx, barY, sx + segW, barY + barH, (a << 24) | STATUS[i].rgb);
                if (here) g.fill(sx, barY - 1, sx + segW, barY, ((int) (64 * headFade) << 24) | 0xFFFFFF);
            }
        }

        super.render(g, mouseX, mouseY, partial);

        if (headFade > 0.02f) {
            String pager = "АКТ " + String.format("%02d", page + 1) + " / " + String.format("%02d", PAGES);
            drawLeft(g, pager, width / 2f - font.width(pager) / 2f, height * 0.855f, 1f, ha, 0x9FB0C4);
            MenuScene.text(g, font, "Эпизоды v4", width - 40, height - 12, 0.8f, 0x607890);
        }
    }

    private void drawLeft(GuiGraphics g, String text, float x, float y, float scale, int alphaBits, int rgb) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1);
        g.drawString(font, text, 0, 0, alphaBits | rgb, true);
        pose.popPose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 262) {
            turn(1);
            return true;
        }
        if (key == 263) {
            turn(-1);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    /** One big episode card: art placeholder over the whole card, number and name in the corner, status strip at the bottom. */
    private static final class EpisodeCard extends Button {
        final int number;
        final int baseY;
        private final String title;
        private final Status status;
        private final java.util.function.Consumer<EpisodeCard> onPick;

        EpisodeCard(int x, int y, int w, int h, int number, String title, Status status, java.util.function.Consumer<EpisodeCard> onPick) {
            super(x, y, w, h, Component.literal("Эпизод " + number), b -> { }, DEFAULT_NARRATION);
            this.number = number;
            this.baseY = y;
            this.title = title;
            this.status = status;
            this.onPick = onPick;
        }

        @Override
        public void onPress() {
            onPick.accept(this);
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
            int y = getY() - (hot ? 4 : 0);
            int x1 = x + width;
            int y1 = y + height;
            int pad = Math.max(6, width / 16);

            int top = mix(status.rgb, 0x0A1424, hot ? 0.3f : 0.45f);
            int bottom = mix(status.rgb, 0x0A1424, 0.75f);
            g.fillGradient(x, y, x1, y1, 0xFF000000 | top, 0xFF000000 | bottom);
            g.fill(x, y, x1, y + 1, 0xFF9FD6FF);
            g.fill(x, y, x + 1, y1, 0xFF9FD6FF);
            g.fill(x, y1 - 1, x1, y1, 0xC8000000);
            g.fill(x1 - 1, y, x1, y1, 0xC8000000);
            if (hot) g.renderOutline(x - 1, y - 1, width + 2, height + 2, 0xFFFFFFFF);

            // art placeholder: a diamond in the middle
            int cx = x + width / 2;
            int cy = y + height / 2;
            int d = Math.max(5, width / 14);
            var pose = g.pose();
            pose.pushPose();
            pose.translate(cx, cy, 0);
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(45));
            g.renderOutline(-d, -d, d * 2, d * 2, 0xFFE8F2FA);
            pose.popPose();

            // number and name, top left
            float ts = Math.max(1f, Math.min(2.2f, width / 80f));
            g.drawString(font, "ЭПИЗОД " + String.format("%02d", number), x + pad, y + pad, 0xFFF0F4FA, true);
            float ty = y + pad + font.lineHeight + 4;
            String shown = title != null ? title.toUpperCase() : "БЕЗ НАЗВАНИЯ";
            List<FormattedCharSequence> lines = font.split(Component.literal(shown), (int) ((width - pad * 2) / ts));
            for (FormattedCharSequence line : lines) {
                pose.pushPose();
                pose.translate(x + pad, ty, 0);
                pose.scale(ts, ts, 1);
                g.drawString(font, line, 0, 0, hot ? 0xFFFFFFA0 : 0xFFFFFFFF, true);
                pose.popPose();
                ty += font.lineHeight * ts + 2;
            }

            // bottom strip: description (placeholder) and status
            int stripH = Math.max(font.lineHeight * 2 + pad + 4, height / 6);
            g.fill(x + 1, y1 - stripH, x1 - 1, y1 - 1, 0xC0080C14);
            g.fill(x + 1, y1 - stripH, x1 - 1, y1 - stripH + 1, 0x409FD6FF);
            g.drawString(font, "Описание будет позже", x + pad, y1 - stripH + pad / 2 + 1, 0xFFB8C8DA, true);
            g.fill(x + pad, y1 - pad - font.lineHeight + 2, x + pad + 3, y1 - pad + 1, 0xFF000000 | status.rgb);
            g.drawString(font, status.label.toUpperCase(), x + pad + 7, y1 - pad - font.lineHeight + 2, 0xFF000000 | status.rgb, true);
        }
    }
}
