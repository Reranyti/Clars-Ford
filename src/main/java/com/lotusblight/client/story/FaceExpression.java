package com.lotusblight.client.story;

import java.util.Locale;

/** The faces a character can make; {@code BLINK} and {@code TALK} are also used by the animation itself. */
public enum FaceExpression {
    NEUTRAL, BLINK, TALK, HAPPY, ANGRY, SAD, SCARED, SHOCK;

    public static FaceExpression parse(String s) {
        if (s == null) return NEUTRAL;
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return NEUTRAL;
        }
    }
}
