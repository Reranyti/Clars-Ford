package com.lotusblight.story;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Every beat of the story in order: a beat is one stretch of an episode (a scene, an event, a free part). The world keeps the id of the
 * current one in {@link StoryState}. The list follows the author's episode notes; a beat is added only when the author has told it.
 * {@code title} is for the developer (command output, logs), not shown to the player.
 */
public final class Beats {
    public record Beat(int episode, String id, String title) {}

    private static final List<Beat> ALL = new ArrayList<>();

    static {
        // Episode 1: "Полночь твоих кошмаров"
        add(1, "dream_intro", "Вступление в измерении снов");
        add(1, "wake_up", "Пробуждение дома, в своей комнате");
        add(1, "scanner", "Сканер взят, изучаем Лотос, записи идут в дневник");
        add(1, "exploration", "Исследование мира (мелкие события, квесты)");
        add(1, "capture", "Захват: Рея берут в плен");
        add(1, "escape", "Побег по огромному комплексу за границей карты");
        add(1, "strangle", "Удушение и избиение (анимация в Blender)");
        add(1, "care", "Рей несёт Трейлда домой, ухаживает и готовит");
        add(1, "architects_exist", "Рей узнаёт, что Архитекторы существуют");
        // Episode 2: "Операция Айсберг"
        add(2, "ford_join", "Вступление в Форд, проверка на предательства");
        add(2, "operation_start", "Начало операции: собрание героев");
        add(2, "assault", "Штурм Форда");
        add(2, "carried_out", "Трейлд выносит Рея, уборка и восстановление");
        // Episode 3: "Расследование предателей"
        add(3, "rift", "Разлом в команде, подозрения");
        add(3, "home_again", "Рей возвращается домой и ложится спать");
        add(3, "dream_forest", "Мир снов: лес, пустыня, замок");
        add(3, "kyle", "Кайл: вызов на бой");
        add(3, "kyle_fight", "Босс-файт с Кайлом, награда «Глаз света»");
        add(3, "eye_hands", "Пробуждение: глаз в руках");
    }

    private Beats() {}

    private static void add(int episode, String id, String title) {
        ALL.add(new Beat(episode, id, title));
    }

    public static List<Beat> all() {
        return List.copyOf(ALL);
    }

    public static Optional<Beat> find(String id) {
        return ALL.stream().filter(b -> b.id().equals(id)).findFirst();
    }

    /** The beat after {@code id}, or empty at the end of the written story. */
    public static Optional<Beat> next(String id) {
        for (int i = 0; i < ALL.size() - 1; i++) {
            if (ALL.get(i).id().equals(id)) return Optional.of(ALL.get(i + 1));
        }
        return Optional.empty();
    }
}
