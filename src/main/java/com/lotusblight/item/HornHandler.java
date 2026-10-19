package com.lotusblight.item;

import com.lotusblight.LotusBlight;
import com.lotusblight.data.LotusPlayerState;
import com.lotusblight.map.HornStatePacket;
import com.lotusblight.map.NetworkHandler;
import com.lotusblight.registry.ModItems;
import com.lotusblight.world.LotusEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What the horn does. Held in the hand, in hotbar slot 1, 2 or 3, each time Shift goes down:
 * slot 1 brings Honcho to you (30 seconds before it can again), slot 2 cleanses the one block at the crosshair
 * within 150 blocks (160 blocks a day), slot 3 is blown - it heals you and gives 12 golden hearts (once in two
 * in-game days).
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID)
public final class HornHandler {
    private static final String KEEP_KEY = "LotusBlightHornKept";

    /** Death: the horn does not drop; it is remembered and handed back with the new body. */
    @SubscribeEvent
    public static void onDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        boolean had = event.getDrops().removeIf(drop -> drop.getItem().is(ModItems.HORN.get()));
        if (had) player.getPersistentData().putBoolean(KEEP_KEY, true);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getOriginal().getPersistentData().getBoolean(KEEP_KEY)) {
            net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(player, new net.minecraft.world.item.ItemStack(ModItems.HORN.get()));
        }
    }

    static final double RANGE = 150.0;
    static final long HONCHO_COOLDOWN_TICKS = 30 * 20L;
    static final long BLOW_COOLDOWN_TICKS = 2 * 24000L;
    static final float GOLDEN_HEARTS = 24.0f;       // 12 hearts

    private static final Map<UUID, Boolean> SNEAKING = new HashMap<>();

    private HornHandler() {}

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        boolean down = player.isShiftKeyDown();
        boolean was = SNEAKING.getOrDefault(player.getUUID(), false);
        SNEAKING.put(player.getUUID(), down);
        if (!down || was) return;                                   // only the moment Shift goes down
        if (!player.getMainHandItem().is(ModItems.HORN.get())) return;
        int slot = player.getInventory().selected;
        if (slot > 2) return;
        use(player, slot);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SNEAKING.remove(event.getEntity().getUUID());
    }

    private static void use(ServerPlayer player, int slot) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        long day = level.getDayTime() / 24000L;
        switch (slot) {
            case 0 -> say(player, "Рог молчит: звать пока некого.");
            case 1 -> {
                int left = LotusPlayerState.hornUsesLeft(player, day);
                if (left <= 0) {
                    say(player, "Рог пуст до следующего дня.");
                    return;
                }
                HitResult hit = player.pick(RANGE, 1.0F, true);
                if (hit.getType() != HitResult.Type.BLOCK || !LotusEvents.cleanseOne(level, ((BlockHitResult) hit).getBlockPos())) {
                    say(player, "Здесь нечего очищать.");
                    return;
                }
                LotusPlayerState.hornSpendUse(player, day);
                level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.4f);
                say(player, "Очищено. Осталось " + (left - 1) + " из " + LotusPlayerState.HORN_DAILY_USES);
            }
            default -> {
                long ready = LotusPlayerState.hornBlowReadyAt(player);
                if (now < ready) {
                    long ticks = ready - now;
                    say(player, "Рог молчит ещё " + String.format("%.1f", ticks / 24000.0) + " дня");
                    return;
                }
                player.setHealth(player.getMaxHealth());
                player.setAbsorptionAmount(GOLDEN_HEARTS);
                LotusPlayerState.setHornBlowReadyAt(player, now + BLOW_COOLDOWN_TICKS);
                horn(player, 0.9f);
                say(player, "Рог трубит: раны затянулись, золотые сердца с тобой.");
            }
        }
        sync(player);
    }

    private static void say(ServerPlayer player, String text) {
        player.displayClientMessage(Component.literal(text), true);
    }

    private static void horn(ServerPlayer player, float pitch) {
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(2).value(),
                SoundSource.PLAYERS, 2.0f, pitch);
    }

    /** Tells the client what the bars should show. */
    public static void sync(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        long day = level.getDayTime() / 24000L;
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new HornStatePacket(
                LotusPlayerState.hornUsesLeft(player, day),
                (int) Math.max(0, LotusPlayerState.hornHonchoReadyAt(player) - now),
                (int) Math.max(0, LotusPlayerState.hornBlowReadyAt(player) - now)));
    }
}
