package net.romeo.sightingsofaplayer.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.romeo.sightingsofaplayer.SOAP;

import java.util.function.Supplier;

/**
 * Remote control for the soundtrack a soap_event starts on a client.
 * <p>
 * Same one-way pattern as {@link SOAPVisualEffectsPayload}: the server only ever says what should
 * happen ("start pigstep at this volume and speed", "pause it", "volume is now 0.5") and the client
 * works out how to do it against its own audio engine. All state - the running track, whether it is
 * paused, the current values - lives client-side; the server keeps nothing beyond the audience it
 * sent the commands to.
 * <p>
 * The two float fields carry different things per action: {@link Action#START} uses both, the
 * setters use the one that matches them, the rest ignore both.
 */
public record SOAPMusicControlPayload(Action action, float volume, float speed, String id) implements CustomPacketPayload {

    public SOAPMusicControlPayload(Action action, float volume, float speed, Supplier<SoundEvent> sound) {
        this(action,volume,speed,sound.get().getLocation().getPath());
    }

    /** What the client should do to the event soundtrack. */
    public enum Action {
        /** Discard any previous event soundtrack and begin a new one. */
        START,
        /** Freeze playback where it is; the playback position is kept. */
        PAUSE,
        /** Continue a soundtrack frozen by {@link #PAUSE}. */
        RESUME,
        /** End playback immediately. */
        STOP,
        /** Change loudness, {@code 0.0} (silent) .. {@code 1.0} (full). */
        SET_VOLUME,
        /** Change playback speed, {@code 0.5} (half speed) .. {@code 2.0} (double speed). */
        SET_SPEED,

        /** Fade loudness into the first value over the course of the second value amount of seconds. */
        FADE_VOLUME,

        /** Fade loudness into the first value over the course of the second value amount of seconds, and then stop playback. */
        FADE_OUT
    }

    public static final CustomPacketPayload.Type<SOAPMusicControlPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(SOAP.MODID, "music_control"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SOAPMusicControlPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeByte(payload.action().ordinal());
                buffer.writeFloat(payload.volume());
                buffer.writeFloat(payload.speed());
                buffer.writeUtf(payload.id());
            },
            buffer -> new SOAPMusicControlPayload(
                    Action.values()[buffer.readByte()], buffer.readFloat(), buffer.readFloat(), buffer.readUtf()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
