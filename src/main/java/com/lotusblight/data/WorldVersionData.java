package com.lotusblight.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Which version of the mod's world content a save has been brought up to (see {@link com.lotusblight.world.WorldMigrations}).
 * 0 means "never migrated". Only ever written for a world the mod has already made something in.
 */
public class WorldVersionData extends SavedData {
    public static final String ID = "lotusblight_world_version";

    private int version = 0;

    public static WorldVersionData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(WorldVersionData::load, WorldVersionData::new, ID);
    }

    public WorldVersionData() {
    }

    public int version() {
        return version;
    }

    public void setVersion(int version) {
        if (this.version == version) return;
        this.version = version;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("Version", version);
        return tag;
    }

    public static WorldVersionData load(CompoundTag tag) {
        WorldVersionData data = new WorldVersionData();
        data.version = tag.getInt("Version");
        return data;
    }
}
