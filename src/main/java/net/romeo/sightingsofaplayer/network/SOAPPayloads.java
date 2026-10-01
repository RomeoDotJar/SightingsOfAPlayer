package net.romeo.sightingsofaplayer.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.romeo.sightingsofaplayer.SOAP;
import net.romeo.sightingsofaplayer.event.SOAPClientVisualEvents;
import net.romeo.sightingsofaplayer.event.SOAPClientMusicEvents;

/**
 * Registers this mod's network payloads.
 * <p>
 * {@link RegisterPayloadHandlersEvent} is a mod-bus event, so {@link EventBusSubscriber} routes it
 * to the mod's own bus automatically - no bus attribute needed. Registration happens on both
 * physical sides even though this payload only ever travels to the client, because the channel
 * handshake between a modded server and a modded client checks both halves.
 * <p>
 * The handler references client-only code, which is safe here: a play-to-client handler can only
 * ever run inside the client process, so on a dedicated server the referenced class is never
 * loaded.
 */
@EventBusSubscriber(modid = SOAP.MODID)
public final class SOAPPayloads {

    @SubscribeEvent
    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(
                        SOAPVisualEffectsPayload.TYPE,
                        SOAPVisualEffectsPayload.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(
                                () -> SOAPClientVisualEvents.setEffectsActive(payload.enabled())))
                .playToClient(
                        SOAPMusicControlPayload.TYPE,
                        SOAPMusicControlPayload.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(
                                () -> SOAPClientMusicEvents.apply(payload)));
    }

    private SOAPPayloads() {
    }
}
