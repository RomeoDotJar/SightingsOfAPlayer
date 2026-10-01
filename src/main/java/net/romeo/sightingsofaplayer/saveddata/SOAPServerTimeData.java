package net.romeo.sightingsofaplayer.saveddata;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.romeo.sightingsofaplayer.SOAP;

/**
 * Server-wide stopwatch: every tick this world has ever run, and the days those ticks add up to.
 * <p>
 * The number is counted by this mod, it is never read from the level. {@code /time} only rewrites
 * {@code dayTime} - the daylight cycle - which this class never looks at, so neither
 * {@code /time set} nor a stopped daylight cycle can move {@link #getTotalTicks()}. For the same
 * reason the count is not {@code gameTime} either: it is a plain counter of the ticks this mod has
 * watched tick by, and it keeps ticking while nobody is online.
 * <p>
 * Despite living in a {@code level} package, this is <b>not</b> stored per dimension. It always
 * uses the overworld's data storage ({@code <world>/data/}), which Minecraft treats as the single,
 * global storage for the whole server (the same place {@link SOAPPlayerSavedData} and the scoreboard
 * live), so the count is shared by every dimension and survives restarts. A brand new world
 * therefore starts at zero.
 * <p>
 * Cost: {@link #tick()} is one {@code long} increment and one boolean assignment on an object that
 * is already in memory - no allocation, no NBT work, no file I/O - so it can run forever. The file
 * write is not done here at all: the record is only <i>flagged</i>, and Minecraft then writes it
 * whenever it saves the level (every autosave, and on every clean shutdown). That makes this
 * counter exactly as durable as the world's own day count: a hard crash can lose the ticks since
 * the last autosave, no more and no less.
 */
public class SOAPServerTimeData extends SavedData {

    /** Name of the {@code .dat} file, stored next to the world's other saved data. */
    public static final String DATA_NAME = SOAP.MODID + "_server_time";

    /**
     * Length of one Minecraft day in ticks.
     * <p>
     * Taken from vanilla purely so that "a day" here means exactly what a day means everywhere
     * else in the game. It is the only thing this class borrows from the daylight cycle, and it
     * is a constant, not the live time of day.
     */
    public static final long TICKS_PER_DAY = Level.TICKS_PER_DAY;
    public static final int TICKS_PER_SECOND = 20;

    /** NBT key of the single counter that is persisted. */
    private static final String TAG_TOTAL_TICKS = "TotalTicks";

    private static final SavedData.Factory<SOAPServerTimeData> FACTORY
            = new SavedData.Factory<>(SOAPServerTimeData::create, SOAPServerTimeData::load);

    /** Ticks this world has run in total: every session it has ever had, added together. */
    private long totalTicks;

    // Create new instance of saved data
    public static SOAPServerTimeData create() {
        return new SOAPServerTimeData();
    }

    // Load existing instance of saved data
    public static SOAPServerTimeData load(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        SOAPServerTimeData data = SOAPServerTimeData.create();
        // A negative value would mean a damaged file; starting over is the only sane reading.
        data.totalTicks = Math.max(0L, tag.getLong(TAG_TOTAL_TICKS));
        return data;
    }

    /**
     * Fetches the <b>server-wide</b> clock, creating (and remembering) it if needed.
     * <p>
     * Keyed on the {@link MinecraftServer} rather than on a level: this deliberately always
     * resolves to the overworld's data storage, so there is exactly one clock for the entire
     * server no matter which dimension it is called from. Once loaded the record is cached by the
     * server, which is why the per-tick callers can afford to call this every tick.
     */
    public static SOAPServerTimeData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong(TAG_TOTAL_TICKS, totalTicks);
        return tag;
    }

    /**
     * Counts a single tick of world time.
     * <p>
     * Only the {@code long} below is touched on the hot path. Days are derived from it rather than
     * stored beside it, so there is no second counter that could ever drift out of step.
     * <p>
     * The dirty flag is set on every tick rather than throttled to, say, once a second: it is a
     * plain boolean assignment ({@code setDirty} has no side effects), and the expensive part - the
     * file write - is already batched by Minecraft's own save cycle. Throttling it would therefore
     * buy nothing and would only make the counter harder to reason about.
     */
    public void tick() {
        this.totalTicks++;
        setDirty();
    }

    public void tick(long ticks) {
        this.totalTicks+=ticks;
        setDirty();
    }

    /** Ticks this world has run in total, across every session it has ever had. */
    public long getTotalTicks() {
        return totalTicks;
    }

    /** Whole days this world has been running for. */
    public long getTotalDays() {
        return totalTicks / TICKS_PER_DAY;
    }

    /** How far into the current day the world is, {@code 0 .. TICKS_PER_DAY - 1}. */
    public long getTicksIntoDay() {
        return totalTicks % TICKS_PER_DAY;
    }

    /** The day the world is on right now, counting the very first day as day 1. */
    public long getDayNumber() {
        return getTotalDays() + 1L;
    }
}
