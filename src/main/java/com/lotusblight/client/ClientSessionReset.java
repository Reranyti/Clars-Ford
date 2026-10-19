package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import com.lotusblight.map.ClientGlandCache;
import com.lotusblight.map.ClientMapCache;
import com.lotusblight.map.ClientPlayerStateCache;
import com.lotusblight.map.ClientReputationCache;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * All client-side mod state is static - without a reset, leaving a world kept the old branch,
 * markers and reputation until the next world's first sync, a half-played scene carried over, and
 * the boss/chase music kept looping in the main menu.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientSessionReset {
    private ClientSessionReset() {}

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientMapCache.clear();
        ClientPlayerStateCache.clear();
        ClientGlandCache.clear();
        ClientReputationCache.clear();
        StarFallOverlay.reset();
        WorldLotusLectureOverlay.reset();
        InnerVoiceOverlay.reset();
        LotusChaseOverlay.stopAll();
        TraitorBossMusicOverlay.stop();
        NormalBranchFreeze.reset();
        NormalBranchClient.setSilenced(false);
    }
}
