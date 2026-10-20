package com.lotusblight.map;

import com.lotusblight.LotusBlight;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * Owns the mod's single {@link SimpleChannel}, used to push the player's
 * currently-visible outbreak markers from server to client (see
 * {@link MapSyncPacket}) so client code never has to scan blocks to know
 * where outbreaks are.
 *
 * {@link #register()} must be called once during mod construction — it is
 * NOT wired up automatically (channel registration isn't an event and can't
 * be done via {@code @Mod.EventBusSubscriber}). Add this line to
 * {@code LotusBlight}'s constructor:
 *
 * <pre>    com.lotusblight.map.NetworkHandler.register();</pre>
 */
public final class NetworkHandler {
    // Bumped whenever a packet is added, removed or changes shape - a mismatched client is refused
    // at login instead of failing to decode mid-game.
    private static final String PROTOCOL_VERSION = "11";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(LotusBlight.MODID, "map_sync"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static boolean registered = false;

    private NetworkHandler() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // Directions are explicit: without them any packet was accepted both ways, so a client could
        // send e.g. ShowStarFallPacket to a LAN host and play the scene on the host's screen.
        int id = 0;
        CHANNEL.registerMessage(id++, MapSyncPacket.class, MapSyncPacket::encode, MapSyncPacket::decode, MapSyncPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, PlayerStateSyncPacket.class, PlayerStateSyncPacket::encode, PlayerStateSyncPacket::decode, PlayerStateSyncPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, ShowInnerVoicePacket.class, ShowInnerVoicePacket::encode, ShowInnerVoicePacket::decode, ShowInnerVoicePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, GlandSyncPacket.class, GlandSyncPacket::encode, GlandSyncPacket::decode, GlandSyncPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, OpenCommandBookPacket.class, OpenCommandBookPacket::encode, OpenCommandBookPacket::decode, OpenCommandBookPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, MeteoriteMarkerSyncPacket.class, MeteoriteMarkerSyncPacket::encode, MeteoriteMarkerSyncPacket::decode, MeteoriteMarkerSyncPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, ReputationSyncPacket.class, ReputationSyncPacket::encode, ReputationSyncPacket::decode, ReputationSyncPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, NormalBranchScenePacket.class, NormalBranchScenePacket::encode, NormalBranchScenePacket::decode, NormalBranchScenePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, NormalBranchSilencePacket.class, NormalBranchSilencePacket::encode, NormalBranchSilencePacket::decode, NormalBranchSilencePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, HornStatePacket.class, HornStatePacket::encode, HornStatePacket::decode, HornStatePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, ChromoStatePacket.class, ChromoStatePacket::encode, ChromoStatePacket::decode, ChromoStatePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, OpenDialoguePacket.class, OpenDialoguePacket::encode, OpenDialoguePacket::decode, OpenDialoguePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, StoryFlagPacket.class, StoryFlagPacket::encode, StoryFlagPacket::decode, StoryFlagPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, DiarySyncPacket.class, DiarySyncPacket::encode, DiarySyncPacket::decode, DiarySyncPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, QuestSyncPacket.class, QuestSyncPacket::encode, QuestSyncPacket::decode, QuestSyncPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, ChromoRequestPacket.class, ChromoRequestPacket::encode, ChromoRequestPacket::decode, ChromoRequestPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, NormalBranchExitPacket.class, NormalBranchExitPacket::encode, NormalBranchExitPacket::decode, NormalBranchExitPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}
