package com.lotusblight.story;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lotusblight.LotusBlight;
import com.lotusblight.map.DiarySyncPacket;
import com.lotusblight.map.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * The player's diary: what the scanner (and the story) wrote down. The entries live in {@link StoryState}; the texts are client files
 * ({@code assets/lotusblight/diary/<id>.json}); what the scanner writes down for what it looks at is in
 * {@code data/lotusblight/diary/scan_targets.json} (registry name of a block or entity -> entry id). The author writes the entries and the
 * targets; the mechanism does not decide any of the story.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID)
public final class Diary {
    private static Map<String, String> targets;

    private Diary() {}

    /** The entry id the scanner writes for a block/entity registry name, or null. */
    public static synchronized String entryFor(String registryName) {
        if (targets == null) targets = loadTargets();
        return targets.get(registryName);
    }

    private static Map<String, String> loadTargets() {
        Map<String, String> out = new HashMap<>();
        try (InputStream in = Diary.class.getResourceAsStream("/data/lotusblight/diary/scan_targets.json")) {
            if (in == null) return out;
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : o.entrySet()) {
                if (e.getKey().startsWith("_")) continue;                 // "_comment" and the like
                out.put(e.getKey(), e.getValue().getAsString());
            }
        } catch (Exception e) {
            LotusBlight.LOGGER.warn("Could not read the diary scan targets: {}", e.toString());
        }
        return out;
    }

    /** Adds an entry for the player and tells the client. Returns false if it was already there. */
    public static boolean add(ServerPlayer player, String id) {
        boolean added = StoryState.get(player.getServer()).addDiary(id);
        if (added) sync(player);
        return added;
    }

    public static void sync(ServerPlayer player) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new DiarySyncPacket(StoryState.get(player.getServer()).diary()));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }
}
