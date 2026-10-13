package com.lotusblight.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import com.mojang.math.Axis;
import net.minecraft.resources.ResourceLocation;

/**
 * A button of the episodes menu in the game's own style: the game font with its drop shadow, the game's item icons, a
 * beveled frame, a white outline on hover. Every kind has its own frame decoration, so they are not six copies of one
 * rectangle. The background shows through as blurred glass (see menu_scene.fsh).
 */
final class GlassButton extends Button {
    enum Kind { CONTINUE, EPISODES, ENDINGS, MODS, SETTINGS, EXIT, SMALL }

    private final Kind kind;
    private final String text;
    private final String iconKind;
    /** Hanging on a thread: the button swings about its top centre by this angle (radians); a broken button is not drawn. */
    float swing;
    boolean broken;

    GlassButton(Kind kind, int x, int y, int w, int h, String text, String iconKind, OnPress press) {
        super(x, y, w, h, Component.literal(text), press, DEFAULT_NARRATION);
        this.kind = kind;
        this.text = text;
        this.iconKind = iconKind;
    }

    private static int argb(int alpha, int rgb) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | rgb;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        double t = Util.getMillis() / 1000.0;
        boolean hot = isHoveredOrFocused();
        boolean danger = kind == Kind.EXIT;
        int x = getX();
        int y = getY();
        int w = width;
        int h = height;
        int x1 = x + w;
        int y1 = y + h;
        int accent = danger ? 0xFF7878 : 0x9FD6FF;

        if (broken) return;
        g.pose().pushPose();
        if (swing != 0) {
            g.pose().translate(x + w / 2f, y, 0);
            g.pose().mulPose(Axis.ZP.rotation(swing));
            g.pose().translate(-(x + w / 2f), -y, 0);
        }
        MenuScene.glass(g, x, y, w, h, sw, sh, hot);
        g.fillGradient(x, y, x1, y1, argb(hot ? 0x40 : 0x70, danger ? 0x2A0A0E : 0x06182C), argb(hot ? 0x70 : 0xA0, danger ? 0x12040A : 0x020A14));

        // bevel: light on the top-left edges, dark on the bottom-right ones
        g.fill(x, y, x1, y + 1, argb(hot ? 255 : 170, accent));
        g.fill(x, y, x + 1, y1, argb(hot ? 255 : 170, accent));
        g.fill(x, y1 - 1, x1, y1, argb(200, 0x000000));
        g.fill(x1 - 1, y, x1, y1, argb(200, 0x000000));
        g.fill(x + 1, y1 - 2, x1 - 1, y1 - 1, argb(90, 0x000000));
        if (hot) {
            g.renderOutline(x - 1, y - 1, w + 2, h + 2, 0xFFFFFFFF);
        }

        switch (kind) {
            case CONTINUE -> decorateContinue(g, x, y, w, h, accent, t, hot);
            case EPISODES -> {
                g.fill(x + 2, y + 2, x + 5, y1 - 2, argb(255, accent));
                for (int i = 0; i < 3; i++) g.fill(x + 8, y + h / 2 - 7 + i * 6, x + 8 + (i == 1 ? 10 : 5), y + h / 2 - 5 + i * 6, argb(170, accent));
            }
            case ENDINGS -> g.renderOutline(x + 4, y + 4, w - 8, h - 8, argb(hot ? 200 : 90, accent));
            case MODS -> {
                for (int i = x + 4; i < x1 - 4; i += 6) g.fill(i, y1 - 5, i + 3, y1 - 3, argb(hot ? 230 : 120, accent));
            }
            case SETTINGS -> {
                for (int i = x + 6; i < x1 - 4; i += 8) g.fill(i, y + 2, i + 2, y + 5, argb(hot ? 230 : 130, accent));
            }
            case EXIT -> {
                for (int i = 0; i < 5; i++) g.fill(x + 4 + i * 4, y + 3, x + 7 + i * 4, y + 4, argb(200, accent));
                g.fill(x + w / 2 - 16, y1 - 4, x + w / 2 + 16, y1 - 3, argb(hot ? 255 : 160, accent));
            }
            default -> { }
        }

        drawContent(g, mc.font, x, y, w, h, hot, danger, t);
        g.pose().popPose();
    }

    private void decorateContinue(GuiGraphics g, int x, int y, int w, int h, int accent, double t, boolean hot) {
        int len = Math.max(8, Math.min(w / 8, 22));
        g.fill(x - 2, y - 2, x + len, y - 1, argb(255, accent));
        g.fill(x - 2, y - 2, x - 1, y + len, argb(255, accent));
        g.fill(x + w - len, y + h + 1, x + w + 2, y + h + 2, argb(255, accent));
        g.fill(x + w + 1, y + h - len, x + w + 2, y + h + 2, argb(255, accent));
        g.enableScissor(x, y, x + w, y + h);
        int sweep = (int) (((t * 0.22) % 1.4 - 0.2) * w);
        g.fillGradient(x + sweep, y, x + sweep + 16, y + h, 0x00BFE6FF, hot ? 0x40BFE6FF : 0x26BFE6FF);
        g.fillGradient(x + sweep + 16, y, x + sweep + 32, y + h, hot ? 0x40BFE6FF : 0x26BFE6FF, 0x00BFE6FF);
        g.disableScissor();
    }

    private void drawContent(GuiGraphics g, Font font, int x, int y, int w, int h, boolean hot, boolean danger, double t) {
        ResourceLocation icon = MenuIcons.get(iconKind);
        boolean hasIcon = icon != null;
        boolean iconRight = kind == Kind.ENDINGS || kind == Kind.SETTINGS;
        float iconBox = h * (kind == Kind.CONTINUE ? 0.5f : 0.55f);
        float cy = y + h / 2f;
        float textCx = x + w / 2f;
        float room = w * 0.9f;
        if (hasIcon) {
            float icx = iconRight ? x + w - h * 0.2f - iconBox : x + h * 0.2f;
            var pose = g.pose();
            pose.pushPose();
            pose.translate(icx + iconBox / 2f, cy, 0);
            if (kind == Kind.SETTINGS) pose.mulPose(Axis.ZP.rotationDegrees((float) (t * (hot ? 60 : 14))));
            float alpha = hot ? 1f : 0.8f;
            if (kind == Kind.CONTINUE) alpha *= 0.75f + 0.25f * (float) Math.sin(t * 2.2);
            int tint = danger ? 0xFFB0B0 : 0xBFE6FF;
            g.setColor(((tint >> 16) & 255) / 255f, ((tint >> 8) & 255) / 255f, (tint & 255) / 255f, alpha);
            g.blit(icon, Math.round(-iconBox / 2f), Math.round(-iconBox / 2f), Math.round(iconBox), Math.round(iconBox), 0, 0, 128, 128, 128, 128);
            g.setColor(1, 1, 1, 1);
            pose.popPose();
            float taken = h * 0.2f * 2 + iconBox;
            room = (w - taken) * 0.92f;
            textCx = iconRight ? x + (w - taken) / 2f + h * 0.1f : x + taken + (w - taken) / 2f - h * 0.1f;
        }
        float scale = Math.max(1f, h * (kind == Kind.CONTINUE ? 0.36f : 0.30f) / font.lineHeight);
        scale = Math.min(scale, room / Math.max(1, font.width(text)));
        int rgb = hot ? (danger ? 0xFF9090 : 0xFFFFA0) : (danger ? 0xE6B0B0 : 0xE8F2FA);
        MenuScene.text(g, font, text, textCx, cy - font.lineHeight * scale / 2f, scale, rgb);
    }
}
