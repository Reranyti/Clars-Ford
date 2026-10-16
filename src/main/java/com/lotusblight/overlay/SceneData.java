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

    record Shot(String name, double from, double to, double[] pos, double[] target, double fov, double roll, String note) {}

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
                                    Double.parseDouble(p[6]), Double.parseDouble(p[7]), p.length > 8 ? unesc(p[8]) : ""));
                        }
                    }
                    case "E" -> {
                        if (cur != null) {
                            cur.events.add(new Event(Integer.parseInt(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]), unesc(p[4]),
                                    p.length > 5 ? Double.parseDouble(p[5]) : 1));
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
