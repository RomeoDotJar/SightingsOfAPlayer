package net.romeo.sightingsofaplayer.soap_event.impl.story;

import java.util.List;

import net.romeo.sightingsofaplayer.myth.Myth;
import net.romeo.sightingsofaplayer.myth.MythRoster;
import net.romeo.sightingsofaplayer.saveddata.SOAPEventSavedData;
import net.romeo.sightingsofaplayer.saveddata.SOAPMythSavedData;
import net.romeo.sightingsofaplayer.saveddata.SOAPServerTimeData;
import net.romeo.sightingsofaplayer.soap_event.SoapEvent;
import net.romeo.sightingsofaplayer.soap_event.SoapEventContext;

/**
 * Story event that runs once per server after 4 days have passed.
 * Activates a random inactive myth.
 */
public class EventActivateRandomMyth extends SoapEvent {

    public static final String ID = "activate_random_myth";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Categories category() {
        return Categories.ForceOnly;
    }

    @Override
    public boolean canRun(SoapEventContext context) {
        SOAPServerTimeData timeData = SOAPServerTimeData.get(context.server());
        SOAPEventSavedData eventData = SOAPEventSavedData.get(context.server());
        SOAPMythSavedData mythData = SOAPMythSavedData.get(context.server());

        return super.canRun(context)
                && timeData.getTotalDays() >= 2
                && (timeData.getTotalTicks() - eventData.getRunTickAt(id())) >= SOAPServerTimeData.TICKS_PER_DAY*2
                && !mythData.getMythsInRoster(MythRoster.INACTIVE).isEmpty();
    }

    @Override
    public boolean run(SoapEventContext context) {
        SOAPMythSavedData mythData = SOAPMythSavedData.get(context.server());
        SOAPServerTimeData timeData = SOAPServerTimeData.get(context.server());
        List<Myth> inactives = mythData.getMythsInRoster(MythRoster.INACTIVE);

        if (inactives.isEmpty()) {
            return false;
        }

        Myth chosen = inactives.get(context.random().nextInt(inactives.size()));
        mythData.setRoster(chosen, MythRoster.ACTIVE, timeData.getTotalDays());
        return true;
    }
}
