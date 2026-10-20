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
 * The quest screen: three tabs (story, ordinary, special), the quests of the tab on the left (done ones struck through), the chosen
 * quest's text on the right. Titles, texts and the kind come from {@code assets/lotusblight/quests/<id>.json}
 * ({@code {"kind": "story|regular|special", "title": "...", "text": "..."}}); a quest without a file is shown by its id among the ordinary ones.
 */
public final class QuestScreen extends Screen {
    private static final String[] KINDS = {"story", "regular", "special"};
    private static final String[] KIND_NAMES = {"Сюжетные", "Обычные", "Особые"};

    private static List<String> ids = new ArrayList<>();
    private static List<Boolean> done = new ArrayList<>();
    private static final Map<String, String[]> CACHE = new HashMap<>();      // id -> {kind, title, text}

    private int tab;
    private int selected;

    public QuestScreen() {
        super(Component.literal("Задания"));
    }

    public static void setQuests(List<String> newIds, List<Boolean> newDone) {
        ids = new ArrayList<>(newIds);
        done = new ArrayList<>(newDone);
        CACHE.clear();
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new QuestScreen());
    }

    private static String[] info(String id) {
        return CACHE.computeIfAbsent(id, k -> {
            ResourceLocation loc = new ResourceLocation(LotusBlight.MODID, "quests/" + k + ".json");
            try (var in = new InputStreamReader(Minecraft.getInstance().getResourceManager().getResource(loc).orElseThrow().open(), StandardCharsets.UTF_8)) {
                JsonObject o = JsonParser.parseReader(in).getAsJsonObject();
                return new String[]{o.get("kind").getAsString(), o.get("title").getAsString(), o.get("text").getAsString()};
            } catch (Exception e) {
                return new String[]{"regular", k, "(у задания «" + k + "» нет текста)"};
            }
        });
    }

    /** Indices into the quest lists that belong to the current tab. */
    private List<Integer> shown() {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) if (KINDS[tab].equals(info(ids.get(i))[0])) out.add(i);
        return out;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int left() { return (int) (width * 0.12); }
    private int top() { return (int) (height * 0.12); }
    private int listW() { return (int) (width * 0.28); }
    private int panelW() { return (int) (width * 0.76); }
    private int panelH() { return (int) (height * 0.76); }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        int tw = panelW() / 3;
        if (my >= top() && my < top() + 20 && mx >= left() && mx < left() + panelW()) {
            tab = Math.min(2, (int) ((mx - left()) / tw));
            selected = 0;
            return true;
        }
        int row = (int) ((my - top() - 30) / (font.lineHeight + 8));
        if (mx >= left() && mx < left() + listW() && row >= 0 && row < shown().size()) {
            selected = row;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 265 && selected > 0) selected--;
        else if (key == 264 && selected < shown().size() - 1) selected++;
        else if (key == 263 && tab > 0) { tab--; selected = 0; }
        else if (key == 262 && tab < 2) { tab++; selected = 0; }
        else return super.keyPressed(key, scan, mods);
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fillGradient(0, 0, width, height, 0x40000000, 0xC0000000);
        int x = left(), y = top(), w = panelW(), h = panelH();
        g.fill(x, y, x + w, y + h, 0xE0060A12);
        g.renderOutline(x, y, w, h, 0xFF6F8CA0);
        int tw = w / 3;
        for (int t = 0; t < 3; t++) {
            g.fill(x + t * tw, y, x + (t + 1) * tw, y + 20, t == tab ? 0xFF1C3350 : 0xFF0C1522);
            g.drawCenteredString(font, KIND_NAMES[t], x + t * tw + tw / 2, y + 6, t == tab ? 0xFFFFFFFF : 0xFF8FA3B5);
        }
        g.fill(x + listW(), y + 24, x + listW() + 1, y + h - 6, 0xFF2A3A4A);
        List<Integer> list = shown();
        if (list.isEmpty()) {
            g.drawString(font, "Заданий нет.", x + 10, y + 34, 0xFF8FA3B5, false);
            return;
        }
        selected = Math.max(0, Math.min(selected, list.size() - 1));
        for (int r = 0; r < list.size(); r++) {
            int ry = y + 30 + r * (font.lineHeight + 8);
            if (ry > y + h - 14) break;
            int i = list.get(r);
            String title = font.plainSubstrByWidth(info(ids.get(i))[1], listW() - 24);
            if (r == selected) g.fill(x + 4, ry - 2, x + listW() - 4, ry + font.lineHeight + 2, 0xFF1C3350);
            g.drawString(font, (done.get(i) ? "✔ " : "• ") + title, x + 10, ry, done.get(i) ? 0xFF7C8C98 : 0xFFE8EEF4, false);
        }
        int i = list.get(selected);
        String[] cur = info(ids.get(i));
        int tx = x + listW() + 14, tw2 = w - listW() - 28;
        g.drawString(font, cur[1] + (done.get(i) ? "  (выполнено)" : ""), tx, y + 30, 0xFFFFE9A8, true);
        int ty = y + 50;
        for (FormattedCharSequence row : font.split(Component.literal(cur[2]), tw2)) {
            if (ty > y + h - font.lineHeight - 6) break;
            g.drawString(font, row, tx, ty, 0xFFE8EEF4, false);
            ty += font.lineHeight + 3;
        }
    }
}
