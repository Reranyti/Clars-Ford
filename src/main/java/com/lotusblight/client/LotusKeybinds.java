package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

public final class LotusKeybinds {
    public static final KeyMapping TOGGLE_MAP = new KeyMapping(
            "key.lotusblight.toggle_map",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "key.categories.lotusblight"
    );
    public static final KeyMapping OPEN_ATLAS = new KeyMapping(
            "key.lotusblight.open_atlas",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories.lotusblight"
    );

    public static final KeyMapping OPEN_DIARY = new KeyMapping(
            "key.lotusblight.open_diary",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "key.categories.lotusblight"
    );

    private LotusKeybinds() {}

    @Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = net.minecraftforge.api.distmarker.Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE_MAP);
            event.register(OPEN_ATLAS);
            event.register(OPEN_DIARY);
        }
    }

    @Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = net.minecraftforge.api.distmarker.Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ClientEvents {
        @SubscribeEvent
        public static void clientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            // KeyMapping state updates before Forge/vanilla route the key event to the focused
            // screen, so pressing M/N while typing in an anvil/sign/chat text field still
            // registers as a click here. Still drain consumeClick() every tick (otherwise a press
            // made while a screen is open queues up and fires unexpectedly once it closes), but
            // only act on it when no other screen has focus - matches the guard InnerVoiceOverlay
            // already uses for Enter for the same reason.
            while (TOGGLE_MAP.consumeClick()) {
                if (mc.screen == null) LotusHudOverlay.toggleHidden();
            }
            while (OPEN_DIARY.consumeClick()) {
                if (mc.screen == null) com.lotusblight.client.story.DiaryScreen.open();
            }
            while (OPEN_ATLAS.consumeClick()) {
                if (mc.screen == null) mc.setScreen(new LotusAtlasScreen());
            }
        }
    }
}
