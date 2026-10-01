package net.romeo.sightingsofaplayer.saveddata;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.romeo.sightingsofaplayer.SOAP;
import net.romeo.sightingsofaplayer.myth.Myth;
import net.romeo.sightingsofaplayer.myth.MythRoster;

/**
 * Tracks the state of all myths on the server.
 * Each myth belongs to one of three rosters:
 * - INACTIVE: no influence on server
 * - ACTIVE: actively haunting the server
 * - PASSIVE: much less influence, activates curse
 *
 * Also tracks the day each myth became active, so we can verify if it has been active
 * for at least a certain number of days (e.g. 5 days).
 */
public class SOAPMythSavedData extends SavedData {

    public static final String DATA_NAME = SOAP.MODID + "_myths";

    private static final String TAG_ROSTER_PREFIX = "roster_";
    private static final String TAG_ACTIVE_DAY_PREFIX = "active_day_";
    private static final String TAG_IDENTIFIED = "identified_";

    private static final SavedData.Factory<SOAPMythSavedData> FACTORY
            = new SavedData.Factory<>(SOAPMythSavedData::create, SOAPMythSavedData::load);

    private final Map<Myth, MythRoster> rosters = new EnumMap<>(Myth.class);
    private final Map<Myth, Long> activeSinceDay = new EnumMap<>(Myth.class);
    private final Map<Myth, Boolean> identified = new EnumMap<>(Myth.class);

    public SOAPMythSavedData() {
        for (Myth myth : Myth.values()) {
            rosters.put(myth, MythRoster.INACTIVE);
            activeSinceDay.put(myth, -1L);
        }
    }

    public static SOAPMythSavedData create() {
        return new SOAPMythSavedData();
    }

    public static SOAPMythSavedData load(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        SOAPMythSavedData data = SOAPMythSavedData.create();
        for (Myth myth : Myth.values()) {
            String rosterName = tag.getString(TAG_ROSTER_PREFIX + myth.getId());
            if (!rosterName.isEmpty()) {
                try {
                    data.rosters.put(myth, MythRoster.valueOf(rosterName));
                } catch (IllegalArgumentException ignored) {
                    data.rosters.put(myth, MythRoster.INACTIVE);
                }
            } else {
                data.rosters.put(myth, MythRoster.INACTIVE);
            }

            if (tag.contains(TAG_ACTIVE_DAY_PREFIX + myth.getId())) {
                data.activeSinceDay.put(myth, tag.getLong(TAG_ACTIVE_DAY_PREFIX + myth.getId()));
            } else {
                data.activeSinceDay.put(myth, -1L);
            }

            try {
                data.identified.put(myth, tag.getBoolean(TAG_IDENTIFIED + myth.getId()));
            } catch (IllegalArgumentException ignored) {
                data.identified.put(myth, false);
            }
        }
        return data;
    }

    public static SOAPMythSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        for (Myth myth : Myth.values()) {
            tag.putString(TAG_ROSTER_PREFIX + myth.getId(), rosters.get(myth).name());
            tag.putLong(TAG_ACTIVE_DAY_PREFIX + myth.getId(), activeSinceDay.get(myth));
            tag.putBoolean(TAG_IDENTIFIED + myth.getId(), identified.get(myth));
        }
        return tag;
    }

    public MythRoster getRoster(Myth myth) {
        return rosters.getOrDefault(myth, MythRoster.INACTIVE);
    }

    public boolean isInactive(Myth myth) {
        return getRoster(myth) == MythRoster.INACTIVE;
    }

    public boolean isActive(Myth myth) {
        return getRoster(myth) == MythRoster.ACTIVE;
    }

    public boolean isPassive(Myth myth) {
        return getRoster(myth) == MythRoster.PASSIVE;
    }

    /**
     * Curse is active when the myth is PASSIVE (and remains active for the rest of the server's lifetime).
     */
    public boolean isCurseActive(Myth myth) {
        return isPassive(myth);
    }

    public long getActiveSinceDay(Myth myth) {
        return activeSinceDay.getOrDefault(myth, -1L);
    }

    public void setRoster(Myth myth, MythRoster roster, long currentDay) {
        rosters.put(myth, roster);
        if (roster == MythRoster.ACTIVE) {
            activeSinceDay.put(myth, currentDay);
        }
        setDirty();
    }

    public List<Myth> getMythsInRoster(MythRoster roster) {
        List<Myth> result = new ArrayList<>();
        for (Myth myth : Myth.values()) {
            if (!myth.isChoosable())
                continue;
            if (rosters.get(myth) == roster) {
                result.add(myth);
            }
        }
        return result;
    }
}
