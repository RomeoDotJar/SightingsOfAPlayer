package net.romeo.sightingsofaplayer.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.romeo.sightingsofaplayer.SOAP;
import net.romeo.sightingsofaplayer.dimension.SOAPBBRoomSession;
import net.romeo.sightingsofaplayer.init.SOAPItems;
import net.romeo.sightingsofaplayer.saveddata.SOAPPlayerSavedData;

/**
 * Player lifecycle handling for the Amulet.
 * <p>
 * Registered on <b>both</b> physical sides on purpose: player events are fired by the logical
 * server, which in singleplayer runs inside the client process. Restricting this to
 * {@link Dist#DEDICATED_SERVER} would silently break singleplayer worlds.
 */
@EventBusSubscriber(modid = SOAP.MODID, value = { Dist.CLIENT, Dist.DEDICATED_SERVER })
public class SOAPPlayerEvents {

    /** First time a player joins the world. */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        onPlayerEnteredWorld(event.getEntity());
    }

    /** Respawning after death, or returning from the End. */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        onPlayerEnteredWorld(event.getEntity());
    }

    /**
     * Runs whenever a player (re)enters the world, so the server can both remember them and
     * hand out an Amulet.
     * <p>
     * Note on timing: the vanilla respawn flow restores the player's inventory before
     * {@code PlayerRespawnEvent} is posted, so the "already carrying one" check below sees the
     * final inventory and can never produce a duplicate when the {@code keepInventory} game
     * rule is enabled.
     */
    private static void onPlayerEnteredWorld(Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        SOAPPlayerSavedData data = SOAPPlayerSavedData.get(serverLevel.getServer());

        // Somebody (re)entering the world inside the detectives' room has no business being there:
        // they logged out mid-gathering, or the server restarted under them. Send them home first,
        // before the Amulet is handed out below - this applies to players who already used theirs,
        // who would otherwise return early and stay stuck.
        if (player instanceof ServerPlayer serverPlayer) {
            SOAPBBRoomSession.rescueIfInRoom(serverPlayer);
        }

        // Record the player as "has not used an Amulet". This is a no-op for players who did.
        data.markAmuletNotUsed(player.getUUID());

        // Players who already used their Amulet are done with it: nothing to hand out.
        if (data.hasUsedAmulet(player.getUUID())) {
            return;
        }

        ItemStack amulet = SOAPItems.AMULET.get().getDefaultInstance();

        // Already carrying an Amulet (anywhere in the inventory)? Leave them alone.
        if (player.getInventory().contains(amulet)) {
            return;
        }

        // A full inventory should not silently eat the Amulet, so drop it at the player's feet.
        if (!player.getInventory().add(amulet)) {
            player.drop(amulet, false);
        }
    }
}
