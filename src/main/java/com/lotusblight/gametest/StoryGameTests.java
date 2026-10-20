package com.lotusblight.gametest;

import com.lotusblight.LotusBlight;
import com.lotusblight.story.Beats;
import com.lotusblight.story.DreamWorld;
import com.lotusblight.story.StoryState;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;

/** The story skeleton: the beat list is in order and complete, the story state keeps its flags, the dream world exists and nothing in it breaks. */
@GameTestHolder(LotusBlight.MODID)
@PrefixGameTestTemplate(false)
public final class StoryGameTests {
    private StoryGameTests() {}

    @GameTest(template = "honcho_arena", timeoutTicks = 20, batch = "story")
    public static void beatsAreInOrder(GameTestHelper helper) {
        Set<String> ids = new HashSet<>();
        int lastEpisode = 0;
        for (Beats.Beat b : Beats.all()) {
            if (!ids.add(b.id())) helper.fail("two beats share the id " + b.id());
            if (b.episode() < lastEpisode) helper.fail("the beat " + b.id() + " goes back to an earlier episode");
            lastEpisode = b.episode();
            if (b.title() == null || b.title().isBlank()) helper.fail("the beat " + b.id() + " has no title");
            if (Beats.find(b.id()).isEmpty()) helper.fail("find() does not know " + b.id());
        }
        if (Beats.all().isEmpty()) helper.fail("no beats");
        String first = Beats.all().get(0).id();
        if (Beats.next(first).isEmpty()) helper.fail("the first beat has no next beat");
        String last = Beats.all().get(Beats.all().size() - 1).id();
        if (Beats.next(last).isPresent()) helper.fail("the last beat has a next beat");
        if (Beats.next("no_such_beat").isPresent()) helper.fail("an unknown beat has a next beat");
        helper.succeed();
    }

    @GameTest(template = "honcho_arena", timeoutTicks = 20, batch = "story")
    public static void storyStateKeepsItsData(GameTestHelper helper) {
        StoryState state = StoryState.get(helper.getLevel().getServer());
        boolean hadLotus = StoryState.lotusShown(helper.getLevel().getServer());
        int episode = state.episode();
        String beat = state.beat();
        state.set("test_flag");
        state.setBeat(2, "assault");
        state.showLotus(true);
        // what is saved must read back the same
        CompoundTag tag = state.save(new CompoundTag());
        if (tag.getInt("Episode") != 2 || !"assault".equals(tag.getString("Beat")) || !tag.getBoolean("LotusShown")) helper.fail("the saved tag is wrong: " + tag);
        if (!state.has("test_flag") || state.has("other_flag")) helper.fail("flags are wrong");
        state.showLotus(hadLotus);
        state.setBeat(episode, beat);
        helper.succeed();
    }

    @GameTest(template = "honcho_arena", timeoutTicks = 20, batch = "story")
    public static void dreamWorldExists(GameTestHelper helper) {
        if (DreamWorld.level(helper.getLevel().getServer()) == null) helper.fail("the dream dimension was not loaded");
        helper.succeed();
    }
}
