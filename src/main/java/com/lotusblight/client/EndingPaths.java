package com.lotusblight.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The way to each ending, step by step, as the mod has it now: every event, dialogue and choice that belongs to the line, taken from
 * the code and from what the project owner has told (steps that are decided but not built yet are marked planned). A choice forks
 * into its answers; an answer that leads to another line says which. Shown on the ending's map; for now everything is open.
 */
final class EndingPaths {
    enum Kind {
        EVENT("событие", 0xFFE6C36A),
        CHOICE("выбор", 0xFF6AD0E6),
        DIALOGUE("диалог", 0xFFE4E7D8),
        BOSS("босс", 0xFFE66A6A),
        SCENE("сцена", 0xFFB98AE6);

        final String label;
        final int color;

        Kind(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    /** One answer of a choice; {@code to} is the line it leads to (null: the answer keeps you where you are). */
    record Opt(String text, String to) {}

    /** One square of the chain. A choice has {@code options}; the chain goes on through {@code main}. */
    record Step(String title, Kind kind, boolean planned, List<Opt> options, int main) {}

    private EndingPaths() {}

    private static Step ev(String t) { return new Step(t, Kind.EVENT, false, List.of(), 0); }
    private static Step dl(String t) { return new Step(t, Kind.DIALOGUE, false, List.of(), 0); }
    private static Step sc(String t) { return new Step(t, Kind.SCENE, false, List.of(), 0); }
    private static Step bs(String t) { return new Step(t, Kind.BOSS, false, List.of(), 0); }
    private static Step plan(Kind k, String t) { return new Step(t, k, true, List.of(), 0); }
    private static Step ch(String t, int main, Opt... o) { return new Step(t, Kind.CHOICE, false, List.of(o), main); }
    private static Opt o(String text) { return new Opt(text, null); }
    private static Opt o(String text, String to) { return new Opt(text, to); }

    /** The common start of every line. {@code main} is kept for the callers; the old first conversation with the Lotus is gone. */
    private static List<Step> start(int main) {
        return List.of(ev("Спавн"));
    }

    private static List<Step> join(List<Step> a, Step... b) {
        List<Step> all = new ArrayList<>(a);
        all.addAll(List.of(b));
        return all;
    }

    private static final Map<String, List<Step>> PATHS = Map.ofEntries(
            Map.entry("traitor", join(start(0),
                    plan(Kind.SCENE, "Альтернативная ветка с персонажами и событиями нового лора: будет написана"))),
            Map.entry("cursed", join(start(0),
                    ev("Не вставать ни на одну сторону"),
                    ev("Три утопления у главного лотоса (в радиусе 16 блоков)"),
                    ev("Три смерти от стража подряд"),
                    ev("Две смерти от удушья подряд"),
                    sc("Сцена входа: красное небо, вода, подъём, обвал мира"),
                    ev("Рог"),
                    bs("Бой: три песни, глитч, урок Хончо между первой и второй, перерыв, финальная анимация"),
                    ev("Побег: сцена выхода"),
                    plan(Kind.SCENE, "Концовка проклятой: единственная без смерти"))),
            Map.entry("true", join(start(0),
                    plan(Kind.SCENE, "Истинная линия: ломаем печать и собираем всех Архитекторов (Светов); не написана"))),
            Map.entry("chromo", join(start(0),
                    ev("Сложность «Хромо»: пятая в выборе сложности, необратима, мобы вдвое агрессивнее"),
                    sc("Проклятая ветка на Хромо: карты песен 1 и 2, щадящие по HP; события первой песни"),
                    plan(Kind.BOSS, "Песни 3 и 4 в конце ветки"),
                    plan(Kind.SCENE, "Глитчер забирает компьютер: игры, уровни из редакторов; четыре сцены в духе игр"),
                    plan(Kind.SCENE, "Четвёртая фаза Хромо-Глитчера"))));

    static List<Step> of(String endingId) {
        List<Step> steps = endingId == null ? null : PATHS.get(endingId);
        return steps == null ? start(0) : steps;
    }
}
