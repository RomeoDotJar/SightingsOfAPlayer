package net.romeo.sightingsofaplayer.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.romeo.sightingsofaplayer.SOAP;

/**
 * Turns the client-side visual effect suite of a soap_event on or off.
 * <p>
 * Everything the effect does to the screen is drawn by the client, so the server only has to say
 * "start" when the event runs and "stop" when its time is up - a single boolean travelling in one
 * direction, with no state kept on the server beyond the queued stop.
 */
public record SOAPVisualEffectsPayload(boolean enabled) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SOAPVisualEffectsPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(SOAP.MODID, "visual_effects"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SOAPVisualEffectsPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeBoolean(payload.enabled()),
            buffer -> new SOAPVisualEffectsPayload(buffer.readBoolean()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
