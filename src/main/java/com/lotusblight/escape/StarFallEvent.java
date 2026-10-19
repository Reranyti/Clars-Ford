package com.lotusblight.escape;

import com.lotusblight.LotusConfig;
import com.lotusblight.data.LotusPlayerState;
import com.lotusblight.data.OutbreakRecord;
import com.lotusblight.data.OutbreakSavedData;
import com.lotusblight.map.NetworkHandler;
import com.lotusblight.map.ShowStarFallPacket;
import net.exmo.meteor_shower.event.MeteorShowerEventManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The real, non-command trigger for StarFall (see {@link com.lotusblight.command.LotusCommands#starFallTrigger}
 * for the admin/testing path this mirrors). Fires once per player, once world infection crosses
 * {@link #TRIGGER_FRACTION} of {@link LotusConfig#WORLD_INFECTION_REFERENCE} - double the Побег
 * event's own 15% (see {@link LotusChaseEvent#maybeUnlock}), so StarFall reads as the later,
 * heavier escalation of the two. Only fires for a player who has already committed to a dialogue
 * branch (undecided players have no script to show) and only once ever per player, tracked via
 * {@link LotusPlayerState#hasSeenStarFall}.
 */
public final class StarFallEvent {
    private static final int SWEEP_INTERVAL_TICKS = 100;
    /** Was 0.30 - lowered with the slower spread; STAR_FALL_PERSONAL_TICKS is the per-player fallback. */
    public static final float TRIGGER_FRACTION = 0.10f;
    /** 6 in-game days after this player chose their branch, however far the world infection got. */
    public static final long STAR_FALL_PERSONAL_TICKS = 6L * 24000L;

    /**
     * Last war-script line index the server has already applied per player, or -1 right after the
     * scene was sent. StarFallLineReachedPacket comes from the client, so without this any client
     * could replay the final line (damage, rod removal, a radius-24 meteor patch) as often as it
     * liked, with or without ever being shown the scene.
     */
    private static final Map<UUID, Integer> WAR_SCENE_PROGRESS = new HashMap<>();

    /**
     * One ShowerScale.LARGE call dropped 117-188 fallen stars at once, each living up to 600 ticks
     * with a 140-point trail updated every tick - everything on screen at the same moment, once per
     * player caught by the sweep. That's what brought the game down when StarFall started. The
     * shower now comes in a few SMALL waves (10-30 stars each) spread over half a minute, and only
     * one shower runs per dimension however many players trigger it together.
     */
    private static final int SHOWER_WAVES = 3;
    private static final int SHOWER_WAVE_INTERVAL_TICKS = 200;
    private static final Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, int[]> SHOWERS = new HashMap<>();

    /** Starts the StarFall meteor shower in this dimension unless one is already falling there. */
    public static void startShower(ServerLevel level) {
        SHOWERS.putIfAbsent(level.dimension(), new int[]{SHOWER_WAVES, 0});
    }

    private static void tickShowers(MinecraftServer server) {
        if (SHOWERS.isEmpty()) return;
        var it = SHOWERS.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            ServerLevel level = server.getLevel(entry.getKey());
            int[] state = entry.getValue(); // {waves left, ticks until the next one}
            if (level == null || state[0] <= 0) {
                it.remove();
                continue;
            }
            if (--state[1] > 0) continue;
            if (!level.players().isEmpty()) {
                MeteorShowerEventManager.forceShower(level, MeteorShowerEventManager.ShowerScale.SMALL);
            }
            state[0]--;
            state[1] = SHOWER_WAVE_INTERVAL_TICKS;
        }
    }

    public static void beginWarScene(ServerPlayer player) {
        WAR_SCENE_PROGRESS.put(player.getUUID(), -1);
    }

    /** True if this line is the next one the player may trigger; the final line closes the scene. Lines may be skipped (the shelter line is), never replayed. */
    public static boolean acceptWarLine(ServerPlayer player, int lineIndex, int finalLineIndex) {
        Integer last = WAR_SCENE_PROGRESS.get(player.getUUID());
        if (last == null || lineIndex <= last || lineIndex > finalLineIndex) return false;
        if (lineIndex == finalLineIndex) {
            WAR_SCENE_PROGRESS.remove(player.getUUID());
        } else {
            WAR_SCENE_PROGRESS.put(player.getUUID(), lineIndex);
        }
        return true;
    }

    /** Nothing from a previous world may keep falling into the next one (integrated server restarts). */
    @SubscribeEvent
    public void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        SHOWERS.clear();
        WAR_SCENE_PROGRESS.clear();
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        WAR_SCENE_PROGRESS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        tickShowers(server);
        if (server.getTickCount() % SWEEP_INTERVAL_TICKS != 0) return;
        if (!LotusConfig.STARFALL_ENABLED.get()) return;

        long totalInfected = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (OutbreakRecord outbreak : OutbreakSavedData.get(level).allOutbreaks()) {
                totalInfected += outbreak.infectedBlockCount();
            }
        }
        long threshold = (long) (LotusConfig.WORLD_INFECTION_REFERENCE.get() * TRIGGER_FRACTION);
        boolean worldReady = totalInfected >= threshold;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int branch = LotusPlayerState.getDialogueBranch(player);
            if (branch == LotusPlayerState.BRANCH_UNDECIDED) continue;
            if (LotusPlayerState.hasSeenStarFall(player)) continue;
            if (!worldReady && LotusPlayerState.ticksSinceBranchChosen(player) < STAR_FALL_PERSONAL_TICKS) continue;

            LotusPlayerState.markStarFallSeen(player);
            // Inverted on purpose: "на войне вы сражаетесь с лотосом а не с НИМ [Звёздным Светом]" -
            // a RESISTANCE player is fighting the same enemy Star Light is (the Lotus), so they get
            // the friendly/allied script; an ALLIANCE player sided WITH the Lotus, so Star Light
            // treats them as hostile and they get the damage/rod-removal script instead.
            boolean allianceBranch = branch == LotusPlayerState.BRANCH_RESISTANCE;
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ShowStarFallPacket(allianceBranch));
            if (!allianceBranch) beginWarScene(player);
            if (player.level() instanceof ServerLevel level) {
                startShower(level);
            }
        }
    }

}
