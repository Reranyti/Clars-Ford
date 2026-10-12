package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Puts the "Концовки" button on the title screen, so the list can be opened before any world exists. */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EndingsTitleButton {
    private EndingsTitleButton() {}

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen title)) return;
        event.addListener(Button.builder(Component.literal("Концовки"), b -> title.getMinecraft().setScreen(new EndingsScreen(title)))
                .bounds(6, 6, 70, 20).build());
        event.addListener(Button.builder(Component.literal("Эпизоды"), b -> EpisodeMenu.openEpisodes())
                .bounds(6, 30, 70, 20).build());
    }
}
