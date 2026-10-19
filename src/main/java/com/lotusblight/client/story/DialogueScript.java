package com.lotusblight.client.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lotusblight.LotusBlight;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A dialogue as a file ({@code assets/lotusblight/dialogues/<id>.json}): a list of lines. A line has a speaker, an expression and a
 * text; it may carry choices, set a flag, or be an introduction card ({@code "intro": "<character id>"}). The text is the author's to edit
 * without rebuilding the mod.
 */
public final class DialogueScript {
    public record Choice(String text, String next, String flag) {}

    public record Line(String label, String speaker, FaceExpression expr, String text, String intro, String flag, String next, boolean end, List<Choice> choices) {}

    private final String id;
    private final List<Line> lines;
    private final Map<String, Integer> labels = new HashMap<>();

    private DialogueScript(String id, List<Line> lines) {
        this.id = id;
        this.lines = lines;
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i).label();
            if (l != null) labels.put(l, i);
        }
    }

    public String id() {
        return id;
    }

    public int size() {
        return lines.size();
    }

    public Line line(int i) {
        return lines.get(i);
    }

    /** The index a line leads to, or -1 when the dialogue ends there. */
    public int after(int i, String choiceNext) {
        Line line = lines.get(i);
        String to = choiceNext != null ? choiceNext : line.next();
        if (to != null) return labels.getOrDefault(to, -1);
        if (line.end()) return -1;
        return i + 1 < lines.size() ? i + 1 : -1;
    }

    public static DialogueScript load(String id) {
        ResourceLocation loc = new ResourceLocation(LotusBlight.MODID, "dialogues/" + id + ".json");
        try (var in = new InputStreamReader(Minecraft.getInstance().getResourceManager().getResource(loc).orElseThrow().open(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(in).getAsJsonObject();
            List<Line> lines = new ArrayList<>();
            for (JsonElement e : root.getAsJsonArray("lines")) {
                JsonObject o = e.getAsJsonObject();
                List<Choice> choices = new ArrayList<>();
                if (o.has("choices")) {
                    JsonArray arr = o.getAsJsonArray("choices");
                    for (JsonElement c : arr) {
                        JsonObject co = c.getAsJsonObject();
                        choices.add(new Choice(co.get("text").getAsString(), str(co, "next"), str(co, "flag")));
                    }
                }
                lines.add(new Line(str(o, "label"), str(o, "speaker"), FaceExpression.parse(str(o, "expr")), str(o, "text"), str(o, "intro"),
                        str(o, "flag"), str(o, "next"), o.has("end") && o.get("end").getAsBoolean(), choices));
            }
            return new DialogueScript(id, lines);
        } catch (Exception ex) {
            LotusBlight.LOGGER.warn("Could not read dialogue {}: {}", id, ex.toString());
            return null;
        }
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }
}
