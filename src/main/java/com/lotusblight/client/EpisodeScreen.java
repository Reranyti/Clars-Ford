package com.lotusblight.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The menu of one episode. Every episode is to get its own menu with its own design; until they are made this is a
 * placeholder that shows the episode and its status and leads back to the list.
 */
public final class EpisodeScreen extends Screen {
    private final Screen parent;
    private final int number;
    private final String name;
    private final EpisodesScreen.Status status;

    public EpisodeScreen(Screen parent, int number, String name, EpisodesScreen.Status status) {
        super(Component.literal("Эпизод " + number));
        this.parent = parent;
        this.number = number;
        this.name = name;
        this.status = status;
    }

    @Override
    protected void init() {
        addRenderableWidget(new GlassButton(GlassButton.Kind.SMALL, (int) (width * 0.43), (int) (height * 0.82), (int) (width * 0.14), (int) (height * 0.07),
                "Назад", null, b -> onClose()));
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
        g.fill(0, 0, width, height, 0xA6000208);
        float big = Math.max(2f, height * 0.08f / font.lineHeight);
        MenuScene.text(g, font, "ЭПИЗОД " + String.format("%02d", number), width / 2f, height * 0.26f, Math.max(1.2f, big / 2.5f), 0x9FB0C4);
        MenuScene.text(g, font, (name != null ? name : "Без названия").toUpperCase(), width / 2f, height * 0.34f, big, 0xFFFFFF);
        MenuScene.text(g, font, status.label.toUpperCase(), width / 2f, height * 0.5f, Math.max(1.2f, big / 2.5f), status.rgb);
        MenuScene.text(g, font, "Меню этого эпизода ещё не сделано", width / 2f, height * 0.6f, 1.1f, 0x7F8CA0);
        super.render(g, mouseX, mouseY, partial);
    }
}
