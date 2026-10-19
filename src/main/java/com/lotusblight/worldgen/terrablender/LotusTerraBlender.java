package com.lotusblight.worldgen.terrablender;

import com.lotusblight.LotusBlight;
import net.minecraft.resources.ResourceLocation;
import terrablender.api.Regions;

/**
 * The only place outside this package that touches TerraBlender types. LotusBlight used to build
 * the LotusRegion right in its own method, and verifying LotusBlight then needed
 * terrablender.api.Region on the classpath before any isLoaded check could run.
 */
public final class LotusTerraBlender {
    private LotusTerraBlender() {}

    /** The two lotus biomes are not generated until the story wants them (Act 1); false = the world is made without them. */
    public static final boolean LOTUS_BIOMES_IN_WORLD = false;

    public static void registerRegions() {
        if (!LOTUS_BIOMES_IN_WORLD) return;
        Regions.register(new LotusRegion(new ResourceLocation(LotusBlight.MODID, "overworld"), 2));
    }
}
