package net.romeo.sightingsofaplayer.soap_event;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.romeo.sightingsofaplayer.myth.Myth;
import net.romeo.sightingsofaplayer.myth.MythRoster;
import net.romeo.sightingsofaplayer.saveddata.SOAPMythSavedData;

import java.util.Arrays;

/**
 * One thing the SOAP event system is able to do to the world.
 * <p>
 * This is deliberately <b>not</b> a Minecraft event: nothing here is posted on an event bus and
 * nothing here is triggered by vanilla. A {@code SoapEvent} is chosen by
 * {@link SoapEventSystem} on its own schedule, which is why the {@value #KEYWORD} keyword is used
 * everywhere these are named - in {@link #name()}, in {@link #nameKey()}, in the pool and in the
 * saved run history - so they can never be mistaken for the game's own events.
 * <p>
 * Every event is its own class (see {@code soap_event.impl}) and decides for itself:
 * <ul>
 *     <li>{@link #canRun} - the conditions under which it may be picked (time of day, where the
 *     players are, what they carry, ...)</li>
 *     <li>{@link #chance} - how often it actually fires when its turn comes</li>
 *     <li>{@link #nextDelayMultiplier} - how much it stretches or shortens the pause before the next event
 *     ({@code 0.75} for a small event, {@code 1.25} for a big one)</li>
 *     <li>{@link #minDelaySeconds} - an absolute floor since the previous event, for the few
 *     events that must never follow anything too closely</li>
 *     <li>{@link #run} - the work itself, which may be as complex as it likes</li>
 * </ul>
 */
public abstract class SoapEvent {
    /**
     * Namespace of this mod's events, used to tell them apart from Minecraft's own events in
     * names, in log lines, in the saved history and in the language file.
     */
    public static final String KEYWORD = "soap_event";

    public enum Categories {
        Curse,
        Small,
        Big,
        Story,
        ForceOnly
    }

    public Myth[] relatedMyths() { return new Myth[] {}; }

    public Categories category() { return Categories.Small; }

    public int tensionGain(SoapEventContext ctx) { return tensionGain(); }

    public int tensionGain() { return 0; }

    /** Short, stable identifier. Must not change, or saved history loses track of the event. */
    public abstract String id();

    /** Full name of the event, {@code soap_event:<id>}. */
    public final String name() {
        return KEYWORD + ":" + id();
    }

    /** Translation key of the readable name, {@code soap_event.sightingsofaplayer.<id>}. */
    public final String nameKey() {
        return KEYWORD + ".sightingsofaplayer." + id();
    }

    /**
     * Whether the event may be chosen right now. Called both when the next event is picked and
     * again in the moment before it runs, because conditions can change in between.
     *
     * By default, check whether the event belongs to ForceOnly or to specific Myths.
     * If it does belong to Myths, then check if it's a Curse and any of the Myths are Passive, or if any of the Myths are Active.
     */
    public boolean canRun(SoapEventContext context) {
        Categories cat = category();
        Myth[] myths = relatedMyths();
        return cat!=Categories.ForceOnly
                &&(myths.length == 0
                    ||cat!=Categories.Curse
                    ||Arrays.stream(myths).anyMatch((myth -> SOAPMythSavedData.get(context.server()).isPassive(myth)))
                )
                &&(myths.length == 0
                    ||cat==Categories.Curse
                    ||Arrays.stream(myths).anyMatch((myth -> SOAPMythSavedData.get(context.server()).isActive(myth)))
                );
    }

    /** Probability, {@code 0.0 .. 1.0}, that the event runs when it is due. */
    public float chance() {
        return 1.0F;
    }

    /**
     * Weight used for calculating the next selected event to run.
     * The higher the weight, the more probable this event is to be selected as the next one
     */
    public float selectionWeight(SoapEventContext ctx) {
        return selectionWeight();
    }

    public float selectionWeight() {
        return 1.0f;
    }

    /** Multiplies the randomised delay before this event. Below 1 is sooner, above 1 is later. */
    public float nextDelayMultiplier() {
        return 1.0F;
    }

    /**
     * Shortest pause, in seconds, this event accepts since the previous event, whatever the
     * multiplier worked out to. Meant for the rare event that must not arrive on the heels of
     * another one, for example something that moves every player somewhere else.
     */
    public long minDelaySeconds() {
        return 0L;
    }

    /**
     * Does the work.
     *
     * @return {@code false} if the event could not do anything after all (nowhere to put it, no
     *         player to do it to, ...). A fruitless event is not written to the run history and
     *         the system simply picks again shortly.
     */
    public abstract boolean run(SoapEventContext context);

    /**
     * Asks the system for one more event right now, from inside {@link #run}. The extra event is
     * chosen the normal way - current conditions and the no-repeat rule still apply - with the one
     * guarantee that it is never this event again, so an event cannot chain itself into a burst.
     *
     * @return whether an extra event actually happened
     */
    protected final boolean callAnotherEvent(SoapEventContext context) {
        return callAnotherEvent(context, new SoapEventFilter());
    }

    protected final boolean callAnotherEvent(SoapEventContext context, SoapEventFilter filter) {
        return SoapEventSystem.triggerExtraEventNow(context.server(), filter, this);
    }

    protected final boolean callEvent(SoapEventContext context, SoapEvent event) {
        return SoapEventSystem.triggerNow(context.server(), event);
    }

    protected static int randomIndex(RandomSource random, int size) {
        return random.nextInt(size);
    }
}
