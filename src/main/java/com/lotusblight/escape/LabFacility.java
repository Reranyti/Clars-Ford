package com.lotusblight.escape;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The BIG laboratory (prototype, 2026-10-10): a grid of rooms joined by short halls, a different maze for each seed.
 *
 * <p>Rooms are 11x11 inside (7 high) with doorways at the middle of the walls; between two neighbouring rooms runs a straight
 * hall (3 wide, 4 high, 7 long). Every hall carries one obstacle: nothing, a ceiling you must crouch under, a pit to jump with
 * water at the bottom and a ladder back, or rubble. The maze is a spanning tree plus a few loops, so there are several ways to the
 * exit; the shortest way is lit yellow, dead ends red, the rest white. The runner starts at the west door of the first room and the
 * exit is the bright room (lime floor) in the far column.
 *
 * <p>Axes as in {@link LabProto.Layout}: i = forward, w = across (positive to the right of forward), h = up, the floor block is h = 0.
 * The same seed always gives the same laboratory (needed later, when an old world has to rebuild it).
 */
public final class LabFacility implements LabProto.Course {
    public static final int GRID_I = 6, GRID_W = 6, PITCH = 20, INNER = 11, ROOM_H = 7, HALL_H = 4, HALL_LEN = 7;

    public enum Kind { START, EXIT, TANKS, RECORDS, CELLS, OFFICE, STORAGE, COLLAPSED, HUB }

    public enum Hall { PLAIN, CROUCH, PIT, RUBBLE }

    public enum Light { WHITE, YELLOW, RED }

    private final ServerLevel level;
    private final BlockPos origin;
    private final Direction dir, right;
    private final long seed;
    private final RandomSource random;

    private final Kind[][] kind = new Kind[GRID_I][GRID_W];
    private final boolean[][] east = new boolean[GRID_I][GRID_W];     // (a,b) - (a+1,b)
    private final boolean[][] north = new boolean[GRID_I][GRID_W];    // (a,b) - (a,b+1), towards +w
    private final Hall[][] eastHall = new Hall[GRID_I][GRID_W];
    private final Hall[][] northHall = new Hall[GRID_I][GRID_W];
    private final Light[][] eastLight = new Light[GRID_I][GRID_W];
    private final Light[][] northLight = new Light[GRID_I][GRID_W];
    private final boolean[][] onPath = new boolean[GRID_I][GRID_W];
    private final int[][][] pathNext = new int[GRID_I][GRID_W][];
    private int startB, exitA, exitB;

    public LabFacility(ServerLevel level, BlockPos floorOrigin, Direction dir, long seed) {
        this.level = level;
        this.origin = floorOrigin;
        this.dir = dir;
        this.right = dir.getClockWise();
        this.seed = seed;
        this.random = RandomSource.create(seed);
        generate();
    }

    // ------------------------------------------------------------------ geometry

    public BlockPos at(int i, int w, int h) {
        return origin.relative(dir, i).relative(right, w).above(h);
    }

    @Override
    public ServerLevel level() {
        return level;
    }

    @Override
    public Direction dir() {
        return dir;
    }

    public int roomI(int a) {
        return a * PITCH;
    }

    public int roomCI(int a) {
        return a * PITCH + 6;
    }

    public int roomCW(int b) {
        return (b - GRID_W / 2) * PITCH;
    }

    public int sizeI() {
        return (GRID_I - 1) * PITCH + 13;
    }

    public int sizeW() {
        return (GRID_W - 1) * PITCH + 13;
    }

    public int roomCount() {
        return GRID_I * GRID_W;
    }

    public int connectorCount() {
        int n = 0;
        for (int a = 0; a < GRID_I; a++) for (int b = 0; b < GRID_W; b++) n += (east[a][b] ? 1 : 0) + (north[a][b] ? 1 : 0);
        return n;
    }

    public boolean eastLinked(int a, int b) {
        return east[a][b];
    }

    public boolean northLinked(int a, int b) {
        return north[a][b];
    }

    public Kind kindOf(int a, int b) {
        return kind[a][b];
    }

    public int startRow() {
        return startB;
    }

    public int exitColumn() {
        return exitA;
    }

    public int exitRow() {
        return exitB;
    }

    @Override
    public Vec3 startPos() {
        BlockPos p = at(roomCI(0) - 2, roomCW(startB), 1);
        return new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
    }

    @Override
    public AABB exitBox() {
        BlockPos a = at(roomI(exitA) + 1, roomCW(exitB) - 5, 1), b = at(roomI(exitA) + 11, roomCW(exitB) + 5, 4);
        return new AABB(Math.min(a.getX(), b.getX()), a.getY(), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, b.getY() + 1, Math.max(a.getZ(), b.getZ()) + 1);
    }

    /** How many halls on the shortest way carry each kind of obstacle. */
    public Map<Hall, Integer> hallTypesOnPath() {
        Map<Hall, Integer> counts = new EnumMap<>(Hall.class);
        for (int a = 0; a < GRID_I; a++) for (int b = 0; b < GRID_W; b++) {
            int[] n = pathNext[a][b];
            if (n == null) continue;
            Hall h = n[0] == a + 1 ? eastHall[a][b] : n[0] == a - 1 ? eastHall[a - 1][b] : n[1] == b + 1 ? northHall[a][b] : northHall[a][b - 1];
            counts.merge(h, 1, Integer::sum);
        }
        return counts;
    }

    // ------------------------------------------------------------------ the maze

    private boolean in(int a, int b) {
        return a >= 0 && a < GRID_I && b >= 0 && b < GRID_W;
    }

    private void link(int a, int b, int a2, int b2) {
        if (a2 == a + 1) east[a][b] = true;
        else if (a2 == a - 1) east[a2][b] = true;
        else if (b2 == b + 1) north[a][b] = true;
        else north[a][b2] = true;
    }

    private boolean linked(int a, int b, int a2, int b2) {
        if (a2 == a + 1) return east[a][b];
        if (a2 == a - 1) return east[a2][b];
        if (b2 == b + 1) return north[a][b];
        return north[a][b2];
    }

    private int degree(int a, int b) {
        int n = 0;
        if (a + 1 < GRID_I && east[a][b]) n++;
        if (a > 0 && east[a - 1][b]) n++;
        if (b + 1 < GRID_W && north[a][b]) n++;
        if (b > 0 && north[a][b - 1]) n++;
        return n;
    }

    private static final int[][] D4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private void generate() {
        startB = GRID_W / 2;
        // a random spanning tree (depth-first), then a few loops so that there are several ways to the exit
        boolean[][] seen = new boolean[GRID_I][GRID_W];
        ArrayDeque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{0, startB});
        seen[0][startB] = true;
        while (!stack.isEmpty()) {
            int[] c = stack.peek();
            List<int[]> options = new ArrayList<>();
            for (int[] d : D4) {
                int na = c[0] + d[0], nb = c[1] + d[1];
                if (in(na, nb) && !seen[na][nb]) options.add(new int[]{na, nb});
            }
            if (options.isEmpty()) {
                stack.pop();
                continue;
            }
            int[] o = options.get(random.nextInt(options.size()));
            link(c[0], c[1], o[0], o[1]);
            seen[o[0]][o[1]] = true;
            stack.push(o);
        }
        int loops = 0;                                    // keep drawing until there really are several loops, not just attempts
        for (int k = 0; k < 500 && loops < GRID_I; k++) {
            int a = random.nextInt(GRID_I), b = random.nextInt(GRID_W);
            int[] d = D4[random.nextInt(4)];
            int na = a + d[0], nb = b + d[1];
            if (in(na, nb) && !linked(a, b, na, nb)) {
                link(a, b, na, nb);
                loops++;
            }
        }
        // distances from the start; the exit is the farthest room of the last column
        int[][] dist = new int[GRID_I][GRID_W];
        int[][][] parent = new int[GRID_I][GRID_W][];
        for (int[] row : dist) java.util.Arrays.fill(row, -1);
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{0, startB});
        dist[0][startB] = 0;
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] d : D4) {
                int na = c[0] + d[0], nb = c[1] + d[1];
                if (in(na, nb) && dist[na][nb] < 0 && linked(c[0], c[1], na, nb)) {
                    dist[na][nb] = dist[c[0]][c[1]] + 1;
                    parent[na][nb] = c;
                    q.add(new int[]{na, nb});
                }
            }
        }
        exitA = GRID_I - 1;
        exitB = 0;
        for (int b = 0; b < GRID_W; b++) if (dist[exitA][b] > dist[exitA][exitB]) exitB = b;
        // the shortest way, marked room by room (pathNext[a][b] = the next room towards the exit)
        int[] cur = {exitA, exitB};
        onPath[exitA][exitB] = true;
        while (parent[cur[0]][cur[1]] != null) {
            int[] p = parent[cur[0]][cur[1]];
            pathNext[p[0]][p[1]] = cur;
            onPath[p[0]][p[1]] = true;
            cur = p;
        }
        // obstacles: the halls on the way cycle through every kind, the others are drawn at random
        Hall[] cycle = {Hall.CROUCH, Hall.PIT, Hall.RUBBLE, Hall.PLAIN};
        int step = 0;
        int[] c = {0, startB};
        while (pathNext[c[0]][c[1]] != null) {
            int[] n = pathNext[c[0]][c[1]];
            setHall(c[0], c[1], n[0], n[1], cycle[step++ % cycle.length]);
            c = n;
        }
        for (int a = 0; a < GRID_I; a++) for (int b = 0; b < GRID_W; b++) {
            if (a + 1 < GRID_I && east[a][b] && eastHall[a][b] == null) eastHall[a][b] = randomHall();
            if (b + 1 < GRID_W && north[a][b] && northHall[a][b] == null) northHall[a][b] = randomHall();
        }
        // light: yellow on the shortest way, red next to a dead end, white elsewhere
        for (int a = 0; a < GRID_I; a++) for (int b = 0; b < GRID_W; b++) {
            if (a + 1 < GRID_I && east[a][b]) eastLight[a][b] = lightFor(a, b, a + 1, b);
            if (b + 1 < GRID_W && north[a][b]) northLight[a][b] = lightFor(a, b, a, b + 1);
        }
        // what each room is
        Kind[] common = {Kind.TANKS, Kind.TANKS, Kind.RECORDS, Kind.RECORDS, Kind.CELLS, Kind.OFFICE, Kind.STORAGE, Kind.COLLAPSED, Kind.HUB};
        for (int a = 0; a < GRID_I; a++) for (int b = 0; b < GRID_W; b++) kind[a][b] = common[random.nextInt(common.length)];
        kind[0][startB] = Kind.START;
        kind[exitA][exitB] = Kind.EXIT;
    }

    private Hall randomHall() {
        int r = random.nextInt(100);
        return r < 45 ? Hall.PLAIN : r < 65 ? Hall.RUBBLE : r < 85 ? Hall.CROUCH : Hall.PIT;
    }

    private void setHall(int a, int b, int a2, int b2, Hall h) {
        if (a2 == a + 1) eastHall[a][b] = h;
        else if (a2 == a - 1) eastHall[a2][b] = h;
        else if (b2 == b + 1) northHall[a][b] = h;
        else northHall[a][b2] = h;
    }

    private boolean isDeadEnd(int a, int b) {
        return degree(a, b) == 1 && !(a == 0 && b == startB) && !(a == exitA && b == exitB);
    }

    private Light lightFor(int a, int b, int a2, int b2) {
        boolean path = (pathNext[a][b] != null && pathNext[a][b][0] == a2 && pathNext[a][b][1] == b2)
                || (pathNext[a2][b2] != null && pathNext[a2][b2][0] == a && pathNext[a2][b2][1] == b);
        if (path) return Light.YELLOW;
        if (isDeadEnd(a, b) || isDeadEnd(a2, b2)) return Light.RED;
        return Light.WHITE;
    }

    private Light roomLight(int a, int b) {
        if (onPath[a][b]) return Light.YELLOW;
        return isDeadEnd(a, b) ? Light.RED : Light.WHITE;
    }

    /** A text map for the chat: rooms (letter and * on the shortest way) joined by halls (- plain, c crouch, p pit, r rubble; = and capitals on the way). */
    public String asciiMap() {
        StringBuilder sb = new StringBuilder("Карта (вправо: вперёд от входа; вверх: направо). S вход, E выход, * верный путь; зал: T ёмкости R записи C камеры O кабинет W склад X обвал H колонны; коридор: - обычный, c присед, p яма, r обломки (заглавные на верном пути)\n");
        for (int b = GRID_W - 1; b >= 0; b--) {
            StringBuilder row = new StringBuilder(), between = new StringBuilder();
            for (int a = 0; a < GRID_I; a++) {
                row.append(kindChar(kind[a][b])).append(onPath[a][b] ? '*' : ' ');
                if (a + 1 < GRID_I) row.append(east[a][b] ? hallChar(eastHall[a][b], eastLight[a][b] == Light.YELLOW, true) : ' ');
                between.append(b > 0 && north[a][b - 1] ? hallChar(northHall[a][b - 1], northLight[a][b - 1] == Light.YELLOW, false) : ' ').append("  ");
            }
            sb.append(row).append('\n');
            if (b > 0) sb.append(between).append('\n');
        }
        return sb.toString();
    }

    private static char kindChar(Kind k) {
        return switch (k) {
            case START -> 'S';
            case EXIT -> 'E';
            case TANKS -> 'T';
            case RECORDS -> 'R';
            case CELLS -> 'C';
            case OFFICE -> 'O';
            case STORAGE -> 'W';
            case COLLAPSED -> 'X';
            case HUB -> 'H';
        };
    }

    private static char hallChar(Hall h, boolean way, boolean horizontal) {
        char c = switch (h) {
            case PLAIN -> horizontal ? '-' : '|';
            case CROUCH -> 'c';
            case PIT -> 'p';
            case RUBBLE -> 'r';
        };
        if (way) return h == Hall.PLAIN ? (horizontal ? '=' : '!') : Character.toUpperCase(c);
        return c;
    }

    // ------------------------------------------------------------------ building

    private void set(int i, int w, int h, BlockState s) {
        level.setBlock(at(i, w, h), s, 2);
    }

    private static BlockState air() {
        return Blocks.AIR.defaultBlockState();
    }

    private BlockState wall() {
        int n = random.nextInt(10);
        return (n < 6 ? Blocks.STONE_BRICKS : n < 9 ? Blocks.POLISHED_ANDESITE : Blocks.IRON_BLOCK).defaultBlockState();
    }

    private static BlockState lightBlock(Light l) {
        return switch (l) {
            case YELLOW -> Blocks.OCHRE_FROGLIGHT.defaultBlockState();
            case RED -> Blocks.REDSTONE_LAMP.defaultBlockState().setValue(BlockStateProperties.LIT, true);
            case WHITE -> Blocks.SEA_LANTERN.defaultBlockState();
        };
    }

    public void preload() {
        for (int i = -4; i <= sizeI() + 4; i += 8) {
            for (int w = roomCW(0) - 8; w <= roomCW(GRID_W - 1) + 8; w += 8) level.getChunk(at(i, w, 0));
        }
    }

    public void build() {
        preload();
        for (int a = 0; a < GRID_I; a++) for (int b = 0; b < GRID_W; b++) buildRoom(a, b);
        for (int a = 0; a < GRID_I; a++) for (int b = 0; b < GRID_W; b++) {
            if (a + 1 < GRID_I && east[a][b]) buildHall(true, a, b);
            if (b + 1 < GRID_W && north[a][b]) buildHall(false, a, b);
        }
        // the way in: a doorway in the west wall of the first room
        int cw = roomCW(startB);
        for (int dw = -1; dw <= 1; dw++) for (int h = 1; h <= HALL_H; h++) set(roomI(0), cw + dw, h, air());
        for (int a = 0; a < GRID_I; a++) for (int b = 0; b < GRID_W; b++) decorate(a, b);
    }

    private BlockState floorBlock(Kind k, int i, int w, boolean inner) {
        if (k == Kind.EXIT && inner) return Blocks.LIME_CONCRETE.defaultBlockState();
        if (k == Kind.COLLAPSED) return (random.nextInt(3) == 0 ? Blocks.COBBLED_DEEPSLATE : Blocks.POLISHED_DEEPSLATE).defaultBlockState();
        return (Math.floorMod(i + w, 2) == 0 ? Blocks.POLISHED_DEEPSLATE : Blocks.DEEPSLATE_TILES).defaultBlockState();
    }

    private void buildRoom(int a, int b) {
        int i0 = roomI(a), cw = roomCW(b);
        Kind k = kind[a][b];
        for (int di = 0; di <= INNER + 1; di++) for (int dw = -6; dw <= 6; dw++) {
            boolean wallCell = di == 0 || di == INNER + 1 || Math.abs(dw) == 6;
            set(i0 + di, cw + dw, 0, floorBlock(k, i0 + di, cw + dw, !wallCell));
            set(i0 + di, cw + dw, ROOM_H + 1, Blocks.SMOOTH_STONE.defaultBlockState());
            for (int h = 1; h <= ROOM_H; h++) set(i0 + di, cw + dw, h, wallCell ? wall() : air());
        }
    }

    /** Position inside the room of cell (a,b) relative to its centre. */
    private BlockPos rp(int a, int b, int di, int dw, int h) {
        return at(roomCI(a) + di, roomCW(b) + dw, h);
    }

    private void rset(int a, int b, int di, int dw, int h, BlockState s) {
        level.setBlock(rp(a, b, di, dw, h), s, 2);
    }

    /** One hall between two rooms, built along its own axis: {@code along} runs from the first room to the second, {@code across} is 0 on the hall's centre line. */
    private BlockPos hp(boolean eastWest, int base, int center, int along, int across, int h) {
        return eastWest ? at(base + along, center + across, h) : at(center + across, base + along, h);
    }

    private void hset(boolean eastWest, int base, int center, int along, int across, int h, BlockState s) {
        level.setBlock(hp(eastWest, base, center, along, across, h), s, 2);
    }

    private void buildHall(boolean eastWest, int a, int b) {
        Hall type = eastWest ? eastHall[a][b] : northHall[a][b];
        Light light = eastWest ? eastLight[a][b] : northLight[a][b];
        int base = eastWest ? roomI(a) + 12 : roomCW(b) + 6;        // the wall slice of the first room, along the hall's axis
        int center = eastWest ? roomCW(b) : roomCI(a);
        int len = HALL_LEN + 2;                                      // both door slices included: 0 .. HALL_LEN+1
        for (int s = 0; s <= len; s++) {
            boolean doorSlice = s == 0 || s == len;
            for (int across = -2; across <= 2; across++) {
                boolean wallCell = Math.abs(across) == 2;
                for (int h = -5; h <= -1; h++) hset(eastWest, base, center, s, across, h, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
                hset(eastWest, base, center, s, across, 0, floorBlock(Kind.HUB, s, across, true));
                hset(eastWest, base, center, s, across, HALL_H + 1, Blocks.SMOOTH_STONE.defaultBlockState());
                for (int h = 1; h <= HALL_H; h++) {
                    if (doorSlice && wallCell) continue;               // the room's own wall stays beside the doorway
                    hset(eastWest, base, center, s, across, h, wallCell ? wall() : air());
                }
            }
        }
        // obstacles in the hall proper (slices 1..HALL_LEN)
        switch (type) {
            case CROUCH -> {
                BlockState slab = Blocks.COBBLED_DEEPSLATE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
                for (int s = 2; s <= HALL_LEN - 1; s++) for (int across = -1; across <= 1; across++) {
                    hset(eastWest, base, center, s, across, 2, slab);           // free height above the floor: exactly 1.5 blocks
                    for (int h = 3; h <= HALL_H; h++) hset(eastWest, base, center, s, across, h, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
                }
            }
            case PIT -> {
                BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, eastWest ? dir : right);
                for (int s = 3; s <= 5; s++) for (int across = -1; across <= 1; across++) {
                    for (int h = -2; h <= 0; h++) hset(eastWest, base, center, s, across, h, air());
                    for (int h = -4; h <= -3; h++) hset(eastWest, base, center, s, across, h, Blocks.WATER.defaultBlockState());
                }
                for (int across : new int[]{-1, 1}) for (int h = -4; h <= 0; h++) hset(eastWest, base, center, 3, across, h, ladder.setValue(LadderBlock.WATERLOGGED, h <= -3));
            }
            case RUBBLE -> {
                for (int s = 2; s <= 5; s++) {
                    int across = random.nextInt(3) - 1;
                    hset(eastWest, base, center, s, across, 1, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
                    if (random.nextBoolean()) hset(eastWest, base, center, s, -across, 1, Blocks.GRAVEL.defaultBlockState());
                }
                hset(eastWest, base, center, 6, 0, 1, Blocks.COBWEB.defaultBlockState());
            }
            case PLAIN -> { }
        }
        // the doorways through the two rooms' walls
        for (int across = -1; across <= 1; across++) for (int h = 1; h <= HALL_H; h++) {
            hset(eastWest, base, center, 0, across, h, air());
            hset(eastWest, base, center, len, across, h, air());
        }
        for (int s : new int[]{2, 6}) hset(eastWest, base, center, s, 0, HALL_H + 1, lightBlock(light));
    }

    // ------------------------------------------------------------------ rooms: what is in them

    private void decorate(int a, int b) {
        Kind k = kind[a][b];
        Light l = roomLight(a, b);
        boolean collapsed = k == Kind.COLLAPSED;
        for (int[] p : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}, {0, 0}}) {
            if (k == Kind.EXIT) rset(a, b, p[0], p[1], ROOM_H + 1, lightBlock(Light.WHITE));
            else if (random.nextInt(100) < (collapsed ? 20 : 75)) rset(a, b, p[0], p[1], ROOM_H + 1, lightBlock(l));
        }
        switch (k) {
            case START -> {
                tank(a, b, -4, -4);
                tank(a, b, 2, 2);
                lectern(a, b, 4, -3, "Запись 1", "[ЗАГЛУШКА] Здесь будет запись из лаборатории. Текст пишет автор.");
            }
            case EXIT -> {
                for (int di = -5; di <= 5; di += 2) for (int dw : new int[]{-5, 5}) rset(a, b, di, dw, 1, Blocks.SEA_LANTERN.defaultBlockState());
            }
            case TANKS -> {
                tank(a, b, -4, -4);
                tank(a, b, 2, -4);
                tank(a, b, -4, 2);
                tank(a, b, 2, 2);
            }
            case RECORDS -> {
                String[] t = {"Запись А", "Запись Б", "Запись В", "Запись Г"};
                int n = 0;
                for (int di : new int[]{-4, 4}) for (int dw : new int[]{-3, 3}) lectern(a, b, di, dw, t[n++], "[ЗАГЛУШКА] Текст записи напишет автор.");
                for (int dw = -1; dw <= 1; dw++) rset(a, b, 0, dw, 1, Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState());
            }
            case CELLS -> {
                cell(a, b, -4, -4);
                cell(a, b, 2, -4);
                cell(a, b, -4, 2);
                cell(a, b, 2, 2);
            }
            case OFFICE -> {
                for (int dw = -2; dw <= 2; dw++) rset(a, b, 0, dw, 1, Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState());
                for (int dw = -4; dw <= 4; dw++) {
                    if (Math.abs(dw) <= 1) continue;                               // the doorway's line stays free
                    for (int h = 1; h <= 3; h++) rset(a, b, -5, dw, h, Blocks.BOOKSHELF.defaultBlockState());
                }
                lectern(a, b, 3, 0, "Рабочий журнал", "[ЗАГЛУШКА] Журнал кабинета. Текст пишет автор.");
            }
            case STORAGE -> {
                for (int[] p : new int[][]{{-4, -4}, {-4, 4}, {4, -4}, {4, 4}, {-4, -3}, {4, 3}}) for (int h = 1; h <= 2; h++) rset(a, b, p[0], p[1], h, Blocks.BARREL.defaultBlockState());
            }
            case COLLAPSED -> {
                for (int di = -5; di <= 5; di++) for (int dw = -5; dw <= 5; dw++) {
                    if (Math.abs(dw) <= 1 || Math.abs(di) <= 1) continue;           // the doorways' lines stay passable
                    int r = random.nextInt(100);
                    if (r < 14) rset(a, b, di, dw, 1, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
                    else if (r < 18) rset(a, b, di, dw, 2, Blocks.COBWEB.defaultBlockState());
                    else if (r < 22) rset(a, b, di, dw, 1, Blocks.GRAVEL.defaultBlockState());
                }
            }
            case HUB -> {
                for (int[] p : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) for (int h = 1; h <= ROOM_H; h++) rset(a, b, p[0], p[1], h, Blocks.IRON_BLOCK.defaultBlockState());
            }
        }
    }

    private void tank(int a, int b, int di, int dw) {
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) for (int h = 1; h <= 4; h++) {
            boolean shell = x == 0 || x == 2 || z == 0 || z == 2 || h == 4;
            rset(a, b, di + x, dw + z, h, shell ? Blocks.GLASS.defaultBlockState() : (h <= 3 ? Blocks.WATER.defaultBlockState() : air()));
        }
        rset(a, b, di + 1, dw + 1, 1, Blocks.ZOMBIE_HEAD.defaultBlockState());     // a stand-in for a subject: a proper model comes later
    }

    private void cell(int a, int b, int di, int dw) {
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) for (int h = 1; h <= 3; h++) {
            boolean shell = x == 0 || x == 2 || z == 0 || z == 2;
            rset(a, b, di + x, dw + z, h, shell ? Blocks.IRON_BARS.defaultBlockState() : air());
        }
        rset(a, b, di + 1, dw + 1, 1, Blocks.ZOMBIE_HEAD.defaultBlockState());
    }

    private void lectern(int a, int b, int di, int dw, String title, String text) {
        Direction face = Math.abs(di) >= Math.abs(dw) ? (di > 0 ? dir.getOpposite() : dir) : (dw > 0 ? right.getOpposite() : right);
        BlockPos p = rp(a, b, di, dw, 1);
        level.setBlock(p, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, face).setValue(LecternBlock.HAS_BOOK, true), 2);
        BlockEntity be = level.getBlockEntity(p);
        if (be instanceof LecternBlockEntity lectern) {
            ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
            CompoundTag tag = book.getOrCreateTag();
            tag.putString("title", title);
            tag.putString("author", "заглушка");
            ListTag pages = new ListTag();
            pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(title + "\n\n" + text))));
            tag.put("pages", pages);
            lectern.setBook(book);
        }
    }

    public long seed() {
        return seed;
    }
}
