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
 * The real GPU shader of the title screen (assets/lotusblight/shaders/core/menu_scene.*): the whole night-ocean scene is
 * computed per pixel, and the same program is drawn behind each button with a blur, so the scene shows through them.
 */
@Mod.EventBusSubscriber(modid = LotusBlight.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MenuShaders {
    private static ShaderInstance scene;

    private MenuShaders() {}

    @SubscribeEvent
    public static void onRegister(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(LotusBlight.MODID, "menu_scene"),
                DefaultVertexFormat.POSITION_TEX), shader -> scene = shader);
    }

    static boolean ready() {
        return scene != null;
    }

    /** Fills the rectangle with the scene (mode 0) or with the blurred scene for a button (1, hovered 2). */
    static void draw(GuiGraphics g, int x0, int y0, int x1, int y1, int sw, int sh, float mode) {
        if (scene == null) return;
        g.flush();
        Minecraft mc = Minecraft.getInstance();
        Matrix4f pose = g.pose().last().pose();
        RenderSystem.setShader(() -> scene);
        scene.safeGetUniform("uTime").set((float) ((Util.getMillis() % 1_000_000L) / 1000.0));
        scene.safeGetUniform("uMode").set(mode);
        scene.safeGetUniform("uRes").set((float) mc.getWindow().getWidth(), (float) mc.getWindow().getHeight());
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(pose, x0, y1, 0).uv(x0 / (float) sw, y1 / (float) sh).endVertex();
        buffer.vertex(pose, x1, y1, 0).uv(x1 / (float) sw, y1 / (float) sh).endVertex();
        buffer.vertex(pose, x1, y0, 0).uv(x1 / (float) sw, y0 / (float) sh).endVertex();
        buffer.vertex(pose, x0, y0, 0).uv(x0 / (float) sw, y0 / (float) sh).endVertex();
        BufferUploader.drawWithShader(buffer.end());
    }
}
