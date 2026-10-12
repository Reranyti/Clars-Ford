package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Swaps the vanilla title screen for the episodes menu. The "Меню" button on that menu turns the swap off
 * for the rest of the session (the vanilla title then shows an "Эпизоды" button to come back).
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EpisodeMenu {
    private static boolean allowVanilla;

    private EpisodeMenu() {}

    static boolean vanillaAllowed() {
        return allowVanilla;
    }

    static void openVanilla() {
        allowVanilla = true;
        Minecraft.getInstance().setScreen(new TitleScreen());
    }

    static void openEpisodes() {
        allowVanilla = false;
        Minecraft.getInstance().setScreen(new EpisodeMenuScreen());
    }

    @SubscribeEvent
    public static void onOpening(ScreenEvent.Opening event) {
        if (allowVanilla) return;
        if (event.getNewScreen() != null && event.getNewScreen().getClass() == TitleScreen.class) {
            event.setNewScreen(new EpisodeMenuScreen());
        }
    }
}
