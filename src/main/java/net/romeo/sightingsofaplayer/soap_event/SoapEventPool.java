package net.romeo.sightingsofaplayer.soap_event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.romeo.sightingsofaplayer.soap_event.impl.EventDebugScreenDistortion;
import net.romeo.sightingsofaplayer.soap_event.impl.entity303.EventEntity303ChangeDayNight;
import net.romeo.sightingsofaplayer.soap_event.impl.entity303.curse.EventEntity303CurseTimeOffset;
import net.romeo.sightingsofaplayer.soap_event.impl.entity303.EventEntity303SpawnTnt;
import net.romeo.sightingsofaplayer.soap_event.impl.herobrine.curse.EventHerobrineCursePlaceTorch;
import net.romeo.sightingsofaplayer.soap_event.impl.shared.EventPlaceRedstoneTorchFarAway;
import net.romeo.sightingsofaplayer.soap_event.impl.shared.EventPlayEerieSound;
import net.romeo.sightingsofaplayer.soap_event.impl.shared.EventReplaceTorches;
import net.romeo.sightingsofaplayer.soap_event.impl.nullmyth.curse.EventNullCurseChat;
import net.romeo.sightingsofaplayer.soap_event.impl.nullmyth.EventNullSpawnBlocks;
import net.romeo.sightingsofaplayer.soap_event.impl.shared.EventSpawnStalker;
import net.romeo.sightingsofaplayer.soap_event.impl.story.EventActivateRandomMyth;
import net.romeo.sightingsofaplayer.soap_event.impl.story.EventBBGatherDetectives;
import net.romeo.sightingsofaplayer.soap_event.impl.story.EventMythBecomePassive;

/**
 * The pool of {@code soap_event}s the system chooses from.
 * <p>
 * Adding an event means writing its class and listing it here - nothing else in the system has to
 * change, which is the point of giving every event its own class.
 * <p>
 * The list is fixed at class-load time and read-only afterwards, so the per-tick path only ever
 * iterates a handful of objects.
 */
public final class SoapEventPool {

    private static final List<SoapEvent> EVENTS = List.of(
            new EventBBGatherDetectives(),

            new EventReplaceTorches(),
            new EventPlaceRedstoneTorchFarAway(),
            new EventPlayEerieSound(),
            new EventSpawnStalker(),

            new EventHerobrineCursePlaceTorch(),

            new EventEntity303SpawnTnt(),
            new EventEntity303ChangeDayNight(),
            new EventEntity303CurseTimeOffset(),

            new EventNullSpawnBlocks(),

            new EventNullCurseChat(),

            new EventActivateRandomMyth(),
            new EventMythBecomePassive(),
            new EventDebugScreenDistortion()
    );

    private static final Map<String, SoapEvent> BY_ID = index();

    /** Every registered event, in the order they are declared. */
    public static List<SoapEvent> all() {
        return EVENTS;
    }

    /** Every event whose conditions are met right now. */
    public static List<SoapEvent> availableEvents(SoapEventContext context, SoapEventFilter filter) {
        List<SoapEvent> available = new ArrayList<>();
        for (SoapEvent event : EVENTS) {
            if (event.canRun(context) && filter.fits(event)) {
                available.add(event);
            }
        }
        return available;
    }

    public static List<SoapEvent> availableEvents(SoapEventContext context) {
        return availableEvents(context, new SoapEventFilter());
    }

    /** The event with this id, or {@code null} when no such event is registered (any more). */
    @Nullable
    public static SoapEvent byId(String id) {
        return BY_ID.get(id);
    }

    /** Every registered id, in declaration order. Used for command suggestions. */
    public static List<String> ids() {
        return List.copyOf(BY_ID.keySet());
    }

    private static Map<String, SoapEvent> index() {
        Map<String, SoapEvent> index = new LinkedHashMap<>();
        for (SoapEvent event : EVENTS) {
            SoapEvent clash = index.put(event.id(), event);
            if (clash != null) {
                // Two events sharing an id would make the saved history ambiguous, so fail loudly
                // and early instead of silently losing runs.
                throw new IllegalStateException("Duplicate " + SoapEvent.KEYWORD + " id: " + event.id());
            }
        }
        return Collections.unmodifiableMap(index);
    }

    private SoapEventPool() {
    }
}

