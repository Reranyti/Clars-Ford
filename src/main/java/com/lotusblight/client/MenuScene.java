package com.lotusblight.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Small helpers shared by the title screen and the episode list: the background, glass and Minecraft-font text.
 * The background is the shader scene, unless the author put his own picture at `<game folder>/lotusblight/menu_background.png`
 * (any size; it is cropped to fill the screen and picked up again when the file changes).
 */
final class MenuScene {
    private static final ResourceLocation CUSTOM = new ResourceLocation("lotusblight", "dynamic/menu_custom_bg");
    private static long customStamp = -1;
    private static int customW;
    private static int customH;
    private static boolean customOk;
    private static long checkedAt;

    private MenuScene() {}

    /** Loads (or drops) the custom background picture; looked at most once a second. */
    private static void refreshCustom() {
        long now = System.currentTimeMillis();
        if (now - checkedAt < 1000) return;
        checkedAt = now;
        try {
            Path file = Minecraft.getInstance().gameDirectory.toPath().resolve("lotusblight").resolve("menu_background.png");
            if (!Files.isRegularFile(file)) {
                customOk = false;
                customStamp = -1;
                return;
            }
            long stamp = Files.getLastModifiedTime(file).toMillis() + Files.size(file);
            if (stamp == customStamp) return;
            try (InputStream in = Files.newInputStream(file)) {
                NativeImage image = NativeImage.read(in);
                customW = image.getWidth();
                customH = image.getHeight();
                DynamicTexture tex = new DynamicTexture(image);
                tex.setFilter(true, false);
                Minecraft.getInstance().getTextureManager().register(CUSTOM, tex);
            }
            customStamp = stamp;
            customOk = true;
        } catch (Exception e) {
            customOk = false;
        }
    }

    static boolean customBackground() {
        return customOk;
    }

    static void renderBack(GuiGraphics g, int sw, int sh) {
        refreshCustom();
        if (customOk) {
            float scale = Math.max(sw / (float) customW, sh / (float) customH);
            int srcW = Math.round(sw / scale);
            int srcH = Math.round(sh / scale);
            g.blit(CUSTOM, 0, 0, sw, sh, (customW - srcW) / 2f, (customH - srcH) / 2f, srcW, srcH, customW, customH);
        } else {
            g.fill(0, 0, sw, sh, 0xFF000000);
            g.fillGradient(0, 0, sw, sh / 2, 0x00000000, 0x28506E96);
        }
    }

    /** The dark glass behind a button (the backdrop is black now, so it is just a deep blue gradient). */
    static void glass(GuiGraphics g, int x, int y, int w, int h, int sw, int sh, boolean hot) {
        g.fillGradient(x, y, x + w, y + h, hot ? 0xE0102640 : 0xE00A1624, hot ? 0xE0071526 : 0xE0040A14);
    }

    /** Text with the game font, scaled and centred on cx, with Minecraft's drop shadow. */
    static void text(GuiGraphics g, Font font, String text, float cx, float y, float scale, int rgb) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx - font.width(text) * scale / 2f, y, 0);
        pose.scale(scale, scale, 1);
        g.drawString(font, text, 0, 0, 0xFF000000 | rgb, true);
        pose.popPose();
    }

    /** Spaces the letters out the way the old title did ("Э П И З О Д"). */
    static String spaced(String text) {
        return String.join(" ", text.split(""));
    }
}
