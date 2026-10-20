package com.lotusblight.client;

import com.lotusblight.map.ClientMapCache;
import com.lotusblight.map.MapMarker;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;

public final class LotusClientHooks {
    private LotusClientHooks() {
    }

    /**
     * RenderGuiOverlayEvent.Post fires once after EVERY registered HUD layer (20+ per frame), not
     * once per frame. The mod's overlays hook it to draw on top of the HUD, so each one checks this
     * and only draws after the chat layer - otherwise translucent fills stacked on themselves every
     * frame and the minimap was redrawn dozens of times over.
     */
    public static boolean isOverlayPass(RenderGuiOverlayEvent.Post event) {
        return event.getOverlay() == VanillaGuiOverlay.CHAT_PANEL.type();
    }

    public static void openWiki() {
        Minecraft.getInstance().setScreen(new LotusWikiScreen());
    }

    /** A line from Honcho (HonchoLinePacket): his own chat message with Chat Overhaul, a plain chat line without it. */
    public static void showHonchoLine(String text) {
        if (ChatDialogue.active()) {
            ChatDialogue.postLine(ChatDialogue.HONCHO, text);
        } else {
            Minecraft.getInstance().gui.getChat().addMessage(net.minecraft.network.chat.Component.literal(text));
        }
    }

    public static void openScientistPage(int variant) {
        Minecraft.getInstance().setScreen(new ScientistPageScreen(variant));
    }

    /**
     * The dialogue screen used to always open at phase 0 regardless of the
     * outbreak's real state — the client never had any outbreak data to read.
     * It does now: {@link ClientMapCache} already carries the phase of every
     * marker the server has synced (see MapSyncManager), so we just look up
     * whichever marker is closest to the block the player clicked.
     */
    private static int nearestKnownPhase(BlockPos clicked) {
        MapMarker nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (MapMarker marker : ClientMapCache.markers()) {
            double distSq = marker.pos().distSqr(clicked);
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = marker;
            }
        }
        return nearest == null ? 0 : nearest.phase();
    }
}
