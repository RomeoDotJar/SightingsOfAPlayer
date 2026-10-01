package net.romeo.sightingsofaplayer.dimension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.romeo.sightingsofaplayer.SOAP;
import net.romeo.sightingsofaplayer.event.SOAPServerTimeEvents;
import net.romeo.sightingsofaplayer.soap_event.SoapEventPool;
import net.romeo.sightingsofaplayer.soap_event.impl.story.EventBBGatherDetectives;

/**
 * One gathering in {@link SOAPBBRoom}: everybody is taken in, held for a while, and put back exactly
 * where they came from.
 * <p>
 * The whole session is one unbroken stretch of server time. {@link #gather} writes down every
 * detective's whereabouts, moves them into the room and hands a return to
 * {@link SOAPServerTimeEvents#queueWork(int, Runnable)}, the same clock the event system runs on, so
 * the detectives are let go after exactly {@link #STAY_TICKS} ticks - {@value #STAY_TICKS} ticks is
 * one minute - and not a moment later.
 * <p>
 * Two things can go wrong with a party that is standing inside a sealed bedrock box a dimension
 * away from home, and both are handled here rather than left to chance:
 * <ul>
 *     <li><b>Disconnecting mid-session.</b> A detective who logs out inside the room would log back
 *     in inside the room, with no way out. {@link #rescueIfInRoom} catches them the moment they
 *     re-enter the world and finishes their return for them, using the whereabouts already on
 *     record.</li>
 *     <li><b>A server that dies mid-session.</b> A crash takes this bookkeeping with it, since none
 *     of it is written to disk. A player who logs back in to find themselves inside the room with
 *     no spot on record is still sent home - to the world spawn, the one place that is never the
 *     room.</li>
 * </ul>
 * A <i>clean</i> shutdown needs none of that: {@code SOAPServerTimeEvents} runs whatever is still
 * queued while the server stops, so a normal restart lets everyone go before the world is saved.
 * <p>
 * All of it is touched from the server thread only - the event that starts the session, the queued
 * return, and the player events that rescue somebody - so the map and the flag below need no
 * synchronisation.
 */
public final class SOAPBBRoomSession {

    /** How long the detectives are kept in the room: 60 seconds, exactly as specified. */
    public static final int STAY_TICKS = 20 * 60;

    /**
     * Where each detective has to be put back, keyed by player. An entry lives from the moment its
     * detective is taken in until the moment they are put back, which may be long after the
     * session ended if they logged out inside the room.
     */
    private static final Map<UUID, ReturnPoint> RETURN_POINTS = new HashMap<>();

    /** Whether a gathering is under way right now. */
    private static boolean active;

    /**
     * Whether a gathering is under way right now. Used by the event's own condition so a second
     * gathering can never be started on top of one that is still holding everybody.
     */
    public static boolean isActive() {
        return active;
    }

    /** One detective's whereabouts at the moment they were taken in. */
    private record ReturnPoint(ResourceKey<Level> dimension, double x, double y, double z, float yRot,
            float xRot) {
    }

    /**
     * Takes the given detectives into the room, one minute after which they are put back where they
     * came from.
     *
     * @return {@code false} when there was nothing to do or nowhere to put them - no detectives, a
     *         gathering already under way, or (with a datapack that removed it) no room dimension.
     *         A {@code false} here lets the event system quietly pick something else instead.
     */
    public static boolean gather(MinecraftServer server, List<ServerPlayer> detectives) {
        if (active || detectives.isEmpty()) {
            return false;
        }

        ServerLevel room = SOAPBBRoom.level(server);
        if (room == null) {
            SOAP.LOGGER.warn("Room dimension {} is missing; the gathering cannot happen.",
                    SOAPBBRoom.DIMENSION.location());
            return false;
        }

        // The room is rebuilt first: the detectives are about to be placed on its floor, so it has
        // to be there before anybody arrives.
        SOAPBBRoom.build(room);

        RETURN_POINTS.clear();

        int index = 0;
        for (ServerPlayer detective : detectives) {
            RETURN_POINTS.put(detective.getUUID(), whereaboutsOf(detective));

            Vec3 spot = SOAPBBRoom.standPosition(index++);
            place(detective, room, spot.x, spot.y, spot.z, SOAPBBRoom.facingYaw(spot), 0.0F);

            room.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ENDERMAN_TELEPORT,
                    SoundSource.PLAYERS, 1.0F, 1.0F);
            detective.displayClientMessage(
                    Component.translatable(SoapEventPool.byId("bb_gather_detectives").nameKey()+".arrive"), false);
        }

        active = true;
        SOAPServerTimeEvents.queueWork(STAY_TICKS, () -> release(server));

        return true;
    }

    /**
     * Ends the gathering and puts everybody who is still in the room back where they came from.
     * <p>
     * Only players who are online right now are returned; a detective who disconnected inside the
     * room keeps their entry in {@link #RETURN_POINTS} and is returned by {@link #rescueIfInRoom}
     * the next time they are seen. Calling this again after the session has ended does nothing,
     * because there is nothing left to return.
     */
    public static void release(MinecraftServer server) {
        active = false;

        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            sendHome(server, player);
        }
    }

    /**
     * Gets a detective out of the room when the room has no business keeping them: they logged out
     * mid-gathering, or the server restarted while they were inside.
     * <p>
     * Meant to be called whenever a player (re)enters the world, so the very first thing that
     * happens to somebody waking up inside the room is that they stop being inside the room. A
     * player anywhere else is left completely alone, which is why this costs a single dimension
     * comparison for everyone else.
     */
    public static void rescueIfInRoom(ServerPlayer player) {
        if (!player.level().dimension().equals(SOAPBBRoom.DIMENSION)) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        // A spot on record means the gathering is still holding their place: finish the return.
        sendHome(server, player);

        if (!player.level().dimension().equals(SOAPBBRoom.DIMENSION)) {
            return;     // sendHome already did the job
        }

        // No spot on record - the gathering was lost to a restart, or the player never was part of
        // one. The world spawn is the only place left that is definitely not the room.
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        place(player, overworld, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
        player.displayClientMessage(
                Component.translatable(SoapEventPool.byId("bb_gather_detectives").nameKey()+".return"), false);
    }

    /**
     * Returns one player to their recorded whereabouts, if they have any. Does nothing otherwise,
     * and does nothing for a player already standing where they belong.
     */
    private static void sendHome(MinecraftServer server, ServerPlayer player) {
        ReturnPoint point = RETURN_POINTS.remove(player.getUUID());
        if (point == null) {
            return;
        }

        // The dimension the detective came from can be gone if a datapack was taken away between
        // the two moments. The overworld is the one level that is always there to fall back on.
        ServerLevel target = server.getLevel(point.dimension());
        if (target == null) {
            target = server.overworld();
        }

        place(player, target, point.x(), point.y(), point.z(), point.yRot(), point.xRot());

        target.playSound(null, point.x(), point.y(), point.z(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        player.displayClientMessage(
                Component.translatable(SoapEventPool.byId("bb_gather_detectives").nameKey()+".return"), false);
    }

    /** Where a detective is right now, as something that can be replayed later. */
    private static ReturnPoint whereaboutsOf(ServerPlayer player) {
        ServerLevel origin = player.serverLevel();

        return new ReturnPoint(origin.dimension(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
    }

    /**
     * Moves a detective to an exact place, and takes care of the two leftovers a plain teleport can
     * leave on the body: momentum from before the move, and a fall distance that would otherwise be
     * charged as damage on arrival.
     */
    private static void place(ServerPlayer player, ServerLevel level, double x, double y, double z,
            float yRot, float xRot) {
        player.teleportTo(level, x, y, z, yRot, xRot);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
    }

    private SOAPBBRoomSession() {
    }
}
