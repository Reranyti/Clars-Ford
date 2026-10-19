package com.lotusblight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The nine save slots of an episode. A slot is an ordinary Minecraft world kept in the saves folder under a fixed folder name
 * (`lotos_ep1_slot_3`), so nothing but these folders is ever read, copied or deleted by the slot screen.
 */
final class SaveSlots {
    static final int COUNT = 9;

    private SaveSlots() {}

    /** What the screen shows about one slot; {@code used} is false for an empty one. */
    record Info(boolean used, String name, long lastPlayed, long playTicks, String ending) {
        static final Info EMPTY = new Info(false, "", 0, 0, null);
    }

    static String id(int episode, int slot) {
        return "lotos_ep" + episode + "_slot_" + slot;
    }

    /** True only for the folder names this class makes itself (the single guard in front of every delete and copy). */
    static boolean isSlotId(String levelId) {
        return levelId != null && levelId.matches("lotos_ep[0-9]{1,2}_slot_[1-9]");
    }

    private static LevelStorageSource source() {
        return Minecraft.getInstance().getLevelSource();
    }

    /** Reads the slots of an episode from the saves folder (only the nine folders of this episode are looked at). */
    static Map<Integer, Info> read(int episode) {
        Map<Integer, Info> out = new HashMap<>();
        Path base = source().getBaseDir();
        for (int slot = 1; slot <= COUNT; slot++) {
            String id = id(episode, slot);
            Path dir = base.resolve(id);
            Path dat = dir.resolve("level.dat");
            if (!Files.isRegularFile(dat)) {
                out.put(slot, Info.EMPTY);
                continue;
            }
            String name = "Файл " + slot;
            long last = 0;
            long ticks = 0;
            String ending = null;
            try {
                CompoundTag root = NbtIo.readCompressed(dat.toFile());
                CompoundTag data = root.getCompound("Data");
                if (data.contains("LevelName")) name = data.getString("LevelName");
                last = data.getLong("LastPlayed");
                ticks = data.getLong("Time");
                ending = endingOf(data);
            } catch (IOException | RuntimeException ignored) {
                // an unreadable level.dat: the slot is shown as used with what is known
            }
            out.put(slot, new Info(true, name, last, ticks, ending));
        }
        return out;
    }

    /**
     * The one ending a file is heading for, worked out from the world itself: the line the player has chosen (it is kept in the player's data
     * inside level.dat). Null while nothing is chosen. Only the two open lines are named here; the hidden ones are never shown.
     */
    private static String endingOf(CompoundTag data) {
        return null;                                          // the old war/alliance lines are gone; the new endings are marked here when they exist
    }

    /** Starts a new world in an empty slot (the game's own creation, with the settings of the episode). */
    static void createNew(int episode, int slot) {
        String id = id(episode, slot);
        if (!isSlotId(id)) return;
        LevelSettings settings = new LevelSettings("Файл " + slot, GameType.SURVIVAL, false, Difficulty.NORMAL, false, new GameRules(), WorldDataConfiguration.DEFAULT);
        Minecraft.getInstance().createWorldOpenFlows().createFreshLevel(id, settings, WorldOptions.defaultWithRandomSeed(), WorldPresets::createNormalWorldDimensions);
    }

    /** Loads a used slot. */
    static void play(net.minecraft.client.gui.screens.Screen parent, int episode, int slot) {
        String id = id(episode, slot);
        if (!isSlotId(id)) return;
        Minecraft.getInstance().createWorldOpenFlows().loadLevel(parent, id);
    }

    /** Deletes the world of a slot (only a folder with a slot name). */
    static boolean erase(int episode, int slot) {
        String id = id(episode, slot);
        if (!isSlotId(id)) return false;
        try (LevelStorageSource.LevelStorageAccess access = source().createAccess(id)) {
            access.deleteLevel();
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** Copies a used slot into an empty one; the copy is renamed to its own slot name. */
    static boolean copy(int episode, int from, int to) {
        String a = id(episode, from);
        String b = id(episode, to);
        if (!isSlotId(a) || !isSlotId(b) || a.equals(b)) return false;
        Path base = source().getBaseDir();
        Path src = base.resolve(a);
        Path dst = base.resolve(b);
        if (!Files.isDirectory(src) || Files.exists(dst)) return false;
        try (Stream<Path> walk = Files.walk(src)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                if (p.getFileName() != null && "session.lock".equals(p.getFileName().toString())) continue;
                Path target = dst.resolve(src.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(target);
                else Files.copy(p, target);
            }
        } catch (IOException | RuntimeException e) {
            deleteQuietly(dst);
            return false;
        }
        try (LevelStorageSource.LevelStorageAccess access = source().createAccess(b)) {
            access.renameLevel("Файл " + to);
        } catch (IOException | RuntimeException ignored) {
            // the copy keeps the name of its source: still a working world
        }
        return true;
    }

    private static void deleteQuietly(Path dir) {
        if (!dir.getFileName().toString().matches("lotos_ep[0-9]{1,2}_slot_[1-9]")) return;       // never anything else
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // left over: harmless
                }
            });
        } catch (IOException ignored) {
            // nothing to remove
        }
    }

    /** Hours and minutes from game ticks (20 per second). */
    static String time(long ticks) {
        long minutes = ticks / 20 / 60;
        return String.format("%d:%02d", minutes / 60, minutes % 60);
    }

    static String when(long millis) {
        if (millis <= 0) return "";
        return new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(new java.util.Date(millis));
    }
}
