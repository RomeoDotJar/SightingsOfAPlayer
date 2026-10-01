package net.romeo.sightingsofaplayer.saveddata;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.romeo.sightingsofaplayer.SOAP;

/**
 * What the {@code SoapEventSystem} has to remember between sessions: how long until the next event,
 * which event was chosen prematurely for it, and what has been run so far.
 * <p>
 * Stored server-wide in the overworld's data storage, like the rest of this mod's records, so a
 * restart neither forgets the schedule nor hands out a fresh batch of events.
 * <p>
 * The run history is kept in two shapes on purpose: a bounded list of the most recent runs, each
 * with the world tick it happened at (used for the no-repeat rule and for display), and a plain
 * counter per event id, which never forgets how often something has ever happened.
 */
public class SOAPEventSavedData extends SavedData {

    /** Name of the {@code .dat} file, stored next to the world's other saved data. */
    public static final String DATA_NAME = SOAP.MODID + "_events";

    private static final String TAG_TICKS_UNTIL_NEXT = "TicksUntilNext";
    private static final String TAG_TICKS_SINCE_RUN = "TicksSinceRun";
    private static final String TAG_NEXT_EVENT = "NextEvent";
    private static final String TAG_TOTAL_RUNS = "TotalRuns";
    private static final String TAG_RUN_TIMES = "RunTimes";
    private static final String TAG_RECENT = "RecentRuns";
    private static final String TAG_RECENT_ID = "Id";
    private static final String TAG_RECENT_AT = "AtTick";
    private static final String TAG_COUNTS = "RunCounts";
    private static final String TAG_TENSION = "Tension";

    /** How many past runs are remembered in detail. Comfortably more than any no-repeat window. */
    public static final int RECENT_LIMIT = 32;

    private static final SavedData.Factory<SOAPEventSavedData> FACTORY
            = new SavedData.Factory<>(SOAPEventSavedData::create, SOAPEventSavedData::load);

    /** Ticks left before the pre-selected event is due. */
    private long ticksUntilNext;

    /**
     * Ticks since the last event.
     * Used when an event fails and a new one needs to be chosen - the new resulting delay is reduced by this value
     * */
    private long ticksSinceRun;

    /** Id of the event chosen for that moment. Empty while nothing has been chosen. */
    private String nextEventId = "";

    /** Every event ever run by this system. */
    private long totalRuns;

    /** The most recent runs, oldest first, capped at {@link #RECENT_LIMIT}. */
    private final Deque<Run> recent = new ArrayDeque<>();

    /** How many times each event id has ever run. */
    private final Map<String, Integer> runCounts = new HashMap<>();

    /** Last time each event id was run. */
    private final Map<String, Long> runTimes = new HashMap<>();

    /** Current tension on the server. Expected to range from 0 to 1000, however it is technically unrestricted. */
    private int tension;

    /** One remembered run: which event, and the world tick it happened at. */
    public record Run(String eventId, long atTick) {
    }

    // Create new instance of saved data
    public static SOAPEventSavedData create() {
        return new SOAPEventSavedData();
    }

    // Load existing instance of saved data
    public static SOAPEventSavedData load(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        SOAPEventSavedData data = SOAPEventSavedData.create();

        data.ticksUntilNext = Math.max(0L, tag.getLong(TAG_TICKS_UNTIL_NEXT));
        data.ticksSinceRun = Math.max(0L, tag.getLong(TAG_TICKS_SINCE_RUN));
        data.nextEventId = tag.getString(TAG_NEXT_EVENT);
        data.totalRuns = Math.max(0L, tag.getLong(TAG_TOTAL_RUNS));
        data.tension = Math.max(0, tag.getInt(TAG_TENSION));

        ListTag runs = tag.getList(TAG_RECENT, Tag.TAG_COMPOUND);
        for (Tag element : runs) {
            CompoundTag entry = (CompoundTag) element;
            Run run = new Run(entry.getString(TAG_RECENT_ID), entry.getLong(TAG_RECENT_AT));

            data.recent.addLast(run);
        }

        CompoundTag counts = tag.getCompound(TAG_COUNTS);
        for (String id : counts.getAllKeys()) {
            data.runCounts.put(id, counts.getInt(id));
        }

        CompoundTag runTimes = tag.getCompound(TAG_RUN_TIMES);
        for (String id : runTimes.getAllKeys()) {
            data.runTimes.put(id, runTimes.getLong(id));
        }

        return data;
    }

    /**
     * Fetches the <b>server-wide</b> event record, creating (and remembering) it if needed.
     * Keyed on the {@link MinecraftServer} so every dimension and every caller sees the same one.
     */
    public static SOAPEventSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong(TAG_TICKS_UNTIL_NEXT, ticksUntilNext);
        tag.putLong(TAG_TICKS_SINCE_RUN, ticksSinceRun);
        tag.putString(TAG_NEXT_EVENT, nextEventId);
        tag.putLong(TAG_TOTAL_RUNS, totalRuns);
        tag.putInt(TAG_TENSION, tension);

        ListTag runs = new ListTag();
        for (Run run : recent) {
            CompoundTag entry = new CompoundTag();
            entry.putString(TAG_RECENT_ID, run.eventId());
            entry.putLong(TAG_RECENT_AT, run.atTick());
            runs.add(entry);
        }
        tag.put(TAG_RECENT, runs);

        CompoundTag counts = new CompoundTag();
        for (Map.Entry<String, Integer> entry : runCounts.entrySet()) {
            counts.putInt(entry.getKey(), entry.getValue());
        }
        tag.put(TAG_COUNTS, counts);

        CompoundTag runTimes = new CompoundTag();
        for (Map.Entry<String, Long> entry : this.runTimes.entrySet()) {
            runTimes.putLong(entry.getKey(), entry.getValue());
        }
        tag.put(TAG_RUN_TIMES, runTimes);

        return tag;
    }

    public long getTicksUntilNext() {
        return ticksUntilNext;
    }

    public long getTicksSinceRun() {
        return ticksSinceRun;
    }

    public String getNextEventId() {
        return nextEventId;
    }

    public long getTotalRuns() {
        return totalRuns;
    }

    public int getTension() {
        return tension;
    }

    /** The most recent runs, newest first. */
    public List<Run> getRecentRuns() {
        List<Run> newestFirst = new ArrayList<>();
        for (Iterator<Run> it = recent.descendingIterator(); it.hasNext(); ) {
            newestFirst.add(it.next());
        }
        return newestFirst;
    }

    /** How many times this event id has ever run. */
    public int getRunCount(String eventId) {
        return runCounts.getOrDefault(eventId, 0);
    }

    public long getRunTickAt(String eventId) {
        return runTimes.getOrDefault(eventId, -1L);
    }

    /**
     * The event ids of the last {@code window} runs, newest first - what the no-repeat rule looks
     * at. Ids of events that no longer exist are harmlessly included; they simply match nothing.
     */
    public Set<String> recentIds(int window) {
        Set<String> ids = new HashSet<>();
        int taken = 0;
        for (Iterator<Run> it = recent.descendingIterator(); it.hasNext() && taken < window; taken++) {
            ids.add(it.next().eventId());
        }
        return ids;
    }

    /** Counts one tick off the countdown. */
    public void decrementCountdown() {
        if (ticksUntilNext > 0) {
            ticksUntilNext--;
            ticksSinceRun++;
            setDirty();
        }
    }

    /** Arms the countdown for the event that has just been chosen prematurely. */
    public void setNextEvent(String eventId, long ticks) {
        this.nextEventId = eventId;
        this.ticksUntilNext = Math.max(1L, ticks);
        setDirty();
    }

    /** Gives up on the current pick and waits this long before choosing again. */
    public void setRetryDelay(long ticks) {
        this.nextEventId = "";
        this.ticksUntilNext = Math.max(1L, ticks);
        setDirty();
    }

    public void addTension(int tensionGain) {
        tension += tensionGain;
        setDirty();
    }

    /** Records that an event ran: bumps the counters and the bounded recent list. */
    public void recordRun(String eventId, long atTick) {
        totalRuns++;
        runCounts.merge(eventId, 1, Integer::sum);
        runTimes.put(eventId, atTick);
        ticksSinceRun = 0;

        recent.addLast(new Run(eventId, atTick));
        while (recent.size() > RECENT_LIMIT) {
            recent.removeFirst();
        }

        setDirty();
    }
}
