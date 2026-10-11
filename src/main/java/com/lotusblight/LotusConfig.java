package com.lotusblight;

import net.minecraftforge.common.ForgeConfigSpec;

public final class LotusConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue SPREAD_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue SPREAD_RADIUS;
    public static final ForgeConfigSpec.IntValue SCAN_RADIUS;
    public static final ForgeConfigSpec.IntValue MAP_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.BooleanValue STREAMS_COMPATIBILITY;
    public static final ForgeConfigSpec.IntValue ACTIVE_CHUNK_RADIUS;
    public static final ForgeConfigSpec.IntValue MAX_PENDING_WORLDGEN_TASKS;
    public static final ForgeConfigSpec.IntValue WORLD_INFECTION_REFERENCE;
    public static final ForgeConfigSpec.BooleanValue STARFALL_ENABLED;
    public static final ForgeConfigSpec.BooleanValue APPLY_RECOMMENDED_SETTINGS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> APPLIED_RECOMMENDATIONS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("lotus infection");
        SPREAD_INTERVAL_TICKS = builder.comment("Ticks between spread attempts. 140 ticks = 7 seconds.")
                .defineInRange("spreadIntervalTicks", 140, 20, 2400);
        SPREAD_RADIUS = builder.comment("Search radius around an active lotus heart.")
                .defineInRange("spreadRadius", 6, 2, 16);
        SCAN_RADIUS = builder.comment("Lotus map detection radius in blocks.")
                .defineInRange("mapScanRadius", 192, 32, 512);
        MAP_COOLDOWN_TICKS = builder.comment("Cooldown of the lotus infection map.")
                .defineInRange("mapCooldownTicks", 100, 20, 1200);
        STREAMS_COMPATIBILITY = builder.comment("Makes infection prefer and follow connected water courses.")
                .define("streamsReflowingCompatibility", true);
        ACTIVE_CHUNK_RADIUS = builder.comment("Maximum active infection and scanning radius around players, in chunks.")
                .defineInRange("activeChunkRadius", 32, 4, 32);
        MAX_PENDING_WORLDGEN_TASKS = builder.comment("Maximum queued natural lotus worldgen tasks.")
                .defineInRange("maxPendingWorldgenTasks", 64, 8, 256);
        WORLD_INFECTION_REFERENCE = builder.comment("Sum of infectedBlockCount across every outbreak on the server treated as \"100% of the world captured\" - the Chase and StarFall thresholds are fractions of it.")
                .defineInRange("worldInfectionReference", 100000, 1000, 10_000_000);
        STARFALL_ENABLED = builder.comment("false = StarFall never starts by itself (the /lotus starfall command still works). Honcho can then be met without it.")
                .define("starFallEnabled", false);
        builder.pop();
        builder.push("compatibility");
        APPLY_RECOMMENDED_SETTINGS = builder.comment("Set the recommended values in other installed mods' configs once (see /lotus compat).",
                        "A value you have changed yourself is never overwritten. false = leave other mods' configs alone.")
                .define("applyRecommendedSettings", true);
        APPLIED_RECOMMENDATIONS = builder.comment("Recommendations already applied - each one is only ever set once. Clear an entry to have it offered again.")
                .defineListAllowEmpty(java.util.List.of("appliedRecommendations"), java.util.List::of, o -> o instanceof String);
        builder.pop();
        SPEC = builder.build();
    }

    private LotusConfig() {}
}
