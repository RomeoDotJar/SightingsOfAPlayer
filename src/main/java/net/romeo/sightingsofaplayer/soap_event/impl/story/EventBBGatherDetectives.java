package net.romeo.sightingsofaplayer.soap_event.impl.story;

import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.romeo.sightingsofaplayer.dimension.SOAPBBRoomSession;
import net.romeo.sightingsofaplayer.myth.MythRoster;
import net.romeo.sightingsofaplayer.saveddata.SOAPEventSavedData;
import net.romeo.sightingsofaplayer.saveddata.SOAPMythSavedData;
import net.romeo.sightingsofaplayer.saveddata.SOAPPlayerSavedData;
import net.romeo.sightingsofaplayer.saveddata.SOAPServerTimeData;
import net.romeo.sightingsofaplayer.soap_event.SoapEvent;
import net.romeo.sightingsofaplayer.soap_event.SoapEventContext;
import net.romeo.sightingsofaplayer.soap_event.SoapEventPool;
import net.romeo.sightingsofaplayer.soap_event.SoapEventSystem;

/**
 * Story event: the moment the whole party is wearing the Amulet, the Amulet answers and everyone is
 * taken into the detectives' room for a minute.
 * <p>
 * The condition is the exact one that gives the event its name, and it is checked against the
 * <i>live</i> roster rather than once at the start: every player online at that moment must already
 * have used their Amulet. A newcomer who has not used theirs yet keeps the event away until they do,
 * so the room is only ever opened for a party that is entirely made of detectives. Nobody online at
 * all is <b>not</b> a party - an empty roster would otherwise satisfy "everyone has used one"
 * vacuously and open the room for nobody.
 * <p>
 * It happens once per world. This is a story beat, the first time the Amulet speaks, not a routine -
 * should it be wanted as a recurring gathering instead, dropping the {@link SOAPEventSavedData}
 * run-count check in {@link #canRun} is the whole change.
 * <p>
 * What actually happens to the detectives - where they land, how long they are held, and how they
 * find their way back - is {@link SOAPBBRoomSession}'s business; this class only decides <i>when</i>.
 */
public class EventBBGatherDetectives extends SoapEvent {

    public static final String ID = "bb_gather_detectives";

    /** The world has to have seen a whole day before the Amulet is allowed to answer. */
    private static final long MIN_WORLD_DAYS = 1L;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Categories category() {
        return Categories.Story;
    }

    @Override
    public int tensionGain() {
        return -100;
    }

    /**
     * A one-off with a narrow window, so it is chosen the moment it can be: the condition is
     * satisfied by the players' own actions and can slip away again the next time somebody joins.
     */
    @Override
    public float selectionWeight() {
        return 16.0F;
    }

    @Override
    public float nextDelayMultiplier() {
        return 2.0F;
    }

    @Override
    public long minDelaySeconds() {
        return 30L;
    }

    @Override
    public boolean canRun(SoapEventContext context) {
        List<ServerPlayer> online = context.players();
        if (online.isEmpty() || SOAPBBRoomSession.isActive()) {
            return false;
        }

        SOAPServerTimeData timeData = SOAPServerTimeData.get(context.server());
        if (timeData.getTotalDays() < MIN_WORLD_DAYS) {
            return false;
        }

        SOAPMythSavedData mythData = SOAPMythSavedData.get(context.server());

        // No myths should still be active by the time of the gathering
        if (!mythData.getMythsInRoster(MythRoster.ACTIVE).isEmpty()) {
            return false;
        }

        // "All the currently online players have put on the Amulet" - and a player who joins later
        // without one takes that away again until they use theirs.
        SOAPPlayerSavedData playerData = SOAPPlayerSavedData.get(context.server());
        for (ServerPlayer player : online) {
            if (!playerData.hasUsedAmulet(player.getUUID())) {
                return false;
            }
        }

        return super.canRun(context);
    }

    @Override
    public boolean run(SoapEventContext context) {
        // A copy, because the session holds on to the party for a minute while the server keeps
        // changing the live roster underneath it.
        boolean happened = SOAPBBRoomSession.gather(context.server(), List.copyOf(context.players()));

        if (happened) {
            SoapEventSystem.triggerNow(context.server(), SoapEventPool.byId("activate_random_myth"));
        }

        return happened;
    }
}
