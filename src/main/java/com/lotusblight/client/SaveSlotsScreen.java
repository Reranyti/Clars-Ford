package com.lotusblight.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The files of an episode: three glass plates hang on threads from the top of the screen and sway; the chosen one is lifted and its threads
 * go taut; erasing a file breaks its plate like the buttons of the title screen; copying draws a thread from one plate to the next.
 * Nine files make three pages of three. A file is a world ({@link SaveSlots}); Copy and Erase ask first. The background is the episode's own
 * ({@link EpisodeBackdrop}).
 */
public final class SaveSlotsScreen extends Screen {
    private static final int PAGES = 3;
    private static final int PER_PAGE = 3;
    private static final String[] ACTIONS = {"Копировать", "Стереть", "К эпизодам", "Страница ◄", "Страница ►", "Выход"};

    private enum Mode { PLAY, COPY, ERASE }

    private static final class Frag {
        float x, y, vx, vy, size;
        int color;
        long born;
    }

    private final Screen parent;
    private final int episode;
    private final EpisodeBackdrop backdrop;
    private Map<Integer, SaveSlots.Info> slots;
    private int page;
    private int cursor;
    private Mode mode = Mode.PLAY;
    private int copyFrom = -1;
    private int confirmSlot = -1;
    private int confirmChoice;
    private String message = "";
    private long messageAt;
    private final long openedAt = Util.getMillis();
    private long pageAt = openedAt;
    private long selectedAt = openedAt;                  // when the chosen card was chosen (the icon flashes then)
    private long lastFrame = openedAt;

    private final float[] swing = new float[PER_PAGE];
    private final float[] omega = new float[PER_PAGE];
    private final long[] brokenAt = new long[PER_PAGE];
    private final List<Frag> frags = new ArrayList<>();
    private final Random random = new Random();
    private Runnable pending;
    private long pendingAt;
    // the special animations
    private long outAt;                                  // the cards fly up before a page changes
    private int nextPage = -1;
    private long launchAt;                               // a file is being started: the plus fills, the card grows to the screen, the others leave
    private int launchRow = -1;
    private long fillMs;                                 // how long the new-game "filling" lasts (0 for an existing file)
    private static final long OUT_MS = 380, LAUNCH_MS = 820;

    public SaveSlotsScreen(Screen parent, int episode, String name) {
        super(Component.literal("Файлы эпизода " + episode));
        this.parent = parent;
        this.episode = episode;
        this.backdrop = new EpisodeBackdrop(episode);
    }

    @Override
    protected void init() {
        refresh();
    }

    private void refresh() {
        slots = SaveSlots.read(episode);
    }

    private int slotOf(int row) {
        return page * PER_PAGE + row + 1;
    }

    private void nudge(int row, float amount) {
        omega[row] += amount;
    }

    private void changePage(int to) {
        if (pending != null || launchRow >= 0) return;
        int target = ((to % PAGES) + PAGES) % PAGES;
        outAt = Util.getMillis();
        nextPage = target;
        for (int i = 0; i < PER_PAGE; i++) omega[i] += 0.5f * (i % 2 == 0 ? 1 : -1);
        pending = () -> {
            page = target;
            nextPage = -1;
            outAt = 0;
            pageAt = Util.getMillis();
            java.util.Arrays.fill(brokenAt, 0);
            for (int i = 0; i < PER_PAGE; i++) omega[i] += 0.6f * (i % 2 == 0 ? 1 : -1);
        };
        pendingAt = outAt + OUT_MS;
    }

    private static float bounce(float x) {
        if (x < 1 / 2.75f) return 7.5625f * x * x;
        if (x < 2 / 2.75f) {
            x -= 1.5f / 2.75f;
            return 7.5625f * x * x + 0.75f;
        }
        if (x < 2.5f / 2.75f) {
            x -= 2.25f / 2.75f;
            return 7.5625f * x * x + 0.9375f;
        }
        x -= 2.625f / 2.75f;
        return 7.5625f * x * x + 0.984375f;
    }

    /** Starts a file with the special animation; {@code newGame} fills an empty card with the night first. */
    private void startFile(int row, boolean newGame, Runnable open) {
        long now = Util.getMillis();
        launchRow = row;
        fillMs = newGame ? 720 : 0;
        launchAt = now;
        pending = open;
        pendingAt = now + fillMs + LAUNCH_MS;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, newGame ? 0.7f : 1.0f, 0.5f));
    }

    // ------------------------------------------------------------------ the plates' places

    private int plateW() {
        return (int) (width * 0.22);
    }

    private int plateX(int row) {
        int gap = (int) (width * 0.04);
        int total = 3 * plateW() + 2 * gap;
        return (width - total) / 2 + row * (plateW() + gap);
    }

    private int plateY(int row) {
        return (int) (height * 0.215);
    }

    private int plateH() {
        return (int) (height * 0.57);
    }

    private int actionY(int i) {
        return (int) (height * (0.845 + (i / 3) * 0.07));
    }

    private int actionX(int i) {
        return (int) (width * (0.19 + (i % 3) * 0.25));
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics g) {
        // drawn in render()
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        long now = Util.getMillis();
        float dt = Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        double t = (now - openedAt) / 1000.0;
        if (MenuScene.customBackground()) MenuScene.renderBack(g, width, height);
        else backdrop.render(g, width, height, t);
        g.fill(0, 0, width, height, 0x70000812);

        float s = Math.max(1.2f, height * 0.04f / font.lineHeight);
        text(g, "ЭПИЗОД " + episode, width * 0.05f + font.width("ЭПИЗОД " + episode) * s / 2f, height * 0.05f, s, 0xFFFFFF);
        String hint = switch (mode) {
            case PLAY -> "Выберите файл.";
            case COPY -> copyFrom < 0 ? "Какой файл копировать?" : "Куда скопировать?";
            case ERASE -> "Какой файл стереть?";
        };
        textLeft(g, hint, width * 0.10f, height * 0.13f, Math.max(1f, s * 0.8f), 0xCFE4FF);
        String pg = "СТРАНИЦА " + (page + 1) + " / " + PAGES;
        textLeft(g, pg, width * 0.78f, height * 0.055f, Math.max(1f, s * 0.6f), 0x8FA8FF);

        // the springs of the plates
        for (int row = 0; row < PER_PAGE; row++) {
            float target = 0.012f * (float) Math.sin(t * 0.9 + row * 1.7);
            omega[row] += (-16f * (swing[row] - target) - 1.5f * omega[row]) * dt;
            swing[row] += omega[row] * dt;
        }
        for (int row = 0; row < PER_PAGE; row++) drawPlate(g, row, now, t);
        drawCopyThread(g, now, t);
        drawActions(g, mouseX, mouseY);
        if (!message.isEmpty() && now - messageAt < 3200) {
            text(g, message, width / 2f, height * 0.93f, Math.max(1f, s * 0.6f), 0xFFE08A);
        }
        drawFrags(g, dt, now);
        if (launchRow >= 0) {
            float lt = (now - launchAt - fillMs) / (float) LAUNCH_MS;
            if (lt > 0.45f) g.fill(0, 0, width, height, ((int) (255 * Math.min(1f, (lt - 0.45f) / 0.55f)) << 24));
        }
        if (confirmSlot >= 0) drawConfirm(g);
        if (pending != null && now >= pendingAt) {
            Runnable r = pending;
            pending = null;
            r.run();
            launchRow = -1;                                  // if the world could not be opened, the cards are back and usable
            fillMs = 0;
        }
        super.render(g, mouseX, mouseY, partial);
    }

    /** One card with its two threads: it drops in when the page opens, then hangs and sways about its top middle. */
    private void drawPlate(GuiGraphics g, int row, long now, double t) {
        int slot = slotOf(row);
        SaveSlots.Info info = slots.getOrDefault(slot, SaveSlots.Info.EMPTY);
        int x = plateX(row), y = plateY(row), w = plateW(), h = plateH();
        boolean sel = row == cursor && confirmSlot < 0;
        boolean source = mode == Mode.COPY && copyFrom == slot;
        float fall = Math.min(1f, Math.max(0f, (now - pageAt - row * 140L) / 700f));
        float drop = 1f - bounce(fall);                                          // 1 above the screen .. 0 landed, with the bounces
        float lift = sel ? -7f : 0f;
        float py = y + lift - drop * height * 0.95f;
        float grow = 0f;                                                         // 0..1: how far the launched card has grown to fill the screen
        float leave = 0f;                                                        // 0..1: how far the other cards have flown up
        if (outAt != 0) {
            float u = Math.min(1f, (now - outAt) / (float) OUT_MS);
            py -= u * u * height * 1.1f + row * 14 * u;
        }
        if (launchRow >= 0) {
            float lt = (now - launchAt - fillMs) / (float) LAUNCH_MS;
            if (lt > 0) {
                lt = Math.min(1f, lt);
                if (row == launchRow) grow = lt * lt * (3 - 2 * lt);
                else {
                    leave = lt;
                    py -= leave * leave * height * 1.1f + row * 12 * leave;
                }
            }
        }
        float cx = x + w / 2f;
        int accent = source ? 0xFFD060 : 0x9FD6FF;
        float sw = swing[row];
        if (brokenAt[row] != 0 && now - brokenAt[row] > 0) return;               // in pieces

        double ca = Math.cos(sw), sa = Math.sin(sw);
        for (int side = -1; side <= 1; side += 2) {
            float ox = side * w * 0.36f;
            float cornerX = cx + (float) (ox * ca), cornerY = py + (float) (ox * sa);
            thread(g, cx + ox * 0.6f, 0, cornerX, cornerY, sel ? 0xE0BFE6FF : 0x90BFE6FF);
        }
        g.pose().pushPose();
        if (grow > 0) {
            float k = 1f + grow * 3.2f;                                          // the card swells until it is the whole picture
            float mx = x + w / 2f, my = py + h / 2f;
            g.pose().translate(width / 2f * grow + mx * (1 - grow), height / 2f * grow + my * (1 - grow), 0);
            g.pose().scale(k, k, 1);
            g.pose().translate(-mx, -my, 0);
        }
        g.pose().translate(cx, py, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotation(sw));
        g.pose().translate(-cx, -py, 0);
        int px = x, pw = w, ph = h, ipy = (int) py;
        MenuScene.glass(g, px, ipy, pw, ph, width, height, sel);
        g.fillGradient(px, ipy, px + pw, ipy + ph, sel ? 0x50102844 : 0x80061830, sel ? 0x80071526 : 0xB0020A14);
        g.fill(px, ipy, px + pw, ipy + 1, 0xFF000000 | accent);
        g.fill(px, ipy, px + 1, ipy + ph, 0xFF000000 | accent);
        g.fill(px, ipy + ph - 1, px + pw, ipy + ph, 0xC8000000);
        g.fill(px + pw - 1, ipy, px + pw, ipy + ph, 0xC8000000);
        if (sel) g.renderOutline(px - 1, ipy - 1, pw + 2, ph + 2, 0xFFFFFFFF);

        // the picture: the episode's night over the file (a used file is lit, an empty one is dark with a plus)
        int ax = px + 6, ay = ipy + 6, aw = pw - 12, ah = (int) (ph * 0.46f);
        g.enableScissor(ax, ay, ax + aw, ay + ah);
        if (info.used()) {
            g.fillGradient(ax, ay, ax + aw, ay + ah, 0xFF04101E, 0xFF14385A);
            int ms = Math.max(6, aw / 7);
            int mx = ax + aw / 2 - ms / 2 + (int) (Math.sin(t * 0.3 + row) * aw * 0.12);
            g.fill(mx, ay + ah / 5, mx + ms, ay + ah / 5 + ms, 0xFFEAF4FF);
            for (int i = 0; i < 9; i++) {
                int sx = ax + (i * 37 + row * 11) % aw, sy = ay + (i * 53 + row * 7) % (ah * 2 / 3);
                int al = (int) (90 + 140 * (0.5 + 0.5 * Math.sin(t * (1 + i * 0.2) + i)));
                g.fill(sx, sy, sx + 1, sy + 1, (al << 24) | 0xFFFFFF);
            }
            g.fillGradient(ax, ay + ah * 2 / 3, ax + aw, ay + ah, 0x00000000, 0x80306090);
        } else {
            g.fillGradient(ax, ay, ax + aw, ay + ah, 0xFF02060E, 0xFF06101C);
            float fp = launchRow == row && fillMs > 0 ? Math.min(1f, (now - launchAt) / (float) fillMs) : 0f;
            int cxp = ax + aw / 2, cyp = ay + ah / 2, arm = (int) (Math.max(4, aw / 12) * (1f + 0.9f * fp));
            int pa = fp > 0 ? 0xFFFFFFFF : (sel ? 0xFF9FD6FF : 0x806F8CA0);
            if (fp > 0) {
                // the night spreads out of the plus: a growing window of sky, a moon rising in it
                int hw = (int) (aw / 2f * Math.min(1f, fp * 1.4f)), hh = (int) (ah / 2f * Math.min(1f, fp * 1.4f));
                g.enableScissor(cxp - hw, cyp - hh, cxp + hw, cyp + hh);
                g.fillGradient(ax, ay, ax + aw, ay + ah, 0xFF04101E, 0xFF14385A);
                int ms = Math.max(6, aw / 7);
                int my2 = ay + ah - (int) ((ah * 0.80f) * Math.min(1f, fp * 1.2f));
                g.fill(ax + aw / 2 - ms / 2, my2, ax + aw / 2 + ms / 2, my2 + ms, 0xFFEAF4FF);
                for (int i = 0; i < 9; i++) {
                    int sx = ax + (i * 37 + row * 11) % aw, sy = ay + (i * 53 + row * 7) % (ah * 2 / 3);
                    g.fill(sx, sy, sx + 1, sy + 1, 0xFFFFFFFF);
                }
                g.disableScissor();
            }
            g.fill(cxp - arm, cyp - 1, cxp + arm + 1, cyp + 1, pa);
            g.fill(cxp - 1, cyp - arm, cxp + 1, cyp + arm + 1, pa);
        }
        g.disableScissor();
        g.renderOutline(ax - 1, ay - 1, aw + 2, ah + 2, 0x80000000 | accent);

        float ns = Math.max(1.1f, ph * 0.075f / font.lineHeight);
        int ty = ay + ah + (int) (ph * 0.04f);
        String title = info.used() ? info.name() : "[ПУСТО]";
        textLeft(g, title, px + pw * 0.08f, ty, ns, info.used() || sel ? 0xFFFFFF : 0xBFD3DF);
        if (info.used()) {
            String tm = SaveSlots.time(info.playTicks());
            textLeft(g, tm, px + pw - font.width(tm) * ns * 0.9f - pw * 0.07f, ty, ns * 0.9f, 0xFFFFFF);
            textLeft(g, SaveSlots.when(info.lastPlayed()), px + pw * 0.08f, ty + font.lineHeight * ns + 3, Math.max(1f, ns * 0.62f), 0xA9C6D8);
        } else {
            textLeft(g, "—   —   —   —   —", px + pw * 0.08f, ty + font.lineHeight * ns + 3, Math.max(1f, ns * 0.62f), 0x6F8CA0);
        }
        // the one mark: the ending this file is heading for (worked out from the world); nothing until the line is chosen
        drawEndingMark(g, info, px, ipy, pw, ph, sel, now);
        if (sel) drawCursor(g, (int) cx - 4, ipy - 14, now);
        g.pose().popPose();
    }

    /** One icon in the corner of the card: the ending of this file's line, with its name under it. */
    private void drawEndingMark(GuiGraphics g, SaveSlots.Info info, int px, int py, int pw, int ph, boolean sel, long now) {
        if (!info.used() || info.ending() == null) return;
        int size = Math.max(14, (int) (ph * 0.13f));
        int ix = px + pw - size - (int) (pw * 0.07f), iy = py + ph - size - (int) (ph * 0.05f);
        // chosen: a flash when the card is picked, then a slow beat, with a soft halo behind the icon
        float beat = sel ? (float) (0.5 + 0.5 * Math.sin((now - openedAt) / 260.0)) : 0f;
        float flash = sel ? Math.max(0f, 1f - (now - selectedAt) / 420f) : 0f;
        if (sel) {
            int halo = (int) (size * (0.55f + 0.25f * beat + 0.6f * flash));
            int ha = (int) (40 + 60 * beat + 120 * flash);
            g.fillGradient(ix + size / 2 - halo, iy + size / 2 - halo, ix + size / 2 + halo, iy + size / 2 + halo, (ha << 24) | 0x9FD6FF, 0x00000000);
        }
        float scale = 1f + 0.10f * beat + 0.35f * flash;
        float shade = 0.82f + 0.18f * beat + 0.4f * flash;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.setColor(Math.min(1f, shade), Math.min(1f, shade), 1f, 1f);
        g.pose().pushPose();
        g.pose().translate(ix + size / 2f, iy + size / 2f, 0);
        g.pose().scale(scale, scale, 1);
        g.blit(new net.minecraft.resources.ResourceLocation("lotusblight", "textures/gui/endings/" + info.ending() + ".png"), -size / 2, -size / 2, size, size, 0, 0, 64, 64, 64, 64);
        g.pose().popPose();
        g.setColor(1f, 1f, 1f, 1f);
        String name = com.lotusblight.data.EndingsBook.ALL.stream().filter(e -> e.id().equals(info.ending())).map(com.lotusblight.data.EndingsBook.Ending::name).findFirst().orElse("");
        float ns = Math.max(1f, ph * 0.05f / font.lineHeight);
        textLeft(g, name, px + pw * 0.08f, iy + size / 2f - font.lineHeight * ns / 2f, ns, 0xCFE4FF);
    }

    /** A thread as a thin rotated rectangle. */
    private void thread(GuiGraphics g, float x0, float y0, float x1, float y1, int argb) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1) return;
        g.pose().pushPose();
        g.pose().translate(x0, y0, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotation((float) Math.atan2(dy, dx)));
        g.fill(0, 0, (int) len, 1, argb);
        g.pose().popPose();
    }

    /** While copying: a glowing thread from the chosen card to the one under the cursor. */
    private void drawCopyThread(GuiGraphics g, long now, double t) {
        if (mode != Mode.COPY || copyFrom < 0) return;
        int fromRow = copyFrom - 1 - page * PER_PAGE;
        if (fromRow < 0 || fromRow >= PER_PAGE) return;
        float x0 = plateX(fromRow) + plateW() / 2f, y0 = plateY(fromRow) + plateH();
        float x1 = plateX(cursor) + plateW() / 2f, y1 = plateY(cursor) + plateH();
        float sag = height * 0.045f + 5 * (float) Math.sin(t * 5);
        int steps = 32;
        float px = x0, py = y0;
        for (int i = 1; i <= steps; i++) {
            float u = i / (float) steps;
            float x = x0 + (x1 - x0) * u;
            float y = y0 + (y1 - y0) * u + (float) Math.sin(u * Math.PI) * sag;
            int a = 0x60 + (int) (0x9F * (0.5 + 0.5 * Math.sin(t * 8 - u * 9)));
            thread(g, px, py, x, y, (a << 24) | 0xFFD060);
            px = x;
            py = y;
        }
    }

    private void drawActions(GuiGraphics g, int mouseX, int mouseY) {
        float as = Math.max(1.1f, height * 0.032f / font.lineHeight);
        for (int i = 0; i < ACTIONS.length; i++) {
            int ax = actionX(i), ay = actionY(i);
            boolean hot = hitAction(mouseX, mouseY) == i;
            boolean active = (i == 0 && mode == Mode.COPY) || (i == 1 && mode == Mode.ERASE);
            int bw = (int) (font.width(ACTIONS[i]) * as) + 12;
            int bh = (int) (font.lineHeight * as) + 8;
            g.fillGradient(ax - 6, ay - 4, ax - 6 + bw, ay - 4 + bh, hot ? 0xB0102640 : 0x90061830, hot ? 0xB0071526 : 0x90020A14);
            g.fill(ax - 6, ay - 4, ax - 6 + bw, ay - 3, active ? 0xFFFFD060 : (hot ? 0xFFFFFFFF : 0xAA9FD6FF));
            textLeft(g, ACTIONS[i], ax, ay, as, active ? 0xFFD060 : (hot ? 0xFFFFFF : 0xA9C6D8));
        }
    }

    private void drawCursor(GuiGraphics g, int x, int y, long now) {
        int pulse = (int) ((Math.sin((now - openedAt) / 180.0) + 1) * 0.5 * 40);
        int c = 0xFF000000 | (0xB0 + pulse << 16) | (0xE0 + pulse / 2 << 8) | 0xFF;
        // a small lotus bud (a diamond)
        g.fill(x + 3, y, x + 6, y + 2, c);
        g.fill(x + 1, y + 2, x + 8, y + 5, c);
        g.fill(x + 3, y + 5, x + 6, y + 7, c);
        g.fill(x + 4, y + 7, x + 5, y + 8, c);
    }

    private void drawConfirm(GuiGraphics g) {
        g.fill(0, 0, width, height, 0xC0000000);
        int w = (int) (width * 0.5), h = (int) (height * 0.26);
        int x = (width - w) / 2, y = (int) (height * 0.36);
        MenuScene.glass(g, x, y, w, h, width, height, true);
        g.fillGradient(x, y, x + w, y + h, 0xF0103848, 0xF00A2634);
        g.renderOutline(x, y, w, h, 0xFFFFFFFF);
        float s = Math.max(1.2f, height * 0.04f / font.lineHeight);
        text(g, "Стереть файл " + confirmSlot + "?", width / 2f, y + h * 0.18f, s, 0xFFFFFF);
        text(g, "Это нельзя отменить.", width / 2f, y + h * 0.42f, Math.max(1f, s * 0.6f), 0xFFB0A0);
        text(g, confirmChoice == 1 ? "  Нет      > ДА <" : "> НЕТ <      Да  ", width / 2f, y + h * 0.66f, s * 0.9f, 0xFFFFFF);
    }

    private void drawFrags(GuiGraphics g, float dt, long now) {
        Iterator<Frag> it = frags.iterator();
        while (it.hasNext()) {
            Frag f = it.next();
            float age = (now - f.born) / 1000f;
            if (age > 1.4f) {
                it.remove();
                continue;
            }
            f.vy += 260 * dt;
            f.x += f.vx * dt;
            f.y += f.vy * dt;
            int alpha = (int) (255 * Math.min(1f, (1.4f - age) / 0.5f));
            g.fill((int) f.x, (int) f.y, (int) (f.x + f.size), (int) (f.y + f.size), (alpha << 24) | (f.color & 0xFFFFFF));
        }
    }

    private void text(GuiGraphics g, String text, float cx, float y, float scale, int rgb) {
        MenuScene.text(g, font, text, cx, y, scale, rgb);
    }

    private void textLeft(GuiGraphics g, String text, float x, float y, float scale, int rgb) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1);
        g.drawString(font, text, 0, 0, 0xFF000000 | rgb, true);
        pose.popPose();
    }

    // ------------------------------------------------------------------ input

    private int hitRow(double mx, double my) {
        for (int row = 0; row < PER_PAGE; row++) {
            if (mx >= plateX(row) && mx < plateX(row) + plateW() && my >= plateY(row) && my < plateY(row) + plateH()) return row;
        }
        return -1;
    }

    private int hitAction(double mx, double my) {
        float as = Math.max(1.1f, height * 0.032f / font.lineHeight);
        for (int i = 0; i < ACTIONS.length; i++) {
            int ax = actionX(i), ay = actionY(i);
            if (mx >= ax - 6 && mx < ax + font.width(ACTIONS[i]) * as + 6 && my >= ay - 4 && my < ay + font.lineHeight * as + 4) return i;
        }
        return -1;
    }

    private void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 0.35f));
    }

    private void say(String text) {
        message = text;
        messageAt = Util.getMillis();
    }

    private void moveCursor(int to) {
        int next = ((to % PER_PAGE) + PER_PAGE) % PER_PAGE;
        if (next != cursor) {
            nudge(next, 0.35f);
            nudge(cursor, -0.2f);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.3f, 0.12f));
        }
        if (next != cursor) selectedAt = Util.getMillis();
        cursor = next;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || pending != null || launchRow >= 0) return false;
        if (confirmSlot >= 0) return true;
        int a = hitAction(mx, my);
        if (a >= 0) {
            action(a);
            return true;
        }
        int row = hitRow(mx, my);
        if (row >= 0) {
            moveCursor(row);
            choose(slotOf(row));
            return true;
        }
        return false;
    }

    @Override
    public void mouseMoved(double mx, double my) {
        int row = hitRow(mx, my);
        if (row >= 0 && confirmSlot < 0 && row != cursor) moveCursor(row);
    }

    private void action(int a) {
        click();
        switch (a) {
            case 0 -> {
                mode = mode == Mode.COPY ? Mode.PLAY : Mode.COPY;
                copyFrom = -1;
            }
            case 1 -> {
                mode = mode == Mode.ERASE ? Mode.PLAY : Mode.ERASE;
                copyFrom = -1;
            }
            case 2 -> onClose();
            case 3 -> changePage(page - 1);
            case 4 -> changePage(page + 1);
            case 5 -> minecraft.stop();
            default -> { }
        }
    }

    private void choose(int slot) {
        click();
        int row = slot - 1 - page * PER_PAGE;
        SaveSlots.Info info = slots.getOrDefault(slot, SaveSlots.Info.EMPTY);
        nudge(row, 0.5f);
        switch (mode) {
            case PLAY -> {
                if (launchRow >= 0) return;
                if (info.used()) startFile(row, false, () -> SaveSlots.play(this, episode, slot));
                else startFile(row, true, () -> SaveSlots.createNew(episode, slot));
            }
            case ERASE -> {
                if (!info.used()) {
                    say("Здесь нечего стирать.");
                } else {
                    confirmSlot = slot;
                    confirmChoice = 0;
                }
            }
            case COPY -> {
                if (copyFrom < 0) {
                    if (!info.used()) say("Этот файл пуст: копировать нечего.");
                    else copyFrom = slot;
                } else if (slot == copyFrom) {
                    say("Файл нельзя скопировать сам в себя.");
                } else if (info.used()) {
                    say("Этот файл занят. Сначала сотри его.");
                } else {
                    boolean ok = SaveSlots.copy(episode, copyFrom, slot);
                    say(ok ? "Файл " + copyFrom + " скопирован в файл " + slot + "." : "Не получилось скопировать.");
                    mode = Mode.PLAY;
                    copyFrom = -1;
                    refresh();
                    pageAt = Util.getMillis() - 300;                                  // the new file drops in
                }
            }
        }
    }

    /** The plate of the file breaks like glass; when the pieces have fallen the file is deleted and the empty plate drops in. */
    private void shatter(int slot) {
        int row = slot - 1 - page * PER_PAGE;
        if (row < 0 || row >= PER_PAGE) {
            doErase(slot);
            return;
        }
        long now = Util.getMillis();
        brokenAt[row] = now;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.GLASS_BREAK, 1.1f, 0.5f));
        int x0 = plateX(row), y0 = plateY(row), pw = plateW(), ph = plateH();
        for (int i = 0; i < 70; i++) {
            Frag f = new Frag();
            f.x = x0 + random.nextFloat() * pw;
            f.y = y0 + random.nextFloat() * ph;
            f.vx = (random.nextFloat() - 0.5f) * 140;
            f.vy = -random.nextFloat() * 80;
            f.size = 1.5f + random.nextFloat() * 4f;
            f.color = random.nextInt(3) == 0 ? 0xFFBFE6FF : (random.nextBoolean() ? 0xFF10243A : 0xFF1B3A58);
            f.born = now;
            frags.add(f);
        }
        pending = () -> doErase(slot);
        pendingAt = now + 520;
    }

    private void doErase(int slot) {
        boolean ok = SaveSlots.erase(episode, slot);
        say(ok ? "Файл " + slot + " стёрт." : "Не получилось стереть.");
        refresh();
        java.util.Arrays.fill(brokenAt, 0);
        pageAt = Util.getMillis() - 200;
        mode = Mode.PLAY;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (pending != null) return true;
        if (confirmSlot >= 0) {
            switch (key) {
                case 263, 262, 265, 264 -> confirmChoice = 1 - confirmChoice;
                case 257, 335, 90 -> {
                    int slot = confirmSlot;
                    confirmSlot = -1;
                    if (confirmChoice == 1) shatter(slot);
                    else mode = Mode.PLAY;
                }
                case 256, 88 -> confirmSlot = -1;
                default -> { }
            }
            return true;
        }
        switch (key) {
            case 263 -> moveCursor(cursor - 1);
            case 262 -> moveCursor(cursor + 1);
            case 265 -> changePage(page - 1);
            case 264 -> changePage(page + 1);
            case 257, 335, 90 -> choose(slotOf(cursor));
            case 256, 88 -> {
                if (mode != Mode.PLAY) {
                    mode = Mode.PLAY;
                    copyFrom = -1;
                } else {
                    onClose();
                }
            }
            default -> {
                return super.keyPressed(key, scan, mods);
            }
        }
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
