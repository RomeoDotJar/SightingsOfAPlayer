package net.romeo.sightingsofaplayer.soap_event.impl.story;

import java.util.ArrayList;
import java.util.List;

import net.romeo.sightingsofaplayer.SOAPConfig;
import net.romeo.sightingsofaplayer.myth.Myth;
import net.romeo.sightingsofaplayer.myth.MythRoster;
import net.romeo.sightingsofaplayer.saveddata.SOAPMythSavedData;
import net.romeo.sightingsofaplayer.saveddata.SOAPServerTimeData;
import net.romeo.sightingsofaplayer.soap_event.SoapEvent;
import net.romeo.sightingsofaplayer.soap_event.SoapEventContext;

/**
 * Story event that can transition an active myth to passive
 * after it has been active for at least 5 days.
 * Transitioning to passive activates the myth's Curse permanently.
 */
public class EventMythBecomePassive extends SoapEvent {

    public static final String ID = "myth_become_passive";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Categories category() {
        return Categories.Story;
    }

    @Override
    public float selectionWeight() {
        return 8.0f;
    }

    @Override
    public boolean canRun(SoapEventContext context) {
        return !getEligibleActiveMyths(context).isEmpty() && super.canRun(context);
    }

    @Override
    public boolean run(SoapEventContext context) {
        List<Myth> eligible = getEligibleActiveMyths(context);
        if (eligible.isEmpty()) {
            return false;
        }

        Myth chosen = eligible.get(context.random().nextInt(eligible.size()));
        SOAPMythSavedData mythData = SOAPMythSavedData.get(context.server());
        SOAPServerTimeData timeData = SOAPServerTimeData.get(context.server());

        mythData.setRoster(chosen, MythRoster.PASSIVE, timeData.getTotalDays());
        return true;
    }

    private static List<Myth> getEligibleActiveMyths(SoapEventContext context) {
        SOAPMythSavedData mythData = SOAPMythSavedData.get(context.server());
        SOAPServerTimeData timeData = SOAPServerTimeData.get(context.server());
        long currentDay = timeData.getTotalDays();

        List<Myth> result = new ArrayList<>();
        for (Myth myth : mythData.getMythsInRoster(MythRoster.ACTIVE)) {
            long activeSince = mythData.getActiveSinceDay(myth);
            if (activeSince >= 0 && (currentDay - activeSince) >= SOAPConfig.INVESTIGATION_DURATION_DAYS.get()) {
                result.add(myth);
            }
        }
        return result;
    }
}
