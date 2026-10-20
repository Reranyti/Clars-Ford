package com.lotusblight.story;

import com.lotusblight.LotusBlight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The dream world: a strange forest where blocks do not break (the author's rules for the dream of episode 3: whichever way you walk
 * you come to a desert, eyes hang in the sky, a castle stands in the centre; those parts are not built yet). Entered and left with
 * {@link #enter} and {@link #wake}; the player's bed spot in the overworld is where they wake up.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID)
public final class DreamWorld {
    public static final ResourceKey<Level> DREAM =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation(LotusBlight.MODID, "dream"));

    private DreamWorld() {}

    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(DREAM);
    }

    /** Puts the player into the dream, on the grass at the origin. */
    public static boolean enter(ServerPlayer player) {
        ServerLevel dream = level(player.getServer());
        if (dream == null) return false;
        BlockPos at = dream.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.ZERO);
        player.teleportTo(dream, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, player.getYRot(), player.getXRot());
        return true;
    }

    /** Wakes the player in the overworld, at their bed or at the world spawn. */
    public static boolean wake(ServerPlayer player) {
        ServerLevel overworld = player.getServer().overworld();
        BlockPos bed = player.getRespawnPosition();
        BlockPos at = bed != null && player.getRespawnDimension() == Level.OVERWORLD ? bed : overworld.getSharedSpawnPos();
        player.teleportTo(overworld, at.getX() + 0.5, at.getY() + 0.1, at.getZ() + 0.5, player.getYRot(), player.getXRot());
        return true;
    }

    /** Nothing breaks in the dream. */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(DREAM)) event.setCanceled(true);
    }
}
