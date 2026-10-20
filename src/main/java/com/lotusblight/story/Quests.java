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
 * is met: every goal is done (a goal is a story flag set or a diary entry written). Goals are in {@code data/lotusblight/quests/quests.json}
 * ({@code "<id>": {"goals": [{"flag": "<flag>"}, {"diary": "<entry id>", "hidden": true}]}}, or one condition written straight in the entry); the texts and the kind (story / regular / special) are client
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

    /** The goals of a quest: its "goals" list, or the single condition written straight in the quest's entry. */
    private static java.util.List<JsonObject> goals(JsonObject c) {
        java.util.List<JsonObject> out = new java.util.ArrayList<>();
        if (c == null) return out;
        if (c.has("goals") && c.get("goals").isJsonArray()) {
            for (JsonElement e : c.getAsJsonArray("goals")) if (e.isJsonObject()) out.add(e.getAsJsonObject());
        } else if (c.has("flag") || c.has("diary")) {
            out.add(c);
        }
        return out;
    }

    private static boolean met(StoryState st, JsonObject g) {
        return (g.has("flag") && st.has(g.get("flag").getAsString())) || (g.has("diary") && st.diary().contains(g.get("diary").getAsString()));
    }

    /**
     * The state of every goal of a quest, one letter each: D done, T to do, U unknown (a goal marked "hidden" stays unknown until the goals
     * before it are done). A quest without goals has an empty string.
     */
    public static String goalStates(StoryState st, String questId) {
        StringBuilder sb = new StringBuilder();
        boolean earlierAllDone = true;
        for (JsonObject g : goals(conditions().get(questId))) {
            boolean done = met(st, g);
            boolean hidden = g.has("hidden") && g.get("hidden").getAsBoolean();
            sb.append(done ? 'D' : (hidden && !earlierAllDone ? 'U' : 'T'));
            if (!done) earlierAllDone = false;
        }
        return sb.toString();
    }

    /** Finishes the given quests whose goals are all met and refreshes the client; call after a flag is set or a diary entry is written. */
    public static void check(ServerPlayer player) {
        StoryState st = StoryState.get(player.getServer());
        for (var q : st.quests().entrySet().stream().filter(e -> !e.getValue()).map(Map.Entry::getKey).toList()) {
            var gs = goals(conditions().get(q));
            if (!gs.isEmpty() && gs.stream().allMatch(g -> met(st, g))) complete(player, q);
        }
        sync(player);
    }

    public static void sync(ServerPlayer player) {
        StoryState st = StoryState.get(player.getServer());
        Map<String, Boolean> q = st.quests();
        java.util.List<String> states = new java.util.ArrayList<>();
        for (String id : q.keySet()) states.add(goalStates(st, id));
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new QuestSyncPacket(new java.util.ArrayList<>(q.keySet()), new java.util.ArrayList<>(q.values()), states));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }
}
