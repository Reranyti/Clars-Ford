package com.lotusblight.overlay;

/**
 * The names of the events the game itself puts on a scene's timeline, as the editor shows them and as the game reads them back: the
 * names are the whole agreement between the two (an event is found by its track and its name). Written with escapes so that no file
 * encoding can spoil them.
 */
final class SceneLabels {
    static final int CAMERA = 0, TEXT = 1, EFFECTS = 2, ACTOR = 3, SNAKES = 4;

    /** The stretches of the close-up Glitcher's part in the song. */
    static final String SILHOUETTE = "Силуэт";
    static final String WHOLE = "Он целиком";
    static final String EYES = "Глаза";
    static final String SCREAM = "Крик";
    static final String STUCK = "Застревание";

    /** The series of blows (the number is the kind: the right fist, the fists in turn, both together). */
    static final String[] SERIES = {
            "Серия: правой",
            "Серия: по очереди",
            "Серия: двумя"};

    /** The rests between the series (slumped, palm on the glass, claws, speaking, peering, staggering back, glancing about). */
    static final String[] RESTS = {
            "Пауза: усталый",
            "Пауза: ладонь на стекле",
            "Пауза: когти",
            "Пауза: говорит",
            "Пауза: вплотную",
            "Пауза: отшатнулся",
            "Пауза: озирается"};

    /** A phrase on its black screen: "Phrase: " and then the words. */
    static final String PHRASE = "Фраза: ";

    /** One snake out of the TV. */
    static final String SNAKE = "Змея";

    static int indexOf(String[] list, String label) {
        for (int i = 0; i < list.length; i++) if (list[i].equals(label)) return i;
        return -1;
    }
}
