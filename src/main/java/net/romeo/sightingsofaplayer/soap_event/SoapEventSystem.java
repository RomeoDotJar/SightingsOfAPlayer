package net.romeo.sightingsofaplayer.soap_event;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.mojang.datafixers.util.Pair;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.romeo.sightingsofaplayer.SOAPConfig;
import net.romeo.sightingsofaplayer.SOAP;
import net.romeo.sightingsofaplayer.myth.Myth;
import net.romeo.sightingsofaplayer.saveddata.SOAPEventSavedData;
import net.romeo.sightingsofaplayer.saveddata.SOAPMythSavedData;
import net.romeo.sightingsofaplayer.saveddata.SOAPServerTimeData;

/**
 * The scheduler that decides when a {@code soap_event} happens and which one it is.
 * <p>
 * The model, in order:
 * <ol>
 *     <li>An event is <b>chosen prematurely</b>: as soon as one event has run, the next one is
 *     picked and its <i>personal</i> pause is armed - the randomised range for the world's current
 *     age, stretched by {@code SoapEvent#nextDelayMultiplier}, and never shorter than
 *     {@code SoapEvent#minDelaySeconds}. That is how a redstone torch can follow something else
 *     almost at once while a world-changing event keeps everyone waiting.</li>
 *     <li>Every world tick the countdown drops by one, so an idle tick costs a single decrement.</li>
 *     <li>When it reaches zero the event runs - but only if its conditions are <i>still</i> met and
 *     its dice roll passes. If not, it is dropped and a new one is chosen shortly after.</li>
 *     <li>Whatever ran is written into the history, and the cycle starts again at step 1.</li>
 * </ol>
 * Because the choice is made early and a pool may be small, the no-repeat rule relaxes as little as
 * possible rather than deadlocking: if the configured window would leave no candidate at all, the
 * window is shortened until something remains.
 * <p>
 * Only ever touched from the server thread (the tick handler and the command), so the RNG and the
 * chain counter below need no synchronisation.
 */
public final class SoapEventSystem {

    /** The player is not supposed to edit this value, {@code SOAPConfig#PACE_MULTIPLIER} is preferable for this instead. */
    public static final long BASE_EVENTS_PER_DAY = 8;

    /** How long to wait before looking again when nothing could be chosen or run. */
    private static final long RETRY_TICKS = 20L;

    /** Depth limit for events that call another event, so a chain cannot run away. */
    private static final int MAX_CHAIN_DEPTH = 2;

    private static final RandomSource RANDOM = RandomSource.create();

    private static int chainDepth;

    public static void tick(MinecraftServer server, long ticks) {
        for (long i=0; i<ticks; i++) {
            tick(server);
        }
    }

    /**
     * Advances the schedule by one tick. Driven from {@code SOAPServerTimeEvents}, so it shares the
     * server's own clock: a frozen world does not fire events either.
     */
    public static void tick(MinecraftServer server) {
        if (!SOAPConfig.EVENTS_ENABLED.get()) {
            return;
        }

        SOAPEventSavedData data = SOAPEventSavedData.get(server);

        if (data.getTicksUntilNext() > 0) {
            data.decrementCountdown();
            return;
        }

        SoapEventContext context = new SoapEventContext(server, RANDOM);
        SoapEvent next = SoapEventPool.byId(data.getNextEventId());

        if (next == null) {
            selectNext(data, context);
            return;
        }

        if (!next.canRun(context) || !rollChance(context, next)) {
            data.setRetryDelay(RETRY_TICKS);
            return;
        }

        runEvent(data, context, next);
    }

    /**
     * Runs one extra event right now, ignoring the schedule: the "and call another event"
     * capability that events reach through {@link SoapEvent#callAnotherEvent}. The schedule then
     * restarts from this extra event, so the "longer pause" rules still hold for whatever comes
     * after it.
     *
     * @param origin the event that is asking for the extra one. It is excluded from the choice,
     *               which matters because the origin has not been written to the run history yet -
     *               without this, an event could pick itself.
     * @return whether the extra event actually happened
     */
    public static boolean triggerExtraEventNow(MinecraftServer server, SoapEventFilter filter, SoapEvent origin) {
        if (!SOAPConfig.EVENTS_ENABLED.get() || chainDepth >= MAX_CHAIN_DEPTH) {
            return false;
        }

        SOAPEventSavedData data = SOAPEventSavedData.get(server);
        SoapEventContext context = new SoapEventContext(server, RANDOM);

        chainDepth++;
        try {
            SoapEvent extra = selectNext(data, context, filter, origin.id());
            return extra != null && runEvent(data, context, extra);
        } finally {
            chainDepth--;
        }
    }

    /**
     * Runs a specific event immediately, whatever its conditions say. Reachable only from the
     * operator command, and the only way anything is allowed to bypass the schedule.
     */
    public static boolean triggerNow(MinecraftServer server, SoapEvent event) {
        return runEvent(SOAPEventSavedData.get(server), new SoapEventContext(server, RANDOM), event);
    }

    /**
     * Runs the event, records it, and chooses the next one prematurely - the cycle's end and its
     * beginning in one place, so every path through the system reschedules the same way.
     *
     * @return whether the event actually did something
     */
    private static boolean runEvent(SOAPEventSavedData data, SoapEventContext context, SoapEvent event) {
        if (event==null)
            return false;

        boolean happened = event.run(context);

        if (happened) {
            int tensionGain = event.tensionGain(context);

            data.addTension(tensionGain);
            data.recordRun(event.id(), context.totalTicks());
            SOAP.LOGGER.info("{} ran (world day {}, {}k ticks); new tension is {}",
                    event.name(), context.totalDays() + 1L, context.totalTicks()/1000, data.getTension());
        } else {
            SOAP.LOGGER.info("{} had nothing to do", event.name());
        }

        selectNext(data, context);
        return happened;
    }

    /** Chooses the next event with nothing excluded, for the ordinary scheduled path. */
    private static void selectNext(SOAPEventSavedData data, SoapEventContext context) {
        selectNext(data, context, new SoapEventFilter(), null);
    }

    private static void selectNext(SOAPEventSavedData data, SoapEventContext context, @Nullable String excludedId) {
        selectNext(data, context, new SoapEventFilter(), excludedId);
    }

    /**
     * Chooses the next event and arms its pause. Honours the no-repeat rule as far as the pool
     * allows, shortening the window only when it would otherwise leave nothing to pick.
     *
     * @param excludedId an event that must not be chosen at all, or {@code null}. Used while an
     *                   event is running and asking for a companion.
     */
    @Nullable
    private static SoapEvent selectNext(SOAPEventSavedData data, SoapEventContext context, SoapEventFilter filter, @Nullable String excludedId) {
        List<SoapEvent> available = SoapEventPool.availableEvents(context, filter);
        if (available.isEmpty()) {
            data.setRetryDelay(RETRY_TICKS);
            return null;
        }

        int window = Math.min(SOAPConfig.EVENT_NO_REPEAT_WINDOW.get(), SOAPEventSavedData.RECENT_LIMIT);
        SoapEvent chosen = null;

        for (int w = window; w >= 0 && chosen == null; w--) { // Keep softening the window until a candidate is chosen

            Set<String> skip = w == 0 ? Set.of() : data.recentIds(w);

            List<Pair<SoapEvent, Float>> candidates = new ArrayList<>();
            float maxWeight = 0;
            for (SoapEvent event : available) {
                if (!skip.contains(event.id()) && !event.id().equals(excludedId)) {
                    maxWeight+=event.selectionWeight(context);
                    candidates.add(new Pair(event,maxWeight));
                }
            }

            if (maxWeight>0) {
                //chosen = candidates.get(context.random().nextInt(candidates.size()));

                float chosenWeight = context.random().nextFloat()*maxWeight;

                for (Pair<SoapEvent, Float> pair : candidates) {
                    SoapEvent event = pair.getFirst();
                    float weight = pair.getSecond();

                    if (chosenWeight <= weight) {
                        chosen = event;
                        break;
                    }
                }
            }
        }

        if (chosen == null) {
            data.setRetryDelay(RETRY_TICKS);
            return null;
        }

        long delay = delayTicksFor(chosen, context);
        if (delay == -1) {
            data.setRetryDelay(RETRY_TICKS);
            return null;
        }

        data.setNextEvent(chosen.id(), delay);
        return chosen;
    }

    public static float averageEventsPerDay(SoapEventContext context) {
        // on average this should be around 8 per day
        MinecraftServer server = context.server();
        float tension = SOAPEventSavedData.get(server).getTension()/1000f*1.5f + .5f;
        int pace = SOAPConfig.PACE_PERCENTAGE.get();

        SOAPServerTimeData timeData = SOAPServerTimeData.get(server);
        SOAPMythSavedData mythData = SOAPMythSavedData.get(server);

        float difficultyRampUp = 1;
        Myth[] myths = Myth.values();
        int amountOfMyths = myths.length;

        for (Myth myth : myths) {
            if (mythData.isPassive(myth)) {
                difficultyRampUp += 1f/amountOfMyths;
            }
            else if (mythData.isActive(myth)) {
                difficultyRampUp += 1f*(timeData.getTotalDays()-mythData.getActiveSinceDay(myth))/SOAPConfig.INVESTIGATION_DURATION_DAYS.get();
            }
        }

        float avg = BASE_EVENTS_PER_DAY * tension * pace/100 * difficultyRampUp;

        return Math.clamp(avg, 0, 12000);
    }

    private static long delayTicksFor(SoapEvent event, SoapEventContext context) {
        SOAPEventSavedData data = SOAPEventSavedData.get(context.server());
        long TICKS_PER_DAY = SOAPServerTimeData.TICKS_PER_DAY;

        float AVG_EVENTS_PER_DAY = averageEventsPerDay(context); // TODO: replace with dynamic events per day calculation
        long avgTicksPerEvent = (long)(TICKS_PER_DAY/AVG_EVENTS_PER_DAY);

        int temperature = SOAPConfig.TEMPERATURE_PERCENTAGE.get();

        long avgSeconds = avgTicksPerEvent/SOAPServerTimeData.TICKS_PER_SECOND;
        int minSeconds = (int)(avgSeconds*(1-(float)temperature/100));
        int maxSeconds = (int)(avgSeconds*(1+(float)temperature/100));

        long randomised = minSeconds + context.random().nextInt(maxSeconds - minSeconds + 1);
        randomised = Math.min(3600, randomised);

        List<SOAPEventSavedData.Run> recentRuns = data.getRecentRuns();
        float nextDelayMultiplier = 1;

        if (!recentRuns.isEmpty()) {
            SoapEvent lastEvent = SoapEventPool.byId(recentRuns.getLast().eventId());
            nextDelayMultiplier = lastEvent!=null ? lastEvent.nextDelayMultiplier() : 1;
        }
        long scaled = Math.round(randomised * nextDelayMultiplier);

        long seconds = Math.max(scaled, event.minDelaySeconds());

        return Math.max(1L, seconds * SOAPServerTimeData.TICKS_PER_SECOND - data.getTicksSinceRun());
    }

    private static boolean rollChance(SoapEventContext context, SoapEvent event) {
        float chance = event.chance();
        return chance >= 1.0F || (chance > 0.0F && context.random().nextFloat() < chance);
    }

    private SoapEventSystem() {
    }
}
