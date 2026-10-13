package com.lotusblight.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.storage.LevelSummary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * The title screen of the episodic game: a dark figure holds the menu by threads. The buttons hang from its fingers and sway; clicking a
 * fingertip bends that finger and jerks its button; clicking a button breaks it (the thread is cut, pieces fall) and opens what it
 * stands for. The figure is a shader ({@link MenuShaders#drawPuppet}); {@link PuppetMath} knows where its fingertips are.
 * The ordinary Minecraft menu stays one click away (the small "Меню" button; {@link EpisodeMenu} then lets it through for the session).
 */
public final class EpisodeMenuScreen extends Screen {
    private static final String SHOT = System.getProperty("lotus.menushot");
    private static final float[] BASE_CURL = {0.25f, 0.35f, 0.4f, 0.4f, 0.3f};

    /** One hanging button: where its thread starts, which finger holds it, how it swings. */
    private static final class Hanging {
        GlassButton button;
        int hand, finger;                   // finger 1..3 (index..ring); "Продолжить" is held by the thumbs
        float ax, ay;                       // the thread's end on the button, GUI pixels (top centre)
        float swing, omega, phase;
        boolean broken;
    }

    private static final class Frag {
        float x, y, vx, vy, size;
        int color;
        long born;
    }

    private final float[] curl = new float[10];
    private final long[] pressAt = new long[10];
    private final float[] lift = {0f, 0f};
    private final float[] threadEnd = new float[24];
    private final float[] threadOn = {1, 1, 1, 1, 1, 1, 1, 1};
    private final float[][] tipScreen = new float[8][2];
    private final List<Hanging> hangings = new ArrayList<>();
    private final List<Frag> frags = new ArrayList<>();
    private final Random random = new Random();
    private final long openedAt = Util.getMillis();
    private long lastFrame = openedAt;
    private Runnable pending;
    private long pendingAt;
    private Hanging continueButton;
    private boolean shotTaken;
    private boolean simulated;

    public EpisodeMenuScreen() {
        super(Component.literal("LOTOS"));
    }

    @Override
    protected void init() {
        hangings.clear();
        frags.clear();
        java.util.Arrays.fill(threadOn, 1f);
        pending = null;
        int w = width, h = height;
        int bw = (int) (w * 0.17f), bh = (int) (h * 0.085f);
        // left side: ring finger (outer) -> Эпизоды, middle -> Моды, index -> ???; the right side mirrors it
        hang("Эпизоды", GlassButton.Kind.EPISODES, "episodes", 0, 3, 0.115f, 0.74f, bw, bh, () -> minecraft.setScreen(new EpisodesScreen(this)));
        hang("Моды", GlassButton.Kind.MODS, "mods", 0, 2, 0.240f, 0.84f, bw, bh, () -> minecraft.setScreen(new net.minecraftforge.client.gui.ModListScreen(this)));
        hang("???", GlassButton.Kind.SMALL, "secret", 0, 1, 0.330f, 0.74f, bw, bh, () -> { });
        hang("Концовки", GlassButton.Kind.ENDINGS, "endings", 1, 3, 0.885f, 0.74f, bw, bh, () -> minecraft.setScreen(new EndingsScreen(this)));
        hang("Настройки", GlassButton.Kind.SETTINGS, "settings", 1, 2, 0.760f, 0.84f, bw, bh, () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options)));
        hang("Выйти из симуляции", GlassButton.Kind.EXIT, "power", 1, 1, 0.670f, 0.74f, bw, bh, () -> minecraft.stop());
        continueButton = hang("Продолжить", GlassButton.Kind.CONTINUE, "play", 0, 0, 0.500f, 0.80f, (int) (w * 0.17f), (int) (h * 0.10f), this::continueGame);
        addRenderableWidget(new GlassButton(GlassButton.Kind.SMALL, (int) (w * 0.01), (int) (h * 0.92), (int) (w * 0.08), (int) (h * 0.055), "Меню", null, b -> EpisodeMenu.openVanilla()));
    }

    private Hanging hang(String label, GlassButton.Kind kind, String icon, int hand, int finger, float cx, float top, int bw, int bh, Runnable action) {
        Hanging hg = new Hanging();
        hg.hand = hand;
        hg.finger = finger;
        hg.ax = width * cx;
        hg.ay = height * top;
        hg.phase = random.nextFloat() * 6.28f;
        hg.swing = (random.nextFloat() - 0.5f) * 0.6f;       // they drop in a little unsettled
        hg.button = new GlassButton(kind, (int) (hg.ax - bw / 2f), (int) hg.ay, bw, bh, label, icon, b -> breakButton(hg, action));
        addRenderableWidget(hg.button);
        hangings.add(hg);
        return hg;
    }

    // ------------------------------------------------------------------ interaction

    private void breakButton(Hanging hg, Runnable action) {
        if (hg.broken || pending != null) return;
        hg.broken = true;
        hg.button.broken = true;
        threadOn[hg.hand * 4 + hg.finger] = 0f;
        if (hg == continueButton) {
            threadOn[0] = 0f;
            threadOn[4] = 0f;
        }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.GLASS_BREAK, 1.1f, 0.5f));
        long now = Util.getMillis();
        int x0 = hg.button.getX(), y0 = hg.button.getY(), bw = hg.button.getWidth(), bh = hg.button.getHeight();
        for (int i = 0; i < 46; i++) {
            Frag f = new Frag();
            f.x = x0 + random.nextFloat() * bw;
            f.y = y0 + random.nextFloat() * bh;
            f.vx = (random.nextFloat() - 0.5f) * 120;
            f.vy = -random.nextFloat() * 70;
            f.size = 1.5f + random.nextFloat() * 3.5f;
            f.color = random.nextInt(3) == 0 ? 0xFFBFE6FF : (random.nextBoolean() ? 0xFF10243A : 0xFF1B3A58);
            f.born = now;
            frags.add(f);
        }
        pending = action;
        pendingAt = now + 480;
    }

    private void pressFinger(int hand, int finger) {
        int i = hand * 5 + finger;
        pressAt[i] = Util.getMillis();
        for (Hanging hg : hangings) {
            if (hg.hand == hand && hg.finger == finger) hg.omega += 1.1f * (hand == 0 ? 1 : -1);
            if (hg == continueButton && finger == 0) hg.omega += 0.6f * (hand == 0 ? 1 : -1);
        }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 0.6f + 0.1f * finger, 0.35f));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && pending == null) {
            for (int hand = 0; hand < 2; hand++) {
                for (int f = 0; f < 4; f++) {
                    float[] t = tipScreen[hand * 4 + f];
                    float dx = (float) mx - t[0], dy = (float) my - t[1];
                    if (dx * dx + dy * dy < 26 * 26) {
                        pressFinger(hand, f);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mx, my, button);
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
    public void renderBackground(GuiGraphics g) {
        // drawn in render()
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        long now = Util.getMillis();
        float dt = Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        double t = (now - openedAt) / 1000.0;
        simulate(now);

        // fingers: relaxed bend with a slow flutter; a pressed finger closes and opens again
        for (int hand = 0; hand < 2; hand++) {
            for (int f = 0; f < 5; f++) {
                int i = hand * 5 + f;
                float c = BASE_CURL[f] + 0.04f * (float) Math.sin(t * 0.9 + i * 1.3);
                if (pressAt[i] != 0) {
                    float p = (now - pressAt[i]) / 380f;
                    if (p >= 0 && p < 1) c += 0.6f * (float) Math.sin(Math.PI * p);
                }
                curl[i] = c;
            }
            lift[hand] = 0.015f * (float) Math.sin(t * 0.6 + hand * 1.7);
        }

        // buttons swing on their threads (a damped spring with a little idle push); the thread's end follows the button
        for (Hanging hg : hangings) {
            float target = 0.018f * (float) Math.sin(t * 0.9 + hg.phase);
            hg.omega += (-14f * (hg.swing - target) - 1.6f * hg.omega) * dt;
            hg.swing += hg.omega * dt;
            hg.button.swing = hg.swing;
            float pull = 0;
            long pa = pressAt[hg.hand * 5 + hg.finger];
            if (pa != 0) {
                float p = (now - pa) / 380f;
                if (p >= 0 && p < 1) pull = 6f * (float) Math.sin(Math.PI * p);
            }
            setThreadEnd(hg.hand * 4 + hg.finger, hg.ax, hg.ay - pull);
        }
        if (continueButton != null) {
            // "Продолжить" hangs from both thumbs: one thread to each top corner
            float lx = continueButton.button.getX() + 10, rx = continueButton.button.getX() + continueButton.button.getWidth() - 10;
            setThreadEnd(0, lx, continueButton.ay);
            setThreadEnd(4, rx, continueButton.ay);
        }

        for (int hand = 0; hand < 2; hand++) {
            for (int f = 0; f < 4; f++) {
                double[] tip = PuppetMath.tip(hand, f, curl[hand * 5 + f], 1.0, lift[hand]);
                tipScreen[hand * 4 + f] = PuppetMath.toScreen(tip, width, height);
            }
        }

        MenuScene.renderBack(g, width, height);
        if (MenuShaders.puppetReady()) {
            MenuShaders.drawPuppet(g, width, height, curl, 1.0f, lift, threadEnd, threadOn);
        }
        super.render(g, mouseX, mouseY, partial);
        drawTips(g, mouseX, mouseY);
        drawFrags(g, dt, now);

        if (pending != null && now >= pendingAt) {
            Runnable r = pending;
            pending = null;
            r.run();
        }
        takeShot(now);
    }

    private void setThreadEnd(int idx, float sx, float sy) {
        double[] w = PuppetMath.fromScreen(sx, sy, width, height, PuppetMath.THREAD_Z);
        threadEnd[idx * 3] = (float) w[0];
        threadEnd[idx * 3 + 1] = (float) w[1];
        threadEnd[idx * 3 + 2] = (float) w[2];
    }

    /** A pale mark on each fingertip that can be clicked, brighter under the mouse. */
    private void drawTips(GuiGraphics g, int mx, int my) {
        for (int i = 0; i < 8; i++) {
            float[] t = tipScreen[i];
            float dx = mx - t[0], dy = my - t[1];
            boolean near = dx * dx + dy * dy < 26 * 26;
            int a = near ? 0xFF : 0x40;
            int r = near ? 3 : 1;
            g.fill((int) t[0] - r, (int) t[1] - r, (int) t[0] + r + 1, (int) t[1] + r + 1, (a << 24) | 0xBFE6FF);
        }
    }

    private void drawFrags(GuiGraphics g, float dt, long now) {
        java.util.Iterator<Frag> it = frags.iterator();
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

    // ------------------------------------------------------------------ review mode

    /** -Dlotus.menushot: take a screenshot a few seconds after opening and close the game ("press" also presses a finger and breaks a button first). */
    private void simulate(long now) {
        if (SHOT == null || !SHOT.equals("press") || simulated) return;
        if (now - openedAt > 1800) {
            simulated = true;
            pressFinger(1, 2);
            for (Hanging hg : hangings) {
                if (hg.button.getMessage().getString().equals("Концовки")) hg.button.onPress();
            }
        }
    }

    private void takeShot(long now) {
        if (SHOT == null) return;
        long after = SHOT.equals("press") ? 2250 : 4500;
        if (!shotTaken && now - openedAt > after) {
            shotTaken = true;
            net.minecraft.client.Screenshot.grab(minecraft.gameDirectory, minecraft.getMainRenderTarget(), c -> { });
        }
        if (shotTaken && now - openedAt > after + 1500) minecraft.stop();
    }
}
