package com.lotusblight.gametest;

import com.lotusblight.LotusBlight;
import com.lotusblight.data.WorldVersionData;
import com.lotusblight.world.WorldMigrations;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** The world-version rules: untouched worlds stay untouched, each step runs once, newer steps run later, a failing step stops the rest. */
@GameTestHolder(LotusBlight.MODID)
@PrefixGameTestTemplate(false)
public final class WorldMigrationGameTests {
    private WorldMigrationGameTests() {}

    @GameTest(template = "honcho_arena", timeoutTicks = 100, batch = "migrations")
    public static void migrationRules(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Integer> ran = new ArrayList<>();
        List<WorldMigrations.Step> steps = new ArrayList<>(List.of(
                WorldMigrations.step(1, "one", (s, w) -> ran.add(1)),
                WorldMigrations.step(2, "two", (s, w) -> ran.add(2))));

        // a world the mod never touched: nothing runs, nothing is recorded
        int before = WorldVersionData.get(level).version();
        if (WorldMigrations.apply(level.getServer(), level, steps, 2, false) != 0 || !ran.isEmpty()) helper.fail("a step ran in a world the mod never touched");
        if (WorldVersionData.get(level).version() != before) helper.fail("a version was written into a world the mod never touched");

        // a world the mod has made something in: both run, in order, once
        if (WorldMigrations.apply(level.getServer(), level, steps, 2, true) != 2 || !ran.equals(List.of(1, 2))) helper.fail("steps did not run in order: " + ran);
        if (WorldVersionData.get(level).version() != 2) helper.fail("the version was not recorded");
        if (WorldMigrations.apply(level.getServer(), level, steps, 2, true) != 0) helper.fail("a step ran twice");

        // a newer build brings step 3: only that one runs
        steps.add(WorldMigrations.step(3, "three", (s, w) -> ran.add(3)));
        if (WorldMigrations.apply(level.getServer(), level, steps, 3, true) != 1 || !ran.equals(List.of(1, 2, 3))) helper.fail("only the new step should have run: " + ran);

        // a failing step stops the rest and the world keeps the version it really reached
        steps.add(WorldMigrations.step(4, "four fails", (s, w) -> { throw new IllegalStateException("on purpose"); }));
        steps.add(WorldMigrations.step(5, "five waits", (s, w) -> ran.add(5)));
        if (WorldMigrations.apply(level.getServer(), level, steps, 5, true) != 0 || ran.contains(5)) helper.fail("a step ran after a failed one");
        if (WorldVersionData.get(level).version() != 3) helper.fail("the failed step must not move the version: " + WorldVersionData.get(level).version());
        WorldVersionData.get(level).setVersion(before);                 // leave the test world as it was
        helper.succeed();
    }
}
