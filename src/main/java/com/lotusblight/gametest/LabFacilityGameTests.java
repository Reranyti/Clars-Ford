package com.lotusblight.gametest;

import com.lotusblight.LotusBlight;
import com.lotusblight.escape.LabFacility;
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
import java.util.Map;
import java.util.Set;

/**
 * The big laboratory, built for real in the test world: it has the size and the rooms that were promised, every kind of obstacle lies
 * on the shortest way, doorways are open where halls join rooms and closed elsewhere, and the way from the start to the exit can be
 * walked. Only for ./gradlew runGameTestServer.
 */
@GameTestHolder(LotusBlight.MODID)
@PrefixGameTestTemplate(false)
public final class LabFacilityGameTests {
    private LabFacilityGameTests() {}

    @GameTest(template = "honcho_arena", timeoutTicks = 1200, batch = "lab")
    public static void facilityLayout(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos floor = helper.absolutePos(new BlockPos(0, 1, 0));
        LabFacility lab = new LabFacility(level, floor, Direction.EAST, 20261010L);
        lab.build();

        // 1. the size that was promised
        if (lab.roomCount() != 36 || lab.sizeI() < 100 || lab.sizeW() < 100) helper.fail("the laboratory is too small: " + lab.sizeI() + "x" + lab.sizeW() + ", rooms " + lab.roomCount());
        if (lab.connectorCount() < 36 + 3) helper.fail("not enough halls for several ways: " + lab.connectorCount());

        // 2. every kind of obstacle is on the shortest way
        Map<LabFacility.Hall, Integer> onWay = lab.hallTypesOnPath();
        for (LabFacility.Hall h : new LabFacility.Hall[]{LabFacility.Hall.CROUCH, LabFacility.Hall.PIT, LabFacility.Hall.RUBBLE}) {
            if (onWay.getOrDefault(h, 0) == 0) helper.fail("no " + h + " hall on the shortest way: " + onWay);
        }

        // 3. doorways: open (3 wide, 4 high) between linked rooms, a solid wall between the others
        for (int a = 0; a < LabFacility.GRID_I; a++) for (int b = 0; b < LabFacility.GRID_W; b++) {
            if (a + 1 < LabFacility.GRID_I) checkDoor(helper, level, lab, true, a, b, lab.eastLinked(a, b));
            if (b + 1 < LabFacility.GRID_W) checkDoor(helper, level, lab, false, a, b, lab.northLinked(a, b));
        }

        // 4. the start can walk to the exit (a crouching player fits; a pit with water counts as crossable, its width is fixed at 3)
        if (!reachable(level, lab)) helper.fail("no walkable way from the start to the exit");
        helper.succeed();
    }

    private static void checkDoor(GameTestHelper helper, ServerLevel level, LabFacility lab, boolean eastWest, int a, int b, boolean linked) {
        int wallA = eastWest ? lab.roomI(a) + 12 : lab.roomCW(b) + 6;                 // first room's wall slice along the axis
        int wallB = eastWest ? lab.roomI(a + 1) : lab.roomCW(b + 1) - 6;              // second room's wall slice
        int centre = eastWest ? lab.roomCW(b) : lab.roomCI(a);
        for (int wall : new int[]{wallA, wallB}) {
            for (int across = -1; across <= 1; across++) for (int h = 1; h <= 4; h++) {
                BlockPos p = eastWest ? lab.at(wall, centre + across, h) : lab.at(centre + across, wall, h);
                boolean air = level.getBlockState(p).isAir();
                if (linked && !air) helper.fail("doorway closed between rooms (" + a + "," + b + ") and the next one, " + (eastWest ? "east" : "north") + " at " + p.toShortString());
                if (!linked && air) helper.fail("a hole in the wall where there is no hall, rooms (" + a + "," + b + ") " + (eastWest ? "east" : "north") + " at " + p.toShortString());
            }
        }
    }

    private static boolean fits(ServerLevel level, BlockPos feet, double height) {
        double x = feet.getX() + 0.5, z = feet.getZ() + 0.5;
        return level.noCollision(new AABB(x - 0.3, feet.getY(), z - 0.3, x + 0.3, feet.getY() + height, z + 0.3));
    }

    /** Whether a column of the floor plan can be stood on (or jumped over, for a pit) by a crouching player. */
    private static boolean open(ServerLevel level, LabFacility lab, int i, int w) {
        BlockPos floor = lab.at(i, w, 0), feet = lab.at(i, w, 1);
        boolean solidFloor = !level.getBlockState(floor).getCollisionShape(level, floor).isEmpty();
        if (solidFloor && fits(level, feet, 1.5)) return true;
        if (solidFloor && !level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() && fits(level, feet.above(), 1.5)) return true;   // rubble to step over
        return !solidFloor && level.getBlockState(lab.at(i, w, -3)).is(Blocks.WATER) && fits(level, feet, 1.5);                                  // a pit to jump
    }

    private static boolean reachable(ServerLevel level, LabFacility lab) {
        Vec3 s = lab.startPos();
        BlockPos sp = BlockPos.containing(s);
        AABB exit = lab.exitBox();
        int i0 = -2, i1 = lab.sizeI() + 2, w0 = lab.roomCW(0) - 8, w1 = lab.roomCW(LabFacility.GRID_W - 1) + 8;
        int si = lab.roomCI(0) - 2, sw = lab.roomCW(lab.startRow());
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{si, sw});
        seen.add(key(si, sw));
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            BlockPos here = lab.at(c[0], c[1], 1);
            if (exit.contains(here.getX() + 0.5, here.getY() + 0.5, here.getZ() + 0.5)) return true;
            // the grid is in (i, w) but the world is in x/z: step in the lab's own axes
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int i = c[0] + d[0], w = c[1] + d[1];
                if (i < i0 || i > i1 || w < w0 || w > w1 || !seen.add(key(i, w))) continue;
                if (open(level, lab, i, w)) queue.add(new int[]{i, w});
            }
        }
        return false;
    }

    private static long key(int i, int w) {
        return ((long) i << 32) ^ (w & 0xffffffffL);
    }
}
