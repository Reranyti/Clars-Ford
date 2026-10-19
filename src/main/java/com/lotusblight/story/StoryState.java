package com.lotusblight.story;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Where a world is in the story: the episode, the beat inside it and the flags the scenes set. It lives in the overworld's saved data,
 * so each save slot keeps its own story. The old Lotus branches (war / alliance) are gone; everything the plot needs to remember goes here.
 */
public final class StoryState extends SavedData {
    private static final String ID = "lotusblight_story";

    private int episode = 1;
    private String beat = "wake_up";
    private boolean lotusShown;
    private final Set<String> flags = new LinkedHashSet<>();

    private StoryState() {}

    public static StoryState get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(StoryState::load, StoryState::new, ID);
    }

    /** The Lotus is not shown in the world until the story reaches it (Act 1); every system that places or spreads it asks this first. */
    public static boolean lotusShown(MinecraftServer server) {
        return server != null && get(server).lotusShown;
    }

    public int episode() {
        return episode;
    }

    public String beat() {
        return beat;
    }

    public void setBeat(int episode, String beat) {
        this.episode = episode;
        this.beat = beat;
        setDirty();
    }

    public void showLotus(boolean shown) {
        this.lotusShown = shown;
        setDirty();
    }

    public boolean has(String flag) {
        return flags.contains(flag);
    }

    public void set(String flag) {
        if (flags.add(flag)) setDirty();
    }

    private static StoryState load(CompoundTag tag) {
        StoryState s = new StoryState();
        s.episode = Math.max(1, tag.getInt("Episode"));
        if (tag.contains("Beat")) s.beat = tag.getString("Beat");
        s.lotusShown = tag.getBoolean("LotusShown");
        for (Tag t : tag.getList("Flags", Tag.TAG_STRING)) s.flags.add(t.getAsString());
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("Episode", episode);
        tag.putString("Beat", beat);
        tag.putBoolean("LotusShown", lotusShown);
        ListTag list = new ListTag();
        for (String f : flags) list.add(StringTag.valueOf(f));
        tag.put("Flags", list);
        return tag;
    }
}
