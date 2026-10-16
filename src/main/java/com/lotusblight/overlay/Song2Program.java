package com.lotusblight.overlay;

import java.awt.Graphics2D;

/**
 * The second song's picture without the circles: the backdrop, the columns, the solid Glitcher, the eye, the windows, the snakes with the TV
 * and the black phrase screens, each as a layer. (The game draws the same parts, with the circles among them, in its own fight.)
 */
final class Song2Program implements SceneProgram {
    private final ColumnsFx columns = new ColumnsFx();
    private final SnakesFx snakes = new SnakesFx();
    private final GlitcherCloseup closeup;
    private final PhraseScreen phrases = new PhraseScreen();
    private final ContactBreak contact = new ContactBreak(ContactBreak.beatTimes(), d -> { }, () -> { });
    private final int[] skin;
    private Hazards hazards;
    private int hazardsH;

    Song2Program(int[] skin) {
        this.skin = skin;
        this.closeup = new GlitcherCloseup(skin);
    }

    @Override
    public String id() {
        return "song2";
    }

    @Override
    public String title() {
        return "Песня 2";
    }

    @Override
    public String[][] layers() {
        return new String[][]{
                {"backdrop", "Фон"},
                {"columns", "Колонки"},
                {"glitcher", "Глитчер"},
                {"eye", "Глаз"},
                {"windows", "Окна (след)"},
                {"windows2", "Окна Глитчера"},
                {"snakes", "Змеи и телевизор"},
                {"phrases", "Чёрные экраны с фразами"}};
    }

    /** The tilt and push of the columns and of the Glitcher's blows, as the game applies it to what it draws. */
    private Graphics2D camera(Graphics2D g, int w, int h, double t) {
        Graphics2D c = (Graphics2D) g.create();
        double[] k1 = columns.camera(t), k2 = closeup.camera(t, w, h);
        double roll = k1[0] + k2[0], zoom = k1[1] * k2[1];
        if (roll != 0 || zoom != 1 || k2[2] != 0 || k2[3] != 0) {
            c.translate(w / 2.0 + k2[2], h / 2.0 + k2[3]);
            c.rotate(roll);
            c.scale(zoom, zoom);
            c.translate(-w / 2.0, -h / 2.0);
        }
        return c;
    }

    @Override
    public void render(Graphics2D g, int w, int h, double t) {
        if (hazards == null || hazardsH != h) {
            hazards = new Hazards(skin, h);
            hazardsH = h;
        }
        Hazards hz = hazards;
        contact.update(t);
        Graphics2D cam = camera(g, w, h, t);
        Layers.draw(cam, w, h, "backdrop", c -> contact.renderBackdrop(c, w, h, t));
        Layers.draw(cam, w, h, "columns", c -> columns.render(c, w, h, t));
        Layers.draw(cam, w, h, "glitcher", c -> closeup.render(c, w, h, t));
        cam.dispose();
        Layers.draw(g, w, h, "eye", c -> contact.renderEye(c, w, h, t));
        Layers.draw(g, w, h, "windows", c -> hz.renderCarryover(c, w, h, t));
        Graphics2D cam2 = camera(g, w, h, t);
        Layers.draw(cam2, w, h, "windows2", c -> hz.renderSong2(c, w, h, t));
        cam2.dispose();
        Graphics2D cam3 = camera(g, w, h, t);
        Layers.draw(cam3, w, h, "snakes", c -> snakes.render(c, w, h, t, ms -> {
            double[] c3 = closeup.hand(ms, h);
            return c3 != null ? c3 : hz.songTwoHand(ms, w, h);
        }));
        cam3.dispose();
        Layers.draw(g, w, h, "phrases", c -> phrases.render(c, w, h, t));
        SceneFx.flash(g, w, h, t);
    }
}
