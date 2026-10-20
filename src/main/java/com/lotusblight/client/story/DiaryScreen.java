package com.lotusblight.client.story;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lotusblight.LotusBlight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The diary: the entries the player has found (titles on the left, the chosen entry's text on the right). The list comes from the server
 * ({@link com.lotusblight.map.DiarySyncPacket}); each entry's title and text from {@code assets/lotusblight/diary/<id>.json}
 * ({@code {"title": "...", "text": "..."}}). An entry without a file is shown by its id, so a missing text is visible, not silent.
 */
public final class DiaryScreen extends Screen {
    private static List<String> entries = new ArrayList<>();
    private static final Map<String, String[]> CACHE = new HashMap<>();

    private int selected;
    private int scroll;

    public DiaryScreen() {
        super(Component.literal("Дневник"));
    }

    public static void setEntries(List<String> ids) {
        entries = new ArrayList<>(ids);
        CACHE.clear();
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new DiaryScreen());
    }

    /** {title, text} of an entry. */
    private static String[] entry(String id) {
        return CACHE.computeIfAbsent(id, k -> {
            ResourceLocation loc = new ResourceLocation(LotusBlight.MODID, "diary/" + k + ".json");
            try (var in = new InputStreamReader(Minecraft.getInstance().getResourceManager().getResource(loc).orElseThrow().open(), StandardCharsets.UTF_8)) {
                JsonObject o = JsonParser.parseReader(in).getAsJsonObject();
                return new String[]{o.get("title").getAsString(), o.get("text").getAsString()};
            } catch (Exception e) {
                return new String[]{k, "(у записи «" + k + "» нет текста)"};
            }
        });
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int left() { return (int) (width * 0.12); }
    private int top() { return (int) (height * 0.12); }
    private int listW() { return (int) (width * 0.26); }
    private int panelW() { return (int) (width * 0.76); }
    private int panelH() { return (int) (height * 0.76); }
    private int rowH() { return font.lineHeight + 8; }
    private int rows() { return Math.max(1, (panelH() - 24) / rowH()); }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 265 && selected > 0) selected--;                     // up
        else if (key == 264 && selected < entries.size() - 1) selected++;   // down
        else return super.keyPressed(key, scan, mods);
        scroll = Math.max(0, Math.min(scroll, selected));
        if (selected >= scroll + rows()) scroll = selected - rows() + 1;
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && mx >= left() && mx < left() + listW()) {
            int row = (int) ((my - top() - 24) / rowH());
            int i = scroll + row;
            if (row >= 0 && row < rows() && i >= 0 && i < entries.size()) {
                selected = i;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = Math.max(0, entries.size() - rows());
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fillGradient(0, 0, width, height, 0x40000000, 0xC0000000);
        int x = left(), y = top(), w = panelW(), h = panelH();
        g.fill(x, y, x + w, y + h, 0xE0060A12);
        g.renderOutline(x, y, w, h, 0xFF6F8CA0);
        g.drawString(font, "Дневник", x + 10, y + 8, 0xFFEAF4FF, true);
        g.fill(x + listW(), y + 6, x + listW() + 1, y + h - 6, 0xFF2A3A4A);
        if (entries.isEmpty()) {
            g.drawString(font, "Записей пока нет.", x + 10, y + 30, 0xFF8FA3B5, false);
            return;
        }
        selected = Math.max(0, Math.min(selected, entries.size() - 1));
        for (int r = 0; r < rows() && scroll + r < entries.size(); r++) {
            int i = scroll + r;
            int ry = y + 24 + r * rowH();
            if (i == selected) g.fill(x + 4, ry - 2, x + listW() - 4, ry + font.lineHeight + 2, 0xFF1C3350);
            String title = font.plainSubstrByWidth(entry(entries.get(i))[0], listW() - 20);
            g.drawString(font, title, x + 10, ry, i == selected ? 0xFFFFFFFF : 0xFFB5C7D6, false);
        }
        String[] cur = entry(entries.get(selected));
        int tx = x + listW() + 14, tw = w - listW() - 28;
        g.drawString(font, cur[0], tx, y + 8, 0xFFFFE9A8, true);
        int ty = y + 28;
        for (FormattedCharSequence row : font.split(Component.literal(cur[1]), tw)) {
            if (ty > y + h - font.lineHeight - 6) break;
            g.drawString(font, row, tx, ty, 0xFFE8EEF4, false);
            ty += font.lineHeight + 3;
        }
    }
}
