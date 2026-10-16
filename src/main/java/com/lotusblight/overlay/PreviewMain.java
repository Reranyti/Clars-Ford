package com.lotusblight.overlay;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * The picture of any scene for the Lotos Editor: the game's own scene programs ({@link SceneProgram}) drawn at the moment asked for, with no
 * sound. The editor starts this and talks to it through the pipes; the lines it sends:
 * <pre>
 * S                              the list of scene programs and their layers
 * R ms width height keep scene   draw the scene at the moment (keep=1: remember the layers, for picking)
 * P x y                          the layer under the point of the last picture
 * B layer                        the box of a layer in the last picture
 * E scene                        the events the scene has in its code (kind 5: track, from, to, strength, name; one a line)
 * </pre>
 * and what comes back, each as four bytes of length, one byte of kind, the data: 3 a picture (width, height, then B G R A for every pixel),
 * 1 the name of a layer, 2 a box, 4 the list. Whatever comes from the editor's scenes (the camera, the layers, the events) is read live.
 */
public final class PreviewMain {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        OutputStream raw = System.out;
        System.setOut(new PrintStream(System.err, true));      // nothing but answers goes down the pipe

        DataOutputStream out = new DataOutputStream(raw);
        BufferedImage frame = null;
        byte[] bytes = null;
        BufferedReader r = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
        String line;
        while ((line = r.readLine()) != null) {
            String[] p = line.trim().split("\\s+");
            if (p[0].equals("S")) {                                                    // what scenes there are, and what each is made of
                StringBuilder sb = new StringBuilder();
                for (SceneProgram sp : ScenePrograms.all()) {
                    sb.append(sp.id()).append('\t').append(sp.title()).append('\t');
                    for (String[] l : sp.layers()) sb.append(l[0]).append('=').append(l[1]).append(';');
                    sb.append('\n');
                }
                answer(out, 4, sb.toString().getBytes("UTF-8"));
                continue;
            }
            if (p[0].equals("E") && p.length >= 2) {                                  // the events a scene already has in its code
                StringBuilder sb = new StringBuilder();
                for (Object[] e : ScenePrograms.get(p[1]).events()) {
                    sb.append(e[0]).append('\t').append(e[1]).append('\t').append(e[2]).append('\t').append(e[3]).append('\t').append(e[4]).append('\n');
                }
                answer(out, 5, sb.toString().getBytes("UTF-8"));
                continue;
            }
            if (p[0].equals("P") && p.length >= 3) {                                  // which layer is under this point of the last picture
                String id = Layers.keep == null ? null : Layers.pick(Layers.keep, Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                answer(out, 1, (id == null ? "" : id).getBytes("UTF-8"));
                continue;
            }
            if (p[0].equals("B") && p.length >= 2) {                                  // the box of a layer in the last picture
                answer(out, 2, (Layers.keep == null ? "" : Layers.bounds(Layers.keep, p[1])).getBytes("UTF-8"));
                continue;
            }
            if (!p[0].equals("R") || p.length < 4) continue;
            double t = Double.parseDouble(p[1]);
            int w = Integer.parseInt(p[2]), h = Integer.parseInt(p[3]);
            boolean keepLayers = p.length < 5 || !p[4].equals("0");                    // the layers are kept (for picking) only when the picture stands still
            Layers.program = p.length >= 6 ? p[5] : "song2";
            GlitcherCloseup.RES = w >= 1400 ? 0.9 : w >= 1000 ? 0.6 : 0.42;
            if (frame == null || frame.getWidth() != w || frame.getHeight() != h) frame = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = frame.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(6, 6, 14));
            g.fillRect(0, 0, w, h);
            Layers.keep = keepLayers ? Layers.newKeep() : null;
            try {
                ScenePrograms.get(Layers.program).render(g, w, h, t);
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
            g.dispose();
            int[] px = ((java.awt.image.DataBufferInt) frame.getRaster().getDataBuffer()).getData();
            if (bytes == null || bytes.length != px.length * 4 + 8) bytes = new byte[px.length * 4 + 8];
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(bytes);
            bb.putInt(w).putInt(h);
            java.nio.IntBuffer ib = bb.order(java.nio.ByteOrder.LITTLE_ENDIAN).asIntBuffer();
            for (int i = 0; i < px.length; i++) ib.put(i, px[i] | 0xFF000000);        // B, G, R, A in the bytes
            answer(out, 3, bytes);
        }
    }

    private static void answer(DataOutputStream out, int kind, byte[] data) throws java.io.IOException {
        out.writeInt(data.length + 1);
        out.writeByte(kind);
        out.write(data);
        out.flush();
    }
}
