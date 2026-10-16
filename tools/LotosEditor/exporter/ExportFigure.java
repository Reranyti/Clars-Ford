package com.lotusblight.overlay;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.util.Locale;

/**
 * Writes the figure of the game (the Glitcher's solid body, R15) as plain text for the editor: for every face its four corners in space,
 * with the skin's texture coordinates. The editor draws exactly the same shape the game does. Run from the repository root:
 * javac -cp ... -sourcepath src/main/java -d OUT tools/LotosEditor/exporter/ExportFigure.java
 */
public final class ExportFigure {
    public static void main(String[] args) throws Exception {
        BufferedImage img;
        try (var in = ExportFigure.class.getResourceAsStream("/assets/lotusblight/textures/overlay/glitcher.png")) {
            img = javax.imageio.ImageIO.read(in);
        }
        javax.imageio.ImageIO.write(img, "png", new File(args[0], "glitcher.png"));
        int[] skin = img.getRGB(0, 0, 64, 64, null, 0, 64);
        Actor actor = new Actor(skin, false);
        actor.begin();
        Rig15.Skel sk = actor.rig.solve(actor.pose);
        try (PrintWriter w = new PrintWriter(new File(args[0], "figure.txt"), "UTF-8")) {
            for (Rig15.Face f : actor.rig.faces) {
                double[][] uv = {{f.u0(), f.v0()}, {f.u1(), f.v0()}, {f.u1(), f.v1()}, {f.u0(), f.v1()}};
                for (int i = 0; i < 4; i++) {
                    double[] p = sk.carry(f.joint(), f.corners()[i]);
                    w.println(String.format(Locale.ROOT, "%.4f %.4f %.4f %.4f %.4f", p[0], p[1], p[2], uv[i][0] / 64.0, uv[i][1] / 64.0));
                }
            }
        }
        System.out.println("faces: " + actor.rig.faces.size());
    }
}
