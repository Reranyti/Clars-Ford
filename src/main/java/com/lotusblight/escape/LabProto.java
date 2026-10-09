package com.lotusblight.escape;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.UUID;

/**
 * PROTOTYPE of the redesigned escape lab (2026-10-10). It is NOT the real lab: it is built on demand by
 * {@code /lotus labproto build} in a test world and nothing else in the mod touches it. It exists so the author can walk the
 * ideas and say "yes" or "not like that" before the real lab is rebuilt around them.
 *
 * <p>Along the way (slice i = distance in the facing direction, w = across, h = up, the floor block is h = 0):
 * <ol>
 *   <li>0-11 a lab room: specimen tanks and lecterns with record books (the texts are placeholders: the author writes the lore);</li>
 *   <li>12-41 a broken zig-zag corridor with rubble, infection patches and dim light;</li>
 *   <li>42-49 a tunnel with a ceiling you can only pass crouching (a gap of exactly 1.5 blocks);</li>
 *   <li>50-67 a parkour hall: pits (jumpable) with water at the bottom and ladders back up;</li>
 *   <li>68-112 a station with a minecart, a fork at slice 85 (left: lit yellow, the way out; right: lit red, a dead end), chosen by looking or A/D.</li>
 * </ol>
 * The Lotus chases from the start: a mass of dark particles and tentacles that follows the player's own trail and speeds up.
 * Being caught here is harmless (back to the start): the real run's deadly outcome stays in {@link LotusChaseEvent}.
 */
public final class LabProto {
    private static LabProto instance;

    public static LabProto get() {
        return instance;
    }

    public LabProto() {
        instance = this;
    }

    /** What a run needs from whatever is built: the world, which way it faces, where the runner starts and where the way out is. */
    public interface Course {
        ServerLevel level();

        Direction dir();

        Vec3 startPos();

        AABB exitBox();
    }

    // ================================================================== the layout (also used by the game tests)

    /** The built geometry: where everything is, how to build it, and a rail walker for checking the cart's way. */
    public static final class Layout implements Course {
        public static final int ROOM_END = 11, CORRIDOR_START = 12, CORRIDOR_END = 41, TUNNEL_START = 42, TUNNEL_END = 49;
        public static final int PARKOUR_START = 50, PARKOUR_END = 67, STATION_START = 68, STATION_END = 112;
        public static final int CART_I = 70, MAIN_END = 84, FORK_I = 85, LEFT_END = 108, RIGHT_END = 100;
        /** Pits: {first empty slice, last empty slice}. All at most 3 empty slices wide: a sprint jump clears that. */
        public static final int[][] PITS = {{53, 55}, {58, 59}, {62, 64}};
        /** The platform at slices 60-61 is one block higher. */
        public static final int RAISED_FROM = 60, RAISED_TO = 61;
        /** Zig-zag: the corridor's centre line (w) for each 5-slice segment. */
        private static final int[] ZIG = {0, 3, -2, 3, -3, 0};

        public final ServerLevel level;
        public final BlockPos origin;
        public final Direction dir;
        private final Direction right;
        private final RandomSource random;

        public Layout(ServerLevel level, BlockPos floorOrigin, Direction dir) {
            this.level = level;
            this.origin = floorOrigin;
            this.dir = dir;
            this.right = dir.getClockWise();
            this.random = RandomSource.create(floorOrigin.asLong());
        }

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

        public BlockPos forkPos() {
            return at(FORK_I, 0, 1);
        }

        public BlockPos cartPos() {
            return at(CART_I, 0, 1);
        }

        /** Where the runner starts: inside the room, near the entrance. */
        public Vec3 startPos() {
            BlockPos p = at(2, 0, 1);
            return new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
        }

        /** The finish: the far end of the left branch. */
        public AABB exitBox() {
            BlockPos a = at(106, -5, 1), b = at(112, -2, 3);
            return new AABB(Math.min(a.getX(), b.getX()), a.getY(), Math.min(a.getZ(), b.getZ()),
                    Math.max(a.getX(), b.getX()) + 1, b.getY() + 1, Math.max(a.getZ(), b.getZ()) + 1);
        }

        // ------------------------------------------------------------ building

        public void preload() {
            for (int i = -4; i <= STATION_END + 4; i += 8) {
                for (int w = -14; w <= 14; w += 8) level.getChunk(at(i, w, 0));
            }
        }

        private void set(int i, int w, int h, BlockState s) {
            level.setBlock(at(i, w, h), s, 2);
        }

        private static BlockState air() {
            return Blocks.AIR.defaultBlockState();
        }

        private static BlockState wall(RandomSource r) {
            int n = r.nextInt(10);
            return (n < 6 ? Blocks.STONE_BRICKS : n < 9 ? Blocks.POLISHED_ANDESITE : Blocks.IRON_BLOCK).defaultBlockState();
        }

        public void build() {
            preload();
            buildRoom();
            buildCorridor();
            buildTunnel();
            buildParkour();
            buildStation();
            buildRails();
            buildProps();
        }

        /** A generic hall: solid shell, air inside; {@code interiorLo/Hi} give the open range of each slice. */
        private void hall(int i0, int i1, int maxW, int height, java.util.function.IntFunction<int[]> interior, BlockState floorA, BlockState floorB) {
            for (int i = i0; i <= i1; i++) {
                int[] in = interior.apply(i);
                for (int w = -maxW; w <= maxW; w++) {
                    boolean open = false;
                    for (int k = 0; k + 1 < in.length; k += 2) open |= w >= in[k] && w <= in[k + 1];
                    set(i, w, 0, Math.floorMod(i + w, 2) == 0 ? floorA : floorB);
                    set(i, w, height + 1, Blocks.SMOOTH_STONE.defaultBlockState());
                    for (int h = 1; h <= height; h++) set(i, w, h, open ? air() : wall(random));
                }
            }
        }

        private void buildRoom() {
            hall(0, ROOM_END, 7, 6, i -> new int[]{-5, 5}, Blocks.POLISHED_DEEPSLATE.defaultBlockState(), Blocks.DEEPSLATE_TILES.defaultBlockState());
            // the wall behind the start, with a 3x3 doorway
            for (int w = -7; w <= 7; w++) for (int h = 0; h <= 7; h++) set(-1, w, h, Math.abs(w) <= 1 && h >= 1 && h <= 3 ? air() : Blocks.STONE_BRICKS.defaultBlockState());
            for (int i = 1; i <= ROOM_END; i += 4) for (int w = -3; w <= 3; w += 3) set(i, w, 7, Blocks.SEA_LANTERN.defaultBlockState());
        }

        private int zigCentre(int i) {
            return ZIG[Math.min((i - CORRIDOR_START) / 5, ZIG.length - 1)];
        }

        private void buildCorridor() {
            hall(CORRIDOR_START, CORRIDOR_END, 10, 5, i -> {
                int c = zigCentre(i);
                if ((i - CORRIDOR_START) % 5 == 0 && i > CORRIDOR_START) {          // the turn: open to both centre lines
                    int p = zigCentre(i - 1);
                    return new int[]{Math.min(c, p) - 1, Math.max(c, p) + 1};
                }
                return new int[]{c - 1, c + 1};
            }, Blocks.POLISHED_DEEPSLATE.defaultBlockState(), Blocks.COBBLED_DEEPSLATE.defaultBlockState());
            for (int i = CORRIDOR_START + 2; i <= CORRIDOR_END - 2; i++) {
                int c = zigCentre(i);
                if (random.nextInt(3) == 0) {                                          // rubble: one or two cells of the three, never all
                    set(i, c + random.nextInt(2) - 1, 1, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
                }
                if (i % 7 == 0 && random.nextBoolean()) set(i, c, 6, Blocks.SEA_LANTERN.defaultBlockState());          // dim light, some are missing
                if (random.nextInt(5) == 0) set(i, c + 2, 2, random.nextBoolean() ? Blocks.NETHER_WART_BLOCK.defaultBlockState() : Blocks.SHROOMLIGHT.defaultBlockState());
                if (random.nextInt(9) == 0) set(i, c, 2, Blocks.COBWEB.defaultBlockState());
            }
        }

        private void buildTunnel() {
            hall(TUNNEL_START, TUNNEL_END, 4, 5, i -> new int[]{-1, 1}, Blocks.POLISHED_DEEPSLATE.defaultBlockState(), Blocks.DEEPSLATE_TILES.defaultBlockState());
            BlockState slab = Blocks.COBBLED_DEEPSLATE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
            for (int i = TUNNEL_START + 1; i <= TUNNEL_END - 1; i++) {
                for (int w = -1; w <= 1; w++) {
                    set(i, w, 2, slab);                                       // free height above the floor: exactly 1.5 blocks, a crouch fits, standing does not
                    for (int h = 3; h <= 5; h++) set(i, w, h, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
                }
            }
        }

        private boolean isPit(int i) {
            for (int[] p : PITS) if (i >= p[0] && i <= p[1]) return true;
            return false;
        }

        private void buildParkour() {
            hall(PARKOUR_START, PARKOUR_END, 6, 7, i -> new int[]{-4, 4}, Blocks.POLISHED_ANDESITE.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState());
            for (int i = PARKOUR_START; i <= PARKOUR_END; i++) {
                for (int w = -6; w <= 6; w++) for (int h = -5; h <= -1; h++) set(i, w, h, Blocks.DEEPSLATE_BRICKS.defaultBlockState());   // solid below
                if (isPit(i)) {
                    for (int w = -4; w <= 4; w++) {
                        for (int h = -2; h <= 0; h++) set(i, w, h, air());
                        for (int h = -4; h <= -3; h++) set(i, w, h, Blocks.WATER.defaultBlockState());     // a soft landing, 3 blocks down
                    }
                }
                if (i >= RAISED_FROM && i <= RAISED_TO) for (int w = -4; w <= 4; w++) set(i, w, 1, Blocks.STONE_BRICKS.defaultBlockState());
                if (i % 4 == 0) for (int w = -2; w <= 2; w += 4) set(i, w, 8, Blocks.SEA_LANTERN.defaultBlockState());
            }
            // a ladder on the near edge of each pit, at both side walls: falling in costs time, not the run
            BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, dir);
            for (int[] p : PITS) for (int w : new int[]{-4, 4}) for (int h = -4; h <= 0; h++) {
                set(p[0], w, h, ladder.setValue(LadderBlock.WATERLOGGED, h <= -3));
            }
        }

        /** Hall interior of the station: wide up to the fork, then two lanes with a pillar between them, then only the way out. */
        private int[] stationInterior(int i) {
            if (i <= FORK_I) return new int[]{-5, 5};
            if (i <= RIGHT_END + 1) return new int[]{-5, -2, 2, 5};
            return new int[]{-5, -2};
        }

        private void buildStation() {
            hall(STATION_START, STATION_END, 7, 6, this::stationInterior, Blocks.POLISHED_DEEPSLATE.defaultBlockState(), Blocks.DEEPSLATE_TILES.defaultBlockState());
            for (int w = -7; w <= 7; w++) for (int h = 0; h <= 7; h++) set(STATION_END + 1, w, h, Blocks.STONE_BRICKS.defaultBlockState());
            // light: yellow on the way out, red on the dead end (the old lab's rule: yellow helps, red misleads)
            for (int i = STATION_START + 1; i <= STATION_END; i += 4) {
                BlockState left = i > FORK_I ? Blocks.OCHRE_FROGLIGHT.defaultBlockState() : Blocks.SEA_LANTERN.defaultBlockState();
                set(i, i > FORK_I ? -3 : -3, 7, left);
                set(i, 3, 7, i > FORK_I ? Blocks.REDSTONE_LAMP.defaultBlockState().setValue(BlockStateProperties.LIT, true) : Blocks.SEA_LANTERN.defaultBlockState());
            }
            // the dead end is closed by a wall
            for (int w = 2; w <= 5; w++) for (int h = 1; h <= 6; h++) set(RIGHT_END + 1, w, h, Blocks.STONE_BRICKS.defaultBlockState());
            // a pillar between the lanes
            for (int i = FORK_I + 1; i <= RIGHT_END + 1; i++) for (int w = -1; w <= 1; w++) for (int h = 1; h <= 6; h++) set(i, w, h, Blocks.IRON_BLOCK.defaultBlockState());
            for (int i = RIGHT_END + 2; i <= STATION_END; i++) for (int w = -1; w <= 5; w++) for (int h = 1; h <= 6; h++) set(i, w, h, Blocks.STONE_BRICKS.defaultBlockState());
        }

        // ------------------------------------------------------------ rails

        public static RailShape shapeFor(Direction a, Direction b) {
            boolean n = a == Direction.NORTH || b == Direction.NORTH, s = a == Direction.SOUTH || b == Direction.SOUTH;
            boolean e = a == Direction.EAST || b == Direction.EAST, w = a == Direction.WEST || b == Direction.WEST;
            if (n && s) return RailShape.NORTH_SOUTH;
            if (e && w) return RailShape.EAST_WEST;
            if (s && e) return RailShape.SOUTH_EAST;
            if (s && w) return RailShape.SOUTH_WEST;
            if (n && w) return RailShape.NORTH_WEST;
            return RailShape.NORTH_EAST;
        }

        private static Direction[] ends(RailShape shape) {
            return switch (shape) {
                case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> new Direction[]{Direction.NORTH, Direction.SOUTH};
                case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> new Direction[]{Direction.EAST, Direction.WEST};
                case SOUTH_EAST -> new Direction[]{Direction.SOUTH, Direction.EAST};
                case SOUTH_WEST -> new Direction[]{Direction.SOUTH, Direction.WEST};
                case NORTH_WEST -> new Direction[]{Direction.NORTH, Direction.WEST};
                case NORTH_EAST -> new Direction[]{Direction.NORTH, Direction.EAST};
            };
        }

        private record RailAt(BlockPos pos, BlockState state) {}

        private final java.util.List<RailAt> rails = new java.util.ArrayList<>();

        private void rail(int i, int w, RailShape shape, int power) {      // power: 0 plain rail, 1 powered (boost), 2 unpowered powered rail (brake)
            BlockPos p = at(i, w, 1);
            BlockState s = power == 0 ? Blocks.RAIL.defaultBlockState().setValue(BlockStateProperties.RAIL_SHAPE, shape)
                    : Blocks.POWERED_RAIL.defaultBlockState().setValue(BlockStateProperties.RAIL_SHAPE_STRAIGHT, shape).setValue(BlockStateProperties.POWERED, power == 1);
            if (power == 1) set(i, w, 0, Blocks.REDSTONE_BLOCK.defaultBlockState());          // the block under a powered rail powers it
            level.setBlock(p, s, 2);
            rails.add(new RailAt(p, s));
        }

        private void buildRails() {
            RailShape along = shapeFor(dir, dir.getOpposite()), across = shapeFor(right, right.getOpposite());
            for (int i = CART_I; i <= MAIN_END; i++) rail(i, 0, along, i > CART_I && (i - CART_I) % 3 == 1 ? 1 : 0);
            // left branch (the way out): across, across, a curve forward, then straight to the end with brake rails
            rail(FORK_I, -1, across, 0);
            rail(FORK_I, -2, across, 0);
            rail(FORK_I, -3, shapeFor(right, dir), 0);
            for (int i = FORK_I + 1; i <= LEFT_END; i++) rail(i, -3, along, i >= LEFT_END - 3 ? 2 : (i - FORK_I) % 3 == 0 ? 1 : 0);
            // right branch (the dead end): the same, shorter, ending at the wall
            rail(FORK_I, 1, across, 0);
            rail(FORK_I, 2, across, 0);
            rail(FORK_I, 3, shapeFor(right.getOpposite(), dir), 0);
            for (int i = FORK_I + 1; i <= RIGHT_END; i++) rail(i, 3, along, i >= RIGHT_END - 3 ? 2 : (i - FORK_I) % 3 == 0 ? 1 : 0);
            setFork(false);                                                  // default: the dead end, so the rider has to choose
            // placing a rail makes it re-aim at its neighbours: say again what each one is meant to be
            for (RailAt r : rails) level.setBlock(r.pos, r.state, 2);
            setFork(false);
        }

        /** Aims the fork rail: left = the way out, right = the dead end. */
        public void setFork(boolean left) {
            RailShape shape = shapeFor(dir.getOpposite(), left ? dir.getCounterClockWise() : dir.getClockWise());
            level.setBlock(forkPos(), Blocks.RAIL.defaultBlockState().setValue(BlockStateProperties.RAIL_SHAPE, shape), 2);
        }

        /** Walks the track from the cart's seat with the fork aimed as given; returns the last rail cell reached (where the cart ends up). */
        public BlockPos railEnd(boolean left) {
            setFork(left);
            BlockPos p = cartPos();
            Direction travel = dir;
            for (int guard = 0; guard < 400; guard++) {
                BlockState s = level.getBlockState(p);
                if (!(s.getBlock() instanceof BaseRailBlock rail)) return p;
                Direction[] e = ends(s.getValue(rail.getShapeProperty()));
                Direction back = travel.getOpposite();
                Direction out = e[0] == back ? e[1] : e[1] == back ? e[0] : null;
                if (out == null) return p;                                   // the cart cannot enter this rail from this side
                BlockPos next = p.relative(out);
                if (!(level.getBlockState(next).getBlock() instanceof BaseRailBlock)) return p;
                travel = out;
                p = next;
            }
            return p;
        }

        // ------------------------------------------------------------ props

        private void tank(int i, int w) {
            for (int di = 0; di < 3; di++) for (int dw = 0; dw < 3; dw++) for (int h = 1; h <= 4; h++) {
                boolean shell = di == 0 || di == 2 || dw == 0 || dw == 2 || h == 4;
                set(i + di, w + dw, h, shell ? Blocks.GLASS.defaultBlockState() : (h <= 3 ? Blocks.WATER.defaultBlockState() : air()));
            }
            set(i + 1, w + 1, 1, Blocks.ZOMBIE_HEAD.defaultBlockState());      // a stand-in for a subject: a proper model comes later
        }

        private void lectern(int i, int w, Direction facing, String title, String text) {
            BlockPos p = at(i, w, 1);
            level.setBlock(p, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, facing).setValue(LecternBlock.HAS_BOOK, true), 2);
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

        private void buildProps() {
            tank(3, -5);
            tank(3, 3);
            lectern(8, -3, dir.getOpposite(), "Запись 1", "[ЗАГЛУШКА] Здесь будет запись из лаборатории. Текст пишет автор.");
            lectern(8, 3, dir.getOpposite(), "Запись 2", "[ЗАГЛУШКА] Вторая запись. Текст пишет автор.");
            // a table to read at, between the lecterns
            for (int w = -1; w <= 1; w++) set(8, w, 1, Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState());
        }
    }

    // ================================================================== the run (chaser, cart, fork)

    private Layout layout;               // the straight test line with the cart, or null
    private Course course;               // whatever is built right now (the line, or the big facility)
    private UUID cartId;
    private long kickAtTick = -1;
    private UUID runner;
    private long runStart;
    private Vec3 chaser;
    private final ArrayDeque<Vec3> trail = new ArrayDeque<>();
    private Vec3 lastTrail;
    private static final DustParticleOptions DARK = new DustParticleOptions(new Vector3f(0.42f, 0.02f, 0.10f), 1.7f);
    private static final DustParticleOptions BLACK = new DustParticleOptions(new Vector3f(0.05f, 0.0f, 0.03f), 2.2f);
    private static final double CATCH_DISTANCE = 2.0, BASE_SPEED = 0.19, SPEED_GAIN_PER_TICK = 0.00008;

    public String build(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return "Только в мире.";
        stopRun(false);
        BlockPos floor = player.blockPosition().below().relative(player.getDirection(), 3);
        layout = new Layout(level, floor, player.getDirection());
        layout.build();
        course = layout;
        Layout l = layout;
        return String.format("Прототип построен: вход у %s, направление %s, длина около %d блоков. Дальше: /lotus labproto start.",
                l.at(0, 0, 1).toShortString(), l.dir, Layout.STATION_END);
    }

    /** Builds the big facility (see {@link LabFacility}) and makes it the course; returns the report with a map. */
    public String buildBig(ServerPlayer player, long seed) {
        if (!(player.level() instanceof ServerLevel level)) return "Только в мире.";
        stopRun(false);
        BlockPos floor = player.blockPosition().below().relative(player.getDirection(), 3);
        long t0 = System.currentTimeMillis();
        LabFacility fac = new LabFacility(level, floor, player.getDirection(), seed);
        fac.build();
        layout = null;                                                   // the cart belongs to the straight line only
        course = fac;
        return String.format("Лаборатория построена за %.1f с: вход у %s, комнат %d, коридоров %d, размер %dx%d. Дальше: /lotus labproto start.%n%s",
                (System.currentTimeMillis() - t0) / 1000.0, fac.at(0, 0, 1).toShortString(), fac.roomCount(), fac.connectorCount(), fac.sizeI(), fac.sizeW(), fac.asciiMap());
    }

    public String start(ServerPlayer player) {
        if (course == null) return "Сначала /lotus labproto build (линия) или bigbuild (лаборатория).";
        stopRun(false);
        ServerLevel level = course.level();
        if (layout != null) {                                              // the straight line has a cart at its station
            Entity old = cartId == null ? null : level.getEntity(cartId);
            if (old != null) old.discard();
            BlockPos c = layout.cartPos();
            Minecart cart = new Minecart(level, c.getX() + 0.5, c.getY() + 0.0625, c.getZ() + 0.5);
            level.addFreshEntity(cart);
            cartId = cart.getUUID();
            layout.setFork(false);
        }
        Vec3 s = course.startPos();
        player.teleportTo(level, s.x, s.y, s.z, course.dir().toYRot(), 0f);
        runner = player.getUUID();
        runStart = level.getGameTime();
        trail.clear();
        trail.addLast(s);
        lastTrail = s;
        Vec3 back = new Vec3(course.dir().getStepX(), 0, course.dir().getStepZ());
        chaser = s.subtract(back.scale(12));                  // it comes in through the doorway behind the runner
        player.displayClientMessage(Component.literal("— Оно уже идёт за тобой. Беги вперёд."), false);
        return "Побег начат. Ищи жёлтый свет и выход (светлый зал с зелёным полом). Остановить: /lotus labproto stop.";
    }

    public String map() {
        return course instanceof LabFacility fac ? fac.asciiMap() : "Карта есть только у большой лаборатории (bigbuild).";
    }

    public String stop() {
        boolean was = runner != null;
        stopRun(true);
        return was ? "Остановлено." : "Ничего не запущено.";
    }

    public String status() {
        if (course == null) return "Ничего не построено.";
        return (course instanceof LabFacility ? "большая лаборатория" : "прямая линия") + ", " + course.dir() + ", бег: " + (runner != null);
    }

    private void stopRun(boolean message) {
        runner = null;
        chaser = null;
        trail.clear();
    }

    @SubscribeEvent
    public void onMount(EntityMountEvent event) {
        if (layout == null || cartId == null || !event.isMounting()) return;
        if (event.getEntityBeingMounted() != null && cartId.equals(event.getEntityBeingMounted().getUUID()) && event.getEntityMounting() instanceof ServerPlayer p) {
            kickAtTick = p.level().getGameTime() + 3;               // the cart is given its first push once the rider is really in
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || course == null) return;
        MinecraftServer server = event.getServer();
        ServerLevel level = course.level();
        long now = level.getGameTime();
        Entity cartEntity = layout == null || cartId == null ? null : level.getEntity(cartId);
        if (cartEntity instanceof Minecart cart) tickCart(cart, now);
        if (runner != null) tickRun(server, level, now);
    }

    private void tickCart(Minecart cart, long now) {
        if (kickAtTick >= 0 && now >= kickAtTick) {
            kickAtTick = -1;
            if (!cart.getPassengers().isEmpty()) cart.setDeltaMovement(layout.dir.getStepX() * 0.45, 0, layout.dir.getStepZ() * 0.45);
        }
        if (!(cart.getFirstPassenger() instanceof ServerPlayer rider)) return;
        Layout l = layout;
        BlockPos fork = l.forkPos();
        double along = (cart.getX() - (fork.getX() + 0.5)) * l.dir.getStepX() + (cart.getZ() - (fork.getZ() + 0.5)) * l.dir.getStepZ();
        if (along < -16 || along > 1) return;
        if (along > -12 && now % 10 == 0) rider.displayClientMessage(Component.literal("Налево (A или взгляд влево): жёлтый свет.   Направо (D): красный."), true);
        // the choice: the strafe keys if they are held, else where the rider is looking relative to the way the cart runs
        boolean left;
        if (Math.abs(rider.xxa) > 0.05f) left = rider.xxa > 0;
        else {
            float delta = net.minecraft.util.Mth.wrapDegrees(rider.getYRot() - l.dir.toYRot());
            if (Math.abs(delta) < 20) return;                      // looking straight: no choice yet, the last one stays
            left = delta < 0;                                      // in Minecraft yaw grows clockwise, so a negative change is a turn to the left
        }
        BlockState now0 = l.level.getBlockState(fork);
        RailShape want = Layout.shapeFor(l.dir.getOpposite(), left ? l.dir.getCounterClockWise() : l.dir.getClockWise());
        if (now0.getBlock() instanceof BaseRailBlock rail && now0.getValue(rail.getShapeProperty()) != want) l.setFork(left);
    }

    private void tickRun(MinecraftServer server, ServerLevel level, long now) {
        ServerPlayer player = server.getPlayerList().getPlayer(runner);
        if (player == null || !player.isAlive() || player.level() != level) {
            stopRun(false);
            return;
        }
        Vec3 pos = player.position();
        if (course.exitBox().contains(pos)) {
            player.displayClientMessage(Component.literal(String.format("— Выход найден за %.1f с. Прототип пройден.", (now - runStart) / 20.0)), false);
            stopRun(false);
            return;
        }
        if (lastTrail == null || pos.distanceTo(lastTrail) >= 0.6) {
            trail.addLast(pos);
            lastTrail = pos;
        }
        double speed = BASE_SPEED + (now - runStart) * SPEED_GAIN_PER_TICK;
        double budget = speed;
        while (budget > 0 && !trail.isEmpty()) {
            Vec3 t = trail.peekFirst();
            Vec3 d = t.subtract(chaser);
            double len = d.length();
            if (len <= budget) {
                chaser = t;
                budget -= len;
                trail.pollFirst();
            } else {
                chaser = chaser.add(d.scale(budget / len));
                budget = 0;
            }
        }
        double dist = chaser.distanceTo(pos);
        if (dist < CATCH_DISTANCE) {
            Vec3 s = course.startPos();
            player.teleportTo(level, s.x, s.y, s.z, course.dir().toYRot(), 0f);
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
            player.displayClientMessage(Component.literal("— Оно достало тебя. (В прототипе это без смерти: назад в начало.)"), false);
            stopRun(false);
            return;
        }
        if (now % 2 == 0) drawChaser(level, pos, now, dist);
        if (now % 20 == 0 && dist < 24) {
            float vol = (float) Math.max(0.15, 1.0 - dist / 24.0);
            level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, vol, 0.8f + vol * 0.3f);
        }
    }

    /** The Lotus: a dark mass with tentacles that wave and, when it is close, reach for the runner. Particles only: no model yet. */
    private void drawChaser(ServerLevel level, Vec3 player, long tick, double dist) {
        Vec3 core = chaser.add(0, 0.9, 0);
        level.sendParticles(BLACK, core.x, core.y, core.z, 10, 0.5, 0.6, 0.5, 0);
        level.sendParticles(DARK, core.x, core.y, core.z, 12, 0.7, 0.8, 0.7, 0);
        level.sendParticles(ParticleTypes.SQUID_INK, core.x, core.y, core.z, 2, 0.4, 0.4, 0.4, 0.01);
        Vec3 toPlayer = player.add(0, 0.9, 0).subtract(core);
        double reach = dist < 14 ? 1.0 - dist / 14.0 : 0.0;          // how far the front tentacles stretch toward the runner
        for (int k = 0; k < 5; k++) {
            double ang = k * (Math.PI * 2 / 5) + tick * 0.04;
            Vec3 outward = new Vec3(Math.cos(ang), 0.25 * Math.sin(tick * 0.1 + k), Math.sin(ang));
            Vec3 aim = k < 2 && reach > 0 ? toPlayer.normalize().scale(reach).add(outward.scale(1.0 - reach)) : outward;
            for (int s = 1; s <= 9; s++) {
                double t = s * 0.55 * (k < 2 ? 1.0 + reach * 1.8 : 1.0);
                double wave = Math.sin(tick * 0.25 + s * 0.7 + k) * 0.35 * (s / 9.0 + 0.3);
                Vec3 p = core.add(aim.scale(t)).add(-aim.z * wave, Math.abs(wave) * 0.4, aim.x * wave);
                level.sendParticles(DARK, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0);
            }
        }
    }
}
