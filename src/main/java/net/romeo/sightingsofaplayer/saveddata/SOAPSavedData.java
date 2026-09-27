package net.romeo.sightingsofaplayer.saveddata;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.romeo.sightingsofaplayer.SOAP;

/**
 * Server-wide memory of the Amulet.
 * <p>
 * Two sets are tracked so the server can answer both halves of the question:
 * <ul>
 *     <li>{@link #getAmuletUsers()} - players who used an Amulet</li>
 *     <li>{@link #getAmuletNonUsers()} - players the server has seen who did not</li>
 * </ul>
 * Using an Amulet moves a player from the second set into the first one.
 * <p>
 * Despite the name, this is <b>not</b> stored per level/dimension. It always lives in the
 * overworld's data storage ({@code <world>/data/}), which Minecraft uses as the single,
 * global storage for the whole server (the same place the scoreboard and command storage
 * live). Using the Amulet in the overworld, the Nether, the End or a modded dimension
 * therefore all read and write the very same record.
 */
public class SOAPSavedData extends SavedData {

    /** Name of the {@code .dat} file, stored next to the world's other saved data. */
    public static final String DATA_NAME = SOAP.MODID + "_amulet_usage";

    private static final SavedData.Factory<SOAPSavedData> FACTORY
            = new SavedData.Factory<>(SOAPSavedData::create, SOAPSavedData::load);

    private static final String TAG_USED = "AmuletUsed";
    private static final String TAG_NOT_USED = "AmuletNotUsed";

    private final Set<UUID> amuletUsers = new HashSet<>();
    private final Set<UUID> amuletNonUsers = new HashSet<>();

    // Create new instance of saved data
    public static SOAPSavedData create() {
        return new SOAPSavedData();
    }

    // Load existing instance of saved data
    public static SOAPSavedData load(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        SOAPSavedData data = SOAPSavedData.create();
        readUuids(tag, TAG_USED, data.amuletUsers);
        readUuids(tag, TAG_NOT_USED, data.amuletNonUsers);
        return data;
    }

    /**
     * Fetches the <b>server-wide</b> Amulet data, creating (and remembering) it if needed.
     * <p>
     * Keyed on the {@link MinecraftServer} rather than on a level: this deliberately always
     * resolves to the overworld's data storage, so there is exactly one Amulet record for the
     * entire server no matter which dimension it is called from. The record is cached by the
     * server, so a player who used the Amulet in one dimension counts as "used" in all of them.
     */
    public static SOAPSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(TAG_USED, writeUuids(amuletUsers));
        tag.put(TAG_NOT_USED, writeUuids(amuletNonUsers));
        return tag;
    }

    /** @return true if the server remembers this player as having used an Amulet. */
    public boolean hasUsedAmulet(UUID playerId) {
        return amuletUsers.contains(playerId);
    }

    /** Remembers that the player used an Amulet. They leave the "did not" set. */
    public void markAmuletUsed(UUID playerId) {
        amuletNonUsers.remove(playerId);
        if (amuletUsers.add(playerId)) {
            setDirty();
        }
    }

    /** Remembers that the player has not used an Amulet (yet). Never overrides a use. */
    public void markAmuletNotUsed(UUID playerId) {
        if (amuletUsers.contains(playerId)) {
            return;
        }

        if (amuletNonUsers.add(playerId)) {
            setDirty();
        }
    }

    /** Every player the server remembers as having used an Amulet. */
    public Set<UUID> getAmuletUsers() {
        return Collections.unmodifiableSet(amuletUsers);
    }

    /** Every player the server remembers as not having used an Amulet. */
    public Set<UUID> getAmuletNonUsers() {
        return Collections.unmodifiableSet(amuletNonUsers);
    }

    private static void readUuids(CompoundTag tag, String key, Set<UUID> target) {
        ListTag list = tag.getList(key, Tag.TAG_INT_ARRAY);
        for (Tag element : list) {
            target.add(NbtUtils.loadUUID(element));
        }
    }

    private static ListTag writeUuids(Set<UUID> source) {
        ListTag list = new ListTag();
        for (UUID playerId : source) {
            list.add(NbtUtils.createUUID(playerId));
        }

        return list;
    }
}