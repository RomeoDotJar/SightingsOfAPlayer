package net.romeo.sightingsofaplayer.event;

import java.util.Locale;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.romeo.sightingsofaplayer.SOAP;
import net.romeo.sightingsofaplayer.myth.Myth;
import net.romeo.sightingsofaplayer.saveddata.SOAPMythSavedData;

/**
 * Listens for player chat messages to handle myth interactions like Null responding.
 * For example, if Null is active and a player types "null", Null responds with "I".
 */
@EventBusSubscriber(modid = SOAP.MODID, value = { Dist.CLIENT, Dist.DEDICATED_SERVER })
public class SOAPMythChatEvents {

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player == null) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        SOAPMythSavedData mythData = SOAPMythSavedData.get(server);
        if (!mythData.isActive(Myth.NULL)) {
            return;
        }

        String text = event.getRawText().toLowerCase(Locale.ROOT).trim();
        if (text.contains("null")) {
            SOAPServerTimeEvents.queueWork(50, () -> {
                Component response = Component.literal("<Null> I");
                server.getPlayerList().broadcastSystemMessage(response, false);
            });
        }
    }
}
