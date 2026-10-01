package net.romeo.sightingsofaplayer.soap_event.impl.entity303;

import net.minecraft.server.level.ServerLevel;
import net.romeo.sightingsofaplayer.myth.Myth;
import net.romeo.sightingsofaplayer.saveddata.SOAPMythSavedData;
import net.romeo.sightingsofaplayer.soap_event.SoapEvent;
import net.romeo.sightingsofaplayer.soap_event.SoapEventContext;

/**
 * Entity 303 active event: changes time from day to night and vice versa.
 */
public class EventEntity303ChangeDayNight extends SoapEvent {

    public static final String ID = "entity303_change_day_night";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Categories category() {
        return Categories.Big;
    }

    @Override
    public int tensionGain() {
        return 35;
    }

    @Override
    public float selectionWeight() {
        return 2.5f;
    }

    @Override
    public boolean canRun(SoapEventContext context) {
        SOAPMythSavedData mythData = SOAPMythSavedData.get(context.server());
        return mythData.isActive(Myth.ENTITY_303) && super.canRun(context);
    }

    @Override
    public boolean run(SoapEventContext context) {
        ServerLevel overworld = context.overworld();
        long currentDayTime = overworld.getDayTime();
        long dayCount = currentDayTime / 24000L;
        long timeInDay = currentDayTime % 24000L;

        // Day is roughly 0..12000 (noon is 6000), Night is roughly 12000..24000 (13000 is night start, 18000 is midnight)
        long newDayTime;
        if (timeInDay < 12000L) {
            // Day -> Switch to night (13000 ticks)
            newDayTime = dayCount * 24000L + 13000L;
        } else {
            // Night -> Switch to day (1000 ticks)
            newDayTime = (dayCount + 1L) * 24000L + 1000L;
        }

        for (ServerLevel level : context.server().getAllLevels()) {
            level.setDayTime(newDayTime);
        }

        return true;
    }
}
