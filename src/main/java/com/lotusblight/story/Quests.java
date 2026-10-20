package com.lotusblight.story;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lotusblight.LotusBlight;
import com.lotusblight.map.NetworkHandler;
import com.lotusblight.map.QuestSyncPacket;
import net.minecraft.network.chat.Component;
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
 * The quests: story, ordinary and special ones. A quest is given (by the story, a command or a dialogue) and finishes when its condition
 * is met: a story flag is set or a diary entry is written. Conditions are in {@code data/lotusblight/quests/quests.json}
 * ({@code "<id>": {"flag": "<flag>"}} or {@code {"diary": "<entry id>"}}); the texts and the kind (story / regular / special) are client
 * files {@code assets/lotusblight/quests/<id>.json}. The author writes the quests; the mechanism decides no story.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID)
public final class Quests {
    private static Map<String, JsonObject> conditions;

    private Quests() {}

    private static synchronized Map<String, JsonObject> conditions() {
        if (conditions != null) return conditions;
        Map<String, JsonObject> out = new HashMap<>();
        try (InputStream in = Quests.class.getResourceAsStream("/data/lotusblight/quests/quests.json")) {
            if (in != null) {
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                    if (!e.getKey().startsWith("_") && e.getValue().isJsonObject()) out.put(e.getKey(), e.getValue().getAsJsonObject());
                }
            }
        } catch (Exception e) {
            LotusBlight.LOGGER.warn("Could not read the quest conditions: {}", e.toString());
        }
        conditions = out;
        return out;
    }

    /** Gives the player a quest. */
    public static boolean start(ServerPlayer player, String id) {
        boolean ok = StoryState.get(player.getServer()).startQuest(id);
        if (ok) {
            player.displayClientMessage(Component.literal("Новое задание."), true);
            sync(player);
            check(player);                                  // it may already be fulfilled
        }
        return ok;
    }

    /** Marks a quest done by hand (a command, a script). */
    public static boolean complete(ServerPlayer player, String id) {
        boolean ok = StoryState.get(player.getServer()).completeQuest(id);
        if (ok) {
            player.displayClientMessage(Component.literal("Задание выполнено."), true);
            sync(player);
        }
        return ok;
    }

    /** Finishes the given quests whose condition is met; call after a flag is set or a diary entry is written. */
    public static void check(ServerPlayer player) {
        StoryState st = StoryState.get(player.getServer());
        for (var q : st.quests().entrySet().stream().filter(e -> !e.getValue()).map(Map.Entry::getKey).toList()) {
            JsonObject c = conditions().get(q);
            if (c == null) continue;
            boolean met = (c.has("flag") && st.has(c.get("flag").getAsString()))
                    || (c.has("diary") && st.diary().contains(c.get("diary").getAsString()));
            if (met) complete(player, q);
        }
    }

    public static void sync(ServerPlayer player) {
        Map<String, Boolean> q = StoryState.get(player.getServer()).quests();
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new QuestSyncPacket(new java.util.ArrayList<>(q.keySet()),
                new java.util.ArrayList<>(q.values())));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }
}
