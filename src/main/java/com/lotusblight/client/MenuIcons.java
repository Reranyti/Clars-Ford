package com.lotusblight.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/** Thin white line icons for the menu buttons (play, episodes, endings, mods, settings, power), drawn once with anti-aliasing. */
final class MenuIcons {
    private static final int SIZE = 128;
    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();

    private MenuIcons() {}

    /** The icon's texture (SIZE x SIZE, white on transparent), or null if it can't be drawn on this machine. */
    static ResourceLocation get(String kind) {
        if (kind == null) return null;
        if (CACHE.containsKey(kind)) return CACHE.get(kind);
        ResourceLocation rl = null;
        try {
            BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
            var g2 = image.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            draw(g2, kind, SIZE / 2.0);
            g2.dispose();
            NativeImage out = new NativeImage(NativeImage.Format.RGBA, SIZE, SIZE, true);
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    int a = (image.getRGB(x, y) >>> 24) & 255;
                    out.setPixelRGBA(x, y, (a << 24) | 0xFFFFFF);
                }
            }
            DynamicTexture tex = new DynamicTexture(out);
            tex.setFilter(true, false);
            rl = new ResourceLocation("lotusblight", "dynamic/icon_" + kind);
            Minecraft.getInstance().getTextureManager().register(rl, tex);
        } catch (Throwable ignored) {
        }
        CACHE.put(kind, rl);
        return rl;
    }

    private static void draw(java.awt.Graphics2D g2, String kind, double c) {
        switch (kind) {
            case "play" -> {
                Path2D tri = new Path2D.Double();
                tri.moveTo(40, 24);
                tri.lineTo(100, 64);
                tri.lineTo(40, 104);
                tri.closePath();
                g2.draw(tri);
                g2.setColor(new Color(255, 255, 255, 70));
                g2.fill(tri);
            }
            case "episodes" -> {
                for (int i = 0; i < 3; i++) {
                    g2.setColor(new Color(255, 255, 255, 110 + i * 70));
                    g2.draw(new RoundRectangle2D.Double(18 + i * 14, 48 - i * 10, 64, 56, 10, 10));
                }
            }
            case "endings" -> {
                for (int i = 0; i < 13; i++) {
                    double a = -Math.PI / 2 + i * 2 * Math.PI / 13;
                    g2.fill(new Ellipse2D.Double(c + Math.cos(a) * 44 - 6, c + Math.sin(a) * 44 - 6, 12, 12));
                }
                g2.draw(new Ellipse2D.Double(c - 16, c - 16, 32, 32));
            }
            case "mods" -> {
                Path2D hex = new Path2D.Double();
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI / 6 + i * Math.PI / 3;
                    double x = c + Math.cos(a) * 46;
                    double y = c + Math.sin(a) * 46;
                    if (i == 0) hex.moveTo(x, y); else hex.lineTo(x, y);
                }
                hex.closePath();
                g2.draw(hex);
                g2.draw(new Line2D.Double(c, c, c, c - 46));
                g2.draw(new Line2D.Double(c, c, c + 40, c + 23));
                g2.draw(new Line2D.Double(c, c, c - 40, c + 23));
            }
            case "settings" -> {
                g2.draw(new Ellipse2D.Double(c - 26, c - 26, 52, 52));
                g2.draw(new Ellipse2D.Double(c - 10, c - 10, 20, 20));
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    g2.draw(new Line2D.Double(c + Math.cos(a) * 33, c + Math.sin(a) * 33, c + Math.cos(a) * 46, c + Math.sin(a) * 46));
                }
            }
            case "power" -> {
                g2.draw(new Arc2D.Double(c - 40, c - 36, 80, 80, 120, 300, Arc2D.OPEN));
                g2.draw(new Line2D.Double(c, 12, c, c + 2));
            }
            default -> { }
        }
    }
}
