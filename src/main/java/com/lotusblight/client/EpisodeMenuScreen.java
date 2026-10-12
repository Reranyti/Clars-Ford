package com.lotusblight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;

import java.util.Comparator;

/**
 * The title screen of the episodic game. The scene is a GPU shader ({@link MenuShaders}); the buttons are laid out on a
 * tidy grid: "Продолжить" in the middle, two rows of two, "Выйти из симуляции" at the bottom. The ordinary Minecraft menu
 * stays one click away (the small "Меню" button; {@link EpisodeMenu} then lets it through for the rest of the session).
 */
public final class EpisodeMenuScreen extends Screen {
    public EpisodeMenuScreen() {
        super(Component.literal("LOTOS"));
    }

    @Override
    protected void init() {
        // the author's sketch, straightened: centred top and bottom buttons, mirrored pairs on the sides (percent of the screen)
        add(GlassButton.Kind.SMALL, "Меню", null, 0.5, 1.1, 6.1, 4, b -> EpisodeMenu.openVanilla());
        add(GlassButton.Kind.CONTINUE, "Продолжить", "play", 31.5, 27, 37, 14.6, b -> continueGame());
        add(GlassButton.Kind.EPISODES, "Эпизоды", "episodes", 17, 50, 29, 11, b -> minecraft.setScreen(new EpisodesScreen(this)));
        add(GlassButton.Kind.ENDINGS, "Концовки", "endings", 54, 50, 29, 11, b -> minecraft.setScreen(new EndingsScreen(this)));
        add(GlassButton.Kind.MODS, "Моды", "mods", 0.5, 69, 24, 10, b -> minecraft.setScreen(new net.minecraftforge.client.gui.ModListScreen(this)));
        add(GlassButton.Kind.SETTINGS, "Настройки", "settings", 75.5, 69, 24, 10, b -> minecraft.setScreen(new OptionsScreen(this, minecraft.options)));
        add(GlassButton.Kind.EXIT, "Выйти из симуляции", "power", 32.5, 74, 35, 8.6, b -> minecraft.stop());
    }

    private void add(GlassButton.Kind kind, String label, String icon, double left, double top, double w, double h, Button.OnPress press) {
        int x = (int) Math.round(width * left / 100.0);
        int bw = Math.min((int) Math.round(width * w / 100.0), width - x);   // a button running off the edge is as wide as what is visible
        addRenderableWidget(new GlassButton(kind, x, (int) Math.round(height * top / 100.0), bw, (int) Math.round(height * h / 100.0), label, icon, press));
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
        // the scene is drawn in render()
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        MenuScene.renderBack(g, width, height);
        float titleScale = Math.max(2f, height * 0.11f / font.lineHeight);
        float ty = height * 0.07f;
        MenuScene.text(g, font, "LOTOS", width / 2f + titleScale * 0.5f, ty + titleScale * 0.5f, titleScale, 0x1A2A44);
        MenuScene.text(g, font, "LOTOS", width / 2f, ty, titleScale, 0xE8F4FF);
        MenuScene.text(g, font, MenuScene.spaced("ЭПИЗОДНАЯ ИСТОРИЯ"), width / 2f, ty + font.lineHeight * titleScale + 4, Math.max(1f, titleScale / 3f), 0x7FA6C2);
        super.render(g, mouseX, mouseY, partial);
    }
}
