package com.lotusblight.client.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
 * The quest screen. On the left a TABLE of the quests (about 8 rows at a time, scrolled with the mouse wheel; the kind of each quest is
 * a mark in its row), on the right the chosen quest: its title, its description and its goals (done / to do / unknown). Titles, texts, the
 * kind and the goal texts come from {@code assets/lotusblight/quests/<id>.json}
 * ({@code {"kind": "story|regular|special", "title": "...", "text": "...", "goals": ["...", "..."]}}); the state of each goal comes from the
 * server (D done, T to do, U unknown, shown as "???"). A quest without a file is shown by its id.
 * Level 1: the layout follows the author's description (table left, details right); the look is a placeholder until the author's design.
 */
public final class QuestScreen extends Screen {
    private static final int ROWS = 8;
    private static final String[] KIND_MARK = {"story", "regular", "special"};
    private static final String[] KIND_NAME = {"сюжетное", "обычное", "особое"};
    private static final int[] KIND_COLOR = {0xFFE6C36A, 0xFF9FD6FF, 0xFFE69FD6};

    private static List<String> ids = new ArrayList<>();
    private static List<Boolean> done = new ArrayList<>();
    private static List<String> states = new ArrayList<>();
    private static final Map<String, Info> CACHE = new HashMap<>();

    private record Info(String kind, String title, String text, List<String> goals) {}

    private int selected;
    private int scroll;

    public QuestScreen() {
        super(Component.literal("Задания"));
    }

    public static void setQuests(List<String> newIds, List<Boolean> newDone, List<String> newStates) {
        ids = new ArrayList<>(newIds);
        done = new ArrayList<>(newDone);
        states = new ArrayList<>(newStates);
        CACHE.clear();
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new QuestScreen());
    }

    private static Info info(String id) {
        return CACHE.computeIfAbsent(id, k -> {
            ResourceLocation loc = new ResourceLocation(LotusBlight.MODID, "quests/" + k + ".json");
            try (var in = new InputStreamReader(Minecraft.getInstance().getResourceManager().getResource(loc).orElseThrow().open(), StandardCharsets.UTF_8)) {
                JsonObject o = JsonParser.parseReader(in).getAsJsonObject();
                List<String> goals = new ArrayList<>();
                if (o.has("goals")) {
                    JsonArray arr = o.getAsJsonArray("goals");
                    for (JsonElement e : arr) goals.add(e.getAsString());
                }
                return new Info(o.get("kind").getAsString(), o.get("title").getAsString(), o.get("text").getAsString(), goals);
            } catch (Exception e) {
                return new Info("regular", k, "(у задания «" + k + "» нет текста)", List.of());
            }
        });
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int left() { return (int) (width * 0.10); }
    private int top() { return (int) (height * 0.10); }
    private int panelW() { return (int) (width * 0.80); }
    private int panelH() { return (int) (height * 0.80); }
    private int tableW() { return (int) (panelW() * 0.36); }
    private int headH() { return 22; }
    private int rowH() { return (panelH() - headH() - 8) / ROWS; }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, ids.size() - ROWS)));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll -= (int) Math.signum(delta);
        clampScroll();
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && mx >= left() && mx < left() + tableW()) {
            int row = (int) ((my - top() - headH()) / rowH());
            int i = scroll + row;
            if (row >= 0 && row < ROWS && i < ids.size()) {
                selected = i;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 265 && selected > 0) selected--;
        else if (key == 264 && selected < ids.size() - 1) selected++;
        else return super.keyPressed(key, scan, mods);
        if (selected < scroll) scroll = selected;
        if (selected >= scroll + ROWS) scroll = selected - ROWS + 1;
        return true;
    }

    private static int kindIndex(String kind) {
        for (int i = 0; i < KIND_MARK.length; i++) if (KIND_MARK[i].equals(kind)) return i;
        return 1;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fillGradient(0, 0, width, height, 0x40000000, 0xC0000000);
        int x = left(), y = top(), w = panelW(), h = panelH(), tw = tableW();
        g.fill(x, y, x + w, y + h, 0xE0060A12);
        g.renderOutline(x, y, w, h, 0xFF6F8CA0);

        // the table header
        g.fill(x, y, x + tw, y + headH(), 0xFF10202F);
        g.drawString(font, "Задания", x + 8, y + (headH() - font.lineHeight) / 2, 0xFFEAF4FF, true);
        String count = ids.size() == 0 ? "" : (Math.min(ids.size(), scroll + ROWS)) + " / " + ids.size();
        g.drawString(font, count, x + tw - 8 - font.width(count), y + (headH() - font.lineHeight) / 2, 0xFF8FA3B5, false);
        g.fill(x + tw, y + 4, x + tw + 1, y + h - 4, 0xFF2A3A4A);

        if (ids.isEmpty()) {
            g.drawString(font, "Заданий пока нет.", x + 10, y + headH() + 12, 0xFF8FA3B5, false);
            return;
        }
        selected = Math.max(0, Math.min(selected, ids.size() - 1));
        clampScroll();

        // the table: ROWS rows, each with the kind mark, the title and a tick when done
        for (int r = 0; r < ROWS; r++) {
            int ry = y + headH() + r * rowH();
            g.fill(x, ry + rowH() - 1, x + tw, ry + rowH(), 0xFF16222E);
            int i = scroll + r;
            if (i >= ids.size()) continue;
            Info inf = info(ids.get(i));
            if (i == selected) g.fill(x + 1, ry, x + tw, ry + rowH() - 1, 0xFF1C3350);
            g.fill(x + 1, ry + 3, x + 4, ry + rowH() - 4, 0xFF000000 | KIND_COLOR[kindIndex(inf.kind())]);
            String mark = done.get(i) ? "✔" : " ";
            String title = font.plainSubstrByWidth(inf.title(), tw - 40);
            int ty = ry + (rowH() - font.lineHeight) / 2;
            g.drawString(font, title, x + 12, ty, done.get(i) ? 0xFF7C8C98 : (i == selected ? 0xFFFFFFFF : 0xFFDDE6EE), false);
            g.drawString(font, mark, x + tw - 18, ty, 0xFF7CE0A0, false);
        }
        // a thin scrollbar when there are more quests than rows
        if (ids.size() > ROWS) {
            int trackH = ROWS * rowH();
            int barH = Math.max(12, trackH * ROWS / ids.size());
            int barY = y + headH() + (trackH - barH) * scroll / (ids.size() - ROWS);
            g.fill(x + tw - 3, y + headH(), x + tw - 1, y + headH() + trackH, 0xFF0C1620);
            g.fill(x + tw - 3, barY, x + tw - 1, barY + barH, 0xFF6F8CA0);
        }

        // the chosen quest on the right
        Info cur = info(ids.get(selected));
        int dx = x + tw + 16, dw = w - tw - 32, dy = y + 12;
        g.drawString(font, cur.title(), dx, dy, 0xFFFFE9A8, true);
        String sub = KIND_NAME[kindIndex(cur.kind())] + (done.get(selected) ? " · выполнено" : "");
        g.drawString(font, sub, dx, dy + font.lineHeight + 3, 0xFF000000 | KIND_COLOR[kindIndex(cur.kind())], false);
        int ty = dy + font.lineHeight * 2 + 14;
        for (FormattedCharSequence row : font.split(Component.literal(cur.text()), dw)) {
            g.drawString(font, row, dx, ty, 0xFFE8EEF4, false);
            ty += font.lineHeight + 3;
        }
        ty += 8;
        String st = selected < states.size() ? states.get(selected) : "";
        if (!cur.goals().isEmpty()) {
            g.drawString(font, "Цели", dx, ty, 0xFF8FA3B5, true);
            ty += font.lineHeight + 5;
            for (int k = 0; k < cur.goals().size(); k++) {
                char s = k < st.length() ? st.charAt(k) : 'T';
                String box = s == 'D' ? "[x] " : (s == 'U' ? "[?] " : "[ ] ");
                String text = s == 'U' ? "???" : cur.goals().get(k);
                int color = s == 'D' ? 0xFF7CE0A0 : (s == 'U' ? 0xFF6F7C88 : 0xFFE8EEF4);
                for (FormattedCharSequence row : font.split(Component.literal(box + text), dw)) {
                    g.drawString(font, row, dx, ty, color, false);
                    ty += font.lineHeight + 3;
                }
            }
        }
    }
}
