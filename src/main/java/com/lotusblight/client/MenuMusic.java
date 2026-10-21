package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The music of the menus: a track looped under the episode screen and another under the slot screen. A screen asks for its track when it
 * opens; the track changes when another menu screen asks for another one and stops (with a short fade) when no menu screen is open.
 * It plays in the music category, so the game's music slider controls it.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MenuMusic {
    private static Track current;
    private static SoundEvent currentEvent;

    private MenuMusic() {}

    /** A looped, relative (not positioned) music sound that fades out when told to stop. */
    private static final class Track extends AbstractTickableSoundInstance {
        private boolean fading;
        private float level = 0.0f;

        Track(SoundEvent event) {
            super(event, SoundSource.MUSIC, RandomSource.create());
            this.looping = true;
            this.delay = 0;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 0.0f;
        }

        @Override
        public boolean canStartSilent() {
            return true;                                   // it starts at volume 0 and fades in: the sound engine would skip a silent start
        }

        void fadeOut() {
            fading = true;
        }

        @Override
        public void tick() {
            level = fading ? Math.max(0f, level - 0.05f) : Math.min(1f, level + 0.04f);
            this.volume = level;
            if (fading && level <= 0f) stop();
        }
    }

    /** Plays the track under the current menu screen (nothing changes if it is already playing). */
    public static void play(SoundEvent event) {
        if (event == null || event == currentEvent && current != null && !current.isStopped()) return;
        stopNow();
        current = new Track(event);
        currentEvent = event;
        LotusBlight.LOGGER.info("Menu music: {}", event.getLocation());
        Minecraft.getInstance().getSoundManager().play(current);
    }

    private static void stopNow() {
        if (current != null) Minecraft.getInstance().getSoundManager().stop(current);
        current = null;
        currentEvent = null;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || current == null) return;
        var screen = Minecraft.getInstance().screen;
        boolean menu = screen instanceof EpisodesScreen || screen instanceof SaveSlotsScreen;
        if (!menu) {
            current.fadeOut();
            if (current.isStopped()) {
                current = null;
                currentEvent = null;
            }
        }
    }
}
