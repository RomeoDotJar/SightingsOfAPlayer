package net.romeo.sightingsofaplayer.dimension;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.romeo.sightingsofaplayer.SOAP;

/**
 * The detectives' room: the single room of the mod's one custom dimension, the place a Story
 * {@code soap_event} can pull everybody into.
 * <p>
 * The dimension on its own is nothing but emptiness. Its datapack files -
 * {@code data/sightingsofaplayer/dimension/soap_room.json} and the {@code ..._type} beside them -
 * describe a flat generator that places nothing but air, so the room does not exist until it is
 * asked for. {@link #build} is what turns that emptiness into the room: it lays the bedrock shell
 * down and clears the inside, which makes the room exactly what it is meant to be every single
 * time, no matter what happened in it before. Breaking through bedrock in creative, a stray
 * explosion, a half-finished build after a crash - none of it survives the next {@link #build}.
 * <p>
 * The room is a cube of {@value #INTERIOR_SIZE} blocks per edge, walled in by bedrock
 * {@value #WALL_THICKNESS} block thick on all six sides. It sits in the very corner of the world
 * ({@code x} and {@code z} from {@value #SHELL_MIN_X} to {@value #SHELL_MAX_Z}) so that the whole
 * shell fits inside the one chunk that the players themselves keep loaded while they are in it.
 * <p>
 * Everything here is static and stateless: where the room is, and how to build it. Who is inside it
 * and how they got there is {@link SOAPBBRoomSession}'s business.
 */
public final class SOAPBBRoom {

    /**
     * Registry key of the room dimension, matching
     * {@code data/sightingsofaplayer/dimension/soap_room.json}. Handing this to
     * {@link MinecraftServer#getLevel(ResourceKey)} yields the level, because a dimension described
     * in a datapack is created with all the others at server start - it does not have to be visited
     * before it exists.
     */
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(SOAP.MODID, "soap_room"));

    /** Edge length of the room's hollow inside, in blocks. */
    public static final int INTERIOR_SIZE = 9;

    /** Thickness of the bedrock shell that surrounds the inside. */
    private static final int WALL_THICKNESS = 2;

    /** Lowest, north-western inside block: the floor the detectives stand on. */
    private static final int INTERIOR_MIN_X = 1;
    private static final int INTERIOR_MIN_Y = 32;
    private static final int INTERIOR_MIN_Z = 1;

    private static final int INTERIOR_MAX_X = INTERIOR_MIN_X + INTERIOR_SIZE - 1;
    private static final int INTERIOR_MAX_Y = INTERIOR_MIN_Y + INTERIOR_SIZE - 1;
    private static final int INTERIOR_MAX_Z = INTERIOR_MIN_Z + INTERIOR_SIZE - 1;

    /** The bedrock shell: one block further out than the inside on every side. */
    private static final int SHELL_MIN_X = INTERIOR_MIN_X - WALL_THICKNESS;
    private static final int SHELL_MIN_Y = INTERIOR_MIN_Y - WALL_THICKNESS;
    private static final int SHELL_MIN_Z = INTERIOR_MIN_Z - WALL_THICKNESS;
    private static final int SHELL_MAX_X = INTERIOR_MAX_X + WALL_THICKNESS;
    private static final int SHELL_MAX_Y = INTERIOR_MAX_Y + WALL_THICKNESS;
    private static final int SHELL_MAX_Z = INTERIOR_MAX_Z + WALL_THICKNESS;

    /**
     * How many standable spots the floor has. Further detectives reuse the first spots rather than
     * being left outside, so the room never refuses anybody.
     */
    public static final int SPOTS = INTERIOR_SIZE * INTERIOR_SIZE;

    /** The loaded room dimension, or {@code null} when this server has no such dimension. */
    @Nullable
    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(DIMENSION);
    }

    /**
     * Puts the room back the way it should be: bedrock all around, air inside.
     * <p>
     * Every block of the shell is considered, so this both repairs damage and wipes anything that
     * was left behind. Blocks that already look right are skipped, which keeps the common case - a
     * room that was already intact - down to a few hundred cheap reads.
     * <p>
     * The first write into an unloaded chunk loads that chunk, and every block of the room shares
     * one chunk, so by the time this returns the floor is guaranteed to exist and to be in memory.
     */
    public static void build(ServerLevel room) {
        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        for (int y = SHELL_MIN_Y; y <= SHELL_MAX_Y; y++) {
            for (int x = SHELL_MIN_X; x <= SHELL_MAX_X; x++) {
                for (int z = SHELL_MIN_Z; z <= SHELL_MAX_Z; z++) {
                    boolean wall = x == SHELL_MIN_X || x == SHELL_MAX_X
                            || y == SHELL_MIN_Y || y == SHELL_MAX_Y
                            || z == SHELL_MIN_Z || z == SHELL_MAX_Z;

                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState wanted = wall ? bedrock : air;

                    if (!room.getBlockState(pos).equals(wanted)) {
                        room.setBlockAndUpdate(pos, wanted);
                    }
                }
            }
        }
    }

    /**
     * The spot the {@code index}-th detective stands on, spread over the floor from the
     * north-western corner. The {@code index} wraps around after {@link #SPOTS}, so the room keeps
     * accepting detectives however many turn up.
     */
    public static Vec3 standPosition(int index) {
        int slot = Math.floorMod(index, SPOTS);
        int dx = slot % INTERIOR_SIZE;
        int dz = slot / INTERIOR_SIZE;

        return new Vec3(
                INTERIOR_MIN_X + dx + 0.5D,
                INTERIOR_MIN_Y,
                INTERIOR_MIN_Z + dz + 0.5D);
    }

    /**
     * The yaw that makes a detective standing at {@code spot} look at the middle of the room, so
     * everyone faces inward on arrival instead of staring at a wall.
     * <p>
     * Minecraft yaw is 0 looking towards +Z and grows clockwise, so a direction of {@code (dx, dz)}
     * is yaw {@code atan2(-dx, dz)}.
     */
    public static float facingYaw(Vec3 spot) {
        double centreX = INTERIOR_MIN_X + INTERIOR_SIZE / 2.0D;
        double centreZ = INTERIOR_MIN_Z + INTERIOR_SIZE / 2.0D;

        double dx = centreX - spot.x;
        double dz = centreZ - spot.z;

        if (dx == 0.0D && dz == 0.0D) {
            return 0.0F;    // already standing in the middle: any direction will do
        }

        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private SOAPBBRoom() {
    }
}
