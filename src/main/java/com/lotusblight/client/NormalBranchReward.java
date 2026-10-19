package com.lotusblight.client;

import com.lotusblight.data.LotusPlayerState;
import com.lotusblight.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** What the player is left with when the fight on the desktop ends its way (see {@code Finale3.EXIT_CODE}). */
public final class NormalBranchReward {
    /** The exit code the fight's process ends with when its closing scene has played out. */
    public static final int FINALE_EXIT_CODE = 77;

    private NormalBranchReward() {}

    /** Puts the horn in the player's hands (or at their feet if the bag is full), once. */
    public static void grantHorn(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return;
        UUID id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) LotusPlayerState.setNormalBranchExitPending(player, false);       // the fight has been played to its end
            if (player == null || LotusPlayerState.isHornGiven(player) || player.getInventory().contains(new ItemStack(ModItems.HORN.get()))) return;
            ItemStack horn = new ItemStack(ModItems.HORN.get());
            if (!player.getInventory().add(horn)) player.drop(horn, false);
            LotusPlayerState.markHornGiven(player);
            com.lotusblight.item.HornHandler.sync(player);
        });
    }
}
