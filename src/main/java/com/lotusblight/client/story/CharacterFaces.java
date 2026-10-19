package com.lotusblight.client.story;

import com.lotusblight.LotusBlight;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * A character's skin as a texture the faces are painted on: the expression changes a few pixels of the face (eyes, brows, mouth),
 * the same texture serves the portrait in a dialogue and, later, the figure in the world. The painting is by rules, so a new skin
 * works at once; a skin that does not follow the usual layout can later bring its own face patches.
 * Face-local coordinates: 0..7 over the 8x8 front of the head (skin pixels 8..15 / 8..15).
 */
public final class CharacterFaces {
    private static final Map<String, CharacterFaces> CACHE = new HashMap<>();

    private final NativeImage original;
    private final NativeImage work;
    private final DynamicTexture texture;
    private final ResourceLocation location;
    private FaceExpression current = FaceExpression.NEUTRAL;

    private CharacterFaces(String id, NativeImage skin) {
        this.original = skin;
        this.work = new NativeImage(skin.getWidth(), skin.getHeight(), false);
        work.copyFrom(skin);
        this.texture = new DynamicTexture(work);
        this.location = Minecraft.getInstance().getTextureManager().register("lotus_face_" + id, texture);
    }

    public static synchronized CharacterFaces of(CharacterDef def) {
        CharacterFaces f = CACHE.get(def.id());
        if (f != null) return f;
        try (var in = Minecraft.getInstance().getResourceManager().getResource(def.skin()).orElseThrow().open()) {
            f = new CharacterFaces(def.id(), NativeImage.read(in));
        } catch (Exception e) {
            LotusBlight.LOGGER.warn("Could not read the skin of {}: {}", def.id(), e.toString());
            return null;
        }
        CACHE.put(def.id(), f);
        return f;
    }

    public static synchronized void clear() {
        for (CharacterFaces f : CACHE.values()) f.texture.close();
        CACHE.clear();
    }

    public ResourceLocation texture() {
        return location;
    }

    public FaceExpression current() {
        return current;
    }

    /** Paints the expression on the face (a handful of pixels and one upload; does nothing if it is already shown). */
    public void set(FaceExpression e) {
        if (e == current) return;
        current = e;
        work.copyFrom(original);
        int skin = px(3, 5);
        int dark = shade(skin, 0.45f);
        int mid = shade(skin, 0.7f);
        int white = abgr(255, 255, 255);
        int mouth = abgr(120, 40, 45);
        switch (e) {
            case NEUTRAL -> { }
            case BLINK -> {
                for (int x : new int[]{1, 2, 5, 6}) {
                    put(x, 4, dark);
                    put(x, 5, skin);
                }
            }
            case TALK -> {
                put(3, 6, mouth); put(4, 6, mouth); put(3, 7, mouth); put(4, 7, mouth);
            }
            case HAPPY -> {
                put(2, 6, dark); put(5, 6, dark); put(3, 7, dark); put(4, 7, dark);
            }
            case ANGRY -> {
                put(1, 3, dark); put(2, 3, dark); put(2, 4, dark); put(5, 3, dark); put(6, 3, dark); put(5, 4, dark);
                put(3, 7, dark); put(4, 7, dark);
            }
            case SAD -> {
                put(2, 3, dark); put(5, 3, dark); put(1, 3, mid); put(6, 3, mid);
                put(2, 7, dark); put(5, 7, dark); put(3, 6, dark); put(4, 6, dark);
            }
            case SCARED -> {
                put(1, 3, mid); put(2, 3, mid); put(5, 3, mid); put(6, 3, mid);
                put(1, 5, white); put(2, 5, white); put(5, 5, white); put(6, 5, white);
                put(3, 6, mouth); put(4, 6, mouth); put(3, 7, mouth); put(4, 7, mouth);
            }
            case SHOCK -> {
                put(1, 5, white); put(2, 5, white); put(5, 5, white); put(6, 5, white);
                put(3, 6, mouth); put(4, 6, mouth); put(3, 7, mouth); put(4, 7, mouth); put(2, 6, mouth); put(5, 6, mouth);
            }
        }
        texture.upload();
    }

    private int px(int fx, int fy) {
        return work.getPixelRGBA(8 + fx, 8 + fy);
    }

    private void put(int fx, int fy, int abgr) {
        work.setPixelRGBA(8 + fx, 8 + fy, abgr);
    }

    private static int abgr(int r, int g, int b) {
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }

    private static int shade(int abgr, float k) {
        int r = (int) ((abgr & 0xFF) * k), g = (int) (((abgr >> 8) & 0xFF) * k), b = (int) (((abgr >> 16) & 0xFF) * k);
        return abgr(r, g, b);
    }
}
