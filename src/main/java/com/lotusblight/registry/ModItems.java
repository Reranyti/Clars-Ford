package com.lotusblight.registry;

import com.lotusblight.LotusBlight;
import com.lotusblight.item.LotusArmorItem;
import com.lotusblight.item.LotusArmorMaterial;
import com.lotusblight.item.LotusGraftingRodItem;
import com.lotusblight.item.LotusMapItem;
import com.lotusblight.item.LotusSeerLensItem;
import com.lotusblight.item.LotusSeedItem;
import com.lotusblight.item.LotusWikiItem;
import com.lotusblight.item.MeteoriteArmorMaterial;
import com.lotusblight.item.MeteoriteTier;
import com.lotusblight.item.ScientistPageItem;
import com.lotusblight.item.LotusPickaxeItem;
import com.lotusblight.item.GlowingBerryItem;
import com.lotusblight.item.VitaminItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.food.FoodProperties;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, LotusBlight.MODID);
    public static final RegistryObject<Item> LOTUS_SEED = ITEMS.register("lotus_seed", () -> new LotusSeedItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> LOTUS_MAP = ITEMS.register("lotus_map", () -> new LotusMapItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> LOTUS_WIKI = ITEMS.register("lotus_wiki", () -> new LotusWikiItem(new Item.Properties()));
    // --- Calendar crafting chain: paper_stack (9 paper) + iron_rim (3 iron) -> calendar (see recipes) ---
    public static final RegistryObject<Item> PAPER_STACK = ITEMS.register("paper_stack", () -> new Item(new Item.Properties().stacksTo(64)));
    public static final RegistryObject<Item> IRON_RIM = ITEMS.register("iron_rim", () -> new Item(new Item.Properties().stacksTo(64)));
    public static final RegistryObject<Item> SCANNER = ITEMS.register("scanner", () -> new com.lotusblight.item.ScannerItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> CALENDAR = ITEMS.register("calendar", () -> new Item(new Item.Properties().stacksTo(1)));
    // --- Discoverable lore item: rare guardian drop, not handed out at spawn (see GuardianManager#onDrops) ---
    public static final RegistryObject<Item> SCIENTIST_PAGE = ITEMS.register("scientist_page", () -> new ScientistPageItem(new Item.Properties()));
    public static final RegistryObject<Item> LOTUS_MIMIC_ITEM = ITEMS.register("lotus_mimic", () -> new BlockItem(ModBlocks.LOTUS_MIMIC.get(), new Item.Properties()));
    public static final RegistryObject<Item> CLEANSING_POWDER = ITEMS.register("cleansing_powder", () -> new Item(new Item.Properties().stacksTo(64)));
    public static final RegistryObject<Item> INFECTED_LOTUS_ITEM = ITEMS.register("infected_lotus", () -> new BlockItem(ModBlocks.INFECTED_LOTUS.get(), new Item.Properties()));
    public static final RegistryObject<Item> INFECTED_SOIL_ITEM = ITEMS.register("infected_soil", () -> new BlockItem(ModBlocks.INFECTED_SOIL.get(), new Item.Properties()));
    public static final RegistryObject<Item> LOTUS_ORE_ITEM = ITEMS.register("lotus_ore", () -> new BlockItem(ModBlocks.LOTUS_ORE.get(), new Item.Properties()));
    public static final RegistryObject<Item> LOTUS_STONE_ITEM = ITEMS.register("lotus_stone", () -> new BlockItem(ModBlocks.LOTUS_STONE.get(), new Item.Properties()));
    public static final RegistryObject<Item> LOTUS_SAND_ITEM = ITEMS.register("lotus_sand", () -> new BlockItem(ModBlocks.LOTUS_SAND.get(), new Item.Properties()));
    public static final RegistryObject<Item> LOTUS_TERRACOTTA_ITEM = ITEMS.register("lotus_terracotta", () -> new BlockItem(ModBlocks.LOTUS_TERRACOTTA.get(), new Item.Properties()));
    public static final RegistryObject<Item> LOTUS_GRAVEL_ITEM = ITEMS.register("lotus_gravel", () -> new BlockItem(ModBlocks.LOTUS_GRAVEL.get(), new Item.Properties()));
    public static final RegistryObject<Item> LOTUS_SHOOT_ITEM = ITEMS.register("lotus_shoot", () -> new BlockItem(ModBlocks.LOTUS_SHOOT.get(), new Item.Properties()));
    public static final RegistryObject<Item> LIANA_BARRIER_ITEM = ITEMS.register("liana_barrier", () -> new BlockItem(ModBlocks.LIANA_BARRIER.get(), new Item.Properties()));
    public static final RegistryObject<Item> LIANA_WEAK_POINT_ITEM = ITEMS.register("liana_weak_point", () -> new BlockItem(ModBlocks.LIANA_WEAK_POINT.get(), new Item.Properties()));
    public static final RegistryObject<Item> LOTUS_ALLOY = ITEMS.register("lotus_alloy", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> LOTUS_PICKAXE = ITEMS.register("lotus_pickaxe", () -> new LotusPickaxeItem(Tiers.DIAMOND, 4, -2.7f, new Item.Properties().durability(900)));
    public static final RegistryObject<Item> LOTUS_ROOTS_ITEM = ITEMS.register("lotus_roots", () -> new BlockItem(ModBlocks.LOTUS_ROOTS.get(), new Item.Properties()));
    public static final RegistryObject<Item> LOTUS_HEART_ITEM = ITEMS.register("lotus_heart", () -> new BlockItem(ModBlocks.LOTUS_HEART.get(), new Item.Properties()));
    public static final RegistryObject<Item> GLOW_BERRIES_ITEM = ITEMS.register("glow_berries", () -> new BlockItem(ModBlocks.GLOW_BERRIES.get(), new Item.Properties()));
    public static final RegistryObject<Item> GLOW_BERRY_FOOD = ITEMS.register("glowing_berry", () -> new GlowingBerryItem(new Item.Properties().food(new FoodProperties.Builder().nutrition(3).saturationMod(0.35f).alwaysEat().build())));
    public static final RegistryObject<Item> BLESSING_NODULE_ITEM = ITEMS.register("blessing_nodule", () -> new BlockItem(ModBlocks.BLESSING_NODULE.get(), new Item.Properties()));
    public static final RegistryObject<Item> BLESSING_SOIL_ITEM = ITEMS.register("blessing_soil", () -> new BlockItem(ModBlocks.BLESSING_SOIL.get(), new Item.Properties()));
    public static final RegistryObject<Item> BLESSING_SAND_ITEM = ITEMS.register("blessing_sand", () -> new BlockItem(ModBlocks.BLESSING_SAND.get(), new Item.Properties()));
    public static final RegistryObject<Item> BLESSING_SOIL_PURPLE_ITEM = ITEMS.register("blessing_soil_purple", () -> new BlockItem(ModBlocks.BLESSING_SOIL_PURPLE.get(), new Item.Properties()));
    public static final RegistryObject<Item> BLESSING_SAND_PURPLE_ITEM = ITEMS.register("blessing_sand_purple", () -> new BlockItem(ModBlocks.BLESSING_SAND_PURPLE.get(), new Item.Properties()));
    public static final RegistryObject<Item> METEORITE_STONE_ITEM = ITEMS.register("meteorite_stone", () -> new BlockItem(ModBlocks.METEORITE_STONE.get(), new Item.Properties()));
    public static final RegistryObject<Item> LOTUS_LOG_ITEM = ITEMS.register("lotus_log", () -> new BlockItem(ModBlocks.LOTUS_LOG.get(), new Item.Properties()));
    public static final RegistryObject<Item> LOTUS_LEAVES_ITEM = ITEMS.register("lotus_leaves", () -> new BlockItem(ModBlocks.LOTUS_LEAVES.get(), new Item.Properties()));
    public static final RegistryObject<Item> BLESSING_LEAVES_ITEM = ITEMS.register("blessing_leaves", () -> new BlockItem(ModBlocks.BLESSING_LEAVES.get(), new Item.Properties()));
    // --- "Побег от лотоса" event item: a burst of Speed, most useful while a chase is active ---
    public static final RegistryObject<Item> VITAMIN = ITEMS.register("vitamin", () -> new VitaminItem(new Item.Properties().stacksTo(16)
            .food(new FoodProperties.Builder().nutrition(1).saturationMod(0.1f).alwaysEat().build())));

    // --- The horn: given at the end of the fight on the desktop (the first part of something bigger) ---
    public static final RegistryObject<Item> FOLDER = ITEMS.register("folder", () -> new com.lotusblight.item.FolderItem(new Item.Properties()));
    public static final RegistryObject<Item> HORN = ITEMS.register("horn", () -> new com.lotusblight.item.HornItem(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));

    // --- Lotus alloy tools (same tier/material as the pickaxe) ---
    public static final RegistryObject<Item> LOTUS_AXE = ITEMS.register("lotus_axe", () -> new AxeItem(Tiers.DIAMOND, 5.0f, -3.0f, new Item.Properties().durability(900)));
    public static final RegistryObject<Item> LOTUS_SWORD = ITEMS.register("lotus_sword", () -> new SwordItem(Tiers.DIAMOND, 3, -2.4f, new Item.Properties().durability(900)));
    public static final RegistryObject<Item> LOTUS_HOE = ITEMS.register("lotus_hoe", () -> new HoeItem(Tiers.DIAMOND, -3, 0.0f, new Item.Properties().durability(900)));

    // --- Lotus alloy armor set ---
    public static final RegistryObject<Item> LOTUS_HELMET = ITEMS.register("lotus_helmet", () -> new LotusArmorItem(LotusArmorMaterial.LOTUS_ALLOY_ARMOR, ArmorItem.Type.HELMET, new Item.Properties()));
    public static final RegistryObject<Item> LOTUS_CHESTPLATE = ITEMS.register("lotus_chestplate", () -> new LotusArmorItem(LotusArmorMaterial.LOTUS_ALLOY_ARMOR, ArmorItem.Type.CHESTPLATE, new Item.Properties()));
    public static final RegistryObject<Item> LOTUS_LEGGINGS = ITEMS.register("lotus_leggings", () -> new LotusArmorItem(LotusArmorMaterial.LOTUS_ALLOY_ARMOR, ArmorItem.Type.LEGGINGS, new Item.Properties()));
    public static final RegistryObject<Item> LOTUS_BOOTS = ITEMS.register("lotus_boots", () -> new LotusArmorItem(LotusArmorMaterial.LOTUS_ALLOY_ARMOR, ArmorItem.Type.BOOTS, new Item.Properties()));

    // --- Join-ending spreading tool (gated behind LotusPlayerState.hasJoinedLotus) ---
    public static final RegistryObject<Item> LOTUS_GRAFTING_ROD = ITEMS.register("lotus_grafting_rod", () -> new LotusGraftingRodItem(new Item.Properties().stacksTo(1).durability(200)));

    // --- Full map visibility modifier (see com.lotusblight.map.MapSyncManager) ---
    public static final RegistryObject<Item> LOTUS_SEER_LENS = ITEMS.register("lotus_seer_lens", () -> new LotusSeerLensItem(new Item.Properties().stacksTo(1)));

    // --- Meteorite tier: smelted from meteorite_stone (see MeteoriteTier/MeteoriteArmorMaterial for the
    // "better than diamond, netherite alternative" stat split), only ever reachable past a StarFall war ending ---
    public static final RegistryObject<Item> METEORITE_INGOT = ITEMS.register("meteorite_ingot", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> METEORITE_PICKAXE = ITEMS.register("meteorite_pickaxe", () -> new PickaxeItem(MeteoriteTier.METEORITE, 4, -2.6f, new Item.Properties().durability(1800)));
    public static final RegistryObject<Item> METEORITE_AXE = ITEMS.register("meteorite_axe", () -> new AxeItem(MeteoriteTier.METEORITE, 5.5f, -2.9f, new Item.Properties().durability(1800)));
    public static final RegistryObject<Item> METEORITE_SWORD = ITEMS.register("meteorite_sword", () -> new SwordItem(MeteoriteTier.METEORITE, 3, -2.3f, new Item.Properties().durability(1800)));
    public static final RegistryObject<Item> METEORITE_HOE = ITEMS.register("meteorite_hoe", () -> new HoeItem(MeteoriteTier.METEORITE, -2, 0.0f, new Item.Properties().durability(1800)));
    public static final RegistryObject<Item> METEORITE_HELMET = ITEMS.register("meteorite_helmet", () -> new LotusArmorItem(MeteoriteArmorMaterial.METEORITE, ArmorItem.Type.HELMET, new Item.Properties()));
    public static final RegistryObject<Item> METEORITE_CHESTPLATE = ITEMS.register("meteorite_chestplate", () -> new LotusArmorItem(MeteoriteArmorMaterial.METEORITE, ArmorItem.Type.CHESTPLATE, new Item.Properties()));
    public static final RegistryObject<Item> METEORITE_LEGGINGS = ITEMS.register("meteorite_leggings", () -> new LotusArmorItem(MeteoriteArmorMaterial.METEORITE, ArmorItem.Type.LEGGINGS, new Item.Properties()));
    public static final RegistryObject<Item> METEORITE_BOOTS = ITEMS.register("meteorite_boots", () -> new LotusArmorItem(MeteoriteArmorMaterial.METEORITE, ArmorItem.Type.BOOTS, new Item.Properties()));

    // --- Honcho's quest item (see com.lotusblight.entity.HonchoEntity) - original design built around star_light.png's own motif ---
    public static final RegistryObject<Item> STAR_LIGHT_VIAL = ITEMS.register("star_light_vial", () -> new com.lotusblight.item.StarLightVialItem(new Item.Properties().stacksTo(1)
            .food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(0).saturationMod(0f).alwaysEat().build())));

    private ModItems() {}
}
