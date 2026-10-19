package com.lotusblight.client;

import com.lotusblight.data.EndingsBook;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The endings, on an old TV with a VCR: a ring of circles, one per ending, each with a line going out from it. Hovering a circle lights
 * its whole line and names it; clicking opens the map of the way to that ending (EndingMapScreen). The menu opens with the cassette
 * going into the deck and the set switching on; a click or a key skips that.
 */
public final class EndingsScreen extends Screen {
    /** How many endings there are. */
    private static final int SLOTS = EndingsBook.ALL.size();
    private static final int NODE_RADIUS = 16;
    private static final int RAY_SHORT = 26;

    private final Screen parent;
    private final VhsTv tv;
    private int hovered = -1;

    public EndingsScreen(Screen parent) {
        this(parent, true);
    }

    EndingsScreen(Screen parent, boolean intro) {
        super(Component.literal("Концовки"));
        this.parent = parent;
        this.tv = new VhsTv(intro);
    }

    @Override
    protected void init() {
        tv.layout(this.width, this.height);
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(tv.vcrX() + tv.vcrW() - 120, tv.vcrY() + tv.vcrH() / 2 - 10, 70, 20).build());
    }

    @Override
    public void onClose() {
        // leaving: the tape is stopped, a blue PAUSE screen and the rewind play, the cassette comes out, and only then the title returns
        if (!tv.ejecting()) {
            tv.startEject();
        } else {
            tv.skipEject();
        }
    }

    private int centerX() {
        return tv.sx + tv.sw / 2;
    }

    private int centerY() {
        return tv.sy + tv.sh / 2;
    }

    private int ringRadius() {
        return Math.min(tv.sw, tv.sh) * 31 / 100;
    }

    /** Angle of the node: the first one at the top, then clockwise. */
    private static double angle(int i) {
        return -Math.PI / 2 + i * 2 * Math.PI / SLOTS;
    }

    private int nodeX(int i) {
        return centerX() + (int) Math.round(Math.cos(angle(i)) * ringRadius());
    }

    private int nodeY(int i) {
        return centerY() + (int) Math.round(Math.sin(angle(i)) * ringRadius());
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (tv.ejecting()) {
            tv.skipEject();
            return true;
        }
        if (!tv.picture()) {
            tv.skip();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (tv.ejecting()) {
            tv.skipEject();
            return true;
        }
        if (!tv.picture()) {
            tv.skip();
            return true;
        }
        for (int i = 0; i < SLOTS; i++) {
            double dx = mx - nodeX(i);
            double dy = my - nodeY(i);
            if (dx * dx + dy * dy <= NODE_RADIUS * NODE_RADIUS) {
                EndingsBook.Ending e = ending(i);
                if (e != null && e.id().equals("truth")) {
                    VhsTv.stopLoops();
                    this.minecraft.setScreen(new Ending13Screen(this));
                    return true;
                }
                this.minecraft.setScreen(new EndingMapScreen(this, e == null ? null : e.id(), seen(i) && e.name() != null ? e.name() : "???"));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private EndingsBook.Ending ending(int i) {
        return i < EndingsBook.ALL.size() ? EndingsBook.ALL.get(i) : null;
    }

    private boolean seen(int i) {
        EndingsBook.Ending e = ending(i);
        return e != null && EndingsBook.isSeen(e.id());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        if (!tv.ejecting()) this.minecraft.getMusicManager().stopPlaying();          // the game's own menu music stays off while the tape is in
        if (tv.ejectDone()) {
            this.minecraft.setScreen(parent);
            return;
        }
        tv.body(g, this.width, this.height);
        tv.beginScreen(g);
        if (tv.picture() && !tv.ejecting()) {
            VhsTv.ensureLoops();
            drawRing(g, mouseX, mouseY);
        }
        tv.endScreen(g, this.font);
        super.render(g, mouseX, mouseY, partial);
    }

    private void drawRing(GuiGraphics g, int mouseX, int mouseY) {
        int tw = this.font.width(this.title) + 40;
        int tx = centerX() - tw / 2;
        int ty = tv.sy + 16;
        frame(g, tx, ty, tx + tw, ty + 22, 0xFFB0B0B0);
        g.drawCenteredString(this.font, this.title, centerX(), ty + 7, 0xFFFFFF);

        hovered = -1;
        for (int i = 0; i < SLOTS; i++) {
            int dx = mouseX - nodeX(i);
            int dy = mouseY - nodeY(i);
            if (dx * dx + dy * dy <= NODE_RADIUS * NODE_RADIUS) hovered = i;
        }
        for (int i = 0; i < SLOTS; i++) {
            int x = nodeX(i);
            int y = nodeY(i);
            double a = angle(i);
            boolean on = i == hovered;
            int len = on ? ringRadius() * 6 / 10 : RAY_SHORT;
            int color = on ? 0xFFE4E7D8 : seen(i) ? 0xFF907070 : 0xFF505050;
            int rx0 = x + (int) (Math.cos(a) * NODE_RADIUS);
            int ry0 = y + (int) (Math.sin(a) * NODE_RADIUS);
            int rx1 = x + (int) (Math.cos(a) * (NODE_RADIUS + len));
            int ry1 = y + (int) (Math.sin(a) * (NODE_RADIUS + len));
            line(g, rx0 - 1, ry0, rx1 - 1, ry1, 0x60FF2020);       // the tape's colour channels slip apart
            line(g, rx0 + 1, ry0, rx1 + 1, ry1, 0x6020D0FF);
            line(g, rx0, ry0, rx1, ry1, color);
            VhsTv.disc(g, x, y, NODE_RADIUS, 0xFF000000 | (seen(i) ? 0x402020 : 0x181818));
            ring(g, x, y, NODE_RADIUS, on ? 0xFFFFFFFF : color);
            EndingsBook.Ending e = ending(i);
            if (e != null && e.icon() != null) {
                RenderSystem.enableBlend();
                float shade = seen(i) || on ? 1f : 0.45f;
                g.setColor(shade, shade, shade, 1f);
                g.blit(new ResourceLocation("lotusblight", "textures/gui/endings/" + e.icon() + ".png"),
                        x - 14, y - 14, 28, 28, 0, 0, 64, 64, 64, 64);
                if (e.icon().equals("alliance") || e.icon().equals("neutral")) {
                    // the player's own face goes into the icon's empty square (see tools/ending_icons.py, FACE)
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    PlayerFaceRenderer.draw(g, mc.getSkinManager().getInsecureSkinLocation(mc.getUser().getGameProfile()), x - 14 + 9, y - 14 + 1, 10);
                }
                g.setColor(1f, 1f, 1f, 1f);
            }
        }
        g.drawCenteredString(this.font, Component.literal(EndingsBook.seenCount() + " / " + SLOTS), centerX(), centerY() - 4, 0xFFA0A0A0);
        if (hovered >= 0) {
            EndingsBook.Ending e = ending(hovered);
            String name = seen(hovered) && e != null && e.name() != null ? e.name() : "???";
            g.drawCenteredString(this.font, Component.literal(name), centerX(), centerY() + 10, 0xFFE4E7D8);
        }
    }

    // ---- small drawing helpers (the GUI only knows rectangles)

    private static void frame(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y0 + 1, color);
        g.fill(x0, y1 - 1, x1, y1, color);
        g.fill(x0, y0, x0 + 1, y1, color);
        g.fill(x1 - 1, y0, x1, y1, color);
    }

    private static void ring(GuiGraphics g, int cx, int cy, int r, int color) {
        int steps = r * 8;
        for (int s = 0; s < steps; s++) {
            double a = s * 2 * Math.PI / steps;
            int x = cx + (int) Math.round(Math.cos(a) * r);
            int y = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int s = 0; s <= steps; s++) {
            int x = x0 + (steps == 0 ? 0 : (x1 - x0) * s / steps);
            int y = y0 + (steps == 0 ? 0 : (y1 - y0) * s / steps);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }
}
