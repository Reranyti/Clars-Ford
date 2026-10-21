package com.lotusblight.client;

/** The menus are used with the arrow keys or W A S D to choose and Enter to go in; the mouse stays for clicking. */
final class MenuKeys {
    private MenuKeys() {}

    /** W A S D as the arrow keys (up, left, down, right); every other key as it is. */
    static int arrow(int key) {
        return switch (key) {
            case 87 -> 265;      // W
            case 65 -> 263;      // A
            case 83 -> 264;      // S
            case 68 -> 262;      // D
            default -> key;
        };
    }
}
