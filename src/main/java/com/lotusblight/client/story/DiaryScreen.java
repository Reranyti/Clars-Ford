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
 * The diary as the notebook made in Blender (textures/gui/journal/book.png, an open spiral-bound notebook): on the left page the table of
 * contents ("name .... page"), on the right page the chosen entry: its title, its text and the sticky notes with Rey's thoughts (the colour of
 * a note is the kind of thought). The list comes from the server ({@link com.lotusblight.map.DiarySyncPacket}); each entry's title, text and
 * notes from {@code assets/lotusblight/diary/<id>.json}
 * ({@code {"title": "...", "text": "...", "notes": [{"color": "pink", "text": "..."}]}}). An entry without a file is shown by its id.
 * Everything is drawn in the picture's own coordinates (940 x 665) and scaled to the screen.
 * Level 1: the layout follows the author's DOORS reference; fonts are the game's own until the handwriting fonts are bundled.
 */
public final class DiaryScreen extends Screen {
    private static final ResourceLocation BOOK = new ResourceLocation(LotusBlight.MODID, "textures/gui/journal/book.png");
    private static final int BW = 940, BH = 665;
    // the text areas of the two pages inside the picture
    private static final int LX = 112, LY = 98, LW = 322;
    private static final int RX = 524, RY = 98, RW = 322;
    private static final int ROW_H = 40, ROWS = 11;

    /** The note colours: pink reputation / attitude, yellow assumption, green fact, purple surprise or pain, red a lie, blue sad memory, black flashback. */
    private static final Map<String, Integer> NOTE_COLORS = Map.of(
            "pink", 0xFFEFB3C2, "yellow", 0xFFF5D96B, "green", 0xFFA6D58F, "purple", 0xFFC5A3E0,
            "red", 0xFFD96A60, "blue", 0xFFA9D3F0, "black", 0xFF202026);

    private static List<String> entries = new ArrayList<>();
    private static final Map<String, Info> CACHE = new HashMap<>();

    private record Note(String color, String text) {}

    private record Info(String title, String text, List<Note> notes) {}

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

    private static Info info(String id) {
        return CACHE.computeIfAbsent(id, k -> {
            ResourceLocation loc = new ResourceLocation(LotusBlight.MODID, "diary/" + k + ".json");
            try (var in = new InputStreamReader(Minecraft.getInstance().getResourceManager().getResource(loc).orElseThrow().open(), StandardCharsets.UTF_8)) {
                JsonObject o = JsonParser.parseReader(in).getAsJsonObject();
                List<Note> notes = new ArrayList<>();
                if (o.has("notes")) {
                    JsonArray arr = o.getAsJsonArray("notes");
                    for (JsonElement e : arr) {
                        JsonObject n = e.getAsJsonObject();
                        notes.add(new Note(n.get("color").getAsString(), n.get("text").getAsString()));
                    }
                }
                return new Info(o.get("title").getAsString(), o.get("text").getAsString(), notes);
            } catch (Exception e) {
                return new Info(k, "(у записи «" + k + "» нет текста)", List.of());
            }
        });
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ placing the picture on the screen

    private float scale() {
        return Math.min(width * 0.94f / BW, height * 0.94f / BH);
    }

    private float originX() { return (width - BW * scale()) / 2f; }

    private float originY() { return (height - BH * scale()) / 2f; }

    private double bookX(double mx) { return (mx - originX()) / scale(); }

    private double bookY(double my) { return (my - originY()) / scale(); }

    private void clamp() {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, entries.size() - ROWS)));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll -= (int) Math.signum(delta);
        clamp();
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        double x = bookX(mx), y = bookY(my);
        if (button == 0 && x >= LX && x < LX + LW && y >= LY + 52) {
            int row = (int) ((y - LY - 52) / ROW_H);
            int i = scroll + row;
            if (row >= 0 && row < ROWS && i < entries.size()) {
                selected = i;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 265 && selected > 0) selected--;
        else if (key == 264 && selected < entries.size() - 1) selected++;
        else return super.keyPressed(key, scan, mods);
        if (selected < scroll) scroll = selected;
        if (selected >= scroll + ROWS) scroll = selected - ROWS + 1;
        return true;
    }

    // ------------------------------------------------------------------ drawing

    private void text(GuiGraphics g, String s, float x, float y, float k, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(k, k, 1);
        g.drawString(font, s, 0, 0, color, false);
        g.pose().popPose();
    }

    private float wrapped(GuiGraphics g, String s, float x, float y, float k, int width, int color) {
        for (FormattedCharSequence row : font.split(Component.literal(s), (int) (width / k))) {
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(k, k, 1);
            g.drawString(font, row, 0, 0, color, false);
            g.pose().popPose();
            y += font.lineHeight * k + 3;
        }
        return y;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fillGradient(0, 0, width, height, 0xA0000000, 0xD0000000);
        float k = scale();
        g.pose().pushPose();
        g.pose().translate(originX(), originY(), 0);
        g.pose().scale(k, k, 1);

        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.blit(BOOK, 0, 0, 0, 0, BW, BH, BW, BH);

        int ink = 0xFF2A1810, soft = 0xFF6B4C3A;
        // the left page: the title and the table of contents
        text(g, "Дневник", LX, LY, 3.0f, ink);
        g.fill(LX, LY + 36, LX + LW, LY + 38, 0x802A1810);
        if (entries.isEmpty()) {
            text(g, "Записей пока нет.", LX, LY + 64, 1.5f, soft);
        }
        clamp();
        for (int r = 0; r < ROWS && scroll + r < entries.size(); r++) {
            int i = scroll + r;
            int ry = LY + 52 + r * ROW_H;
            if (i == selected) g.fill(LX - 6, ry - 4, LX + LW + 4, ry + ROW_H - 8, 0x40803010);
            String title = font.plainSubstrByWidth(info(entries.get(i)).title(), (int) ((LW - 70) / 1.7f));
            text(g, title, LX, ry, 1.7f, ink);
            String no = String.valueOf(i + 1);
            float nx = LX + LW - font.width(no) * 1.7f;
            text(g, no, nx, ry, 1.7f, ink);
            // the dotted leader between the name and the number
            float dx = LX + font.width(title) * 1.7f + 8;
            for (float x = dx; x < nx - 8; x += 7) g.fill((int) x, ry + 17, (int) x + 2, ry + 19, 0x906B4C3A);
        }
        if (entries.size() > ROWS) text(g, (Math.min(entries.size(), scroll + ROWS)) + " / " + entries.size(), LX + LW - 60, LY + 560 - 30, 1.1f, soft);

        // the right page: the chosen entry
        if (!entries.isEmpty()) {
            selected = Math.max(0, Math.min(selected, entries.size() - 1));
            Info cur = info(entries.get(selected));
            text(g, String.valueOf(selected + 1), RX + RW - 12, RY - 14, 1.2f, soft);
            text(g, cur.title(), RX, RY, 2.8f, ink);
            g.fill(RX, RY + 34, RX + RW, RY + 36, 0x802A1810);
            float y = wrapped(g, cur.text(), RX, RY + 50, 1.6f, RW, ink);
            // the sticky notes: each a small coloured square with its text, laid out in two columns
            int nx = RX, ny = (int) y + 8, col = 0;
            for (Note n : cur.notes()) {
                int nc = NOTE_COLORS.getOrDefault(n.color(), 0xFFF5D96B);
                int nw = 152, nh = 74;
                int px = nx + col * (nw + 18) + (col == 0 ? 0 : 2), py = ny;
                g.fill(px + 3, py + 3, px + nw + 3, py + nh + 3, 0x50000000);          // a soft shadow
                g.fill(px, py, px + nw, py + nh, nc);
                g.fill(px, py, px + nw, py + 5, 0x30FFFFFF);
                int tc = n.color().equals("black") ? 0xFFE8E8EE : 0xFF2A1810;
                int ty = py + 8;
                for (FormattedCharSequence row : font.split(Component.literal(n.text()), (int) ((nw - 14) / 1.1f))) {
                    if (ty > py + nh - 12) break;
                    g.pose().pushPose();
                    g.pose().translate(px + 7, ty, 0);
                    g.pose().scale(1.1f, 1.1f, 1);
                    g.drawString(font, row, 0, 0, tc, false);
                    g.pose().popPose();
                    ty += (int) (font.lineHeight * 1.1f) + 2;
                }
                if (++col == 2) {
                    col = 0;
                    ny += nh + 8;
                }
            }
        }
        g.pose().popPose();
    }
}
