package com.lotusblight.item;

import com.lotusblight.story.Diary;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The scanner: the hero uses it to study things; what it looks at is written into the diary if the author has a note for it (see
 * {@link Diary}). It looks at what is under the crosshair, a block or a creature, up to {@link #RANGE} blocks away.
 */
public class ScannerItem extends Item {
    private static final double RANGE = 8.0;

    public ScannerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(stack);

        String name = lookAt(level, player);
        level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6f, 1.6f);
        if (name == null) {
            sp.displayClientMessage(Component.literal("Сканер ничего не видит."), true);
        } else {
            String entry = Diary.entryFor(name);
            if (entry == null) {
                sp.displayClientMessage(Component.literal("Сканер: нечего записать."), true);
            } else if (Diary.add(sp, entry)) {
                sp.displayClientMessage(Component.literal("Новая запись в дневнике."), true);
                level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.2f);
            } else {
                sp.displayClientMessage(Component.literal("Это уже в дневнике."), true);
            }
        }
        sp.getCooldowns().addCooldown(this, 10);
        return InteractionResultHolder.success(stack);
    }

    /** The registry name of the block or creature under the crosshair, or null. */
    private static String lookAt(Level level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(RANGE));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        double blockDist = block.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : block.getLocation().distanceToSqr(eye);
        EntityHitResult entity = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(level, player, eye, end,
                new AABB(eye, end).inflate(1.0), e -> !e.isSpectator() && e.isPickable());
        if (entity != null && entity.getLocation().distanceToSqr(eye) < blockDist) {
            return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getEntity().getType()).toString();
        }
        if (block.getType() == HitResult.Type.BLOCK) {
            return BuiltInRegistries.BLOCK.getKey(level.getBlockState(block.getBlockPos()).getBlock()).toString();
        }
        return null;
    }
}
