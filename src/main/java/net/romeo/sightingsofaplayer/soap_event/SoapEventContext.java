package net.romeo.sightingsofaplayer.soap_event;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.romeo.sightingsofaplayer.saveddata.SOAPServerTimeData;
import net.romeo.sightingsofaplayer.saveddata.SOAPEventSavedData;

/**
 * Everything an event needs to decide and to act: who is playing, where they are, what time it is
 * and how old the world is.
 * <p>
 * Handed to {@link SoapEvent#canRun} and {@link SoapEvent#run}, so an event class never has to
 * reach for a server or a level itself and stays easy to read and to test. Built once per
 * scheduling decision, never per tick.
 * <p>
 * The world age comes from {@link SOAPServerTimeData}, the same counter the rest of the mod uses,
 * which is what ties the event system to the server's real age rather than to the daylight cycle.
 */
public final class SoapEventContext {

    /**
     * Below this height a player counts as being underground.
     * <p>
     * Sea level is 63 and the default surface sits just above it, so anything below 60 is under
     * ground almost everywhere - in mountains it is far deeper still. Kept simple on purpose: a
     * "true cave" test would have to guess at the player's intent, and this is an example rule.
     */
    public static final int UNDERGROUND_Y = 60;

    private final MinecraftServer server;
    private final ServerLevel overworld;
    private final RandomSource random;
    private final long totalTicks;
    private final int tension;

    public SoapEventContext(MinecraftServer server, RandomSource random) {
        this.server = server;
        this.overworld = server.overworld();
        this.random = random;
        this.totalTicks = SOAPServerTimeData.get(server).getTotalTicks();
        this.tension = SOAPEventSavedData.get(server).getTension();
    }

    public MinecraftServer server() {
        return server;
    }

    public ServerLevel overworld() {
        return overworld;
    }

    public RandomSource random() {
        return random;
    }

    // Current tension on the server
    public int tension() {
        return tension;
    }

    /** Ticks this world has run in total, across every session it has ever had. */
    public long totalTicks() {
        return totalTicks;
    }

    /** Whole days this world has been running for. */
    public long totalDays() {
        return totalTicks / SOAPServerTimeData.TICKS_PER_DAY;
    }

    /** Whether it is currently night in the overworld, by the daylight cycle. */
    public boolean isNight() {
        return !overworld.isDay();
    }

    /** Everyone currently online, in every dimension. */
    public List<ServerPlayer> players() {
        return server.getPlayerList().getPlayers();
    }

    /** Everyone currently below {@link #UNDERGROUND_Y}. */
    public List<ServerPlayer> playersUnderground() {
        return players().stream().filter(SoapEventContext::isUnderground).toList();
    }

    /** A random player, or {@code null} when nobody is online. */
    @Nullable
    public ServerPlayer randomPlayer() {
        List<ServerPlayer> online = players();
        return online.isEmpty() ? null : online.get(random.nextInt(online.size()));
    }

    /** Whether this player is below {@link #UNDERGROUND_Y}. */
    public static boolean isUnderground(Player player) {
        return player.getY() < UNDERGROUND_Y;
    }
}
