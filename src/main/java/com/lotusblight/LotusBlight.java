package com.lotusblight;

import com.mojang.logging.LogUtils;
import com.lotusblight.registry.ModBlocks;
import com.lotusblight.registry.ModBlockEntities;
import com.lotusblight.registry.ModItems;
import com.lotusblight.registry.ModEffects;
import com.lotusblight.registry.ModSounds;
import com.lotusblight.registry.ModVillagers;
import com.lotusblight.registry.ModBiomes;
import com.lotusblight.registry.ModFeatures;
import net.minecraftforge.common.BiomeManager;
import com.lotusblight.world.LotusEvents;
import com.lotusblight.spread.InfectionSpreadEngine;
import com.lotusblight.spread.BarrierEvents;
import com.lotusblight.spread.roots.RootGrowthEngine;
import com.lotusblight.worldgen.GuaranteedSpawnManager;
import com.lotusblight.advancement.SingleBiomeWorldTrigger;
import com.lotusblight.advancement.TwoInfectionsTrigger;
import com.lotusblight.advancement.SurvivorOfTheBloomTrigger;
import com.lotusblight.advancement.TheHeartWaitsTrigger;
import com.lotusblight.map.journeymap.JourneyMapSyncTicker;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(LotusBlight.MODID)
public class LotusBlight {
    public static final String MODID = "lotusblight";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final RegistryObject<CreativeModeTab> LOTUS_TAB = TABS.register("lotus_tab", () -> CreativeModeTab.builder()
            .title(net.minecraft.network.chat.Component.translatable("itemGroup.lotusblight.lotus_tab"))
            .icon(() -> ModItems.LOTUS_MAP.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.LOTUS_SEED.get());
                output.accept(ModItems.LOTUS_WIKI.get());
                output.accept(ModItems.PAPER_STACK.get());
                output.accept(ModItems.IRON_RIM.get());
                output.accept(ModItems.CALENDAR.get());
                output.accept(ModItems.SCIENTIST_PAGE.get());
                output.accept(ModItems.LOTUS_MIMIC_ITEM.get());
                output.accept(ModItems.LOTUS_MAP.get());
                output.accept(ModItems.CLEANSING_POWDER.get());
                output.accept(ModItems.LOTUS_ORE_ITEM.get());
                output.accept(ModItems.LOTUS_ALLOY.get());
                output.accept(ModItems.LOTUS_PICKAXE.get());
                output.accept(ModBlocks.LOTUS_HEART.get());
                output.accept(ModBlocks.INFECTED_LOTUS.get());
                output.accept(ModBlocks.INFECTED_SOIL.get());
                output.accept(ModBlocks.LOTUS_ROOTS.get());
                output.accept(ModItems.GLOW_BERRIES_ITEM.get());
                output.accept(ModItems.BLESSING_NODULE_ITEM.get());
                output.accept(ModItems.BLESSING_SOIL_ITEM.get());
                output.accept(ModItems.BLESSING_SAND_ITEM.get());
                output.accept(ModItems.LOTUS_LOG_ITEM.get());
                output.accept(ModItems.LOTUS_LEAVES_ITEM.get());
                output.accept(ModItems.BLESSING_LEAVES_ITEM.get());
                output.accept(ModItems.LOTUS_AXE.get());
                output.accept(ModItems.LOTUS_SWORD.get());
                output.accept(ModItems.LOTUS_HOE.get());
                output.accept(ModItems.LOTUS_HELMET.get());
                output.accept(ModItems.LOTUS_CHESTPLATE.get());
                output.accept(ModItems.LOTUS_LEGGINGS.get());
                output.accept(ModItems.LOTUS_BOOTS.get());
                output.accept(ModItems.LOTUS_GRAFTING_ROD.get());
                output.accept(ModItems.LOTUS_SEER_LENS.get());
                output.accept(ModItems.VITAMIN.get());
                output.accept(ModItems.METEORITE_STONE_ITEM.get());
                output.accept(ModItems.METEORITE_INGOT.get());
                output.accept(ModItems.METEORITE_PICKAXE.get());
                output.accept(ModItems.METEORITE_AXE.get());
                output.accept(ModItems.METEORITE_SWORD.get());
                output.accept(ModItems.METEORITE_HOE.get());
                output.accept(ModItems.METEORITE_HELMET.get());
                output.accept(ModItems.METEORITE_CHESTPLATE.get());
                output.accept(ModItems.METEORITE_LEGGINGS.get());
                output.accept(ModItems.METEORITE_BOOTS.get());
                output.accept(ModItems.STAR_LIGHT_VIAL.get());
            }).build());

    /**
     * Every block that only ever appears via worldgen or the spread engines - never crafted, only
     * found (or, for testing, grabbed here) - separate from the main tab's mix of tools/food/lore
     * items, so they're all in one place to browse or place for testing.
     */
    public static final RegistryObject<CreativeModeTab> WORLD_BLOCKS_TAB = TABS.register("world_blocks_tab", () -> CreativeModeTab.builder()
            .title(net.minecraft.network.chat.Component.translatable("itemGroup.lotusblight.world_blocks_tab"))
            .icon(() -> ModItems.LOTUS_HEART_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModBlocks.LOTUS_HEART.get());
                output.accept(ModBlocks.INFECTED_LOTUS.get());
                output.accept(ModItems.LOTUS_MIMIC_ITEM.get());
                output.accept(ModItems.LOTUS_SHOOT_ITEM.get());
                output.accept(ModBlocks.INFECTED_SOIL.get());
                output.accept(ModItems.LOTUS_STONE_ITEM.get());
                output.accept(ModItems.LOTUS_SAND_ITEM.get());
                output.accept(ModItems.LOTUS_GRAVEL_ITEM.get());
                output.accept(ModItems.LOTUS_TERRACOTTA_ITEM.get());
                output.accept(ModItems.LOTUS_ORE_ITEM.get());
                output.accept(ModBlocks.LOTUS_ROOTS.get());
                output.accept(ModItems.LIANA_BARRIER_ITEM.get());
                output.accept(ModItems.LIANA_WEAK_POINT_ITEM.get());
                output.accept(ModItems.GLOW_BERRIES_ITEM.get());
                output.accept(ModItems.LOTUS_LOG_ITEM.get());
                output.accept(ModItems.LOTUS_LEAVES_ITEM.get());
                output.accept(ModItems.BLESSING_NODULE_ITEM.get());
                output.accept(ModItems.BLESSING_SOIL_ITEM.get());
                output.accept(ModItems.BLESSING_SAND_ITEM.get());
                output.accept(ModItems.BLESSING_LEAVES_ITEM.get());
            }).build());

    public LotusBlight() {
        FMLJavaModLoadingContext context = FMLJavaModLoadingContext.get();
        IEventBus modBus = context.getModEventBus();
        ModBlocks.BLOCKS.register(modBus);
        ModFeatures.FEATURES.register(modBus);
        com.lotusblight.registry.ModEntities.ENTITY_TYPES.register(modBus);
        modBus.addListener(this::registerEntityAttributes);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModEffects.EFFECTS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModVillagers.POI_TYPES.register(modBus);
        ModVillagers.PROFESSIONS.register(modBus);
        if (ModList.get().isLoaded("terrablender")) {
            // Better overworld placement/compatibility than the plain BiomeManager fallback —
            // see com.lotusblight.worldgen.terrablender.LotusRegion.
            modBus.addListener(this::registerTerraBlenderRegions);
        } else {
            BiomeManager.addAdditionalOverworldBiomes(ModBiomes.LOTUS_BIOME);
            BiomeManager.addAdditionalOverworldBiomes(ModBiomes.BLESSING_BIOME);
        }
        TABS.register(modBus);
        modBus.addListener(this::addVanillaCreativeItems);
        modBus.addListener(this::applyRecommendedConfigs);
        // Renderers, render layers and color handlers live in client.LotusClientSetup - referencing
        // client classes from this class made the whole mod fail to construct on a dedicated server.
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, LotusConfig.SPEC);
        // Once the Нормальная_ветка ending has fired for this jar, the mod stands down until it's
        // reinstalled: its behaviour handlers below are simply never registered, so no spread, events,
        // HUD or dialogue run - it acts as if it isn't installed. Blocks, items, commands and networking
        // still register, so existing worlds load and the player can put a fresh jar in to bring it back.
        if (!com.lotusblight.branch.NormalBranchLock.isLocked()) {
            MinecraftForge.EVENT_BUS.register(new LotusEvents());
            MinecraftForge.EVENT_BUS.register(new InfectionSpreadEngine());
            MinecraftForge.EVENT_BUS.register(new com.lotusblight.spread.MossyGlandSpreadEngine());
            MinecraftForge.EVENT_BUS.register(new com.lotusblight.spread.MeteoriteSpreadEngine());
            MinecraftForge.EVENT_BUS.register(new com.lotusblight.worldgen.MossyGlandWorldgen());
            MinecraftForge.EVENT_BUS.register(new BarrierEvents());
            MinecraftForge.EVENT_BUS.register(new RootGrowthEngine());
            MinecraftForge.EVENT_BUS.register(new com.lotusblight.spread.GuardianManager());
            MinecraftForge.EVENT_BUS.register(com.lotusblight.boss.TraitorBossFight.class);
            MinecraftForge.EVENT_BUS.register(com.lotusblight.boss.TraitorBossArena.class);
            MinecraftForge.EVENT_BUS.register(new GuaranteedSpawnManager());
            MinecraftForge.EVENT_BUS.register(new com.lotusblight.worldgen.EpicenterManager());
            MinecraftForge.EVENT_BUS.register(new com.lotusblight.escape.LotusChaseEvent());
            MinecraftForge.EVENT_BUS.register(com.lotusblight.escape.LotusChaseStructure.class);
            MinecraftForge.EVENT_BUS.register(new com.lotusblight.escape.StarFallEvent());
            MinecraftForge.EVENT_BUS.register(new com.lotusblight.escape.LabProto());
            MinecraftForge.EVENT_BUS.register(new com.lotusblight.worldgen.WorldBorderSetup());
            MinecraftForge.EVENT_BUS.register(new com.lotusblight.worldgen.QuarantineBarrier());
        }
        MinecraftForge.EVENT_BUS.register(com.lotusblight.command.LotusCommands.class);
        MinecraftForge.EVENT_BUS.register(com.lotusblight.world.ChromoDifficulty.class);
        com.lotusblight.map.NetworkHandler.register();
        CriteriaTriggers.register(SingleBiomeWorldTrigger.INSTANCE);
        CriteriaTriggers.register(TwoInfectionsTrigger.INSTANCE);
        CriteriaTriggers.register(SurvivorOfTheBloomTrigger.INSTANCE);
        CriteriaTriggers.register(TheHeartWaitsTrigger.INSTANCE);
        CriteriaTriggers.register(com.lotusblight.advancement.GuardianTamedTrigger.INSTANCE);
        CriteriaTriggers.register(com.lotusblight.advancement.GuardianPackTamedTrigger.INSTANCE);
        CriteriaTriggers.register(com.lotusblight.advancement.LotusAlloyCraftedTrigger.INSTANCE);
        CriteriaTriggers.register(com.lotusblight.advancement.AllianceGuardianSlaughterTrigger.INSTANCE);
        CriteriaTriggers.register(com.lotusblight.advancement.WorldLotusLectureTrigger.INSTANCE);
        CriteriaTriggers.register(com.lotusblight.advancement.TraitorBossDefeatedTrigger.INSTANCE);
        CriteriaTriggers.register(com.lotusblight.advancement.HonchoBestBossTrigger.INSTANCE);
        CriteriaTriggers.register(com.lotusblight.advancement.HonchoCruelTrigger.INSTANCE);
        CriteriaTriggers.register(com.lotusblight.advancement.HonchoTripRudeTrigger.INSTANCE);
        CriteriaTriggers.register(com.lotusblight.advancement.HonchoTripPleasedTrigger.INSTANCE);
        MinecraftForge.EVENT_BUS.register(new JourneyMapSyncTicker());

        // Config-screen button in the mods list — soft dependency, only touches YACL classes
        // (client-only, and absent unless the player installed YACL themselves) after confirming
        // both the physical side and the mod's actual presence.
        if (ModList.get().isLoaded("yet_another_config_lib_v3")) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                            () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> com.lotusblight.client.config.LotusConfigScreen.create(parent))));
        }
    }


    /** Forge requires every living entity type to have a registered attribute supplier or it crashes the instant one is spawned. Reuses vanilla Wolf's own attribute map - same base stats, GuardianManager-style code tunes health/damage per instance afterward. */
    private void registerEntityAttributes(net.minecraftforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(com.lotusblight.registry.ModEntities.WORLD_LOTUS_GUARDIAN.get(), net.minecraft.world.entity.animal.Wolf.createAttributes().build());
        event.put(com.lotusblight.registry.ModEntities.HONCHO.get(), com.lotusblight.entity.HonchoEntity.createAttributes().build());
        event.put(com.lotusblight.registry.ModEntities.CREAK.get(), com.lotusblight.entity.CreakEntity.createAttributes().build());
    }




    private void registerTerraBlenderRegions(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(com.lotusblight.worldgen.terrablender.LotusTerraBlender::registerRegions);
    }

    /** Other mods' COMMON configs are loaded by now - see compat.RecommendedConfigs. */
    private void applyRecommendedConfigs(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(() -> com.lotusblight.compat.RecommendedConfigs.apply(net.minecraftforge.fml.config.ModConfig.Type.COMMON));
    }

    private void addVanillaCreativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.LOTUS_MAP.get());
            event.accept(ModItems.LOTUS_WIKI.get());
            event.accept(ModItems.CLEANSING_POWDER.get());
        }
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(ModItems.LOTUS_SEED.get());
        }
    }
}
