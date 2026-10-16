package com.lotusblight.overlay;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Every scene the editor can open. A new mini-game or cutscene is added here, once, and shows up in the editor by itself. */
final class ScenePrograms {
    private static List<SceneProgram> all;

    static synchronized List<SceneProgram> all() {
        if (all == null) {
            all = new ArrayList<>();
            int[] skin = new int[64 * 64];
            try (var in = ScenePrograms.class.getResourceAsStream("/assets/lotusblight/textures/overlay/glitcher.png")) {
                BufferedImage img = javax.imageio.ImageIO.read(in);
                skin = img.getRGB(0, 0, 64, 64, null, 0, 64);
            } catch (Exception e) {
                System.err.println("Glitcher skin: " + e);
            }
            all.add(new Song2Program(skin));
        }
        return all;
    }

    static SceneProgram get(String id) {
        for (SceneProgram p : all()) if (p.id().equals(id)) return p;
        return all().get(0);
    }
}
