package com.lotusblight.overlay;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The scenes made in the Lotos Editor (tools/LotosEditor, project.txt, copied into the resources as scenes.txt): for every cut its shots (a camera
 * each) and its events on the tracks. The game plays them; the editor draws the same thing. One line a record, tab separated:
 * {@code CUT name song}, {@code S name from to "x y z" "x y z" fov roll note}, {@code E track from to label value}. Times are seconds.
 */
final class SceneData {
    static final String[] TRACKS = {"camera", "text", "effects", "actor", "snakes"};

    record Shot(String name, double from, double to, double[] pos, double[] target, double fov, double roll, String note, boolean move) {}

    record Event(int track, double from, double to, String label, double value) {
        boolean at(double ms) {
            return ms >= from * 1000 && ms < to * 1000;
        }
    }

    static final class Cut {
        final String name;
        final String song;
        final List<Shot> shots = new ArrayList<>();
        final List<Event> events = new ArrayList<>();
        /** What the scene says about each layer, by name ({@code O} records). */
        final Map<String, Layers.Xf> objects = new LinkedHashMap<>();

        Cut(String name, String song) {
            this.name = name;
            this.song = song;
        }

        /** The shot whose camera is on at the time: the latest one that has begun. */
        Shot shotAt(double ms) {
            Shot best = null;
            for (Shot s : shots) if (s.from * 1000 <= ms && (best == null || s.from >= best.from)) best = s;
            return best == null && !shots.isEmpty() ? shots.get(0) : best;
        }

        /**
         * The camera at a time: {x, y, z, target x, y, z, fov, roll}. A shot marked "move" glides (smoothly) to the camera of the next one
         * over its own length; the others hold still.
         */
        double[] cameraAt(double ms) {
            Shot s = shotAt(ms);
            if (s == null) return null;
            double[] c = {s.pos[0], s.pos[1], s.pos[2], s.target[0], s.target[1], s.target[2], s.fov, s.roll};
            if (!s.move) return c;
            Shot next = null;
            for (Shot o : shots) if (o.from > s.from && (next == null || o.from < next.from)) next = o;
            if (next == null || next.to <= next.from) return c;
            double k = Math.max(0, Math.min(1, (ms / 1000.0 - s.from) / Math.max(0.001, next.from - s.from)));
            k = k * k * (3 - 2 * k);
            double[] d = {next.pos[0], next.pos[1], next.pos[2], next.target[0], next.target[1], next.target[2], next.fov, next.roll};
            for (int i = 0; i < 8; i++) c[i] += (d[i] - c[i]) * k;
            return c;
        }

        /** The events of a track that are on at the time. */
        List<Event> active(int track, double ms) {
            List<Event> out = new ArrayList<>();
            for (Event e : events) if (e.track == track && e.at(ms)) out.add(e);
            return out;
        }

        /** The events of a track with the given label (the preset names the editor offers), on or not. */
        List<Event> named(int track, String label) {
            List<Event> out = new ArrayList<>();
            for (Event e : events) if (e.track == track && e.label.equals(label)) out.add(e);
            return out;
        }
    }

    private final Map<String, Cut> cuts = new LinkedHashMap<>();

    Cut cut(String name) {
        return cuts.get(name);
    }

    Iterable<Cut> all() {
        return cuts.values();
    }

    private static double[] vec(String s) {
        String[] p = s.trim().split("\\s+");
        return new double[]{Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2])};
    }

    private static String unesc(String s) {
        return s.replace("\\n", "\n");
    }

    static SceneData parse(BufferedReader r) throws IOException {
        SceneData d = new SceneData();
        Cut cur = null;
        String line;
        while ((line = r.readLine()) != null) {
            String[] p = line.split("\t", -1);
            try {
                switch (p[0]) {
                    case "CUT" -> {
                        cur = new Cut(unesc(p[1]), p[2]);
                        d.cuts.put(cur.name, cur);
                    }
                    case "S" -> {
                        if (cur != null) {
                            cur.shots.add(new Shot(unesc(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]), vec(p[4]), vec(p[5]),
                                    Double.parseDouble(p[6]), Double.parseDouble(p[7]), p.length > 8 ? unesc(p[8]) : "", p.length > 9 && p[9].equals("1")));
                        }
                    }
                    case "E" -> {
                        if (cur != null) {
                            cur.events.add(new Event(Integer.parseInt(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]), unesc(p[4]),
                                    p.length > 5 ? Double.parseDouble(p[5]) : 1));
                        }
                    }
                    case "O" -> {
                        if (cur != null) {
                            cur.objects.put(p[1], new Layers.Xf(p[2].equals("1"), Double.parseDouble(p[3]), Double.parseDouble(p[4]), Double.parseDouble(p[5]),
                                    Double.parseDouble(p[6]), Double.parseDouble(p[7])));
                        }
                    }
                    default -> {
                        // the header and anything unknown: skipped
                    }
                }
            } catch (RuntimeException ignored) {
                // a broken line is skipped, not fatal
            }
        }
        return d;
    }

    private static SceneData live;
    private static long liveStamp, liveChecked;

    /**
     * The scenes now: while developing (-Dlotos.scenes=path to the editor's project.txt) the file is read straight from the editor and read again
     * whenever it is saved there, so a change in the editor shows in the game at once; otherwise the ones shipped with the game.
     */
    static synchronized SceneData current() {
        String path = System.getProperty("lotos.scenes");
        if (path == null) {
            if (live == null) live = load();
            return live;
        }
        long now = System.currentTimeMillis();
        if (live == null || now - liveChecked > 30) {
            liveChecked = now;
            java.io.File f = new java.io.File(path);
            if (f.exists() && (live == null || f.lastModified() != liveStamp)) {
                try (BufferedReader r = java.nio.file.Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
                    live = parse(r);
                    liveStamp = f.lastModified();
                } catch (IOException ignored) {
                    // kept as it was
                }
            }
            if (live == null) live = load();
        }
        return live;
    }

    /** The scenes shipped with the game; an empty set when there are none. */
    static SceneData load() {
        try (InputStream in = SceneData.class.getResourceAsStream("/assets/lotusblight/overlay/scenes.txt")) {
            if (in != null) return parse(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (IOException ignored) {
            // no scenes
        }
        return new SceneData();
    }
}
