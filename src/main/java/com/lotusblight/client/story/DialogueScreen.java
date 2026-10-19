package com.lotusblight.client.story;

import com.lotusblight.map.NetworkHandler;
import com.lotusblight.map.StoryFlagPacket;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * The dialogue screen: the speaker's face on the left (the head of their skin, painted with the expression), the name above the box, the
 * text typed out, choices under it. Blinks by itself and moves the mouth while the text is typed. Lines come from a {@link DialogueScript}.
 */
public final class DialogueScreen extends Screen {
    private static final long CHAR_MS = 28;
    private static final long INTRO_MS = 2300;

    private final DialogueScript script;
    private int index;
    private long lineAt = Util.getMillis();
    private boolean flagSent;

    public DialogueScreen(DialogueScript script) {
        super(Component.literal("dialogue"));
        this.script = script;
    }

    public static void open(String id) {
        DialogueScript s = DialogueScript.load(id);
        if (s == null || s.size() == 0) return;
        Minecraft.getInstance().setScreen(new DialogueScreen(s));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private DialogueScript.Line line() {
        return script.line(index);
    }

    private void go(int to) {
        if (to < 0) {
            onClose();
            return;
        }
        index = to;
        lineAt = Util.getMillis();
        flagSent = false;
    }

    private void sendFlag(String flag) {
        if (flag != null && !flag.isBlank()) NetworkHandler.CHANNEL.sendToServer(new StoryFlagPacket(flag));
    }

    private int typed(DialogueScript.Line l) {
        if (l.text() == null) return 0;
        return (int) Math.min(l.text().length(), (Util.getMillis() - lineAt) / CHAR_MS);
    }

    private boolean complete(DialogueScript.Line l) {
        return l.text() == null || typed(l) >= l.text().length();
    }

    private void advance() {
        DialogueScript.Line l = line();
        if (l.intro() != null) {
            go(script.after(index, null));
            return;
        }
        if (!complete(l)) {
            lineAt = Util.getMillis() - CHAR_MS * (l.text().length() + 1);
            return;
        }
        if (!l.choices().isEmpty()) return;
        sendFlag(l.flag());
        go(script.after(index, null));
    }

    private void choose(int n) {
        DialogueScript.Line l = line();
        if (n < 0 || n >= l.choices().size() || !complete(l)) return;
        DialogueScript.Choice c = l.choices().get(n);
        sendFlag(l.flag());
        sendFlag(c.flag());
        go(script.after(index, c.next()));
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) {                       // Esc
            onClose();
            return true;
        }
        if (key == 257 || key == 32 || key == 335) {
            advance();
            return true;
        }
        if (key >= 49 && key <= 57) {           // 1..9
            choose(key - 49);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        DialogueScript.Line l = line();
        if (button == 0 && l.intro() == null && complete(l) && !l.choices().isEmpty()) {
            int n = choiceAt(mx, my);
            if (n >= 0) {
                choose(n);
                return true;
            }
            return true;
        }
        if (button == 0) advance();
        return true;
    }

    private int boxX() { return (int) (width * 0.08); }
    private int boxW() { return (int) (width * 0.84); }
    private int boxH() { return (int) (height * 0.27); }
    private int boxY() { return height - boxH() - (int) (height * 0.05); }

    private int choiceY(int n) {
        return boxY() - (int) (height * 0.075) * (line().choices().size() - n);
    }

    private int choiceAt(double mx, double my) {
        List<DialogueScript.Choice> cs = line().choices();
        for (int i = 0; i < cs.size(); i++) {
            int y = choiceY(i), h = (int) (height * 0.065);
            if (mx >= boxX() + boxH() && mx <= boxX() + boxW() && my >= y && my < y + h) return i;
        }
        return -1;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        long now = Util.getMillis();
        g.fillGradient(0, 0, width, height, 0x30000000, 0xB0000000);
        DialogueScript.Line l = line();

        if (l.intro() != null) {
            renderIntro(g, l, now);
            return;
        }
        CharacterDef def = l.speaker() == null ? null : Characters.get(l.speaker());
        CharacterFaces faces = def == null ? null : CharacterFaces.of(def);
        boolean typing = !complete(l);
        if (faces != null) {
            FaceExpression e = l.expr();
            if (typing && (now / 110) % 2 == 0 && (e == FaceExpression.NEUTRAL || e == FaceExpression.HAPPY)) e = FaceExpression.TALK;
            else if (!typing && e == FaceExpression.NEUTRAL && now % 4300 < 140) e = FaceExpression.BLINK;
            faces.set(e);
        }

        int bx = boxX(), by = boxY(), bw = boxW(), bh = boxH();
        g.fill(bx, by, bx + bw, by + bh, 0xD0060A12);
        g.renderOutline(bx, by, bw, bh, 0xFF000000 | (def == null ? 0x9FD6FF : def.color()));
        int face = bh - 12;
        if (faces != null) {
            g.fill(bx + 6, by + 6, bx + 6 + face, by + 6 + face, 0xFF10161F);
            g.blit(faces.texture(), bx + 6, by + 6, face, face, 8, 8, 8, 8, 64, 64);
            g.blit(faces.texture(), bx + 6, by + 6, face, face, 40, 8, 8, 8, 64, 64);
        }
        int tx = bx + face + 16;
        if (def != null) g.drawString(font, def.name(), tx, by + 8, 0xFF000000 | def.color(), true);
        if (l.text() != null) {
            String shown = l.text().substring(0, typed(l));
            List<FormattedCharSequence> rows = font.split(Component.literal(shown), bw - face - 28);
            int y = by + 24;
            for (FormattedCharSequence r : rows) {
                g.drawString(font, r, tx, y, 0xFFE8EEF4, false);
                y += font.lineHeight + 3;
            }
        }
        if (complete(l)) {
            List<DialogueScript.Choice> cs = l.choices();
            for (int i = 0; i < cs.size(); i++) {
                int y = choiceY(i), h = (int) (height * 0.065);
                boolean hover = choiceAt(mx, my) == i;
                g.fill(bx + bh, y, bx + bw, y + h, hover ? 0xE02A4A6A : 0xC0101822);
                g.renderOutline(bx + bh, y, bw - bh, h, 0xFF000000 | (hover ? 0xFFFFFF : 0x6F8CA0));
                g.drawString(font, (i + 1) + ".  " + cs.get(i).text(), bx + bh + 10, y + (h - font.lineHeight) / 2, 0xFFFFFFFF, false);
            }
            if (cs.isEmpty() && (now / 500) % 2 == 0) g.drawString(font, "▼", bx + bw - 16, by + bh - 14, 0xFFBFD6E6, false);
        }
    }

    private void renderIntro(GuiGraphics g, DialogueScript.Line l, long now) {
        CharacterDef def = Characters.get(l.intro());
        long t = now - lineAt;
        if (t > INTRO_MS) {
            if (!flagSent) {
                flagSent = true;
                sendFlag("met_" + l.intro());
            }
            go(script.after(index, null));
            return;
        }
        float a = t < 400 ? t / 400f : (t > INTRO_MS - 500 ? (INTRO_MS - t) / 500f : 1f);
        int alpha = (int) (255 * Math.max(0, Math.min(1, a)));
        if (def == null) return;
        CharacterFaces faces = CharacterFaces.of(def);
        int size = (int) (height * 0.28);
        int x = width / 2 - size / 2, y = (int) (height * 0.28);
        if (faces != null) {
            faces.set(FaceExpression.NEUTRAL);
            g.setColor(1f, 1f, 1f, alpha / 255f);
            g.blit(faces.texture(), x, y, size, size, 8, 8, 8, 8, 64, 64);
            g.blit(faces.texture(), x, y, size, size, 40, 8, 8, 8, 64, 64);
            g.setColor(1f, 1f, 1f, 1f);
        }
        g.pose().pushPose();
        float s = 3.0f;
        g.pose().translate(width / 2f, y + size + 18, 0);
        g.pose().scale(s, s, 1);
        g.drawCenteredString(font, def.name(), 0, 0, (alpha << 24) | def.color());
        g.pose().popPose();
    }
}
