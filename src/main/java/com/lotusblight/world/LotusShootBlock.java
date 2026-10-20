package com.lotusblight.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A small surface shoot belonging to an existing main lotus anchor. Floats on water with its own
 * built-in pad (lotus_shoot_on_pad.json) - it used to be a separate block placed on top of a
 * vanilla lily pad, which left the flower hanging almost a whole block above the pad (the model
 * starts at the floor of its own cell) and let anyone knock the whole thing down by breaking the
 * pad with one hit or a boat, even though the shoot itself is meant to be unbreakable.
 */
public final class LotusShootBlock extends LotusBloomBlock {
    /** Pad and flower for picking; only the pad is solid to walk on, same as a lily pad. */
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 8, 15);
    private static final VoxelShape COLLISION = Block.box(1, 0, 1, 15, 1.5, 15);

    /** shootLines() was written for this block but never actually wired up anywhere - the pool of ambient lines sat completely unused. */
    private static final int AMBIENT_CHANCE = 30;
    private static final double AMBIENT_PLAYER_RADIUS = 12.0;

    public LotusShootBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, net.minecraft.core.BlockPos pos) {
        return 0.0f;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION;
    }

    /**
     * LotusBloomBlock (like plain Block) has no support check at all - without one, the water
     * under it draining or freezing left the shoot floating in mid-air forever (bug #2). Water
     * source below, like a lily pad, or solid ground for a hand-placed one. An old-style shoot
     * still standing on a vanilla pad survives until randomTick moves it down.
     */
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos belowPos = pos.below();
        BlockState below = level.getBlockState(belowPos);
        FluidState belowFluid = level.getFluidState(belowPos);
        return (belowFluid.is(Fluids.WATER) && belowFluid.isSource() && level.getFluidState(pos).isEmpty())
                || below.is(Blocks.LILY_PAD) || below.isFaceSturdy(level, belowPos, Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    public void randomTick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos belowPos = pos.below();
        if (level.getBlockState(belowPos).is(Blocks.LILY_PAD)) {
            // A shoot from before the pad was built in: take the pad's place on the water.
            level.setBlock(belowPos, state, 3);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            return;
        }
        // the shoots no longer talk: the old Lotus voice lines were cut with the conversation
    }
}
