package com.lotusblight.overlay;

import java.awt.Graphics2D;

/**
 * A scene the editor can open: a song's picture, a cutscene, another mini-game's screen. The game says what the scene is made of (its layers
 * and their names) and how to draw it at a moment; the editor asks, shows the layers and lets the author work them, with nothing about any
 * particular scene written into the editor. A scene draws every visible part through {@link Layers#draw}, so that the author's changes
 * (hide, move, scale, fade) and the events of the timeline reach it without the scene knowing about them.
 */
interface SceneProgram {
    /** A short name for the files, e.g. "song2". */
    String id();

    /** The name to show the author. */
    String title();

    /** The layers from the bottom to the top: {id, name to show}. */
    String[][] layers();

    /**
     * The events the scene already has in its own code, as the author's defaults, for the editor to put on the timeline: {track, from s, to s,
     * strength, name}. Only the events the game really obeys when the timeline has them belong here.
     */
    default java.util.List<Object[]> events() {
        return java.util.List.of();
    }

    /** Draws the picture at the moment (ms of the scene's own clock) at the size. */
    void render(Graphics2D g, int w, int h, double ms);
}
