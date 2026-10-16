package com.lotusblight.overlay;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;

/**
 * The events of the scene's timeline made real: what the editor puts on the "camera" and "effects" tracks is done to the picture here, in the
 * game and in the editor's preview alike. An event is on from its start to its end; its strength is the number set in the editor (1 is the
 * usual). The kinds are named as in the editor (the preset names):
 * <ul>
 * <li>camera: shake (the picture trembles, dying away), push-in (it comes nearer and goes back), tilt (it leans and straightens),
 * jerk (a quick punch in with a lean, settling at once);</li>
 * <li>effects: flash (white, fading out).</li>
 * </ul>
 */
final class SceneFx {
    static final int CAMERA = 0, EFFECTS = 2;

    private static final String SHAKE = "Тряска";
    private static final String PUSH = "Наезд";
    private static final String TILT = "Наклон";
    private static final String JERK = "Рывок";
    private static final String FLASH = "Вспышка";

    private static List<SceneData.Event> active(int track, double ms) {
        SceneData.Cut c = SceneData.current().cut(Layers.CUT);
        return c == null ? List.of() : c.active(track, ms);
    }

    /** What the camera events add at a time: {roll (rad), zoom (a factor), dx, dy (in screen heights)}. */
    static double[] camera(double ms) {
        double roll = 0, zoom = 1, dx = 0, dy = 0;
        for (SceneData.Event e : active(CAMERA, ms)) {
            double len = Math.max(1, (e.to() - e.from()) * 1000), age = ms - e.from() * 1000, u = Math.max(0, Math.min(1, age / len));
            double k = e.value();
            if (e.label().equals(SHAKE)) {
                java.util.Random r = new java.util.Random((long) (ms / 24));
                double a = 0.022 * k * (1 - u);
                dx += (r.nextDouble() - 0.5) * 2 * a;
                dy += (r.nextDouble() - 0.5) * 2 * a;
            } else if (e.label().equals(PUSH)) {
                zoom *= 1 + 0.14 * k * Math.sin(Math.PI * u);
            } else if (e.label().equals(TILT)) {
                roll += 0.12 * k * Math.sin(Math.PI * u);
            } else if (e.label().equals(JERK)) {
                double s = Math.exp(-age / Math.max(60, len * 0.35));
                zoom *= 1 + 0.12 * k * s;
                roll += 0.03 * k * s * (((long) (e.from() * 10) % 2 == 0) ? 1 : -1);
            }
        }
        return new double[]{roll, zoom, dx, dy};
    }

    /** The flashes over the whole picture. */
    static void flash(Graphics2D g, int w, int h, double ms) {
        for (SceneData.Event e : active(EFFECTS, ms)) {
            if (!e.label().equals(FLASH)) continue;
            double len = Math.max(1, (e.to() - e.from()) * 1000), u = Math.max(0, Math.min(1, (ms - e.from() * 1000) / len));
            double a = Math.min(1, 0.85 * e.value() * (1 - u) * (1 - u));
            if (a <= 0.005) continue;
            Graphics2D f = (Graphics2D) g.create();
            f.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) a));
            f.setColor(Color.WHITE);
            f.fillRect(0, 0, w, h);
            f.dispose();
        }
    }
}
