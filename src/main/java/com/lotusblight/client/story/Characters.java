package com.lotusblight.client.story;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lotusblight.LotusBlight;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every story character, read from {@code assets/lotusblight/characters/*.json}. A new character is a new file; no code changes.
 * Loaded on first use and again after {@link #reload()}.
 */
public final class Characters {
    private static Map<String, CharacterDef> all;

    private Characters() {}

    public static synchronized void reload() {
        all = null;
        CharacterFaces.clear();
    }

    public static synchronized CharacterDef get(String id) {
        return load().get(id);
    }

    public static synchronized Map<String, CharacterDef> all() {
        return load();
    }

    private static Map<String, CharacterDef> load() {
        if (all != null) return all;
        Map<String, CharacterDef> out = new LinkedHashMap<>();
        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        for (var e : rm.listResources("characters", l -> l.getNamespace().equals(LotusBlight.MODID) && l.getPath().endsWith(".json")).entrySet()) {
            try (var in = new InputStreamReader(e.getValue().open(), StandardCharsets.UTF_8)) {
                JsonObject o = JsonParser.parseReader(in).getAsJsonObject();
                String id = o.get("id").getAsString();
                int color = o.has("color") ? Integer.parseInt(o.get("color").getAsString(), 16) : 0xFFFFFF;
                Integer floor = o.has("floor") && !o.get("floor").isJsonNull() ? o.get("floor").getAsInt() : null;
                boolean ph = o.has("placeholder_skin") && o.get("placeholder_skin").getAsBoolean();
                out.put(id, new CharacterDef(id, o.get("name").getAsString(), new ResourceLocation(o.get("skin").getAsString()), color, floor, ph));
            } catch (Exception ex) {
                LotusBlight.LOGGER.warn("Bad character file {}: {}", e.getKey(), ex.toString());
            }
        }
        all = out;
        return out;
    }
}
