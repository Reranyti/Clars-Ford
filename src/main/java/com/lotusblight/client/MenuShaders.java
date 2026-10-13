package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.io.IOException;

/**
 * The GPU shader of the title screen (assets/lotusblight/shaders/core/puppet.*): the dark figure with its hands and the threads, ray-marched per pixel.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MenuShaders {
    private static ShaderInstance puppet;

    private MenuShaders() {}

    @SubscribeEvent
    public static void onRegister(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(LotusBlight.MODID, "puppet"),
                DefaultVertexFormat.POSITION_TEX), shader -> puppet = shader);
    }

    static boolean puppetReady() {
        return puppet != null;
    }

    /**
     * Draws the figure (puppet.fsh) over the whole screen: {@code curl} the bend of every finger (right hand first = the screen's left), {@code lift}
     * the height of the two hands, {@code threadEnd} the world position where each of the eight threads ends, {@code threadOn} which are not cut.
     */
    static void drawPuppet(GuiGraphics g, int sw, int sh, float[] curl, float spread, float[] lift, float[] threadEnd, float[] threadOn) {
        if (puppet == null) return;
        g.flush();
        Minecraft mc = Minecraft.getInstance();
        Matrix4f pose = g.pose().last().pose();
        RenderSystem.setShader(() -> puppet);
        puppet.safeGetUniform("uTime").set((float) ((Util.getMillis() % 1_000_000L) / 1000.0));
        puppet.safeGetUniform("uRes").set((float) mc.getWindow().getWidth(), (float) mc.getWindow().getHeight());
        puppet.safeGetUniform("uSpread").set(spread);
        puppet.safeGetUniform("uLift").set(lift);
        puppet.safeGetUniform("uCurl").set(curl);
        puppet.safeGetUniform("uThreadEnd").set(threadEnd);
        puppet.safeGetUniform("uThreadOn").set(threadOn);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(pose, 0, sh, 0).uv(0, 1).endVertex();
        buffer.vertex(pose, sw, sh, 0).uv(1, 1).endVertex();
        buffer.vertex(pose, sw, 0, 0).uv(1, 0).endVertex();
        buffer.vertex(pose, 0, 0, 0).uv(0, 0).endVertex();
        BufferUploader.drawWithShader(buffer.end());
    }
}
