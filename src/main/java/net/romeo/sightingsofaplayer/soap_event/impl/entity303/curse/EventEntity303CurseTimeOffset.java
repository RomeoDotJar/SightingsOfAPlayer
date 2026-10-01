package net.romeo.sightingsofaplayer.soap_event.impl.entity303.curse;

import net.minecraft.server.level.ServerLevel;
import net.romeo.sightingsofaplayer.myth.Myth;
import net.romeo.sightingsofaplayer.saveddata.SOAPMythSavedData;
import net.romeo.sightingsofaplayer.soap_event.SoapEvent;
import net.romeo.sightingsofaplayer.soap_event.SoapEventContext;

/**
 * Entity 303 Curse event:
 * As a Curse (active when Entity 303 is Passive), Entity 303 will occasionally offset the time of day
 * by -1000 to 1000 ticks.
 */
public class EventEntity303CurseTimeOffset extends SoapEvent {

    public static final String ID = "entity303_curse_time_offset";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Categories category() {
        return Categories.Curse;
    }

    @Override
    public int tensionGain() {
        return 5;
    }

    @Override
    public float selectionWeight() {
        // Runs rarely
        return 0.5f;
    }

    @Override
    public float nextDelayMultiplier() {
        return .01f;
    }

    @Override
    public boolean canRun(SoapEventContext context) {
        SOAPMythSavedData mythData = SOAPMythSavedData.get(context.server());
        return mythData.isCurseActive(Myth.ENTITY_303) && super.canRun(context);
    }

    @Override
    public boolean run(SoapEventContext context) {
        // Offset range: -1000 to 1000 ticks (excluding 0)
        int offset = context.random().nextInt(2001) - 1000;
        if (offset == 0) {
            offset = context.random().nextBoolean() ? 100 : -100;
        }

        for (ServerLevel level : context.server().getAllLevels()) {
            long newTime = Math.max(0L, level.getDayTime() + offset);
            level.setDayTime(newTime);
        }

        return true;
    }
}
