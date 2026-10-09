package com.lotusblight.gametest;

import com.lotusblight.LotusBlight;
import com.lotusblight.escape.LabProto;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * The prototype of the redesigned lab, built for real in the test world. These checks guard what the design promises:
 * the cart's way leads out on the left and into a dead end on the right, the low tunnel is crouch-only, every pit is
 * jumpable and has a ladder back, and the corridors can be walked. Only for ./gradlew runGameTestServer.
 */
@GameTestHolder(LotusBlight.MODID)
@PrefixGameTestTemplate(false)
public final class LabProtoGameTests {
    private LabProtoGameTests() {}

    @GameTest(template = "honcho_arena", timeoutTicks = 600, batch = "lab")
    public static void protoLayout(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos floor = helper.absolutePos(new BlockPos(0, 1, 0));
        LabProto.Layout lab = new LabProto.Layout(level, floor, Direction.EAST);
        lab.build();

        // 1. the cart: left leads to the exit, right ends in the dead end
        BlockPos leftEnd = lab.railEnd(true);
        if (!lab.exitBox().contains(Vec3.atCenterOf(leftEnd))) helper.fail("the left branch does not end in the exit: " + leftEnd.toShortString());
        BlockPos rightEnd = lab.railEnd(false);
        if (lab.exitBox().contains(Vec3.atCenterOf(rightEnd))) helper.fail("the right branch leads to the exit too");
        if (!rightEnd.equals(lab.at(LabProto.Layout.RIGHT_END, 3, 1))) helper.fail("the right branch does not run to its dead end: " + rightEnd.toShortString());
        lab.setFork(false);

        // 2. the low tunnel: a crouch fits, standing does not
        for (int i = LabProto.Layout.TUNNEL_START + 1; i <= LabProto.Layout.TUNNEL_END - 1; i++) {
            if (!fits(level, lab.at(i, 0, 1), 1.5)) helper.fail("a crouching player does not fit in the tunnel at slice " + i);
            if (fits(level, lab.at(i, 0, 1), 1.8)) helper.fail("a standing player fits in the tunnel at slice " + i + " (it must be crouch-only)");
        }

        // 3. the pits: at most 3 wide, solid platforms either side, a ladder up at both walls, water at the bottom
        for (int[] pit : LabProto.Layout.PITS) {
            if (pit[1] - pit[0] + 1 > 3) helper.fail("a pit wider than 3 is not a fair jump: " + pit[0] + "-" + pit[1]);
            for (int w = -4; w <= 4; w++) {
                if (level.getBlockState(lab.at(pit[0] - 1, w, 0)).isAir()) helper.fail("no platform before the pit at " + pit[0]);
                if (level.getBlockState(lab.at(pit[1] + 1, w, 0)).isAir()) helper.fail("no platform after the pit at " + pit[1]);
                for (int i = pit[0]; i <= pit[1]; i++) {
                    if (i == pit[0] && Math.abs(w) == 4) continue;      // the ladder column: checked below
                    if (!level.getBlockState(lab.at(i, w, 0)).isAir()) helper.fail("the pit is not empty at slice " + i + ", w=" + w);
                    if (!level.getBlockState(lab.at(i, w, -3)).is(Blocks.WATER)) helper.fail("no water at the bottom of the pit at slice " + i + ", w=" + w);
                }
            }
            for (int w : new int[]{-4, 4}) if (!level.getBlockState(lab.at(pit[0], w, 0)).is(Blocks.LADDER)) helper.fail("no ladder at the pit near " + pit[0]);
        }

        // 4. the first half can be walked from the start to the parkour hall (rubble may be stepped over)
        if (!walkable(level, lab, lab.at(1, 0, 1), LabProto.Layout.PARKOUR_START)) helper.fail("no walkable way from the room to the parkour hall");
        helper.succeed();
    }

    /** Whether a player box of the given height fits with its feet on the floor of {@code feet}'s cell. */
    private static boolean fits(ServerLevel level, BlockPos feet, double height) {
        double x = feet.getX() + 0.5, z = feet.getZ() + 0.5;
        return level.noCollision(new AABB(x - 0.3, feet.getY(), z - 0.3, x + 0.3, feet.getY() + height, z + 0.3));
    }

    /** Breadth-first walk over the floor plan: a cell is open if a crouching player fits on it, or on a one-block step above it. */
    private static boolean walkable(ServerLevel level, LabProto.Layout lab, BlockPos start, int targetSlice) {
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{1, 0});
        seen.add(key(1, 0));
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            if (c[0] >= targetSlice) return true;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int i = c[0] + d[0], w = c[1] + d[1];
                if (i < 0 || i > targetSlice || Math.abs(w) > 9 || !seen.add(key(i, w))) continue;
                BlockPos cell = lab.at(i, w, 1);
                boolean open = fits(level, cell, 1.5) && !level.noCollision(new AABB(cell.getX() + 0.2, cell.getY() - 0.5, cell.getZ() + 0.2, cell.getX() + 0.8, cell.getY() - 0.01, cell.getZ() + 0.8)) // floor under it
                        || (!fits(level, cell, 1.5) && fits(level, cell.above(), 1.5) && !level.getBlockState(cell).getCollisionShape(level, cell).isEmpty());
                if (open) queue.add(new int[]{i, w});
            }
        }
        return false;
    }

    private static long key(int i, int w) {
        return ((long) i << 32) ^ (w & 0xffffffffL);
    }
}
