package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only registration that used to sit in LotusBlight itself. Keeping it in the main mod class
 * meant LotusBlight referenced BlockEntityRenderer and friends, and a dedicated server refused to
 * load the class at all ("invalid dist DEDICATED_SERVER") - the mod never constructed there.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class LotusClientSetup {
    private LotusClientSetup() {}

    @SubscribeEvent
    public static void registerRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(com.lotusblight.registry.ModBlockEntities.LOTUS_CROWN.get(), context -> new com.lotusblight.client.gecko.LotusCrownBlockRenderer());
        event.registerEntityRenderer(com.lotusblight.registry.ModEntities.WORLD_LOTUS_GUARDIAN.get(), net.minecraft.client.renderer.entity.WolfRenderer::new);
        event.registerEntityRenderer(com.lotusblight.registry.ModEntities.CREAK.get(), com.lotusblight.client.CreakRenderer::new);
    }

    /**
     * The heart's model switched from a cube_all (the painted icon texture wallpapered across
     * all 6 faces, which is what made it look flat/tiled) to a floating cross emblem, matching
     * how vanilla flowers use a single icon texture. That needs cutout rendering instead of the
     * default solid layer, or the texture's transparent background renders as solid black.
     */
    @SubscribeEvent
    public static void registerRenderLayers(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                com.lotusblight.registry.ModBlocks.LOTUS_HEART.get(), net.minecraft.client.renderer.RenderType.cutout()));
    }

    /**
     * The 5 infected ground blocks get a per-biome color accent (see InfectedGroundColor) instead
     * of a fixed hand-painted look everywhere - reuses the same grass color vanilla already
     * computes for every biome, so it works on any biome (vanilla or modded) with no new data.
     */
    @SubscribeEvent
    public static void registerBlockColors(net.minecraftforge.client.event.RegisterColorHandlersEvent.Block event) {
        event.register(com.lotusblight.client.InfectedGroundColor.INSTANCE,
                com.lotusblight.registry.ModBlocks.INFECTED_SOIL.get(), com.lotusblight.registry.ModBlocks.LOTUS_STONE.get(), com.lotusblight.registry.ModBlocks.LOTUS_SAND.get(),
                com.lotusblight.registry.ModBlocks.LOTUS_GRAVEL.get(), com.lotusblight.registry.ModBlocks.LOTUS_TERRACOTTA.get(),
                com.lotusblight.registry.ModBlocks.LOTUS_LOG.get(), com.lotusblight.registry.ModBlocks.LOTUS_LEAVES.get());
        // The shoot's built-in pad reuses the vanilla lily_pad sprite, which is grayscale and gets
        // its green from the same fixed color vanilla uses for a placed lily pad.
        event.register((state, level, pos, tintIndex) -> tintIndex == 0 ? LILY_PAD_WORLD_COLOR : 0xFFFFFF,
                com.lotusblight.registry.ModBlocks.LOTUS_SHOOT.get());
    }

    private static final int LILY_PAD_WORLD_COLOR = 0x208030;

    /**
     * A model face with a tintindex renders solid black in item/inventory form unless something
     * is registered here too - there's no world/biome to sample from in an inventory slot, so this
     * just registers a flat white (no-op) multiplier, matching InfectedGroundColor's own
     * no-context fallback.
     */
    @SubscribeEvent
    public static void registerItemColors(net.minecraftforge.client.event.RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> 0xFFFFFF,
                com.lotusblight.registry.ModBlocks.INFECTED_SOIL.get(), com.lotusblight.registry.ModBlocks.LOTUS_STONE.get(), com.lotusblight.registry.ModBlocks.LOTUS_SAND.get(),
                com.lotusblight.registry.ModBlocks.LOTUS_GRAVEL.get(), com.lotusblight.registry.ModBlocks.LOTUS_TERRACOTTA.get(),
                com.lotusblight.registry.ModBlocks.LOTUS_LOG.get(), com.lotusblight.registry.ModBlocks.LOTUS_LEAVES.get());
    }
}
